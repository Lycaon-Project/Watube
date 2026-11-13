package com.watube.yard.ui.dialogs

import android.content.DialogInterface
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.watube.yard.R
import com.watube.yard.api.StreamsCache
import com.watube.yard.api.obj.PipedStream
import com.watube.yard.api.obj.Streams
import com.watube.yard.api.obj.Subtitle
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.DialogDownloadBinding
import com.watube.yard.db.DatabaseHolder
import com.watube.yard.extensions.TAG
import com.watube.yard.extensions.bundleOf
import com.watube.yard.extensions.getWhileDigit
import com.watube.yard.helpers.DownloadChoices
import com.watube.yard.helpers.DownloadChoices.Codec
import com.watube.yard.helpers.DownloadHelper
import com.watube.yard.helpers.ErrorMessages
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.obj.VideoResolution
import com.watube.yard.parcelable.DownloadData
import com.watube.yard.ui.adapters.DownloadQualityAdapter
import com.watube.yard.ui.adapters.QualityOption
import com.watube.yard.ui.sheets.ExpandedBottomSheet
import com.watube.yard.ui.views.DownloadMotion
import com.watube.yard.ui.views.Efecto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// region Downloads
/**
 * The download sheet of one video, in the bottom bar's glass. It opens at once (placeholder
 * cards pulse meanwhile) and shows its qualities as soon as the streams are there: right away
 * when the player just extracted them ([StreamsCache]), else after one extraction, which the
 * download then reuses. Video or Audio only, one card per quality; the codec, the audio track
 * and the captions wait under More options, already set to the last choices.
 */
class DownloadDialog : ExpandedBottomSheet(R.layout.dialog_download) {
    private lateinit var videoId: String
    private var binding: DialogDownloadBinding? = null
    private var glassApplied = false

    private var streams: Streams? = null
    private var subtitles: List<Subtitle> = emptyList()
    private var codecs: List<Codec> = emptyList()
    private var tracks: List<String?> = emptyList()
    private var videoChoices: List<PipedStream> = emptyList()
    private var audioChoices: List<PipedStream> = emptyList()

    private var audioOnly = false
    private var codec: Codec? = null
    private var audioTrack: String? = null

    private val qualityAdapter = DownloadQualityAdapter { updateDownloadButton() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoId = arguments?.getString(IntentData.videoId).orEmpty()
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
        val binding = DialogDownloadBinding.bind(view).also { binding = it }

        binding.qualityGrid.layoutManager = GridLayoutManager(requireContext(), GRID_COLUMNS)
        binding.qualityGrid.adapter = qualityAdapter
        binding.qualityGrid.itemAnimator = null

        audioOnly = PreferenceHelper.getBoolean(DOWNLOAD_AUDIO_ONLY, false)
        binding.downloadMode.check(if (audioOnly) R.id.mode_audio else R.id.mode_video)
        binding.downloadMode.addOnButtonCheckedListener { _, id, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            audioOnly = id == R.id.mode_audio
            showChoices()
        }
        binding.moreOptions.setOnClickListener {
            DownloadMotion.disclose(binding.moreOptions, binding.optionsContainer)
        }
        binding.qualityRetry.setOnClickListener { loadStreams() }
        binding.downloadButton.setOnClickListener { confirm() }

        // the player's streams, when it just played this video: the sheet is complete at once
        StreamsCache.get(videoId)?.let { showStreams(it) } ?: loadStreams()
        showAlreadyDownloaded(binding)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun loadStreams() {
        val binding = binding ?: return
        binding.qualityError.isGone = true
        DownloadMotion.pulse(binding.qualitySkeleton)
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = try {
                withContext(Dispatchers.IO) { StreamsCache.forDownload(videoId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG(), e.stackTraceToString())
                showLoadError(getString(ErrorMessages.of(e)))
                return@launch
            }
            showStreams(loaded)
        }
    }

    private fun showLoadError(message: String?) {
        val binding = binding ?: return
        DownloadMotion.stopPulse(binding.qualitySkeleton)
        binding.qualitySkeleton.isGone = true
        binding.qualityErrorText.text = listOfNotNull(
            getString(R.string.download_qualities_failed),
            message?.takeIf { it.isNotBlank() }
        ).joinToString("\n")
        binding.qualityError.isVisible = true
    }

    private fun showStreams(loaded: Streams) {
        val binding = binding ?: return
        streams = loaded
        binding.videoTitle.text = loaded.title
        binding.videoUploader.text = loaded.uploader
        ImageHelper.loadImage(loaded.thumbnailUrl, binding.videoThumbnail)

        codecs = DownloadChoices.codecsOf(loaded.videoStreams)
        tracks = DownloadChoices.audioTracks(loaded.audioStreams)
        subtitles = loaded.subtitles
            .filter { !it.url.isNullOrEmpty() && !it.name.isNullOrEmpty() }
            .sortedBy { it.name }
        setUpOptions(binding, loaded)

        DownloadMotion.stopPulse(binding.qualitySkeleton)
        binding.qualitySkeleton.isGone = true
        showChoices()
    }

    private fun setUpOptions(binding: DialogDownloadBinding, loaded: Streams) {
        codec = Codec.entries.firstOrNull { it.name == getSel(VIDEO_DOWNLOAD_CODEC) }
            ?.takeIf { it in codecs }
        binding.codecSpinner.items = listOf(getString(R.string.download_codec_auto)) +
            codecs.map { it.label }
        binding.codecSpinner.selectedItemPosition = codec?.let { codecs.indexOf(it) + 1 } ?: 0
        binding.codecSpinner.setOnSelectionChangeListener { position ->
            codec = codecs.getOrNull(position - 1)
            showChoices()
        }

        audioTrack = getSel(AUDIO_DOWNLOAD_TRACK).takeIf { it in tracks } ?: tracks.firstOrNull()
        binding.audioTrackSpinner.items = tracks.map { trackName(loaded, it) }
        binding.audioTrackSpinner.selectedItemPosition = tracks.indexOf(audioTrack).coerceAtLeast(0)
        binding.audioTrackSpinner.isVisible = tracks.size > 1
        binding.audioTrackSpinner.setOnSelectionChangeListener { position ->
            audioTrack = tracks.getOrNull(position)
            showChoices()
        }

        binding.subtitleSpinner.items = listOf(getString(R.string.no_subtitle)) +
            subtitles.map { it.name.orEmpty() }
        subtitles.indexOfFirst { it.code == getSel(SUBTITLE_LANGUAGE) }
            .takeIf { it != -1 }
            ?.let { binding.subtitleSpinner.selectedItemPosition = it + 1 }
        binding.subtitleSpinner.isVisible = subtitles.isNotEmpty()
    }

    /** The audio track's own name (English original...), else its language, else Default. */
    private fun trackName(loaded: Streams, track: String?): String =
        loaded.audioStreams.firstOrNull { it.audioTrackLocale == track }?.audioTrackName
            ?: track?.let { Locale.forLanguageTag(it).displayName }
            ?: getString(R.string.default_language)

    /** The cards of the current tab, rebuilt after a change of tab, codec or audio track. */
    private fun showChoices() {
        val binding = binding ?: return
        val loaded = streams ?: return
        val videoFormat = getSel(VIDEO_DOWNLOAD_FORMAT).takeIf { codec == null }
        videoChoices = DownloadChoices.videoChoices(loaded.videoStreams, codec, videoFormat)
        audioChoices = DownloadChoices.audioChoices(loaded.audioStreams, audioTrack)

        binding.codecSpinner.isVisible = !audioOnly && codecs.size > 1
        binding.moreOptions.isVisible = binding.codecSpinner.isVisible ||
            binding.audioTrackSpinner.isVisible || binding.subtitleSpinner.isVisible

        val (options, selected) = if (audioOnly) {
            audioChoices.map { audioOption(it) } to restoredAudioIndex()
        } else {
            val audio = mainAudio(loaded)
            videoChoices.map { videoOption(it, audio) } to restoredVideoIndex()
        }
        qualityAdapter.submit(options, selected)
        DownloadMotion.staggerIn(binding.qualityGrid)
        binding.qualityError.isVisible = options.isEmpty()
        if (options.isEmpty()) binding.qualityErrorText.setText(R.string.download_qualities_failed)
        updateDownloadButton()
    }

    private fun mainAudio(loaded: Streams) = DownloadChoices.bestAudio(
        loaded.audioStreams, audioTrack, getSel(AUDIO_DOWNLOAD_FORMAT)
    )

    private fun videoOption(stream: PipedStream, audio: PipedStream?): QualityOption {
        // YouTube's label ("1080p") gives the short side; the height of a vertical video is its long side
        val height = qualityHeight(stream)
        val fps = stream.fps?.takeIf { it > STANDARD_FPS }
        val detail = listOfNotNull(
            fps?.let { getString(R.string.download_fps, it) },
            Codec.of(stream.codec)?.label,
            DownloadChoices.totalSize(stream, audio)?.let(::formatSize)
        ).joinToString(" · ")
        return QualityOption(
            VideoResolution.qualityLabel(height),
            VideoResolution.qualityBadge(height),
            detail
        )
    }

    private fun qualityHeight(stream: PipedStream): Int =
        stream.quality.getWhileDigit() ?: stream.height ?: 0

    private fun audioOption(stream: PipedStream): QualityOption {
        val kbps = stream.quality.getWhileDigit()?.toString() ?: stream.quality.orEmpty()
        val detail = listOfNotNull(
            audioCodecName(stream),
            DownloadChoices.totalSize(null, stream)?.let(::formatSize)
        ).joinToString(" · ")
        return QualityOption(getString(R.string.download_kbps, kbps), null, detail)
    }

    private fun audioCodecName(stream: PipedStream): String? = when {
        stream.codec?.startsWith("opus", true) == true -> "Opus"
        stream.codec?.startsWith("mp4a", true) == true -> "AAC"
        else -> stream.format
    }

    private fun formatSize(bytes: Long) = Formatter.formatShortFileSize(requireContext(), bytes)

    private fun restoredVideoIndex(): Int =
        getStreamSelection(videoChoices, getSel(VIDEO_DOWNLOAD_QUALITY), getSel(VIDEO_DOWNLOAD_FORMAT))
            // first time: the best quality up to Full HD, not a 4K file by default
            ?: videoChoices.indexOfFirst { qualityHeight(it) in 1..DEFAULT_MAX_HEIGHT }
                .takeIf { it != -1 }
            ?: 0

    private fun restoredAudioIndex(): Int =
        getStreamSelection(audioChoices, getSel(AUDIO_DOWNLOAD_QUALITY), getSel(AUDIO_DOWNLOAD_FORMAT))
            ?: 0

    private fun updateDownloadButton() {
        val binding = binding ?: return
        val (video, audio) = chosenStreams()
        binding.downloadButton.isEnabled = streams != null && (video != null || audio != null)
        val size = DownloadChoices.totalSize(video, audio)
        binding.downloadButton.text = size
            ?.let { getString(R.string.download_with_size, formatSize(it)) }
            ?: getString(R.string.download)
    }

    /** The video and audio streams the cards and the options point to. */
    private fun chosenStreams(): Pair<PipedStream?, PipedStream?> {
        val loaded = streams ?: return null to null
        val position = qualityAdapter.selected
        return if (audioOnly) {
            null to audioChoices.getOrNull(position)
        } else {
            videoChoices.getOrNull(position) to mainAudio(loaded)
        }
    }

    private fun showAlreadyDownloaded(binding: DialogDownloadBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            val download = withContext(Dispatchers.IO) {
                runCatching {
                    DatabaseHolder.Database.downloadDao().getDownloadById(videoId)
                }.getOrNull()
            }
            if (download != null) binding.alreadyDownloaded.isVisible = true
        }
    }

    private fun confirm() {
        val binding = binding ?: return
        val (videoStream, audioStream) = chosenStreams()
        val subtitle = subtitles.getOrNull(binding.subtitleSpinner.selectedItemPosition - 1)
        if (videoStream == null && audioStream == null && subtitle == null) {
            Toast.makeText(context, R.string.nothing_selected, Toast.LENGTH_SHORT).show()
            return
        }
        // a second tap must not start the same download twice
        binding.downloadButton.isEnabled = false
        saveSelections(videoStream, audioStream, subtitle)

        val downloadData = DownloadData(
            videoId = videoId,
            videoFormat = videoStream?.format,
            videoQuality = videoStream?.quality,
            audioFormat = audioStream?.format,
            audioQuality = audioStream?.quality,
            audioLanguage = audioStream?.audioTrackLocale,
            subtitleCode = subtitle?.code
        )
        DownloadHelper.startDownloadService(requireContext(), downloadData)
        dismiss()
    }

    /** Save the download selection to the preferences */
    private fun saveSelections(
        videoStream: PipedStream?,
        audioStream: PipedStream?,
        subtitle: Subtitle?
    ) {
        PreferenceHelper.putBoolean(DOWNLOAD_AUDIO_ONLY, audioOnly)
        PreferenceHelper.putString(VIDEO_DOWNLOAD_CODEC, codec?.name.orEmpty())
        PreferenceHelper.putString(AUDIO_DOWNLOAD_TRACK, audioTrack.orEmpty())
        PreferenceHelper.putString(SUBTITLE_LANGUAGE, subtitle?.code.orEmpty())
        // audio only keeps the last video choice for the next video download, and the other way
        if (videoStream != null) {
            PreferenceHelper.putString(VIDEO_DOWNLOAD_FORMAT, videoStream.format.orEmpty())
            PreferenceHelper.putString(VIDEO_DOWNLOAD_QUALITY, videoStream.quality.orEmpty())
        }
        if (audioStream != null) {
            PreferenceHelper.putString(AUDIO_DOWNLOAD_FORMAT, audioStream.format.orEmpty())
            PreferenceHelper.putString(AUDIO_DOWNLOAD_QUALITY, audioStream.quality.orEmpty())
        }
    }

    private fun getSel(key: String) = PreferenceHelper.getString(key, "")

    private fun getStreamSelection(
        streams: List<PipedStream>,
        quality: String,
        format: String
    ): Int? {
        if (quality.isBlank()) return null

        streams.forEachIndexed { index, pipedStream ->
            if (quality == pipedStream.quality && format == pipedStream.format) return index
        }

        streams.forEachIndexed { index, pipedStream ->
            if (quality == pipedStream.quality) return index
        }

        val qualityInt = quality.getWhileDigit() ?: return null

        streams.forEachIndexed { index, pipedStream ->
            if ((pipedStream.quality.getWhileDigit() ?: Int.MAX_VALUE) < qualityInt) return index
        }

        return null
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        setFragmentResult(DOWNLOAD_DIALOG_DISMISSED_KEY, bundleOf())
    }

    companion object {
        private const val GRID_COLUMNS = 2
        private const val STANDARD_FPS = 30
        private const val DEFAULT_MAX_HEIGHT = 1080

        private const val VIDEO_DOWNLOAD_QUALITY = "video_download_quality"
        private const val VIDEO_DOWNLOAD_FORMAT = "video_download_format"
        private const val VIDEO_DOWNLOAD_CODEC = "download_video_codec"
        private const val AUDIO_DOWNLOAD_QUALITY = "audio_download_quality"
        private const val AUDIO_DOWNLOAD_FORMAT = "audio_download_format"
        private const val AUDIO_DOWNLOAD_TRACK = "download_audio_track"
        private const val DOWNLOAD_AUDIO_ONLY = "download_audio_only"
        private const val SUBTITLE_LANGUAGE = "subtitle_download_language"

        const val DOWNLOAD_DIALOG_DISMISSED_KEY = "download_dialog_dismissed_key"
    }
}
// endregion
