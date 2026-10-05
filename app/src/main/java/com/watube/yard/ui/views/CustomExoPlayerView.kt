package com.watube.yard.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import com.watube.yard.extensions.bundleOf
import androidx.core.os.postDelayed
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.marginStart
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.PlayerView.ControllerVisibilityListener
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.TimeBar
import com.watube.yard.R
import com.watube.yard.constants.IntentData
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.databinding.CustomExoPlayerViewTemplateBinding
import com.watube.yard.databinding.DoubleTapOverlayBinding
import com.watube.yard.databinding.ExoStyledPlayerControlViewBinding
import com.watube.yard.databinding.PlayerGestureControlsViewBinding
import com.watube.yard.enums.PlayerCommand
import com.watube.yard.extensions.dpToPx
import com.watube.yard.extensions.navigateVideo
import com.watube.yard.extensions.normalize
import com.watube.yard.extensions.round
import com.watube.yard.extensions.seekBy
import com.watube.yard.extensions.toID
import com.watube.yard.extensions.togglePlayPauseState
import com.watube.yard.extensions.updateIfChanged
import com.watube.yard.helpers.AudioHelper
import com.watube.yard.helpers.BrightnessHelper
import com.watube.yard.helpers.PlayerHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.helpers.WindowHelper
import com.watube.yard.obj.BottomSheetItem
import com.watube.yard.obj.VideoResolution
import com.watube.yard.services.AbstractPlayerService
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.controllers.FullscreenGestureAnimationController
import com.watube.yard.ui.dialogs.SubmitDeArrowDialog
import com.watube.yard.ui.dialogs.SubmitSegmentDialog
import com.watube.yard.ui.extensions.preferLowFrameRate
import com.watube.yard.ui.extensions.toggleSystemBars
import com.watube.yard.ui.interfaces.CustomPlayerCallback
import com.watube.yard.ui.interfaces.PlayerGestureOptions
import com.watube.yard.ui.interfaces.PlayerOptions
import com.watube.yard.ui.listeners.PlayerGestureController
import com.watube.yard.ui.models.ChaptersViewModel
import com.watube.yard.ui.models.CommonPlayerViewModel
import com.watube.yard.ui.models.PlayerViewModel
import com.watube.yard.ui.sheets.BaseBottomSheet
import com.watube.yard.ui.sheets.ChaptersBottomSheet
import com.watube.yard.ui.sheets.PlaybackOptionsSheet
import com.watube.yard.ui.sheets.PlayingQueueSheet
import com.watube.yard.ui.sheets.StatsSheet
import com.watube.yard.ui.sheets.TranslationSheet
import com.watube.yard.util.PlayingQueue
import java.util.Locale
import kotlin.math.ceil

@SuppressLint("ClickableViewAccessibility")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CustomExoPlayerView(
    context: Context,
    attributeSet: AttributeSet? = null
) : PlayerView(context, attributeSet), PlayerOptions, PlayerGestureOptions {
    @Suppress("LeakingThis")
    val binding = ExoStyledPlayerControlViewBinding.bind(this)
    val backgroundBinding = CustomExoPlayerViewTemplateBinding.bind(this)

    /**
     * Objects for player tap and swipe gesture
     */
    private val gestureViewBinding: PlayerGestureControlsViewBinding get() = backgroundBinding.playerGestureControlsView.binding
    private val doubleTapOverlayBinding: DoubleTapOverlayBinding get() = backgroundBinding.doubleTapOverlay.binding

    private var playerGestureController: PlayerGestureController
    private var brightnessHelper: BrightnessHelper
    private var audioHelper: AudioHelper
    private lateinit var chaptersViewModel: ChaptersViewModel
    private lateinit var seekBarListener: TimeBar.OnScrubListener
    private var fullscreenGestureAnimationController: FullscreenGestureAnimationController
    private var chaptersBottomSheet: ChaptersBottomSheet? = null
    private var scrubbingTimeBar = false

    /** last values shown in the position labels, used to skip redundant text layouts */
    private var lastPositionText: String? = null
    private var lastTimeLeftText: String? = null

    /**
     * Objects from the parent fragment
     */

    private val runnableHandler = Handler(Looper.getMainLooper())
    private var isPlayerLocked: Boolean = false

    private var resizeModePref: Int
        set(value) {
            PreferenceHelper.putInt(
                PreferenceKeys.PLAYER_RESIZE_MODE,
                value
            )
        }
        get() = PreferenceHelper.getInt(
            PreferenceKeys.PLAYER_RESIZE_MODE,
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        )
    private val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to R.string.resize_mode_fit,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to R.string.resize_mode_zoom,
        AspectRatioFrameLayout.RESIZE_MODE_FILL to R.string.resize_mode_fill
    )

    private val activity get() = context as BaseActivity

    private val supportFragmentManager
        get() = activity.supportFragmentManager

    /**
     * Playback speed that has been set before the fast forward mode
     * has been triggered by a long press.
     */
    private var rememberedPlaybackSpeed: Float? = null

    /** Whether the controls, hence the position labels, are on screen. */
    private var controlsShown = false

    private fun toggleController(show: Boolean = !isControllerFullyVisible) {
        if (show) showController() else hideController()
    }

    private var playerViewModel: PlayerViewModel? = null
    private var commonPlayerViewModel: CommonPlayerViewModel? = null
    private var viewLifecycleOwner: LifecycleOwner? = null

    private val handler = Handler(Looper.getMainLooper())

    /**
     * The window that needs to be addressed for showing and hiding the system bars
     * If null, the activity's default/main window will be used
     */
    var currentWindow: Window? = null

    private var selectedResolution: Int? = null
    var sponsorBlockAutoSkip = true
        private set

    private var selectedAudioLanguageAndRoleFlags: Pair<String?, @C.RoleFlags Int>? = null
    private lateinit var playerCallback: CustomPlayerCallback


    // if null, it's been set to automatic
    private var fullscreenResolution: Int? = null

    // the resolution to use when the video is not played in fullscreen
    // if null, use same quality as fullscreen
    private var noFullscreenResolution: Int? = null

    init {
        brightnessHelper = BrightnessHelper(activity)
        playerGestureController = PlayerGestureController(activity, this)
        audioHelper = AudioHelper(context)
        fullscreenGestureAnimationController = FullscreenGestureAnimationController(
            playerView = this,
            videoFrameView = backgroundBinding.exoContentFrame,
            onSwipeUpCompleted = {
                if (!isFullscreen()) playerCallback.toggleFullscreen()
            },
            onSwipeDownCompleted = {
                if (isFullscreen()) playerCallback.toggleFullscreen()
            }
        )
        // a placeholder until the first video size: the opening transition must not resize it
        fixSurfaceSize(DEFAULT_SURFACE_WIDTH, DEFAULT_SURFACE_HEIGHT)

        setControllerVisibilityListener(ControllerVisibilityListener { visibility ->
            controlsShown = visibility == VISIBLE
            updateCurrentPosition()
        })
    }

    /**
     * Gives the video SurfaceView a fixed buffer size instead of the layout size. Every resize
     * of the view otherwise re-sends the buffer geometry, and Android 15 can apply the geometry
     * of a resize made during a draw pass before the previous one (MotionLayout lays the player
     * out while drawing; the 10 ms mini -> full player transition on a 120/144 Hz screen): the
     * video then stayed shrunk in the top left corner of the player. A fixed size is only
     * scaled, frame by frame, in sync with the view.
     */
    private fun fixSurfaceSize(width: Int, height: Int) {
        if (width > 0 && height > 0) (videoSurfaceView as? SurfaceView)?.holder?.setFixedSize(width, height)
    }

    fun initialize(
        chaptersViewModel: ChaptersViewModel,
        commonPlayerViewModel: CommonPlayerViewModel,
        playerViewModel: PlayerViewModel,
        viewLifecycleOwner: LifecycleOwner,
        playerCallback: CustomPlayerCallback,
        player: Player,
    ) {
        this.chaptersViewModel = chaptersViewModel
        this.playerViewModel = playerViewModel
        this.commonPlayerViewModel = commonPlayerViewModel
        this.viewLifecycleOwner = viewLifecycleOwner
        this.playerCallback = playerCallback
        super.player = player
        fixSurfaceSize(player.videoSize.width, player.videoSize.height)

        playerGestureController.observeFullscreen(viewLifecycleOwner)

        initializeGestureProgress()

        initRewindAndForward()
        applyCaptionsStyle()
        initializeAdvancedOptions()

        // don't let the player view hide its controls automatically
        controllerShowTimeoutMs = -1
        // don't let the player view show its controls automatically
        controllerAutoShow = false

        binding.fullscreen.setOnClickListener { playerCallback.toggleFullscreen() }

        resizeMode = resizeModePref

        // prevent the controls from disappearing while scrubbing the time bar
        if (!::seekBarListener.isInitialized) {
            seekBarListener = object : TimeBar.OnScrubListener {
                override fun onScrubStart(timeBar: TimeBar, position: Long) {
                    cancelHideControllerTask()
                }

                override fun onScrubMove(timeBar: TimeBar, position: Long) {
                    cancelHideControllerTask()

                    setCurrentChapterName(forceUpdate = true, enqueueNew = false)
                    scrubbingTimeBar = true
                }

                override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                    enqueueHideControllerTask()

                    setCurrentChapterName(forceUpdate = true, enqueueNew = false)
                    scrubbingTimeBar = false
                }
            }
            binding.exoProgress.addSeekBarListener(seekBarListener)
        }

        binding.autoPlay.isChecked = PlayerHelper.autoPlayEnabled

        binding.autoPlay.setOnCheckedChangeListener { _, isChecked ->
            PlayerHelper.autoPlayEnabled = isChecked
        }

        // redrawn every 100 ms while playing: no need for the screen's top refresh rate
        listOf(binding.exoProgress, binding.position, binding.timeLeft, binding.duration)
            .forEach { it.preferLowFrameRate() }

        // restore the duration type from the previous session
        updateDisplayedDurationType()

        binding.duration.setOnClickListener {
            updateDisplayedDurationType(true)
        }
        binding.timeLeft.setOnClickListener {
            updateDisplayedDurationType(false)
        }
        binding.position.setOnClickListener {
            if (playerCallback.isVideoLive()) player.let { it.seekTo(it.duration) }
        }

        activity.supportFragmentManager.setFragmentResultListener(
            ChaptersBottomSheet.SEEK_TO_POSITION_REQUEST_KEY,
            findViewTreeLifecycleOwner() ?: activity
        ) { _, bundle ->
            player.seekTo(bundle.getLong(IntentData.currentPosition))
        }

        // enable the chapters dialog in the player
        binding.chapterName.setOnClickListener {
            val sheet = chaptersBottomSheet ?: ChaptersBottomSheet()
                .apply {
                    arguments = bundleOf(
                        IntentData.duration to player.duration.div(1000)
                    )
                }
                .also {
                    chaptersBottomSheet = it
                }

            if (sheet.isVisible) {
                sheet.dismiss()
            } else {
                sheet.show(activity.supportFragmentManager)
            }
        }

        // the floating speed and quality chips reuse the exact pickers the portrait
        // controls open, so a selection always applies to the running playback
        binding.watubeSpeedChip.setOnClickListener { onPlaybackSpeedClicked() }
        binding.watubeQualityChip.setOnClickListener { onQualityClicked() }

        supportFragmentManager.setFragmentResultListener(
            PlayingQueueSheet.PLAYING_QUEUE_REQUEST_KEY,
            findViewTreeLifecycleOwner() ?: activity
        ) { _, args ->
            (player as? MediaController)?.navigateVideo(
                args.getString(IntentData.videoId) ?: return@setFragmentResultListener
            )
        }
        binding.queueToggle.setOnClickListener {
            PlayingQueueSheet().show(supportFragmentManager, null)
        }

        updateMarginsByFullscreenMode()

        commonPlayerViewModel.isFullscreen.observe(viewLifecycleOwner) { isFullscreen ->
            updateTopBarMargin()

            val fullscreenDrawable =
                if (isFullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen
            binding.fullscreen.setImageResource(fullscreenDrawable)

            binding.exoTitle.isInvisible = !isFullscreen

            updateResolution(isFullscreen)
        }

        val updateSbImageResource = {
            binding.sbToggle.setImageResource(
                if (sponsorBlockAutoSkip) R.drawable.ic_sb_enabled else R.drawable.ic_sb_disabled
            )
        }
        updateSbImageResource()
        binding.sbToggle.setOnClickListener {
            sponsorBlockAutoSkip = !sponsorBlockAutoSkip
            (player as? MediaController)?.sendCustomCommand(
                AbstractPlayerService.runPlayerActionCommand, bundleOf(
                    PlayerCommand.SET_SB_AUTO_SKIP_ENABLED.name to sponsorBlockAutoSkip
                )
            )
            updateSbImageResource()
        }

        syncQueueButtons()

        binding.sbSubmit.isVisible =
            PreferenceHelper.getBoolean(PreferenceKeys.CONTRIBUTE_TO_SB, false)
        binding.sbSubmit.setOnClickListener {
            val submitSegmentDialog = SubmitSegmentDialog()
            submitSegmentDialog.arguments = buildSbBundleArgs() ?: return@setOnClickListener
            submitSegmentDialog.show((context as BaseActivity).supportFragmentManager, null)
        }

        binding.dearrowSubmit.isVisible =
            PreferenceHelper.getBoolean(PreferenceKeys.CONTRIBUTE_TO_DEARROW, false)
        binding.dearrowSubmit.setOnClickListener {
            val submitDialog = SubmitDeArrowDialog()
            submitDialog.arguments = buildSbBundleArgs() ?: return@setOnClickListener
            submitDialog.show((context as BaseActivity).supportFragmentManager, null)
        }

        binding.playPauseBTN.setOnClickListener {
            player.togglePlayPauseState()
        }

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                super.onEvents(player, events)
                this@CustomExoPlayerView.onPlaybackEvents(player, events)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) =
                fixSurfaceSize(videoSize.width, videoSize.height)

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) = updateCurrentPosition()

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                keepScreenOn = isPlaying

                // the labels are polled less frequently while paused, refresh them directly
                updateCurrentPosition()
                setCurrentChapterName(forceUpdate = true, enqueueNew = false)
            }
        })

        binding.playPauseBTN.setImageResource(
            PlayerHelper.getPlayPauseActionIcon(player)
        )

        binding.exoProgress.setPlayer(player)

        if (player.isPlaying) keepScreenOn = true

        // locking the player
        binding.lockPlayer.setOnClickListener {
            // change the locked/unlocked icon
            val icon = if (!isPlayerLocked) R.drawable.ic_locked else R.drawable.ic_unlocked
            val tooltip = if (!isPlayerLocked) {
                R.string.tooltip_unlocked
            } else {
                R.string.tooltip_locked
            }

            binding.lockPlayer.setImageResource(icon)
            TooltipCompat.setTooltipText(binding.lockPlayer, context.getString(tooltip))

            // show/hide all the controls
            lockPlayer(isPlayerLocked)

            // change locked status
            isPlayerLocked = !isPlayerLocked

            if (isFullscreen()) toggleSystemBars(!isPlayerLocked)
        }

        updateCurrentPosition()
    }

    /**
     * @see CustomExoPlayerView.initialize
     * @see CustomExoPlayerView.detachPlayer
     */
    @Deprecated("Use `initialize()` instead to attach `Player` and use `detachPlayer()` to detach it")
    override fun setPlayer(player: Player?) {
        super.setPlayer(player)
    }

    fun detachPlayer(){
        super.setPlayer(null)

        // stop the polling loops tied to the player, otherwise they keep this view alive
        runnableHandler.removeCallbacksAndMessages(UPDATE_POSITION_TOKEN)
        handler.removeCallbacksAndMessages(null)
        removeCallbacks(chapterNameUpdater)
    }

    private val queueButtonsSync = Runnable { syncQueueButtons() }

    private fun syncQueueButtons() {
        // one loop only, initialize() can run again for the same view
        handler.removeCallbacks(queueButtonsSync)
        if (player == null) {
            // without a player the buttons are unusable, stop the polling loop
            handler.removeCallbacksAndMessages(null)
            return
        }

        // toggle the visibility of next and prev buttons based on queue and whether the player view is locked
        setQueueButtonState(binding.skipPrev, !PlayingQueue.hasPrev() || isPlayerLocked)
        setQueueButtonState(binding.skipNext, !PlayingQueue.hasNext() || isPlayerLocked)

        // battery: la visibilité des boutons précédent/suivant n'est pas critique en
        // temps réel — 250 ms au lieu de 100 ms divise par ~2,5 les réveils de cette
        // boucle qui tourne en continu tant qu'un lecteur est attaché
        handler.postDelayed(queueButtonsSync, 250)
    }

    private fun setQueueButtonState(button: View, isInvisible: Boolean) {
        if (button.isVisible == isInvisible) return
        button.isInvisible = isInvisible
    }

    /**
     * Update the displayed duration of the video
     */
    private fun updateDisplayedDuration() {
        if (playerCallback.isVideoLive()) return

        val duration = player?.duration?.div(1000) ?: return
        if (duration < 0) return

        val durationWithoutSegments = duration - playerViewModel?.segments?.value.orEmpty().sumOf {
            val (start, end) = it.segmentStartAndEnd
            end.toDouble() - start.toDouble()
        }.toLong()
        val durationString = DateUtils.formatElapsedTime(duration)

        binding.duration.text = if (durationWithoutSegments < duration) {
            "$durationString (${DateUtils.formatElapsedTime(durationWithoutSegments)})"
        } else {
            durationString
        }
    }

    private fun buildSbBundleArgs(): Bundle? {
        val currentPosition = player?.currentPosition?.takeIf { it != C.TIME_UNSET } ?: 0
        val duration = player?.duration?.takeIf { it != C.TIME_UNSET }
        val videoId = PlayingQueue.getCurrent()?.url?.toID() ?: return null

        return bundleOf(
            IntentData.currentPosition to currentPosition,
            IntentData.duration to duration,
            IntentData.videoId to videoId
        )
    }

    private val chapterNameUpdater = Runnable { setCurrentChapterName() }

    /**
     * Set the name of the video chapter in the [CustomExoPlayerView]
     * @param forceUpdate Update the current chapter name no matter if the seek bar is scrubbed
     * @param enqueueNew set a timeout to automatically repeat this function again in 100ms
     */
    fun setCurrentChapterName(forceUpdate: Boolean = false, enqueueNew: Boolean = true) {
        val player = player ?: return
        val chapters = chaptersViewModel.chapters

        binding.chapterName.isInvisible = chapters.isEmpty()

        // the following logic to set the chapter title can be skipped if no chapters are available
        if (chapters.isEmpty()) return

        // call the function again soon, the delay only needs to be short while actually
        // playing since the chapter changes together with the playback position
        if (enqueueNew) {
            // a single chain: every chapters update calls this again, which used to stack loops
            removeCallbacks(chapterNameUpdater)
            postDelayed(chapterNameUpdater, if (player.isPlaying) 100L else 1000L)
        }

        // if the user is scrubbing the time bar, don't update
        if (scrubbingTimeBar && !forceUpdate) return

        val currentIndex = PlayerHelper.getCurrentChapterIndex(player.currentPosition, chapters)
        val newChapterName = currentIndex?.let { chapters[it].title.trim() }.orEmpty()

        chaptersViewModel.currentChapterIndex.updateIfChanged(currentIndex ?: -1)

        // change the chapter name textView text to the chapterName
        if (newChapterName != binding.chapterName.text) {
            binding.chapterName.text = newChapterName
        }
    }

    fun toggleSystemBars(showBars: Boolean) {
        getWindow().toggleSystemBars(
            types = if (showBars) {
                WindowHelper.getGestureControlledBars(context)
            } else {
                WindowInsetsCompat.Type.systemBars()
            },
            showBars = showBars
        )
    }

    private fun updateDisplayedDurationType(showTimeLeft: Boolean? = null) {
        var shouldShowTimeLeft = showTimeLeft ?: PreferenceHelper
            .getBoolean(PreferenceKeys.SHOW_TIME_LEFT, false)
        // always show the time left only if it's a livestream
        if (playerCallback.isVideoLive()) shouldShowTimeLeft = true
        if (showTimeLeft != null) {
            // save whether to show time left or duration for next session
            PreferenceHelper.putBoolean(PreferenceKeys.SHOW_TIME_LEFT, shouldShowTimeLeft)
        }
        binding.timeLeft.isVisible = shouldShowTimeLeft
        binding.duration.isGone = shouldShowTimeLeft
    }

    private fun enqueueHideControllerTask() {
        runnableHandler.postDelayed(AUTO_HIDE_CONTROLLER_DELAY, HIDE_CONTROLLER_TOKEN) {
            hideController()
        }
    }

    private fun cancelHideControllerTask() {
        runnableHandler.removeCallbacksAndMessages(HIDE_CONTROLLER_TOKEN)
    }

    override fun hideController() {
        // remove the callback to hide the controller
        cancelHideControllerTask()
        super.hideController()
        backgroundBinding.exoControlsBackground.animate()
            .alpha(0f)
            .setDuration(500)
            .start()

        if (isFullscreen()) {
            toggleSystemBars(false)
        }
    }

    override fun showController() {
        // remove the previous callback from the queue to prevent a flashing behavior
        cancelHideControllerTask()
        // automatically hide the controller after 2 seconds
        enqueueHideControllerTask()
        super.showController()
        backgroundBinding.exoControlsBackground.animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        if (isFullscreen() && !isPlayerLocked) {
            toggleSystemBars(true)
        }
    }

    fun showControllerPermanently() {
        // remove the previous callback from the queue to prevent a flashing behavior
        cancelHideControllerTask()
        super.showController()
    }

    private fun initRewindAndForward() {
        val seekIncrementText = (PlayerHelper.seekIncrement / 1000).toString()
        listOf(
            doubleTapOverlayBinding.rewindLayout.rewindTV,
            doubleTapOverlayBinding.forwardLayout.forwardTV
        ).forEach {
            it.text = seekIncrementText
        }
    }

    private fun initializeAdvancedOptions() {
        binding.toggleOptions.setOnClickListener {
            val items = getOptionsMenuItems()
            val bottomSheetFragment = BaseBottomSheet().setItems(items, null)
            bottomSheetFragment.show(supportFragmentManager, null)
        }
    }

    fun getOptionsMenuItems(): List<BottomSheetItem> = listOfNotNull(
        BottomSheetItem(
            context.getString(R.string.repeat_mode),
            R.drawable.ic_repeat,
            {
                when (PlayingQueue.repeatMode) {
                    Player.REPEAT_MODE_OFF -> context.getString(R.string.repeat_mode_none)
                    Player.REPEAT_MODE_ONE -> context.getString(R.string.repeat_mode_current)
                    Player.REPEAT_MODE_ALL -> context.getString(R.string.repeat_mode_all)
                    else -> throw IllegalArgumentException()
                }
            }
        ) {
            onRepeatModeClicked()
        },
        BottomSheetItem(
            context.getString(R.string.player_resize_mode),
            R.drawable.ic_aspect_ratio,
            {
                resizeModes.find { it.first == resizeMode }?.second?.let {
                    context.getString(it)
                }
            }
        ) {
            onResizeModeClicked()
        },
        BottomSheetItem(
            context.getString(R.string.playback_speed),
            R.drawable.ic_speed,
            {
                "${player?.playbackParameters?.speed?.round(2)}x"
            }
        ) {
            onPlaybackSpeedClicked()
        },
        BottomSheetItem(
            context.getString(R.string.quality),
            R.drawable.ic_hd,
            this::getCurrentResolutionSummary
        ) {
            onQualityClicked()
        },
        // audio channel and subtitles share one entry, and one sheet
        BottomSheetItem(
            context.getString(R.string.translation),
            R.drawable.ic_translate,
            {
                val captions = player?.let { PlayerHelper.getCurrentPlayedCaptionFormat(it)?.language }
                    ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()) }
                    ?: context.getString(R.string.none)
                listOf(getCurrentAudioTrackTitle(), captions)
                    .joinToString(" · ") { it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }
            }
        ) {
            onTranslationClicked()
        },
        // hidden unless enabled in the player settings
        BottomSheetItem(
            context.getString(R.string.stats_for_nerds),
            R.drawable.ic_info
        ) {
            onStatsClicked()
        }.takeIf { PlayerHelper.statsForNerdsEnabled }
    )

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun getCurrentResolutionSummary(): String {
        val currentHeight = player?.videoSize?.height ?: 0
        if (currentHeight <= 0) return context.getString(R.string.auto)
        // same standard tiers as the quality menu, so 1012p reads 1080p in both places
        val currentTier = VideoResolution.snapToStandardHeight(currentHeight)
        val selected = selectedResolution
        return when {
            // "Auto" is stored as Int.MAX_VALUE: it must not read as a limited resolution
            selected == null || selected == Int.MAX_VALUE ->
                "${currentTier}p - ${context.getString(R.string.auto)}"
            VideoResolution.snapToStandardHeight(selected) > currentTier ->
                "${currentTier}p - ${context.getString(R.string.resolution_limited)}"
            else -> "${currentTier}p"
        }
    }

    private fun getCurrentAudioTrackTitle(): String {
        if (player == null) {
            return context.getString(R.string.unknown_or_no_audio)
        }

        // The player reference should be not changed between the null check
        // and its access, so a non-null assertion should be safe here
        val selectedAudioLanguagesAndRoleFlags =
            PlayerHelper.getAudioLanguagesAndRoleFlagsFromTrackGroups(
                player!!.currentTracks.groups,
                true
            )

        if (selectedAudioLanguagesAndRoleFlags.isEmpty()) {
            return context.getString(R.string.unknown_or_no_audio)
        }

        // At most one audio track should be selected regardless of audio
        // format or quality
        val firstSelectedAudioFormat = selectedAudioLanguagesAndRoleFlags[0]

        if (selectedAudioLanguagesAndRoleFlags.size == 1 &&
            firstSelectedAudioFormat.first == null &&
            !PlayerHelper.haveAudioTrackRoleFlagSet(
                firstSelectedAudioFormat.second
            )
        ) {
            // Regardless of audio format or quality, if there is only one
            // audio stream which has no language and no role flags, it
            // should mean that there is only a single audio track which
            // has no language or track type set in the video played
            // Consider it as the default audio track (or unknown)
            return context.getString(R.string.default_or_unknown_audio_track)
        }


        return PlayerHelper.getAudioTrackNameFromFormat(
            context,
            firstSelectedAudioFormat,
        )
    }


    // lock the player
    private fun lockPlayer(isLocked: Boolean) {
        // isLocked is the current (old) state of the player lock
        binding.exoTopBarRight.isVisible = isLocked
        binding.exoCenterControls.isVisible = isLocked
        binding.bottomBar.isVisible = isLocked
        binding.closeImageButton.isVisible = isLocked
        binding.exoTitle.isVisible = isLocked
        binding.playPauseBTN.isVisible = isLocked

        // hide the dimming background overlay if locked
        backgroundBinding.exoControlsBackground.setBackgroundColor(
            if (isLocked) {
                ContextCompat.getColor(
                    context,
                    R.color.player_scrim_60
                )
            } else {
                Color.TRANSPARENT
            }
        )

        // disable tap and swipe gesture if the player is locked
        playerGestureController.areControlsLocked = !isLocked
    }

    private fun rewind() {
        player?.seekBy(-PlayerHelper.seekIncrement)

        // show the rewind button
        doubleTapOverlayBinding.apply {
            animateSeeking(
                rewindLayout.rewindBTN,
                rewindLayout.rewindIV,
                rewindLayout.rewindTV,
                true
            )

            // start callback to hide the button
            runnableHandler.removeCallbacksAndMessages(HIDE_REWIND_BUTTON_TOKEN)
            runnableHandler.postDelayed(700, HIDE_REWIND_BUTTON_TOKEN) {
                rewindLayout.rewindBTN.isGone = true
            }
        }
    }

    private fun forward() {
        player?.seekBy(PlayerHelper.seekIncrement)

        // show the forward button
        doubleTapOverlayBinding.apply {
            animateSeeking(
                forwardLayout.forwardBTN,
                forwardLayout.forwardIV,
                forwardLayout.forwardTV,
                false
            )

            // start callback to hide the button
            runnableHandler.removeCallbacksAndMessages(HIDE_FORWARD_BUTTON_TOKEN)
            runnableHandler.postDelayed(700, HIDE_FORWARD_BUTTON_TOKEN) {
                forwardLayout.forwardBTN.isGone = true
            }
        }
    }

    private fun animateSeeking(
        container: FrameLayout,
        imageView: ImageView,
        textView: TextView,
        isRewind: Boolean
    ) {
        container.isVisible = true
        // the direction of the action
        val direction = if (isRewind) -1 else 1

        // clear previous animation
        imageView.animate()
            .rotation(0F)
            .setDuration(0)
            .start()

        textView.animate()
            .translationX(0f)
            .setDuration(0)
            .start()

        // start the rotate animation of the drawable
        imageView.animate()
            .rotation(direction * 30F)
            .setDuration(ANIMATION_DURATION)
            .withEndAction {
                // reset the animation when finished
                imageView.animate()
                    .rotation(0F)
                    .setDuration(ANIMATION_DURATION)
                    .start()
            }
            .start()

        // animate the text view to move outside the image view
        textView.animate()
            .translationX(direction * 100f)
            .setDuration((ANIMATION_DURATION * 1.5).toLong())
            .withEndAction {
                // move the text back into the button
                runnableHandler.postDelayed(100) {
                    textView.animate()
                        .setDuration(ANIMATION_DURATION / 2)
                        .translationX(0f)
                        .start()
                }
            }
    }

    private fun initializeGestureProgress() {
        gestureViewBinding.brightnessProgressBar.let { bar ->
            bar.progress = (brightnessHelper.savedWindowBrightness * bar.max).toInt().coerceIn(0, bar.max)
        }
        gestureViewBinding.volumeProgressBar.let { bar ->
            bar.progress = (audioHelper.deviceVolume * bar.max).toInt().coerceIn(0, bar.max)
        }
    }

    private fun updateBrightness(distance: Float) {
        gestureViewBinding.brightnessControlView.isVisible = true
        val bar = gestureViewBinding.brightnessProgressBar

        if (bar.progress == 0) {
            // If brightness progress goes to below 0, set to system brightness
            if (distance <= 0) {
                brightnessHelper.resetToSystemBrightness()
                gestureViewBinding.brightnessImageView.setImageResource(
                    R.drawable.ic_brightness_auto
                )
                gestureViewBinding.brightnessTextView.text = resources.getString(R.string.auto)
                return
            }
            gestureViewBinding.brightnessImageView.setImageResource(R.drawable.ic_brightness)
        }

        bar.incrementProgressBy(distance.toInt())
        gestureViewBinding.brightnessTextView.text = "${bar.progress.normalize(0, bar.max, 0, 100)}"
        brightnessHelper.windowBrightness = bar.progress.toFloat() / bar.max
    }

    private fun updateVolume(distance: Float) {
        val bar = gestureViewBinding.volumeProgressBar
        gestureViewBinding.volumeControlView.apply {
            if (isGone) {
                isVisible = true
                // Volume could be changed using other mediums, sync progress
                // bar with new value.
                bar.progress = (audioHelper.deviceVolume * bar.max).toInt().coerceIn(0, bar.max)
            }
        }

        if (bar.progress == 0) {
            gestureViewBinding.volumeImageView.setImageResource(
                when {
                    distance > 0 -> R.drawable.ic_volume_up
                    else -> R.drawable.ic_volume_off
                }
            )
        }
        bar.incrementProgressBy(distance.toInt())
        audioHelper.deviceVolume = bar.progress.toFloat() / bar.max

        gestureViewBinding.volumeTextView.text = "${bar.progress.normalize(0, bar.max, 0, 100)}"
    }

    override fun onPlaybackSpeedClicked() {
        (player as? MediaController)?.let {
            PlaybackOptionsSheet(it).show(supportFragmentManager)
        }
    }

    override fun onResizeModeClicked() {
        // switching between original aspect ratio (black bars) and zoomed to fill device screen
        BaseBottomSheet()
            .setSimpleItems(
                resizeModes.map { context.getString(it.second) },
                preselectedItem = resizeModes.firstOrNull { it.first == resizeMode }
                    ?.let { context.getString(it.second) }
            ) { index ->
                resizeMode = resizeModes[index].first
            }
            .show(supportFragmentManager)
    }

    override fun setResizeMode(resizeMode: Int) {
        super.setResizeMode(resizeMode)
        // automatically remember the resize mode for the next session
        resizeModePref = resizeMode
    }

    override fun onRepeatModeClicked() {
        // repeat mode options dialog
        BaseBottomSheet()
            .setSimpleItems(
                PlayerHelper.repeatModes.map { context.getString(it.second) },
                preselectedItem = PlayerHelper.repeatModes
                    .firstOrNull { it.first == PlayingQueue.repeatMode }
                    ?.second?.let {
                        context.getString(it)
                    }
            ) { index ->
                PlayingQueue.repeatMode = PlayerHelper.repeatModes[index].first
            }
            .show(supportFragmentManager)
    }

    override fun onTranslationClicked() {
        val player = player as? MediaController ?: return
        TranslationSheet()
            .setChoices(audioChoices(player), captionChoices(player))
            .show(supportFragmentManager)
    }

    private fun captionChoices(player: Player): List<TranslationSheet.Choice> {
        val currentSubtitle = PlayerHelper.getCurrentPlayedCaptionFormat(player)
        val selectCaption = { format: Format? ->
            updateCurrentSubtitle(format?.id)
            playerViewModel?.currentCaptionId = format?.id
        }
        val none = TranslationSheet.Choice(
            context.getString(R.string.none), currentSubtitle == null
        ) { selectCaption(null) }

        return listOf(none) + PlayerHelper.getCaptionTracks(player)
            // put normal tracks before auto-generated tracks
            .sortedBy { it.roleFlags == PlayerHelper.ROLE_FLAG_AUTO_GEN_SUBTITLE }
            .map { format ->
                val displayName = Locale.forLanguageTag(format.language.orEmpty())
                    .getDisplayLanguage(Locale.getDefault())
                val title = if (format.roleFlags == PlayerHelper.ROLE_FLAG_AUTO_GEN_SUBTITLE) {
                    "$displayName (${context.getString(R.string.auto_generated)})"
                } else {
                    displayName
                }
                TranslationSheet.Choice(title, format == currentSubtitle) { selectCaption(format) }
            }
    }

    fun updateCurrentSubtitle(trackId: String?) {
        val player = player as? MediaController ?: return

        player.sendCustomCommand(
            AbstractPlayerService.runPlayerActionCommand, bundleOf(
                PlayerCommand.SET_CAPTION_TRACK.name to trackId
            )
        )
    }

    /**
     * Get all available player resolutions.
     *
     * Les hauteurs renvoyées par les pistes ne sont pas toujours « rondes » (une vidéo qui
     * n'est pas exactement en 16:9 peut donner 2026, 1012, …). On ramène chaque hauteur au
     * palier de qualité YouTube standard le plus proche et on dédoublonne par palier, afin
     * d'afficher une échelle correcte (2160p 4K / 1440p HD / … / 144p) au lieu de valeurs
     * incohérentes comme « 2026p ». Seules les pistes réellement présentes dans le flux sont
     * listées : si la source plafonne à 720p, rien au-dessus n'apparaît.
     */
    private fun getAvailableResolutions(): List<VideoResolution> {
        val player = player ?: return emptyList()

        val resolutions = player.currentTracks.groups.asSequence()
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                (0 until group.length).map { group.getTrackFormat(it).height }
            }
            .filter { it > 0 }
            .distinct()
            .map(VideoResolution::fromHeight)
            // garder la hauteur la plus haute de chaque palier (ex. 1080p avc/vp9/av1, ou une
            // hauteur non standard ramenée au même palier) pour que la sélection inclue la piste
            .sortedByDescending { it.resolution }
            .distinctBy { it.name }
            .toMutableList()

        resolutions.add(0, VideoResolution(context.getString(R.string.auto_quality), Int.MAX_VALUE))
        return resolutions
    }

    override fun onQualityClicked() {
        // get the available resolutions
        val resolutions = getAvailableResolutions()

        // "Auto" (first entry) is preselected when no listed resolution is the selected one
        val selectedIndex = resolutions.indexOfFirst { it.resolution == selectedResolution }
            .coerceAtLeast(0)

        // Dialog for quality selection
        BaseBottomSheet()
            .setItems(
                resolutions.mapIndexed { index, resolution ->
                    BottomSheetItem(
                        resolution.name,
                        isSelected = index == selectedIndex,
                        badge = resolution.badge
                    )
                }
            ) { which ->
                val newResolution = resolutions[which].resolution
                setPlayerResolution(newResolution, true)

                // a manual choice holds in both modes: toggling fullscreen used to bring back
                // the default of the other mode and silently drop the user's selection
                fullscreenResolution = newResolution
                if (noFullscreenResolution != null) noFullscreenResolution = newResolution
            }
            .show(supportFragmentManager)
    }

    fun setToDefaultResolution() {
        fullscreenResolution = PlayerHelper.getDefaultResolution(context, true)
        noFullscreenResolution = PlayerHelper.getDefaultResolution(context, false)
        updateResolution(isFullscreen())
    }

    private fun updateResolution(isFullscreen: Boolean) {
        if (!isFullscreen && noFullscreenResolution != null) {
            setPlayerResolution(noFullscreenResolution!!)
        } else if (fullscreenResolution != null) {
            setPlayerResolution(fullscreenResolution!!)
        } else {
            setPlayerResolution(Int.MAX_VALUE)
        }
    }

    fun setPlayerResolution(resolution: Int, isSelectedByUser: Boolean = false) {
        val player = player as? MediaController ?: return

        val transformedResolution =
            if (!isSelectedByUser && playerCallback.isVideoShort()) {
                ceil(resolution * 16.0 / 9.0).toInt()
            } else {
                resolution
            }

        player.sendCustomCommand(
            AbstractPlayerService.runPlayerActionCommand, bundleOf(
                PlayerCommand.SET_RESOLUTION.name to transformedResolution
            )
        )

        selectedResolution = resolution
    }

    private fun audioChoices(player: MediaController): List<TranslationSheet.Choice> {
        val audioLanguagesAndRoleFlags = PlayerHelper.getAudioLanguagesAndRoleFlagsFromTrackGroups(
            player.currentTracks.groups,
            false
        )

        if (audioLanguagesAndRoleFlags.isEmpty() || (audioLanguagesAndRoleFlags.size == 1 &&
                    audioLanguagesAndRoleFlags[0].first == null &&
                    !PlayerHelper.haveAudioTrackRoleFlagSet(
                        audioLanguagesAndRoleFlags[0].second
                    ))
        ) {
            // Regardless of audio format or quality, if there is only one audio stream which has
            // no language and no role flags, it should mean that there is only a single audio
            // track which has no language or track type set in the video played
            // Consider it as the default audio track (or unknown)
            return listOf(
                TranslationSheet.Choice(context.getString(R.string.default_or_unknown_audio_track), true) {}
            )
        }

        val currentTitle = getCurrentAudioTrackTitle()
        return audioLanguagesAndRoleFlags
            // audio tracks have only a single flag set
            // ordered by main, dubbed, audio descriptive
            .sortedBy { it.second }
            .map { audioFormat ->
                val title = PlayerHelper.getAudioTrackNameFromFormat(context, audioFormat)
                TranslationSheet.Choice(title, title == currentTitle) {
                    player.sendCustomCommand(
                        AbstractPlayerService.runPlayerActionCommand, bundleOf(
                            PlayerCommand.SET_AUDIO_ROLE_FLAGS.name to audioFormat.second
                        )
                    )
                    player.sendCustomCommand(
                        AbstractPlayerService.runPlayerActionCommand, bundleOf(
                            PlayerCommand.SET_AUDIO_LANGUAGE.name to audioFormat.first
                        )
                    )
                    selectedAudioLanguageAndRoleFlags = audioFormat
                }
            }
    }

    override fun onStatsClicked() {
        val player = player ?: return

        val videoStats =
            PlayerHelper.getVideoStats(player.currentTracks, playerCallback.getVideoId())
        StatsSheet()
            .apply { arguments = bundleOf(IntentData.videoStats to videoStats) }
            .show(supportFragmentManager)
    }

    fun isFullscreen() = commonPlayerViewModel?.isFullscreen?.value ?: false

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)

        updateMarginsByFullscreenMode()
    }

    /**
     * Updates the margins according to the current orientation and fullscreen mode
     */
    fun updateMarginsByFullscreenMode() {
        // Anchor the bottom control cluster (seekbar row) to the bottom of the control
        // container: a small fullscreen lift only, read from this view's Resources so the
        // value follows the configuration of the current screen. Converting dp in code went
        // through Resources.getSystem(), which the Android docs describe as "not configured
        // for the current screen (can not use dimension units)" and which can therefore
        // inflate the offset on some devices.
        binding.bottomBar.updateLayoutParams<MarginLayoutParams> {
            bottomMargin = if (isFullscreen()) {
                resources.getDimensionPixelSize(
                    R.dimen.watube_player_controls_fullscreen_bottom_margin
                )
            } else {
                0
            }
        }

        updateTopBarMargin()

        // don't add extra padding if there's no cutout and no margin set that would need to be undone
        if (!activity.hasCutout && binding.topBar.marginStart == LANDSCAPE_MARGIN_HORIZONTAL_NONE) return

        // add a margin to the top and the bottom bar in landscape mode for notches
        val isForcedLandscape =
            activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val isInLandscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val horizontalMargin =
            if (isFullscreen() && (isInLandscape || isForcedLandscape)) LANDSCAPE_MARGIN_HORIZONTAL else LANDSCAPE_MARGIN_HORIZONTAL_NONE

        listOf(binding.topBar, binding.bottomBar).forEach {
            it.updateLayoutParams<MarginLayoutParams> {
                marginStart = horizontalMargin
                marginEnd = horizontalMargin
            }
        }
    }

    /**
     * Load the captions style according to the users preferences
     */
    private fun applyCaptionsStyle() {
        val captionStyle = PlayerHelper.getCaptionStyle(context)
        subtitleView?.apply {
            setApplyEmbeddedFontSizes(false)
            setFixedTextSize(Cue.TEXT_SIZE_TYPE_ABSOLUTE, PlayerHelper.captionsTextSize)
            if (PlayerHelper.useRichCaptionRendering) setViewType(SubtitleView.VIEW_TYPE_WEB)
            if (!PlayerHelper.useSystemCaptionStyle) return
            setApplyEmbeddedStyles(captionStyle == CaptionStyleCompat.DEFAULT)
            setStyle(captionStyle)
        }
    }

    /**
     * Set the current position text (e.g. "10:00 - 17:37"). This does not set the timebar
     * progress, ExoPlayer handles that automatically.
     *
     * The labels are only written when the displayed value actually changes, otherwise each
     * tick would trigger a text layout (10 per second while playing).
     */
    @SuppressLint("SetTextI18n")
    private fun updateCurrentPosition() {
        // one polling chain only: this is also called on every play/pause change, and each
        // call used to start an extra 100 ms loop next to the running one
        runnableHandler.removeCallbacksAndMessages(UPDATE_POSITION_TOKEN)
        // no player attached anymore: the loop stops here, so it can not leak this view
        if (player == null) return

        val position = player?.currentPosition?.div(1000) ?: 0
        val duration = player?.duration?.takeIf { it != C.TIME_UNSET }?.div(1000) ?: 0
        val timeLeft = duration - position

        val positionText = if (playerCallback.isVideoLive()) {
            context.getString(R.string.live)
        } else {
            DateUtils.formatElapsedTime(position)
        }
        val timeLeftText = "-${DateUtils.formatElapsedTime(timeLeft)}"

        if (positionText != lastPositionText) {
            lastPositionText = positionText
            binding.position.text = positionText
        }
        if (timeLeftText != lastTimeLeftText) {
            lastTimeLeftText = timeLeftText
            binding.timeLeft.text = timeLeftText
        }

        // battery: the labels only show with the controls and only move while playing, so the
        // loop stops otherwise (it used to wake the main thread 10 times a second for the whole
        // session, paused or in the background). Play / pause changes, seeks and the controls
        // showing up restart it.
        if (controlsShown && player?.isPlaying == true) {
            runnableHandler.postDelayed(100, UPDATE_POSITION_TOKEN, this::updateCurrentPosition)
        }
    }

    /**
     * Add extra margin to the top bar to not overlap the status bar.
     */
    fun updateTopBarMargin() {
        binding.topBar.updateLayoutParams<MarginLayoutParams> {
            topMargin = (if (isFullscreen()) 18f else 0f).dpToPx()
        }
    }

    override fun onSingleTap(areControlsLocked: Boolean) {
        if (areControlsLocked) {
            // keep showing the 'locked' icon
            toggleController(true)
            return
        }
        toggleController()
    }

    override fun onDoubleTapCenterScreen() {
        player?.togglePlayPauseState()
    }

    override fun onDoubleTapLeftScreen() {
        if (!PlayerHelper.doubleTapToSeek) return
        rewind()
    }

    override fun onDoubleTapRightScreen() {
        if (!PlayerHelper.doubleTapToSeek) return
        forward()
    }

    override fun onSwipeLeftScreen(distanceY: Float, positionY: Float) {
        if (!PlayerHelper.swipeGestureEnabled) {
            if (PlayerHelper.fullscreenGesturesEnabled) onSwipeCenterScreen(distanceY, positionY)
            return
        }

        if (isControllerFullyVisible) hideController()
        updateBrightness(distanceY)
    }

    override fun onSwipeRightScreen(distanceY: Float, positionY: Float) {
        if (!PlayerHelper.swipeGestureEnabled) {
            if (PlayerHelper.fullscreenGesturesEnabled) onSwipeCenterScreen(distanceY, positionY)
            return
        }

        if (isControllerFullyVisible) hideController()
        updateVolume(distanceY)
    }

    override fun onSwipeCenterScreen(distanceY: Float, positionY: Float) {
        if (!PlayerHelper.fullscreenGesturesEnabled) return
        fullscreenGestureAnimationController.onSwipe(distanceY, positionY)
    }

    override fun onSwipeEnd() {
        fullscreenGestureAnimationController.onSwipeEnd()
        gestureViewBinding.brightnessControlView.isGone = true
        gestureViewBinding.volumeControlView.isGone = true
    }

    override fun onZoom() {
        if (!PlayerHelper.pinchGestureEnabled) return
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            subtitleView?.setBottomPaddingFraction(SUBTITLE_BOTTOM_PADDING_FRACTION)
        }
    }

    override fun onMinimize() {
        if (!PlayerHelper.pinchGestureEnabled) return
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

        subtitleView?.setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
    }

    override fun onLongPress() {
        if (!PlayerHelper.longPressFastForward) return

        backgroundBinding.fastForwardView.isVisible = true
        val player = player ?: return

        // using the fast forward action wouldn't change anything in this case
        if (player.playbackParameters.speed >= PlayerHelper.MAXIMUM_PLAYBACK_SPEED) {
            return
        }

        // backup current playback speed in order to restore it
        // after the fast forward action is done
        rememberedPlaybackSpeed = player.playbackParameters.speed

        val newSpeed = minOf(
            player.playbackParameters.speed * PlayerHelper.FAST_FORWARD_SPEED_FACTOR,
            PlayerHelper.MAXIMUM_PLAYBACK_SPEED
        )
        player.playbackParameters = PlaybackParameters(newSpeed, player.playbackParameters.pitch)
    }

    override fun onLongPressEnd() {
        if (!PlayerHelper.longPressFastForward) return

        backgroundBinding.fastForwardView.isGone = true

        val player = player ?: return
        rememberedPlaybackSpeed?.let {
            player.playbackParameters = PlaybackParameters(it, player.playbackParameters.pitch)
        }
        rememberedPlaybackSpeed = null
    }

    override fun onFullscreenChange(isFullscreen: Boolean) {
        if (isFullscreen) {
            if (PlayerHelper.swipeGestureEnabled) {
                brightnessHelper.restoreSavedBrightness()
            }
            subtitleView?.setFixedTextSize(
                Cue.TEXT_SIZE_TYPE_ABSOLUTE,
                PlayerHelper.captionsTextSize * 1.5f
            )
            if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                subtitleView?.setBottomPaddingFraction(SUBTITLE_BOTTOM_PADDING_FRACTION)
            }
        } else {
            if (PlayerHelper.swipeGestureEnabled) {
                brightnessHelper.resetToSystemBrightness()
            }
            subtitleView?.setFixedTextSize(
                Cue.TEXT_SIZE_TYPE_ABSOLUTE,
                PlayerHelper.captionsTextSize
            )
            subtitleView?.setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
        }

        updateMarginsByFullscreenMode()
    }

    /**
     * Listen for all child touch events
     */
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        // when a control is clicked, restart the countdown to hide the controller
        if (isControllerFullyVisible) {
            cancelHideControllerTask()
            enqueueHideControllerTask()
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
        if (!useController) return false

        return playerGestureController.onTouchEvent(event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.togglePlayPauseState()
            }

            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                forward()
            }

            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                rewind()
            }

            KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_NAVIGATE_NEXT -> {
                PlayingQueue.getNext()?.let { (player as? MediaController)?.navigateVideo(it) }
            }

            KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> {
                PlayingQueue.getPrev()?.let { (player as? MediaController)?.navigateVideo(it) }
            }

            KeyEvent.KEYCODE_F -> {
                playerCallback.toggleFullscreen()
            }

            else -> return false
        }

        return true
    }

    override fun getViewMeasures(): Pair<Int, Int> {
        return width to height
    }

    var alreadySetDefaultSubtitle: Boolean = false
    fun onPlaybackEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {
            binding.playPauseBTN.setImageResource(
                PlayerHelper.getPlayPauseActionIcon(player)
            )

            // keep screen on if the video is playing
            keepScreenOn = player.isPlaying == true
        }

        if (events.contains(Player.EVENT_RENDERED_FIRST_FRAME)) {
            // if the video is not starting automatically, show the controller
            if (!PlayerHelper.playAutomatically) showControllerPermanently()
        }

        if (events.contains(Player.EVENT_RENDERED_FIRST_FRAME) && !alreadySetDefaultSubtitle) {
            // only set the default subtitle at the start of the playback session
            alreadySetDefaultSubtitle = true

            // set default caption language from preferences if caption language is available
            val captions = PlayerHelper.getCaptionTracks(player)
            val defaultLangCaption =
                captions.firstOrNull { it.language == PlayerHelper.defaultSubtitleCode }

            updateCurrentSubtitle(defaultLangCaption?.id)

            // if the video is live, the remaining time is displayed instead of duration
            updateDisplayedDurationType()
        }
        if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
            // new video started
            alreadySetDefaultSubtitle = false
        }

        updateDisplayedDuration()
    }

    fun getWindow(): Window = currentWindow ?: activity.window

    companion object {
        private const val HIDE_CONTROLLER_TOKEN = "hideController"
        private const val HIDE_FORWARD_BUTTON_TOKEN = "hideForwardButton"
        private const val HIDE_REWIND_BUTTON_TOKEN = "hideRewindButton"
        private const val UPDATE_POSITION_TOKEN = "updatePosition"

        private const val SUBTITLE_BOTTOM_PADDING_FRACTION = 0.158f
        private const val ANIMATION_DURATION = 100L
        private const val AUTO_HIDE_CONTROLLER_DELAY = 2000L
        private const val DEFAULT_SURFACE_WIDTH = 1920
        private const val DEFAULT_SURFACE_HEIGHT = 1080
        private val LANDSCAPE_MARGIN_HORIZONTAL = 20f.dpToPx()
        private val LANDSCAPE_MARGIN_HORIZONTAL_NONE = 0f.dpToPx()
    }
}
