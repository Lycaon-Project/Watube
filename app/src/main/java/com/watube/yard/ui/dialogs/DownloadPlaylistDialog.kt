package com.watube.yard.ui.dialogs

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.watube.yard.R
import com.watube.yard.api.MediaServiceRepository
import com.watube.yard.api.PlaylistsHelper
import com.watube.yard.api.obj.StreamItem
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.DialogDownloadPlaylistBinding
import com.watube.yard.enums.PlaylistType
import com.watube.yard.extensions.TAG
import com.watube.yard.extensions.dpToPx
import com.watube.yard.extensions.getWhileDigit
import com.watube.yard.extensions.serializable
import com.watube.yard.extensions.toID
import com.watube.yard.helpers.ErrorMessages
import com.watube.yard.helpers.LocaleHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.obj.VideoResolution
import com.watube.yard.services.PlaylistDownloadEnqueueService
import com.watube.yard.ui.adapters.DownloadPlaylistVideosAdapter
import com.watube.yard.ui.adapters.DownloadQualityAdapter
import com.watube.yard.ui.adapters.QualityOption
import com.watube.yard.ui.sheets.ExpandedBottomSheet
import com.watube.yard.ui.views.DownloadMotion
import com.watube.yard.ui.views.Efecto
import com.watube.yard.util.PlayingQueue
import com.watube.yard.util.TextUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// region Downloads
/**
 * The download sheet of a playlist, in the bottom bar's glass, in two steps. First the videos:
 * only the one playing is ticked when the sheet opens, the user ticks the others (or all of
 * them). Next, one format for every ticked video: Video or Audio only, a maximum quality card,
 * the audio language and the captions under More options. Only the ticked videos are queued.
 */
class DownloadPlaylistDialog : ExpandedBottomSheet(R.layout.dialog_download_playlist) {
    private lateinit var playlistId: String
    private lateinit var playlistName: String
    private lateinit var playlistType: PlaylistType
    private var binding: DialogDownloadPlaylistBinding? = null
    private var glassApplied = false

    private val availableLanguages = LocaleHelper.getAvailableLocales()

    /** The playlist's videos, in order, without repeats. */
    private var videos: List<StreamItem> = emptyList()
    private val checked = linkedSetOf<String>()
    private var restoredChecks = false
    private var audioOnly = false

    private lateinit var videosAdapter: DownloadPlaylistVideosAdapter
    private val qualityAdapter = DownloadQualityAdapter { updateDownloadButton() }

    private val videoQualities by lazy {
        // without the automatic quality entry
        resources.getStringArray(R.array.defres).drop(1)
    }
    private val audioQualities by lazy {
        resources.getStringArray(R.array.audioQualityBitrates).toList()
    }

    /** Back on the format step goes back to the videos, not out of the sheet. */
    private val backToVideos = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = showVideosStep(animate = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val arguments = requireArguments()
        playlistId = arguments.getString(IntentData.playlistId).orEmpty()
        playlistName = arguments.getString(IntentData.playlistName)
            ?: TextUtils.getFileSafeTimeStampNow()
        playlistType = arguments.serializable(IntentData.playlistType) ?: PlaylistType.PUBLIC

        savedInstanceState?.getStringArrayList(STATE_CHECKED)?.let {
            checked.addAll(it)
            restoredChecks = true
        }
        videosAdapter = DownloadPlaylistVideosAdapter(checked) { updateSelection() }
    }

    override fun onStart() {
        super.onStart()
        if (!glassApplied) {
            (dialog as? BottomSheetDialog)?.let(Efecto::applyToSheet)
            glassApplied = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = DialogDownloadPlaylistBinding.bind(view).also { binding = it }
        (dialog as? ComponentDialog)?.onBackPressedDispatcher
            ?.addCallback(viewLifecycleOwner, backToVideos)

        binding.playlistTitle.text = playlistName
        binding.formatTitle.text = playlistName
        binding.videoList.layoutManager = LinearLayoutManager(requireContext())
        binding.videoList.adapter = videosAdapter
        binding.playlistQualityGrid.layoutManager = GridLayoutManager(requireContext(), GRID_COLUMNS)
        binding.playlistQualityGrid.adapter = qualityAdapter
        binding.playlistQualityGrid.itemAnimator = null

        binding.toggleAll.setOnClickListener { toggleAll() }
        binding.videosRetry.setOnClickListener { loadVideos() }
        binding.nextButton.setOnClickListener { showFormatStep(animate = true) }
        binding.backButton.setOnClickListener { showVideosStep(animate = true) }
        binding.playlistMoreOptions.setOnClickListener {
            DownloadMotion.disclose(binding.playlistMoreOptions, binding.playlistOptions)
        }
        binding.playlistDownloadButton.setOnClickListener { confirm() }

        audioOnly = PreferenceHelper.getBoolean(PLAYLIST_DOWNLOAD_AUDIO_ONLY, false)
        binding.formatMode.check(if (audioOnly) R.id.format_audio else R.id.format_video)
        binding.formatMode.addOnButtonCheckedListener { _, id, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            audioOnly = id == R.id.format_audio
            showQualities(animate = true)
        }
        setUpLanguages(binding)

        loadVideos()
        if (savedInstanceState?.getBoolean(STATE_FORMAT_STEP) == true) {
            showFormatStep(animate = false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_CHECKED, ArrayList(checked))
        outState.putBoolean(STATE_FORMAT_STEP, backToVideos.isEnabled)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    // Step 1: the videos
    private fun loadVideos() {
        val binding = binding ?: return
        binding.videosError.isGone = true
        DownloadMotion.pulse(binding.videosSkeleton)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val first = withContext(Dispatchers.IO) { PlaylistsHelper.getPlaylist(playlistId) }
                addVideos(first.relatedStreams)
                // a public playlist comes in pages: they keep arriving while the user ticks
                var nextPage = first.nextpage.takeIf { playlistType == PlaylistType.PUBLIC }
                while (nextPage != null) {
                    val pageToken: String = nextPage
                    val page = withContext(Dispatchers.IO) {
                        MediaServiceRepository.instance.getPlaylistNextPage(playlistId, pageToken)
                    }
                    addVideos(page.relatedStreams)
                    nextPage = page.nextpage
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG(), e.stackTraceToString())
                // the pages already there stay usable; only an empty list shows the error
                if (videos.isEmpty()) showVideosError(getString(ErrorMessages.of(e)))
            }
            if (videos.isEmpty() && binding.videosError.isGone) showVideosError(null)
            updateSelection()
        }
    }

    private fun addVideos(page: List<StreamItem>) {
        val binding = binding ?: return
        val firstPage = videos.isEmpty()
        val known = videos.mapNotNullTo(HashSet()) { it.url?.toID() }
        videos = videos + page.filter { item ->
            val id = item.url?.toID() ?: return@filter false
            known.add(id)
        }
        if (firstPage && !restoredChecks) preCheckPlaying()
        videosAdapter.submit(videos)
        fitVideoList(binding)

        if (firstPage && videos.isNotEmpty()) {
            DownloadMotion.stopPulse(binding.videosSkeleton)
            binding.videosSkeleton.isGone = true
            DownloadMotion.staggerIn(binding.videoList)
            scrollToFirstChecked(binding)
        }
        updateSelection()
    }

    /** The video playing, when it belongs to this playlist, starts ticked; nothing else. */
    private fun preCheckPlaying() {
        val playingId = PlayingQueue.getCurrent()?.url?.toID() ?: return
        if (videos.any { it.url?.toID() == playingId }) checked.add(playingId)
    }

    private fun scrollToFirstChecked(binding: DialogDownloadPlaylistBinding) {
        val index = videos.indexOfFirst { it.url?.toID() in checked }
        if (index > 0) binding.videoList.scrollToPosition(index)
    }

    /** A long playlist scrolls in half of the screen; a short one takes its own height. */
    private fun fitVideoList(binding: DialogDownloadPlaylistBinding) {
        val maxHeight = (resources.displayMetrics.heightPixels * LIST_SCREEN_SHARE).toInt()
        val contentHeight = videos.size * ROW_HEIGHT_DP.dpToPx()
        binding.videoList.updateLayoutParams<ViewGroup.LayoutParams> {
            height = if (contentHeight > maxHeight) maxHeight else ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    private fun showVideosError(message: String?) {
        val binding = binding ?: return
        DownloadMotion.stopPulse(binding.videosSkeleton)
        binding.videosSkeleton.isGone = true
        binding.videosErrorText.text = listOfNotNull(
            getString(R.string.download_playlist_empty),
            message?.takeIf { it.isNotBlank() }
        ).joinToString("\n")
        binding.videosError.isVisible = true
    }

    private fun toggleAll() {
        val ids = videos.mapNotNull { it.url?.toID() }
        if (checked.containsAll(ids)) checked.clear() else checked.addAll(ids)
        videosAdapter.refreshChecks()
        updateSelection()
    }

    private fun updateSelection() {
        val binding = binding ?: return
        val total = videos.size
        val count = videos.count { it.url?.toID() in checked }
        binding.selectionCount.text = if (total == 0) {
            getString(R.string.download_choose_videos)
        } else {
            resources.getQuantityString(R.plurals.download_selected_count, count, count, total)
        }
        binding.toggleAll.isEnabled = total > 0
        binding.toggleAll.setText(
            if (total > 0 && count == total) R.string.download_select_none else R.string.download_select_all
        )
        binding.nextButton.isEnabled = count > 0
        updateDownloadButton()
    }

    // Step 2: one format for all
    private fun showFormatStep(animate: Boolean) {
        val binding = binding ?: return
        backToVideos.isEnabled = true
        showQualities(animate = false)
        if (animate) {
            DownloadMotion.step(binding.stepVideos, binding.stepFormat, forward = true)
        } else {
            binding.stepVideos.isGone = true
            binding.stepFormat.isVisible = true
        }
    }

    private fun showVideosStep(animate: Boolean) {
        val binding = binding ?: return
        backToVideos.isEnabled = false
        if (animate) {
            DownloadMotion.step(binding.stepFormat, binding.stepVideos, forward = false)
        } else {
            binding.stepFormat.isGone = true
            binding.stepVideos.isVisible = true
        }
    }

    private fun showQualities(animate: Boolean) {
        val binding = binding ?: return
        val qualities = if (audioOnly) audioQualities else videoQualities
        val options = qualities.map { quality ->
            if (audioOnly) {
                val kbps = quality.getWhileDigit()?.toString() ?: quality
                QualityOption(getString(R.string.download_kbps, kbps), null, null)
            } else {
                QualityOption(quality, VideoResolution.qualityBadge(quality.getWhileDigit() ?: 0), null)
            }
        }
        val saved = PreferenceHelper.getString(
            if (audioOnly) PLAYLIST_DOWNLOAD_AUDIO_QUALITY else PLAYLIST_DOWNLOAD_VIDEO_QUALITY, ""
        )
        val selected = qualities.indexOf(saved).takeIf { it != -1 }
            ?: if (audioOnly) 0 else videoQualities.indexOf(DEFAULT_VIDEO_QUALITY).coerceAtLeast(0)
        qualityAdapter.submit(options, selected)
        if (animate) DownloadMotion.staggerIn(binding.playlistQualityGrid)
        updateDownloadButton()
    }

    private fun setUpLanguages(binding: DialogDownloadPlaylistBinding) {
        binding.audioLanguageSpinner.items =
            listOf(getString(R.string.default_language)) + availableLanguages.map { it.name }
        binding.subtitleSpinner.items =
            listOf(getString(R.string.no_subtitle)) + availableLanguages.map { it.name }

        val audioLanguage = PreferenceHelper.getString(PLAYLIST_DOWNLOAD_AUDIO_LANGUAGE, "")
        binding.audioLanguageSpinner.selectedItemPosition =
            availableLanguages.indexOfFirst { it.code == audioLanguage } + 1
        val captionLanguage = PreferenceHelper.getString(PLAYLIST_DOWNLOAD_CAPTION_LANGUAGE, "")
        binding.subtitleSpinner.selectedItemPosition =
            availableLanguages.indexOfFirst { it.code == captionLanguage } + 1
    }

    private fun updateDownloadButton() {
        val binding = binding ?: return
        val count = checkedIdsInOrder().size
        binding.playlistDownloadButton.text =
            resources.getQuantityString(R.plurals.download_videos, count, count)
        binding.playlistDownloadButton.isEnabled =
            count > 0 && qualityAdapter.selected in 0 until qualityAdapter.itemCount
    }

    private fun checkedIdsInOrder(): List<String> =
        videos.mapNotNull { it.url?.toID() }.filter { it in checked }

    private fun confirm() {
        val binding = binding ?: return
        val ids = checkedIdsInOrder()
        val quality = (if (audioOnly) audioQualities else videoQualities)
            .getOrNull(qualityAdapter.selected)
        if (ids.isEmpty() || quality == null) {
            Toast.makeText(context, R.string.nothing_selected, Toast.LENGTH_SHORT).show()
            return
        }
        // a second tap must not queue the same videos twice
        binding.playlistDownloadButton.isEnabled = false

        val maxVideoQuality = if (audioOnly) null else quality.getWhileDigit()
        // a video keeps the best audio next to it; Audio only follows its card
        val maxAudioQuality = if (audioOnly) {
            quality.getWhileDigit()
        } else {
            audioQualities.mapNotNull { it.getWhileDigit() }.maxOrNull()
        }
        val audioLanguage = availableLanguages
            .getOrNull(binding.audioLanguageSpinner.selectedItemPosition - 1)?.code
        val captionLanguage = availableLanguages
            .getOrNull(binding.subtitleSpinner.selectedItemPosition - 1)?.code
        saveSelections(quality, audioLanguage, captionLanguage)

        val intent = Intent(requireContext(), PlaylistDownloadEnqueueService::class.java)
            .putExtra(IntentData.playlistId, playlistId)
            .putExtra(IntentData.playlistType, playlistType)
            .putExtra(IntentData.playlistName, playlistName)
            .putExtra(IntentData.audioLanguage, audioLanguage)
            .putExtra(IntentData.maxVideoQuality, maxVideoQuality ?: 0)
            .putExtra(IntentData.maxAudioQuality, maxAudioQuality ?: 0)
            .putExtra(IntentData.captionLanguage, captionLanguage)
            .putStringArrayListExtra(IntentData.videoIds, ArrayList(ids))
        ContextCompat.startForegroundService(requireContext(), intent)
        dismiss()
    }

    private fun saveSelections(quality: String, audioLanguage: String?, captionLanguage: String?) {
        PreferenceHelper.putBoolean(PLAYLIST_DOWNLOAD_AUDIO_ONLY, audioOnly)
        PreferenceHelper.putString(
            if (audioOnly) PLAYLIST_DOWNLOAD_AUDIO_QUALITY else PLAYLIST_DOWNLOAD_VIDEO_QUALITY,
            quality
        )
        PreferenceHelper.putString(PLAYLIST_DOWNLOAD_AUDIO_LANGUAGE, audioLanguage.orEmpty())
        PreferenceHelper.putString(PLAYLIST_DOWNLOAD_CAPTION_LANGUAGE, captionLanguage.orEmpty())
    }

    companion object {
        private const val GRID_COLUMNS = 2
        private const val LIST_SCREEN_SHARE = 0.5f

        /** One row: a 50 dp thumbnail and its 8 dp margins. */
        private const val ROW_HEIGHT_DP = 66f
        private const val DEFAULT_VIDEO_QUALITY = "1080p"

        private const val STATE_CHECKED = "checked"
        private const val STATE_FORMAT_STEP = "format_step"

        private const val PLAYLIST_DOWNLOAD_VIDEO_QUALITY = "playlist_download_video_quality"
        private const val PLAYLIST_DOWNLOAD_AUDIO_QUALITY = "playlist_download_audio_quality"
        private const val PLAYLIST_DOWNLOAD_AUDIO_ONLY = "playlist_download_audio_only"
        private const val PLAYLIST_DOWNLOAD_AUDIO_LANGUAGE = "playlist_download_audio_language"
        private const val PLAYLIST_DOWNLOAD_CAPTION_LANGUAGE = "playlist_download_caption_language"
    }
}
// endregion
