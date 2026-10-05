package com.watube.yard.ui.fragments

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.graphics.Outline
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import androidx.activity.BackEventCompat
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.trackPipAnimationHintView
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.constraintlayout.motion.widget.TransitionAdapter
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.os.postDelayed
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.recyclerview.widget.LinearLayoutManager
import com.watube.yard.R
import com.watube.yard.api.JsonHelper
import com.watube.yard.api.obj.ChapterSegment
import com.watube.yard.api.obj.Segment
import com.watube.yard.api.obj.Streams
import com.watube.yard.cast.CastHelper
import com.watube.yard.compat.PictureInPictureCompat
import com.watube.yard.compat.PictureInPictureParamsCompat
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.FragmentPlayerBinding
import com.watube.yard.db.DatabaseHolder
import com.watube.yard.enums.FileType
import com.watube.yard.enums.PlayerCommand
import com.watube.yard.enums.PlayerEvent
import com.watube.yard.enums.SbSkipOptions
import com.watube.yard.enums.ShareObjectType
import com.watube.yard.extensions.formatShort
import com.watube.yard.extensions.parcelable
import com.watube.yard.extensions.serializableExtra
import com.watube.yard.extensions.toID
import com.watube.yard.extensions.toastFromMainThread
import com.watube.yard.extensions.togglePlayPauseState
import com.watube.yard.extensions.updateIfChanged
import com.watube.yard.helpers.BackgroundHelper
import com.watube.yard.helpers.DownloadHelper
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.IntentHelper
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.helpers.PlayerHelper
import com.watube.yard.helpers.PlayerHelper.getCurrentSegment
import com.watube.yard.helpers.WindowHelper
import com.watube.yard.obj.ShareData
import com.watube.yard.obj.VideoResolution
import com.watube.yard.parcelable.PlayerData
import com.watube.yard.player.SabrAttestationException
import com.watube.yard.services.AbstractPlayerService
import com.watube.yard.services.OfflinePlayerService
import com.watube.yard.services.OnlinePlayerService
import com.watube.yard.ui.activities.AbstractPlayerHostActivity
import com.watube.yard.ui.activities.NoInternetActivity
import com.watube.yard.ui.adapters.VideoCardsAdapter
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.dialogs.AddToPlaylistDialog
import com.watube.yard.ui.dialogs.PlayOfflineDialog
import com.watube.yard.ui.dialogs.ShareDialog
import com.watube.yard.ui.extensions.animateDown
import com.watube.yard.ui.extensions.getSystemInsets
import com.watube.yard.ui.extensions.preferLowFrameRate
import com.watube.yard.ui.extensions.setOnBackPressed
import com.watube.yard.ui.extensions.setupSubscriptionButton
import com.watube.yard.ui.interfaces.CustomPlayerCallback
import com.watube.yard.ui.interfaces.TimeFrameReceiver
import com.watube.yard.ui.listeners.SeekbarPreviewListener
import com.watube.yard.ui.models.ChaptersViewModel
import com.watube.yard.ui.models.CommentsViewModel
import com.watube.yard.ui.models.CommonPlayerViewModel
import com.watube.yard.ui.models.PlayerViewModel
import com.watube.yard.ui.sheets.CommentsSheet
import com.watube.yard.ui.sheets.PlayingQueueSheet
import com.watube.yard.ui.tools.SleepTimer
import com.watube.yard.util.OfflineTimeFrameReceiver
import com.watube.yard.util.OnlineTimeFrameReceiver
import com.watube.yard.util.PlayingQueue
import com.watube.yard.util.TextUtils
import com.watube.yard.util.TextUtils.toTimeInSeconds
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import androidx.mediarouter.app.MediaRouteControllerDialogFragment
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.io.path.exists
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private const val CAST_ROUTE_DIALOG_TAG = "cast_route_dialog"

/** Time given to a restored orientation request to rotate the activity before re-checking */
private const val ORIENTATION_SETTLE_DELAY_MS = 1000L

/** Continuity animation between the page and fullscreen (Material 3 medium duration) */
private const val FULLSCREEN_TRANSITION_MS = 300L


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerFragment : Fragment(R.layout.fragment_player), CustomPlayerCallback {
    private var _binding: FragmentPlayerBinding? = null
    val binding get() = _binding!!

    private val playerControlsBinding get() = binding.player.binding
    private val playerBackgroundBinding get() = binding.player.backgroundBinding

    private val commonPlayerViewModel: CommonPlayerViewModel by activityViewModels()
    private val viewModel: PlayerViewModel by viewModels()
    private val commentsViewModel: CommentsViewModel by activityViewModels()
    private val chaptersViewModel: ChaptersViewModel by activityViewModels()
    private lateinit var playerController: MediaController

    private lateinit var videoId: String
    private var playlistId: String? = null
    private var channelId: String? = null
    var isOffline: Boolean = false
        private set

    private lateinit var streams: Streams

    private val handler = Handler(Looper.getMainLooper())

    private var seekBarPreviewListener: SeekbarPreviewListener? = null
    private var closedVideo = false

    /** la liste des vidéos associées est elle affichée sous la feuille ? (titre « À suivre ») */
    private var hasRelatedStreams = false

    /** format de la vitesse de lecture affiché par la pastille flottante (1,5× / 1.5x) */
    private val speedFormat = NumberFormat.getNumberInstance().apply {
        isGroupingUsed = false
        maximumFractionDigits = 2
    }
    private var autoPlayCountdownEnabled = PlayerHelper.autoPlayCountdown
    private var playerLayoutOrientation = Int.MIN_VALUE
    private var pipActivity: Activity? = null
    private var isEnteringPiPMode = false

    /** Cast : contexte SDK partage au sein du fragment, obtenu lazily au premier appui */
    private var castContext: CastContext? = null
    private var castSessionListener: SessionManagerListener<CastSession>? = null
    /** Google Play services indisponible : le bouton est masque pour la session du fragment */
    private var castUnavailable = false

    private val requestNearbyWifiPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) showCastRoutePicker()
            else context?.toastFromMainThread(R.string.toast_cast_unavailable)
        }

    /** dernière taille vidéo connue : conserve le ratio de la fenêtre PiP pendant les pauses */
    private var lastPipVideoSize: VideoSize? = null

    /** limite les tentatives de reprise après une erreur source (évite la boucle infinie) */
    private var playbackErrorRetries = 0

    /**
     * Après une longue veille, les URLs de flux YouTube expirent : la reprise en place
     * échoue alors en boucle (HTTP 403/410). Ce drapeau garantit qu'on ne relance
     * l'extraction complète du flux qu'une seule fois par vidéo, remis à zéro dès que
     * la lecture repart (STATE_READY).
     */
    private var hasReExtractedOnError = false

    private val baseActivity get() = activity as AbstractPlayerHostActivity

    /** Black behind the fullscreen video, faded by the continuity animation */
    private val fullscreenBackdrop = Color.BLACK.toDrawable()

    /** Where the video sat in the page when fullscreen started (screen pixels) */
    private var inlineVideoBounds: Rect? = null
    private var inlineOrientation = Configuration.ORIENTATION_UNDEFINED

    /** Concludes the running page <-> fullscreen animation, also when it has not started yet */
    private var finishFullscreenTransition: (() -> Unit)? = null

    private val fullscreenDialog by lazy {
        object : ComponentDialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                // translucent: the page stays visible under the backdrop while it fades
                window?.setFormat(PixelFormat.TRANSLUCENT)
                window?.setBackgroundDrawable(fullscreenBackdrop)
                // not bound to the dialog lifecycle: ComponentDialog destroys it on every dismiss,
                // which dropped the callback after the first exit, so back then closed the dialog
                // without leaving fullscreen (player gone from the page, fullscreen state stuck)
                onBackPressedDispatcher.addCallback { unsetFullscreen(animate = true) }
            }

            override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
                if (_binding?.player?.onKeyUp(keyCode, event) == true) {
                    return true
                }
                return super.onKeyUp(keyCode, event)
            }
        }
    }

    private val playerActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!::playerController.isInitialized) return
            val event = intent.serializableExtra<PlayerEvent>(PlayerHelper.CONTROL_TYPE) ?: return

            if (PlayerHelper.handlePlayerAction(playerController, event)) return

            when (event) {
                PlayerEvent.Next -> {
                    PlayingQueue.getNext()?.let { playVideo(it) }
                }
                PlayerEvent.Prev -> {
                    PlayingQueue.getPrev()?.let { playVideo(it) }
                }
                PlayerEvent.Background -> {
                    switchToAudioMode()
                    handler.postDelayed(500) {
                        pipActivity?.moveTaskToBack(false)
                        pipActivity = null
                    }
                }
                else -> Unit
            }
        }
    }

    private var bufferingTimeoutTask: Runnable? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            PictureInPictureCompat.setPictureInPictureParams(requireActivity(), pipParams)
            if (isPlaying && ::videoId.isInitialized) SleepTimer.startForVideo(requireActivity(), videoId)

            if (isPlaying && PlayerHelper.sponsorBlockEnabled) {
                handler.removeCallbacks(segmentsChecker)
                handler.postDelayed(segmentsChecker, 100)
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            super.onEvents(player, events)

            if (events.containsAny(
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED
                ) && _binding != null
            ) {
                updatePlayPauseButton()
            }

            if (events.containsAny(
                    Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                    Player.EVENT_VIDEO_SIZE_CHANGED
                ) && _binding != null
            ) {
                updateOverlayChips()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                // On réarme seulement les tentatives « en place ». hasReExtractedOnError n'est
                // volontairement PAS réarmé ici : sinon une erreur qui revient toujours au même
                // point relancerait une ré-extraction → redémarrage → même erreur, en boucle.
                playbackErrorRetries = 0
            }

            if (!::playerController.isInitialized) return

            if (playbackState == Player.STATE_BUFFERING && streams.isLive &&
                playerController.duration - playerController.currentPosition < 700
            ) {
                playerController.setPlaybackSpeed(1f)
            }

            if (playbackState == Player.STATE_ENDED) {
                playerBackgroundBinding.sbSkipBtn.isGone = true

                val isTransitioning = playerController.currentTracks.isEmpty
                if ((PlayingQueue.hasNext() || PlayerHelper.autoPlayEnabled) && autoPlayCountdownEnabled && !isTransitioning) {
                    showAutoPlayCountdown()
                } else {
                    binding.player.showControllerPermanently()
                }
            }

            if (playbackState == PlaybackState.STATE_STOPPED &&
                PictureInPictureCompat.isInPictureInPictureMode(requireActivity())
            ) {
                activity?.finish()
            }

            if (playbackState == Player.STATE_BUFFERING) {
                if (bufferingTimeoutTask == null) {
                    bufferingTimeoutTask = Runnable {
                        if (::playerController.isInitialized) {
                            playerController.pause()
                        }
                    }
                }
                handler.postDelayed(bufferingTimeoutTask!!, PlayerHelper.MAX_BUFFER_DELAY)
            } else {
                bufferingTimeoutTask?.let { handler.removeCallbacks(it) }
            }

            super.onPlaybackStateChanged(playbackState)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            super.onMediaMetadataChanged(mediaMetadata)

            val maybeStreams: Streams? = mediaMetadata.extras?.getString(IntentData.streams)?.let {
                JsonHelper.json.decodeFromString(it)
            }
            maybeStreams?.let { streams ->
                this@PlayerFragment.streams = streams
                viewModel.segments.postValue(emptyList())
                updatePlayerView()
            }
        }

        override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {
            super.onPlaylistMetadataChanged(mediaMetadata)

            mediaMetadata.extras?.getString(IntentData.videoId)?.let {
                videoId = it
                if (_binding != null) playerBackgroundBinding.autoplayCountdown.cancelAndHideCountdown()

                arguments?.run {
                    val playerData =
                        parcelable<PlayerData>(IntentData.playerData)!!.copy(videoId = videoId)
                    putParcelable(IntentData.playerData, playerData)
                }
            }

            val segments: List<Segment>? =
                mediaMetadata.extras?.getString(IntentData.segments)?.let {
                    JsonHelper.json.decodeFromString(it)
                }
            viewModel.segments.postValue(segments.orEmpty())
            // the skip button loop waits for segments: runs after the value lands (same looper)
            handler.post(segmentsChecker)
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            // the service is already replacing a session flagged by YouTube, at the same
            // position: a retry from here would race it (only the cause's message survives the
            // trip to the controller, not its class)
            if (error.cause?.message == SabrAttestationException.MESSAGE) return
            // Erreurs transitoires (coupure réseau brève, fenêtre live dépassée) : on
            // retente la même source en place. Plafonné, sinon un live dont la source
            // est morte déclenche une boucle prepare()/play() infinie.
            val transient = error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
            // Source périmée : après une longue veille, l'URL de flux a expiré
            // (HTTP 403/410, 416, fichier introuvable). Retenter la même URL est
            // inutile — il faut ré-extraire le flux pour obtenir des URLs fraîches.
            val staleSource = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
            try {
                when {
                    ::playerController.isInitialized && transient && playbackErrorRetries < 3 -> {
                        playbackErrorRetries++
                        // back to the live edge for a live stream only: for a video the default
                        // position is 0:00, every network hiccup used to restart it from the start
                        if (playerController.isCurrentMediaItemLive) playerController.seekToDefaultPosition()
                        // togglePlayPauseState prepares an errored player again before playing,
                        // a plain play() left it stuck
                        playerController.togglePlayPauseState()
                    }
                    // Source périmée, ou reprise en place épuisée : on relance une
                    // extraction complète de la vidéo courante (une seule fois), ce qui
                    // réarme aussi la SeekBar restée inactive sur un lecteur en erreur.
                    ::playerController.isInitialized && ::videoId.isInitialized &&
                        !hasReExtractedOnError && (staleSource || playbackErrorRetries >= 3) -> {
                        hasReExtractedOnError = true
                        playVideo(videoId)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            if (mediaItem == null) {
                toggleVideoInfoVisibility(false)
                disableController()
                binding.titleTextView.text = ""
            }
        }
    }

    private val lockedOrientations = listOf(
        ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    )

    private var screenshotBitmap: Bitmap? = null
    private val openScreenshotFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            if (uri == null) {
                screenshotBitmap = null
                return@registerForActivityResult
            }

            lifecycleScope.launch(Dispatchers.IO) {
                context?.contentResolver?.openOutputStream(uri)?.use { outputStream ->
                    screenshotBitmap?.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                screenshotBitmap = null

                withContext(Dispatchers.Main) {
                    _binding?.let { binding ->
                        Snackbar.make(
                            binding.root,
                            R.string.screenshot_saved,
                            2500
                        ).apply {
                            setAction(R.string.share) {
                                startActivity(Intent.createChooser(Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                }, null))
                            }
                            show()
                        }
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ContextCompat.registerReceiver(
            requireContext(),
            playerActionReceiver,
            IntentFilter(PlayerHelper.getIntentActionName(requireContext())),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentPlayerBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)
        // ticks twice a second in the mini player
        binding.miniplayerProgress.preferLowFrameRate()

        activity?.getSystemInsets()?.let { systemBars ->
            with(binding.root) {
                setPadding(
                    paddingLeft,
                    paddingTop + systemBars.top,
                    paddingRight,
                    paddingBottom
                )
            }
        }

        val playerData = requireArguments().parcelable<PlayerData>(IntentData.playerData)!!
        playerData.videoId?.let { videoId = it }
        isOffline = playerData.isOffline
        playlistId = playerData.playlistId
        channelId = playerData.channelId

        requireArguments().putBoolean(IntentData.alreadyStarted, true)

        changeOrientationMode()

        playerLayoutOrientation = resources.configuration.orientation

        initializeTransitionLayout(
            restoreMiniPlayer = savedInstanceState != null &&
                commonPlayerViewModel.isMiniPlayerVisible.value == true
        )
        initializeOnClickActions()

        // isFullscreen lives in the activity scoped view model: after a recreation that
        // happened in fullscreen (density, navigation mode, theme...) it is still true, but the
        // fullscreen dialog died with the previous instance. Left as is, the portrait layout
        // stayed stuck in a landscape window (restartActivityIfNeeded() skips while
        // "fullscreen") and the screen could not be used anymore: reopen it for real instead.
        // A brand new player never inherits a stale value.
        val restoreFullscreen = savedInstanceState != null &&
            commonPlayerViewModel.isFullscreen.value == true
        if (savedInstanceState == null) commonPlayerViewModel.isFullscreen.value = false
        if (restoreFullscreen || (PlayerHelper.autoFullscreenEnabled &&
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        ) {
            setFullscreen()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                requireActivity().trackPipAnimationHintView(binding.player)
            }
        }

        chaptersViewModel.chaptersLiveData.observe(viewLifecycleOwner) {
            binding.player.setCurrentChapterName()
            playerControlsBinding.exoProgress.setChapters(it.orEmpty())
            updateOverlayChips()
        }

        chaptersViewModel.currentChapterIndex.observe(viewLifecycleOwner) {
            updateOverlayChips()
        }

        viewModel.segments.observe(viewLifecycleOwner) { segments ->
            binding.descriptionLayout.setSegments(segments)
            playerControlsBinding.exoProgress.setSegments(segments)
            playerControlsBinding.sbToggle.isVisible = segments.isNotEmpty()
            getHighlight(segments)?.let {
                lifecycleScope.launch(Dispatchers.IO) { initializeHighlight(it) }
            }
        }

        if (!isOffline) {
            viewLifecycleOwner.lifecycleScope.launch {
                val localDownloadVersion = withContext(Dispatchers.IO) {
                    DatabaseHolder.Database.downloadDao().findById(videoId)
                }

                if (localDownloadVersion != null) {
                    val fragmentManager = requireActivity().supportFragmentManager

                    fragmentManager.setFragmentResultListener(
                        PlayOfflineDialog.PLAY_OFFLINE_DIALOG_REQUEST_KEY, viewLifecycleOwner
                    ) { _, bundle ->
                        isOffline = bundle.getBoolean(IntentData.isPlayingOffline)
                        attachToPlayerService(playerData)
                    }

                    val downloadInfo = withContext(Dispatchers.IO) {
                        DownloadHelper.extractDownloadInfoText(
                            requireContext(),
                            localDownloadVersion
                        ).toTypedArray()
                    }

                    PlayOfflineDialog().apply {
                        arguments = Bundle().apply {
                            putString(IntentData.videoId, videoId)
                            putString(IntentData.videoTitle, localDownloadVersion.download.title)
                            putStringArray(IntentData.downloadInfo, downloadInfo)
                        }
                    }.show(fragmentManager, null)
                } else {
                    attachToPlayerService(playerData)
                }
            }
        } else {
            attachToPlayerService(playerData)
        }

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (commonPlayerViewModel.isFullscreen.value == true) unsetFullscreen()
                else {
                    binding.playerMotionLayout.setTransitionDuration(250)
                    binding.playerMotionLayout.transitionToEnd()
                    baseActivity.minimizePlayerContainerLayout()
                    baseActivity.requestOrientationChange()
                }
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                binding.playerMotionLayout.progress = backEvent.progress
            }

            override fun handleOnBackCancelled() {
                binding.playerMotionLayout.transitionToStart()
            }
        }
        setOnBackPressed(onBackPressedCallback)

        commonPlayerViewModel.isMiniPlayerVisible.observe(viewLifecycleOwner) { isMiniPlayerVisible ->
            if (!isMiniPlayerVisible) {
                onBackPressedCallback.remove()
                setOnBackPressed(onBackPressedCallback)
            }
            onBackPressedCallback.isEnabled = isMiniPlayerVisible != true

            // mini-player chrome: rounded video corners + discreet progress bar
            updateMiniPlayerChrome(isMiniPlayerVisible == true)
        }

        toggleVideoInfoVisibility(false)

        // onPictureInPictureModeChanged() n'est dispatché que sur TRANSITION : si
        // l'activity est (re)créée alors qu'elle est déjà en PiP (réouverture de
        // l'app), le callback n'arrive jamais et le lecteur reste dans le layout
        // normal -> la fenêtre PiP affiche l'UI de l'application au lieu de la vidéo.
        if (PictureInPictureCompat.isInPictureInPictureMode(requireActivity())) {
            onPictureInPictureModeChanged(true)
        }
    }

    private fun attachToPlayerService(playerData: PlayerData) {
        val (serviceClass, args) = if (isOffline) {
            val isNoInternet = activity is NoInternetActivity

            OfflinePlayerService::class.java to Bundle().apply {
                putParcelable(IntentData.playerData, playerData.copy(downloadTab = playerData.downloadTab ?: DownloadTab.VIDEO))
                putBoolean(IntentData.noInternet, isNoInternet)
            }
        } else {
            OnlinePlayerService::class.java to Bundle().apply {
                putParcelable(IntentData.playerData, playerData)
                putBoolean(IntentData.audioOnly, false)
            }
        }

        BackgroundHelper.startMediaService(
            requireContext(),
            serviceClass,
            args,
        ) {
            if (_binding == null) {
                if (::playerController.isInitialized) {
                    playerController.sendCustomCommand(
                        AbstractPlayerService.stopServiceCommand,
                        Bundle.EMPTY
                    )
                    playerController.release()
                }
                return@startMediaService
            }

            playerController = it
            playerController.addListener(playerListener)
            connectToPlayerView(playerController)
            updatePlayPauseButton()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeTransitionLayout(restoreMiniPlayer: Boolean) {
        baseActivity.setPlayerContainerProgress(0f)

        binding.playerMotionLayout.addTransitionListener(object : TransitionAdapter() {
            override fun onTransitionChange(
                motionLayout: MotionLayout?,
                startId: Int,
                endId: Int,
                progress: Float
            ) {
                if (_binding == null) return

                baseActivity.setPlayerContainerProgress(progress.absoluteValue)
                // once per transition, not on every frame (it restarts a fade animation)
                if (binding.player.useController) disableController()
                commonPlayerViewModel.setSheetExpand(false)
            }

            override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
                if (_binding == null) return

                when (currentId) {
                    R.id.start -> onPlayerMaximized()
                    R.id.end -> onPlayerMinimized()
                }
            }
        })

        binding.playerMotionLayout
            .addSwipeDownListener {
                if (commonPlayerViewModel.isMiniPlayerVisible.value == true) {
                    closeMiniPlayer()
                }
            }

        // Le lecteur s'ouvre en glissant du mini (progress 1) vers le plein écran
        // (progress 0). Lancer transitionToStart() ici, avant la première passe de
        // layout du MotionLayout, est non déterministe : ~1 fois sur 3 la transition
        // était ignorée et le lecteur restait en mini (petit carré à gauche, le son
        // tournant). On fixe l'état mini tout de suite, puis on anime une fois le
        // layout effectué (doOnLayout s'exécute aussitôt si la vue est déjà mesurée).
        // A player that was collapsed when the activity got recreated (rotation) stays the
        // mini player instead of popping back open over the content.
        binding.playerMotionLayout.progress = 1F
        binding.playerMotionLayout.doOnLayout {
            if (_binding == null) return@doOnLayout
            if (restoreMiniPlayer) onPlayerMinimized()
            else binding.playerMotionLayout.transitionToStart()
        }

        val activity = requireActivity()
        PictureInPictureCompat.setPictureInPictureParams(activity, pipParams)
    }

    private fun onPlayerMaximized() {
        commonPlayerViewModel.isMiniPlayerVisible.value = false
        binding.player.updateCurrentSubtitle(viewModel.currentCaptionId)
        binding.player.useController = true
        commonPlayerViewModel.setSheetExpand(true)
        baseActivity.setPlayerContainerProgress(0f)
        changeOrientationMode()
        baseActivity.clearSearchViewFocus()
        updateMaxSheetHeight()
    }

    private fun onPlayerMinimized() {
        commonPlayerViewModel.isMiniPlayerVisible.value = true
        binding.player.updateCurrentSubtitle(null)
        disableController()
        commonPlayerViewModel.setSheetExpand(null)
        playerBackgroundBinding.sbSkipBtn.isGone = true

        baseActivity.setPlayerContainerProgress(1f)
        baseActivity.requestOrientationChange()
        updateMaxSheetHeight()
    }

    private fun closeMiniPlayer() {
        binding
            .playerMotionLayout
            .animateDown(
                duration = 300L,
                dy = 500F,
                onEnd = ::killPlayerFragment
            )
    }

    private fun initializeOnClickActions() {
        binding.closeImageView.setOnClickListener {
            killPlayerFragment()
        }
        playerControlsBinding.closeImageButton.setOnClickListener {
            killPlayerFragment()
        }

        binding.playImageView.setOnClickListener {
            if (::playerController.isInitialized) playerController.togglePlayPauseState()
        }

        activity?.supportFragmentManager
            ?.setFragmentResultListener(
                CommentsSheet.HANDLE_LINK_REQUEST_KEY,
                viewLifecycleOwner
            ) { _, bundle ->
                bundle.getString(IntentData.url)?.let { handleLink(it) }
            }

        binding.commentsToggle.setOnClickListener {
            if (!this::streams.isInitialized) return@setOnClickListener
            updateMaxSheetHeight()
            commentsViewModel.videoIdLiveData.updateIfChanged(videoId)
            CommentsSheet()
                .apply {
                    arguments = Bundle().apply {
                        putString(IntentData.channelAvatar, streams.uploaderAvatar)
                    }
                }
                .show(childFragmentManager)
        }

        binding.relPlayerShare.setOnClickListener {
            if (!this::streams.isInitialized) return@setOnClickListener
            val bundle = Bundle().apply {
                putString(IntentData.id, videoId)
                putSerializable(IntentData.shareObjectType, ShareObjectType.VIDEO)
                putParcelable(IntentData.shareData, ShareData(
                    currentVideo = streams.title,
                    currentPosition = if (::playerController.isInitialized) playerController.currentPosition / 1000 else 0
                ))
            }
            val newShareDialog = ShareDialog()
            newShareDialog.arguments = bundle
            newShareDialog.show(childFragmentManager, ShareDialog::class.java.name)
        }

        binding.relPlayerBackground.setOnClickListener {
            switchToAudioMode()
        }

        binding.relPlayerPip.isVisible = isPipAvailable()

        binding.relPlayerPip.setOnClickListener {
            PictureInPictureCompat.enterPictureInPictureMode(requireActivity(), pipParams)
            isEnteringPiPMode = true
        }

        binding.relatedRecView.layoutManager = LinearLayoutManager(
            context,
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                LinearLayoutManager.HORIZONTAL
            } else {
                LinearLayoutManager.VERTICAL
            },
            false
        )

        binding.relPlayerSave.setOnClickListener {
            if (!::streams.isInitialized) return@setOnClickListener

            AddToPlaylistDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(IntentData.videoInfo, streams.toStreamItem(videoId))
                }
            }.show(childFragmentManager, AddToPlaylistDialog::class.java.name)
        }

        playerControlsBinding.skipPrev.setOnClickListener {
            PlayingQueue.getPrev()?.let { prev -> playVideo(prev) }
        }

        playerControlsBinding.skipNext.setOnClickListener {
            PlayingQueue.getNext()?.let { next -> playVideo(next) }
        }

        binding.relPlayerCast.isVisible =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isOffline && !castUnavailable
        binding.relPlayerCast.setOnClickListener {
            onCastButtonClick()
        }

        binding.relPlayerDownload.setOnClickListener {
            if (!this::streams.isInitialized) return@setOnClickListener

            DownloadHelper.startDownloadDialog(requireContext(), childFragmentManager, videoId)
        }

        binding.relPlayerScreenshot.setOnClickListener {
            if (!this::streams.isInitialized) return@setOnClickListener
            val surfaceView =
                binding.player.videoSurfaceView as? SurfaceView ?: return@setOnClickListener

            val bmp = createBitmap(surfaceView.width, surfaceView.height)

            PixelCopy.request(surfaceView, bmp, { _ ->
                screenshotBitmap = bmp
                val currentPosition =
                    if (::playerController.isInitialized) playerController.currentPosition.toFloat() / 1000 else 0f
                openScreenshotFile.launch("${streams.title}-${currentPosition}.png")
            }, handler)
        }

        binding.relPlayerQueue.setOnClickListener {
            PlayingQueueSheet().show(requireActivity().supportFragmentManager)
        }

        playerControlsBinding.watubeChapterChip.setOnClickListener {
            playerControlsBinding.chapterName.callOnClick()
        }

        binding.playerChannel.setOnClickListener {
            if (!this::streams.isInitialized) return@setOnClickListener

            NavigationHelper.navigateChannel(requireContext(), streams.uploaderUrl)
        }

        binding.descriptionLayout.handleLink = this::handleLink
    }

    /**
     * Clic sur le bouton Cast :
     * - API < 33 : le bouton est desactive (masque), aucun permission fallback
     *   (decouverte de route exigerait la localisation, interdite).
     * - API 33+ : NEARBY_WIFI_DEVICES (neverForLocation) doit etre accordee.
     * - CastContext lazily : si Google Play services est absent, le bouton est
     *   masque et un toast existe deja, sans crash.
     */
    private fun onCastButtonClick() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || castUnavailable) return
        if (!::streams.isInitialized || isOffline) return

        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.NEARBY_WIFI_DEVICES
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) showCastRoutePicker()
        else requestNearbyWifiPermission.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    private fun showCastRoutePicker() {
        if (_binding == null) return
        val ctx = context ?: return

        val cast = castContext
            ?: runCatching { CastHelper.getCastContext(ctx) }.getOrElse {
                markCastUnavailable(ctx)
                return
            }

        castContext = cast

        val selector = runCatching {
            registerCastSessionListener(cast)
            cast.mergedSelector
        }.getOrElse {
            markCastUnavailable(ctx)
            return
        }

        if (selector == null || selector.isEmpty()) {
            ctx.toastFromMainThread(R.string.toast_cast_unavailable)
            return
        }

        // Connecte deja a un recepteur : controle ; sinon : choix de la route.
        // getInstance().selectedRoute ne renvoie jamais null (au minimum la route par
        // défaut), d'où le contrôle direct sans vérification de nullité redondante.
        val selectedRoute = MediaRouter.getInstance(ctx).selectedRoute
        val connected = selectedRoute.matchesSelector(selector)
        val dialog = if (connected) {
            MediaRouteControllerDialogFragment()
        } else {
            MediaRouteChooserDialogFragment().apply { setRouteSelector(selector) }
        }
        dialog.show(childFragmentManager, CAST_ROUTE_DIALOG_TAG)
    }

    private fun markCastUnavailable(ctx: Context) {
        castUnavailable = true
        if (_binding != null) binding.relPlayerCast.isVisible = false
        ctx.toastFromMainThread(R.string.toast_cast_unavailable)
    }

    private fun registerCastSessionListener(cast: CastContext) {
        if (castSessionListener != null) return

        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) = Unit

            override fun onSessionStarted(session: CastSession, sessionId: String) {
                loadCurrentStreamOnCast(session)
            }

            override fun onSessionStartFailed(session: CastSession, errorCode: Int) = Unit
            override fun onSessionEnding(session: CastSession) = Unit
            override fun onSessionEnded(session: CastSession, errorCode: Int) = Unit
            override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = Unit
            override fun onSessionResumeFailed(session: CastSession, errorCode: Int) = Unit
            override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
        }
        cast.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
        castSessionListener = listener
    }

    /**
     * Chargement sur le recepteur : uniquement l'URL https du flux courant et
     * les metadonnees minimales (titre, duree, vignette publique). Aucun journal
     * d'URL (jetons signes). La lecture locale est mise en pause ; en fin de
     * session elle reste en pause (reprise manuelle, aucun auto-resume).
     */
    private fun loadCurrentStreamOnCast(session: CastSession) {
        if (_binding == null || !::streams.isInitialized || isOffline) return

        val remoteClient = session.remoteMediaClient ?: return
        val mediaInfo = CastHelper.buildMediaInfo(streams)
        if (mediaInfo == null) {
            context?.toastFromMainThread(R.string.toast_cast_unavailable)
            return
        }

        val startPosition = if (!streams.isLive && ::playerController.isInitialized) {
            playerController.currentPosition
        } else {
            0L
        }
        if (::playerController.isInitialized) playerController.pause()

        remoteClient.load(
            MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .setCurrentTime(startPosition)
                .build()
        )
    }

    private fun updateMaxSheetHeight() {
        val systemBars = baseActivity.getSystemInsets() ?: return
        val maxHeight = binding.root.height - (binding.player.height + systemBars.top)
        commonPlayerViewModel.maxSheetHeightPx = maxHeight
        chaptersViewModel.maxSheetHeightPx = maxHeight
    }

    fun switchToAudioMode() {
        if (!::playerController.isInitialized) return

        playerController.sendCustomCommand(
            AbstractPlayerService.runPlayerActionCommand,
            Bundle().apply {
                putBoolean(PlayerCommand.TOGGLE_AUDIO_ONLY_MODE.name, true)
            }
        )
        playerController.sendCustomCommand(
            AbstractPlayerService.runPlayerActionCommand,
            Bundle().apply {
                putBoolean(PlayerCommand.SET_AUTOPLAY_COUNTDOWN_ENABLED.name, false)
            }
        )

        binding.player.detachPlayer()

        playerController.release()
        killPlayerFragment()

        NavigationHelper.openAudioPlayerFragment(requireContext(), offlinePlayer = isOffline)
    }

    private fun updateFullscreenOrientation() {
        if (PlayerHelper.autoFullscreenEnabled || !this::streams.isInitialized) return

        baseActivity.requestedOrientation = PlayerHelper.getFullscreenOrientation(streams.isShort)
    }

    private fun setFullscreen(animate: Boolean = false) {
        finishFullscreenTransition?.invoke()
        inlineVideoBounds = videoBoundsOnScreen()
        inlineOrientation = resources.configuration.orientation

        commonPlayerViewModel.isFullscreen.value = true
        updateFullscreenOrientation()

        commonPlayerViewModel.setSheetExpand(null)

        openOrCloseFullscreenDialog(true)

        binding.player.updateMarginsByFullscreenMode()

        if (animate) animateFullscreen(enter = true) {}
    }

    @SuppressLint("SourceLockedOrientationActivity")
    fun unsetFullscreen(animate: Boolean = false) {
        if (activity == null || _binding == null) return

        finishFullscreenTransition?.invoke()
        commonPlayerViewModel.isFullscreen.value = false

        if (!PlayerHelper.autoFullscreenEnabled) {
            baseActivity.requestedOrientation = baseActivity.screenOrientationPref
        }

        val leaveFullscreenWindow = {
            openOrCloseFullscreenDialog(false)
            binding.player.updateMarginsByFullscreenMode()
        }
        // the page is still laid out as when fullscreen started: the video can travel back
        if (animate && fullscreenDialog.isShowing &&
            inlineOrientation == resources.configuration.orientation
        ) {
            animateFullscreen(enter = false, onEnd = leaveFullscreenWindow)
        } else {
            leaveFullscreenWindow()
        }

        // Leaving fullscreen while the device is still held in landscape causes no
        // configuration change (the activity already is in landscape), so the player kept its
        // portrait layout: a cropped video overflowing the screen. Check again once the
        // restored orientation request has been applied; it is a no-op when a rotation back to
        // the layout orientation happens (or already triggered the check) in the meantime.
        handler.postDelayed(ORIENTATION_SETTLE_DELAY_MS) {
            if (_binding == null ||
                PictureInPictureCompat.isInPictureInPictureMode(requireActivity()) ||
                // the screen is already rotated but the activity did not get the matching
                // configuration yet: onConfigurationChanged() will take care of it
                displayOrientation() != resources.configuration.orientation
            ) return@postDelayed
            restartActivityIfNeeded()
        }
    }

    /**
     * Orientation the display is physically rotated to right now, which the system applies
     * before it delivers the matching configuration to the activity.
     */
    private fun displayOrientation(): Int {
        val display = ContextCompat.getDisplayOrDefault(requireActivity())
        val naturalLandscape = display.mode.physicalWidth > display.mode.physicalHeight
        val quarterTurn = display.rotation == Surface.ROTATION_90 ||
            display.rotation == Surface.ROTATION_270
        return if (naturalLandscape != quarterTurn) Configuration.ORIENTATION_LANDSCAPE
        else Configuration.ORIENTATION_PORTRAIT
    }

    override fun toggleFullscreen() {
        binding.player.hideController()

        val isFullscreen = commonPlayerViewModel.isFullscreen.value == true
        if (!isFullscreen) {
            setFullscreen(animate = !fullscreenRotatesScreen())
        } else {
            unsetFullscreen(animate = true)
        }
    }

    /** Whether going fullscreen turns the screen: the system animates that rotation itself. */
    private fun fullscreenRotatesScreen(): Boolean {
        if (PlayerHelper.autoFullscreenEnabled || !this::streams.isInitialized) return false
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return when (PlayerHelper.getFullscreenOrientation(streams.isShort)) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> !landscape
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT -> landscape
            else -> false
        }
    }

    /** The video frame of the player in screen pixels, its transformations ignored. */
    private fun videoBoundsOnScreen(): Rect {
        val player = binding.player
        val frame = player.backgroundBinding.exoContentFrame
        val bounds = Rect(0, 0, frame.width, frame.height)
        player.offsetDescendantRectToMyCoords(frame, bounds)
        val origin = IntArray(2).also { player.getLocationOnScreen(it) }
        bounds.offset(origin[0], origin[1])
        return bounds
    }

    /**
     * Continuity between the page and the fullscreen window, instead of a cut: the video travels
     * from its place in the page to its fullscreen place (or back), the black around it is
     * cropped until it unfolds, and the backdrop fades over the page. [onEnd] runs at the end
     * (right away when there is nothing to animate).
     */
    private fun animateFullscreen(enter: Boolean, onEnd: () -> Unit) {
        val inline = inlineVideoBounds?.takeUnless { it.isEmpty } ?: return onEnd()
        val player = binding.player
        var animator: ValueAnimator? = null
        var finished = false
        val finish = {
            if (!finished) {
                finished = true
                finishFullscreenTransition = null
                animator?.removeAllListeners()
                animator?.cancel()
                // leaving, the player moves back to the page first: resetting it while still in
                // the fullscreen window would flash it there for a frame
                onEnd()
                player.scaleX = 1f
                player.scaleY = 1f
                player.translationX = 0f
                player.translationY = 0f
                player.resetPivot()
                player.clipBounds = null
                fullscreenBackdrop.alpha = 255
            }
        }
        finishFullscreenTransition = finish
        if (enter) fullscreenBackdrop.alpha = 0

        // entering, the fullscreen window is measured during its first layout, before any frame
        player.doOnLayout {
            if (finished) return@doOnLayout
            val full = videoBoundsOnScreen()
            if (full.isEmpty) return@doOnLayout finish()
            val origin = IntArray(2).also { player.getLocationOnScreen(it) }
            val scale = inline.width().toFloat() / full.width()
            val dx = inline.exactCenterX() - full.exactCenterX()
            val dy = inline.exactCenterY() - full.exactCenterY()
            val video = Rect(full).apply { offset(-origin[0], -origin[1]) }
            val clip = Rect()
            fun lerp(from: Int, to: Int, f: Float) = (from + (to - from) * f).roundToInt()

            player.pivotX = video.exactCenterX()
            player.pivotY = video.exactCenterY()
            // 0 = the video in its place in the page, 1 = fullscreen
            animator = ValueAnimator.ofFloat(if (enter) 0f else 1f, if (enter) 1f else 0f).apply {
                duration = FULLSCREEN_TRANSITION_MS
                // Material 3 "emphasized" easing
                interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                addUpdateListener {
                    val f = it.animatedValue as Float
                    val s = scale + (1 - scale) * f
                    player.scaleX = s
                    player.scaleY = s
                    player.translationX = dx * (1 - f)
                    player.translationY = dy * (1 - f)
                    clip.set(
                        lerp(video.left, 0, f), lerp(video.top, 0, f),
                        lerp(video.right, player.width, f), lerp(video.bottom, player.height, f)
                    )
                    player.clipBounds = clip
                    fullscreenBackdrop.alpha = lerp(0, 255, f)
                }
                doOnEnd { finish() }
                start()
            }
        }
    }

    private fun openOrCloseFullscreenDialog(open: Boolean) {
        val playerView = binding.player
        (playerView.parent as ViewGroup).removeView(playerView)

        if (open) {
            fullscreenBackdrop.alpha = 255
            fullscreenDialog.addContentView(
                binding.player,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )
            // configured before it is shown, so its first frame is already fullscreen; a
            // dismissed dialog has no window on screen, closing needs nothing
            WindowHelper.applyFullscreen(fullscreenDialog.window!!)
            fullscreenDialog.show()
            // Force the dialog window to fill the whole screen. Without this it keeps its
            // default (smaller) size, so the player - and therefore the seek bar - only
            // reaches part of the way down, leaving an empty band below the controls.
            fullscreenDialog.window?.setLayout(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            playerView.currentWindow = fullscreenDialog.window
        } else {
            binding.playerMotionLayout.addView(playerView)
            playerView.currentWindow = null
            fullscreenDialog.dismiss()
        }
    }

    override fun onPause() {
        super.onPause()

        // battery: le tick du mini-lecteur ne sert à rien hors de l'avant-plan
        // (écran éteint ou app en arrière-plan) — on l'arrête, il repart dans onResume
        handler.removeCallbacks(miniProgressUpdater)

        if (!::playerController.isInitialized) return

        val isInteractive = requireContext().getSystemService<PowerManager>()?.isInteractive ?: true

        if (!isInteractive && !isEnteringPiPMode) {
            setAutoPlayCountdownEnabled(false)
            setVideoTrackTypeDisabled(true)
        }

        if (PlayerHelper.pausePlayerOnScreenOffEnabled && !isInteractive && !isEnteringPiPMode) {
            playerController.pause()
        }

        isEnteringPiPMode = false
    }

    override fun onResume() {
        super.onResume()

        if (closedVideo) {
            closedVideo = false
        }

        if (!::playerController.isInitialized) return

        setAutoPlayCountdownEnabled(PlayerHelper.autoPlayCountdown)
        setVideoTrackTypeDisabled(false)

        // the skip button loop stopped while unseen
        checkForSegments()

        // battery: relancer le tick du mini-lecteur seulement s'il est réellement affiché
        if (commonPlayerViewModel.isMiniPlayerVisible.value == true) {
            handler.removeCallbacks(miniProgressUpdater)
            handler.post(miniProgressUpdater)
        }
    }

    private fun setAutoPlayCountdownEnabled(enabled: Boolean) {
        if (!::playerController.isInitialized) return

        this.autoPlayCountdownEnabled = enabled

        playerController.sendCustomCommand(
            AbstractPlayerService.runPlayerActionCommand,
            Bundle().apply {
                putBoolean(PlayerCommand.SET_AUTOPLAY_COUNTDOWN_ENABLED.name, enabled)
            }
        )
    }

    private fun setVideoTrackTypeDisabled(disabled: Boolean) {
        if (!::playerController.isInitialized) return

        playerController.sendCustomCommand(
            AbstractPlayerService.runPlayerActionCommand,
            Bundle().apply {
                putBoolean(PlayerCommand.SET_VIDEO_TRACK_TYPE_DISABLED.name, disabled)
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()

        handler.removeCallbacksAndMessages(null)

        if (::playerController.isInitialized && playerController.isConnected) {
            playerController.removeListener(playerListener)
            playerController.pause()

            playerController.sendCustomCommand(
                AbstractPlayerService.stopServiceCommand,
                Bundle.EMPTY
            )
            playerController.release()
        }

        runCatching {
            PictureInPictureCompat
                .setPictureInPictureParams(requireActivity(), pipParams)
        }

        finishFullscreenTransition?.invoke()

        runCatching {
            if (fullscreenDialog.isShowing) fullscreenDialog.dismiss()
        }

        runCatching {
            context?.unregisterReceiver(playerActionReceiver)
        }

        castSessionListener?.let { listener ->
            castContext?.let { cast ->
                runCatching {
                    cast.sessionManager.removeSessionManagerListener(
                        listener,
                        CastSession::class.java
                    )
                }
            }
        }
        castSessionListener = null

        baseActivity.requestOrientationChange()

        _binding = null
    }

    private fun killPlayerFragment() {
        // a second close request (double tap on X, end of the swipe-out animation) may come
        // in once the view is already gone
        val binding = _binding ?: return
        binding.playerMotionLayout.transitionToEnd()

        commonPlayerViewModel.isMiniPlayerVisible.value = false

        if (commonPlayerViewModel.isFullscreen.value == true) {
            binding.playerMotionLayout.addTransitionListener(object : TransitionAdapter() {
                override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
                    super.onTransitionCompleted(motionLayout, currentId)

                    baseActivity.supportFragmentManager.commit {
                        remove(this@PlayerFragment)
                    }
                }
            })

            unsetFullscreen()
        } else {
            baseActivity.supportFragmentManager.commit {
                remove(this@PlayerFragment)
            }
        }
    }

    private val segmentsChecker = Runnable { checkForSegments() }

    private fun checkForSegments() {
        // a single loop: play / pause / play within one tick used to leave two of them running
        handler.removeCallbacks(segmentsChecker)
        if (!::playerController.isInitialized || !playerController.isPlaying || !PlayerHelper.sponsorBlockEnabled) return
        // battery: the loop only drives the skip button (the service does the skipping), so it
        // stops while unseen or for a video without segments, most of them; onResume() and the
        // arrival of segments start it again
        if (viewModel.segments.value.isNullOrEmpty()) {
            playerBackgroundBinding.sbSkipBtn.isGone = true
            return
        }
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return

        // battery: 200 ms au lieu de 100 ms — latence de saut imperceptible, mais
        // deux fois moins de réveils du thread principal pendant toute la lecture
        handler.postDelayed(segmentsChecker, 200)

        val segmentData = playerController.getCurrentSegment(
            viewModel.segments.value.orEmpty(),
            viewModel.sponsorBlockConfig
        )

        if (segmentData != null && commonPlayerViewModel.isMiniPlayerVisible.value != true) {
            val (segment, sbSkipOption) = segmentData

            val autoSkipTemporarilyDisabled =
                !binding.player.sponsorBlockAutoSkip && sbSkipOption != SbSkipOptions.OFF

            if (sbSkipOption in arrayOf(
                    SbSkipOptions.AUTOMATIC_ONCE,
                    SbSkipOptions.MANUAL
                ) || autoSkipTemporarilyDisabled
            ) {
                if (!PictureInPictureCompat.isInPictureInPictureMode(requireActivity())) {
                    playerBackgroundBinding.sbSkipBtn.isVisible = true
                }
                playerBackgroundBinding.sbSkipBtn.setOnClickListener {
                    playerController.seekTo((segment.segmentStartAndEnd.second * 1000f).toLong())
                    segment.skipped = true
                }
            }
        } else {
            playerBackgroundBinding.sbSkipBtn.isGone = true
        }
    }

    private fun setPlayerDefaults() {
        if (!::playerController.isInitialized) return

        playerControlsBinding.exoProgress.clearSegments()
        playerControlsBinding.sbToggle.isGone = true

        commentsViewModel.reset()

        playerBackgroundBinding.sbSkipBtn.isGone = true

        playerController.sendCustomCommand(
            AbstractPlayerService.runPlayerActionCommand,
            Bundle().apply {
                putInt(PlayerCommand.SET_AUDIO_ROLE_FLAGS.name, C.ROLE_FLAG_MAIN)
            }
        )

        setAutoPlayCountdownEnabled(PlayerHelper.autoPlayCountdown)

        binding.player.updateCurrentSubtitle(viewModel.currentCaptionId)

        binding.player.setToDefaultResolution()

        if (streams.category == Streams.CATEGORY_MUSIC) {
            playerController.setPlaybackSpeed(1f)
        }
    }

    fun playVideo(videoId: String) {
        if (!::playerController.isInitialized) return

        // Nouvelle vidéo (navigation, file d'attente) : on réarme les garde-fous de
        // récupération d'erreur. Une ré-extraction de la vidéo courante (même id, déclenchée
        // depuis onPlayerError) les laisse inchangés, garantissant une seule ré-extraction par
        // vidéo et évitant la boucle « erreur → redémarrage » signalée par les utilisateurs.
        if (!this::videoId.isInitialized || this.videoId != videoId) {
            playbackErrorRetries = 0
            hasReExtractedOnError = false
        }

        playerController.sendCustomCommand(
            AbstractPlayerService.runPlayerActionCommand,
            Bundle().apply {
                putString(PlayerCommand.PLAY_VIDEO_BY_ID.name, videoId)
            }
        )
    }

    fun playNextVideo() {
        if (!::playerController.isInitialized) return

        playerController.sendCustomCommand(
            AbstractPlayerService.runPlayerActionCommand,
            Bundle().apply {
                putString(PlayerCommand.PLAY_NEXT_VIDEO.name, "")
            }
        )
    }

    private fun dismissCommentsSheet() {
        childFragmentManager.fragments
            .filterIsInstance<CommentsSheet>()
            .firstOrNull()
            ?.dismiss()
    }

    private fun toggleVideoInfoVisibility(show: Boolean) {
        binding.descriptionLayout.collapseDescription()
        binding.descriptionLayout.isInvisible = !show
        binding.relatedRecView.isInvisible = !show
        binding.playerChannel.isInvisible = !show
        binding.watubeNextTitle.isInvisible = !show || !hasRelatedStreams
        playerBackgroundBinding.videoTransitionProgress.isVisible = !show
    }

    private fun connectToPlayerView(player: Player) {
        binding.player.initialize(
            chaptersViewModel,
            commonPlayerViewModel,
            viewModel,
            viewLifecycleOwner,
            this,
            player
        )
    }

    @SuppressLint("SetTextI18n")
    private fun updatePlayerView() {
        dismissCommentsSheet()

        setPlayerDefaults()

        binding.player.useController = false

        if (binding.playerMotionLayout.progress != 1.0f) {
            val inPipMode = PictureInPictureCompat.isInPictureInPictureMode(requireActivity())
            if (!inPipMode) {
                binding.player.useController = true
            }
        }

        viewModel.isOrientationChangeInProgress = false
        // a rotation that arrived while the previous recreation was still in progress was
        // skipped by restartActivityIfNeeded(): catch up now, or the layout of the wrong
        // orientation would stay on screen (never from picture-in-picture, whose window has
        // its own orientation)
        if (!PictureInPictureCompat.isInPictureInPictureMode(requireActivity())) {
            restartActivityIfNeeded()
        }

        binding.descriptionLayout.setStreams(streams)

        toggleVideoInfoVisibility(true)

        binding.apply {
            ImageHelper.loadImage(streams.uploaderAvatar, binding.playerChannelImage, true)
            binding.playerChannelImage.isVisible = streams.uploaderAvatar != null

            playerChannelName.text = streams.uploader
            titleTextView.text = streams.title

            playerChannelSubCount.text = context?.getString(
                R.string.subscribers,
                streams.uploaderSubscriberCount.formatShort()
            )
            playerChannelSubCount.isVisible = streams.uploaderSubscriberCount >= 0

            relPlayerDownload.isVisible = !streams.isLive && !isOffline

            relPlayerCast.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !isOffline && !castUnavailable
        }
        playerControlsBinding.exoTitle.text = streams.title
        updateOverlayChips()

        chaptersViewModel.chaptersLiveData.postValue(streams.chapters)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            showRelatedStreams()
        }

        if (streams.uploaderUrl != null) {
            binding.playerSubscribe.setupSubscriptionButton(
                streams.uploaderUrl!!.toID(),
                streams.uploader,
                streams.uploaderAvatar,
                streams.uploaderVerified
            )
        } else {
            binding.playerSubscribe.isGone = true
        }

        playerControlsBinding.seekbarPreview.isGone = true
        seekBarPreviewListener?.let { playerControlsBinding.exoProgress.removeSeekBarListener(it) }

        viewLifecycleOwner.lifecycleScope.launch {
            val timeFrameReceiver = getTimeFrameReceiver() ?: return@launch
            val listener = SeekbarPreviewListener(
                timeFrameReceiver,
                playerControlsBinding,
                streams.duration * 1000
            )

            seekBarPreviewListener = listener
            playerControlsBinding.exoProgress.addSeekBarListener(listener)
        }

        if (binding.playerMotionLayout.progress == 0f && PlayerHelper.autoFullscreenShortsEnabled && streams.isShort) {
            setFullscreen()
        }

        getHighlight(viewModel.segments.value.orEmpty())?.let {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { initializeHighlight(it) }
        }
    }

    /** Met à jour les pastilles flottantes du contrôleur : chapitre, vitesse de lecture, qualité. */
    private fun updateOverlayChips() {
        if (_binding == null || !::playerController.isInitialized) return
        val controls = playerControlsBinding

        val chapters = chaptersViewModel.chapters
        if (chapters.isEmpty()) {
            controls.watubeChapterChip.isGone = true
        } else {
            val index = (chaptersViewModel.currentChapterIndex.value ?: 0)
                .coerceIn(0, chapters.size - 1)
            controls.watubeChapterChip.isVisible = true
            controls.watubeChapterChip.text =
                getString(R.string.player_chapter_chip, index + 1, chapters.size)
        }

        controls.watubeSpeedChip.isVisible = true
        controls.watubeSpeedChip.text = getString(
            R.string.player_speed_chip,
            speedFormat.format(playerController.playbackParameters.speed.toDouble())
        )

        val videoHeight = playerController.videoSize.height
        controls.watubeQualityChip.isVisible = videoHeight > 0
        if (videoHeight > 0) {
            controls.watubeQualityChip.text =
                getString(
                    R.string.player_quality_chip,
                    VideoResolution.snapToStandardHeight(videoHeight)
                )
        }
    }

    private suspend fun showRelatedStreams() {
        if (!PlayerHelper.relatedStreamsEnabled) return

        val relatedStreams = if (isOffline) {
            withContext(Dispatchers.IO) {
                DatabaseHolder.Database.downloadDao().getAll()
                    .filter { it.download.videoId != videoId }
                    .map { it.download.toStreamItem() }
            }
        } else {
            streams.relatedStreams.filter { !it.title.isNullOrBlank() }
        }

        withContext(Dispatchers.Main) {
            val binding = _binding ?: return@withContext
            val relatedLayoutManager = binding.relatedRecView.layoutManager as LinearLayoutManager

            hasRelatedStreams = relatedStreams.isNotEmpty()
            binding.watubeNextTitle.isInvisible =
                !hasRelatedStreams || !binding.descriptionLayout.isVisible

            binding.relatedRecView.adapter = VideoCardsAdapter(
                columnWidthDp = if (relatedLayoutManager.orientation == LinearLayoutManager.HORIZONTAL) 250f else null
            ).also { adapter ->
                adapter.submitList(relatedStreams)
            }
        }
    }

    private fun showAutoPlayCountdown() {
        if (!PlayingQueue.hasNext()) return

        disableController()
        playerBackgroundBinding.autoplayCountdown.setHideSelfListener {
            runCatching {
                playerBackgroundBinding.autoplayCountdown.isGone = true
                binding.player.useController = true
            }
        }
        playerBackgroundBinding.autoplayCountdown.startCountdown {
            playNextVideo()
        }
    }

    private fun handleLink(link: String) {
        val uri = link.toUri()
        val videoId = TextUtils.getVideoIdFromUri(uri)

        if (videoId.isNullOrEmpty()) {
            // description links are untrusted: never hand a foreign scheme to another app
            val ctx = context ?: return
            if (!IntentHelper.isAllowedLink(link)) {
                ctx.toastFromMainThread(R.string.error)
                return
            }

            val intent = Intent(Intent.ACTION_VIEW, uri)

            onUserLeaveHint()
            try {
                startActivity(intent)
            } catch (_: Exception) {
                ctx.toastFromMainThread(R.string.error)
            }

            return
        }

        if (videoId == this.videoId) {
            uri.getQueryParameter("t")?.toTimeInSeconds()?.let {
                if (::playerController.isInitialized) {
                    playerController.seekTo(it * 1000)
                }
            }
        } else {
            playVideo(videoId)
        }
    }

    private fun updatePlayPauseButton() {
        if (!::playerController.isInitialized) return
        val playPauseAction = PlayerHelper.getPlayPauseActionIcon(playerController)
        binding.playImageView.setImageResource(playPauseAction)
    }

    private suspend fun getTimeFrameReceiver(): TimeFrameReceiver? = withContext(Dispatchers.IO) {
        return@withContext if (isOffline) {
            if (!::videoId.isInitialized) return@withContext null

            val downloadItems =
                DatabaseHolder.Database.downloadDao().getDownloadById(videoId)?.downloadItems
            downloadItems?.firstOrNull { it.path.exists() && it.type == FileType.VIDEO }?.path?.let {
                OfflineTimeFrameReceiver(requireContext(), it)
            }
        } else {
            if (!::streams.isInitialized) return@withContext null

            OnlineTimeFrameReceiver(requireContext(), streams.previewFrames)
        }
    }

    private fun getHighlight(segments: List<Segment>): Segment? {
        return segments.firstOrNull { it.category == PlayerHelper.SPONSOR_HIGHLIGHT_CATEGORY }
    }

    private suspend fun initializeHighlight(highlight: Segment) {
        val frameReceiver = getTimeFrameReceiver() ?: return

        val highlightStart = highlight.segmentStartAndEnd.first.toLong()
        val frame = withContext(Dispatchers.IO) {
            frameReceiver.getFrameAtTime(highlightStart * 1000)
        }
        val highlightChapter = ChapterSegment(
            title = getString(R.string.chapters_videoHighlight),
            start = highlightStart,
            highlightDrawable = frame?.toDrawable(requireContext().resources)
        )
        chaptersViewModel.chaptersLiveData.postValue(
            chaptersViewModel.chapters.plus(highlightChapter).sortedBy { it.start }
        )
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun changeOrientationMode() {
        if (PlayerHelper.autoFullscreenEnabled) {
            baseActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            baseActivity.requestedOrientation =
                (requireActivity() as BaseActivity).screenOrientationPref
        }
    }


    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        // dispatché aussi pendant la transition qui suit onDestroyView : sans cette
        // garde l'accès au binding (et aux vues dérivées) provoquait un NPE
        val binding = _binding ?: return
        if (isInPictureInPictureMode) {
            disableController()

            binding.player.updateCurrentSubtitle(null)
            playerBackgroundBinding.sbSkipBtn.isGone = true

            openOrCloseFullscreenDialog(true)
            pipActivity = activity
        } else {
            binding.player.useController = true

            if (lifecycle.currentState == Lifecycle.State.CREATED) {
                if (::playerController.isInitialized) {
                    playerController.pause()
                }
                closedVideo = true
            }

            binding.player.updateCurrentSubtitle(viewModel.currentCaptionId)

            if (commonPlayerViewModel.isFullscreen.value != true) {
                openOrCloseFullscreenDialog(false)
            }
        }
    }

    fun onUserLeaveHint() {
        if (shouldStartPiP()) {
            PictureInPictureCompat.enterPictureInPictureMode(requireActivity(), pipParams)
        }
    }

    private val pipParams: PictureInPictureParamsCompat
        get() = run {
            val isPlaying = ::playerController.isInitialized && playerController.isPlaying

            if (::playerController.isInitialized &&
                playerController.videoSize.width > 0 &&
                playerController.videoSize.height > 0
            ) {
                lastPipVideoSize = playerController.videoSize
            }

            PictureInPictureParamsCompat.Builder()
                .setActions(
                    PlayerHelper.getPiPModeActions(
                        requireActivity(),
                        isPlaying
                    )
                )
                .setAutoEnterEnabled(isPlaying && PlayerHelper.autoPipEnabled)
                .apply {
                    // On ne retire JAMAIS l'aspect ratio : sans lui le système
                    // re-border la fenêtre PiP avec le ratio par défaut (écran),
                    // ce qui cassait l'affichage après réouverture de l'application.
                    lastPipVideoSize?.let { setAspectRatio(it) }
                }
                .build()
        }

    private fun isPipAvailable() =
        PictureInPictureCompat.isPictureInPictureAvailable(requireContext())
                && PictureInPictureCompat.isPictureInPictureEnabled(requireContext())

    private fun shouldStartPiP(): Boolean {
        return PlayerHelper.autoPipEnabled && isPipAvailable() && ::playerController.isInitialized && playerController.isPlaying
    }

    private fun restartActivityIfNeeded() {
        if (baseActivity.screenOrientationPref in lockedOrientations || viewModel.isOrientationChangeInProgress) return

        val orientation = resources.configuration.orientation
        if (commonPlayerViewModel.isFullscreen.value != true && orientation != playerLayoutOrientation) {
            playerLayoutOrientation = orientation

            viewModel.isOrientationChangeInProgress = true

            binding.player.detachPlayer()

            if (::playerController.isInitialized) playerController.release()

            activity?.recreate()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // a rotation relays everything out: the trip the animation computed is void
        finishFullscreenTransition?.invoke()

        if (_binding == null ||
            PictureInPictureCompat.isInPictureInPictureMode(requireActivity())
        ) {
            return
        }

        if (PlayerHelper.autoFullscreenEnabled) {
            when (newConfig.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> setFullscreen()
                else -> unsetFullscreen()
            }
        }

        restartActivityIfNeeded()
    }

    private fun disableController() {
        binding.player.useController = false
        binding.player.hideController()
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return _binding?.player?.onKeyUp(keyCode, event) ?: false
    }

    /** Ticks the mini-player progress bar while the player is collapsed. */
    private val miniProgressUpdater = object : Runnable {
        override fun run() {
            val binding = _binding ?: return
            if (::playerController.isInitialized) {
                val duration = playerController.duration
                if (duration > 0) {
                    binding.miniplayerProgress.progress =
                        (playerController.currentPosition * 1000 / duration).toInt()
                }
            }
            handler.postDelayed(this, 500L)
        }
    }

    /**
     * Applies the collapsed mini-player chrome: the video thumbnail gets rounded corners
     * matching the card, and a discreet progress bar tracks playback. Both are reverted
     * (square corners, hidden bar) once the player is expanded again.
     */
    private fun updateMiniPlayerChrome(mini: Boolean) {
        if (_binding == null) return

        binding.player.clipToOutline = mini
        binding.player.outlineProvider = if (mini) {
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(
                        0, 0, view.width, view.height,
                        resources.getDimension(R.dimen.watube_mini_player_video_radius)
                    )
                }
            }
        } else {
            ViewOutlineProvider.BACKGROUND
        }

        binding.miniplayerProgress.isVisible = mini
        handler.removeCallbacks(miniProgressUpdater)
        if (mini) handler.post(miniProgressUpdater)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(miniProgressUpdater)
        _binding = null
    }

    override fun getVideoId(): String {
        return videoId
    }

    override fun isVideoShort(): Boolean {
        return ::streams.isInitialized && streams.isShort
    }

    override fun isVideoLive(): Boolean {
        return ::streams.isInitialized && streams.isLive
    }
}