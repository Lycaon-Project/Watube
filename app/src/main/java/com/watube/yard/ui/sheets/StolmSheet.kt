package com.watube.yard.ui.sheets

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.postDelayed
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.TransitionManager
import com.google.android.material.transition.MaterialSharedAxis
import com.watube.yard.R
import com.watube.yard.cast.CastDevice
import com.watube.yard.cast.CastDiscovery
import com.watube.yard.cast.DiscoveryFailedException
import com.watube.yard.cast.Stolm
import com.watube.yard.cast.StolmPlayer
import com.watube.yard.databinding.StolmDeviceItemBinding
import com.watube.yard.databinding.StolmSheetBinding
import com.watube.yard.extensions.bundleOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// region Stolm
/**
 * Stolm: picks the TV to cast to, then controls the cast, the same on every Android version. It
 * holds no player: choices go to the player screen as fragment results. Devices are only looked
 * for while it shows the list.
 *
 * While casting, the audio track, subtitles and quality come first: they are what changes most
 * during a cast, and they open the same sheets as the player's options.
 */
class StolmSheet : ExpandedBottomSheet(R.layout.stolm_sheet) {
    private var _binding: StolmSheetBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())

    /**
     * The rows shown, by DNS-SD instance name. Updated in place as the search goes on: they used
     * to be rebuilt at every answer of the network, and a tap landing during a rebuild was lost
     * (the device had to be tapped twice).
     */
    private val rows = LinkedHashMap<String, StolmDeviceItemBinding>()

    /** The device tapped, while the phone connects to it: further taps wait. */
    private var chosen: String? = null

    /** What the sheet shows, to animate only a real change of it. */
    private var showsControls: Boolean? = null

    private var renderJob: Job? = null
    private var askedLocalNetwork = false

    /** Android 17: looking for devices on the local network needs the user's consent. */
    private val requestLocalNetwork =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // the answer may come back after the sheet's view is gone
            if (_binding == null) return@registerForActivityResult
            if (granted) startRendering() else showLocalNetworkNeeded()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = StolmSheetBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        val arguments = requireArguments()
        binding.volume.value = arguments.getInt(ARG_VOLUME).coerceIn(0, StolmPlayer.MAX_VOLUME).toFloat()
        binding.translationSummary.text = arguments.getString(ARG_TRANSLATION)
        binding.qualitySummary.text = arguments.getString(ARG_QUALITY)

        binding.volume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) setFragmentResult(REQUEST_KEY, bundleOf(VOLUME to value.toInt()))
        }
        binding.translationOption.setOnClickListener { openOption(TRANSLATION) }
        binding.qualityOption.setOnClickListener { openOption(QUALITY) }
        binding.stop.setOnClickListener {
            setFragmentResult(REQUEST_KEY, bundleOf(STOP to true))
            dismiss()
        }
        binding.allowLocalNetwork.setOnClickListener {
            if (askedLocalNetwork && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_LOCAL_NETWORK)) {
                // refused for good: Android no longer asks, only its settings can allow it
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireContext().packageName, null))
                )
            } else {
                requestLocalNetwork.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
        }

        startRendering()
    }

    /** The player screen opens the sheet of that option once this one is gone. */
    private fun openOption(option: String) {
        setFragmentResult(REQUEST_KEY, bundleOf(option to true))
        dismiss()
    }

    private fun startRendering() {
        renderJob?.cancel()
        renderJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // a new state cancels the previous one's work, the device search included
                Stolm.state.collectLatest(::render)
            }
        }
    }

    private suspend fun render(state: Stolm.State) {
        handler.removeCallbacksAndMessages(null)
        when (state) {
            is Stolm.State.Casting -> {
                chosen = null
                showSection(controls = true)
                binding.title.text = getString(R.string.stolm_casting_to, state.deviceName)
            }

            is Stolm.State.Connecting -> {
                showSection(controls = false)
                binding.title.text = getString(R.string.stolm_picker_title)
                showConnecting(state.deviceName)
            }

            else -> {
                chosen = null
                rows.values.forEach { it.connecting.isGone = true }
                searchDevices()
            }
        }
    }

    private fun showSection(controls: Boolean) {
        // one moment of motion: the list giving way to the cast controls, and back
        if (showsControls != null && showsControls != controls && animationsEnabled()) {
            TransitionManager.beginDelayedTransition(
                binding.content,
                MaterialSharedAxis(MaterialSharedAxis.Y, controls).setDuration(SECTION_MS)
            )
        }
        showsControls = controls
        binding.controls.isVisible = controls
        binding.progressRow.isGone = controls
        binding.devices.isGone = controls
        binding.emptyHint.isVisible = false
        binding.allowLocalNetwork.isVisible = false
    }

    /** The row tapped shows the connection; without one (the sheet opened meanwhile), the text does. */
    private fun showConnecting(deviceName: String) {
        val row = chosen?.let(rows::get) ?: rows.values.firstOrNull { it.name.text == deviceName }
        rows.values.forEach { it.root.isEnabled = false }
        if (row != null) {
            binding.progressRow.isGone = true
            row.connecting.fadeIn()
        } else {
            binding.progressRow.isVisible = true
            binding.progressText.text = getString(R.string.stolm_connecting, deviceName)
        }
    }

    private suspend fun searchDevices() {
        showSection(controls = false)
        binding.title.text = getString(R.string.stolm_picker_title)
        binding.progressText.text = getString(R.string.stolm_searching)
        binding.emptyHint.text = getString(R.string.stolm_empty)
        rows.values.forEach { it.root.isEnabled = true }

        if (needsLocalNetwork()) {
            if (!askedLocalNetwork) {
                askedLocalNetwork = true
                requestLocalNetwork.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
            showLocalNetworkNeeded()
            return
        }

        handler.postDelayed(EMPTY_HINT_DELAY_MS) {
            _binding?.let { it.emptyHint.isVisible = rows.isEmpty() }
        }
        try {
            CastDiscovery(requireContext()).devices.collect(::showDevices)
        } catch (e: DiscoveryFailedException) {
            // the system refused to search (Wi-Fi off, no network...)
            _binding?.let {
                it.progressRow.isGone = true
                it.emptyHint.isVisible = true
            }
        }
    }

    private fun showLocalNetworkNeeded() {
        val binding = _binding ?: return
        binding.progressRow.isGone = true
        binding.emptyHint.text = getString(R.string.stolm_local_network_needed)
        binding.emptyHint.isVisible = true
        binding.allowLocalNetwork.isVisible = true
    }

    private fun needsLocalNetwork() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_LOCAL_NETWORK) !=
        PackageManager.PERMISSION_GRANTED

    private fun showDevices(devices: List<CastDevice>) {
        if (devices.isNotEmpty()) binding.emptyHint.isVisible = false
        val found = devices.associateBy { it.serviceName }

        // gone from the network, unless it is the one being connected to
        rows.keys.filter { it !in found && it != chosen }.forEach { key ->
            rows.remove(key)?.let { binding.devices.removeView(it.root) }
        }

        var added = 0
        found.forEach { (key, device) ->
            val row = rows[key] ?: StolmDeviceItemBinding.inflate(layoutInflater, binding.devices, true).also {
                rows[key] = it
                it.root.enter(added++)
            }
            row.name.text = device.name
            row.model.text = device.model
            row.model.isGone = device.model.isEmpty()
            row.root.setOnClickListener { choose(key, device) }
        }
    }

    private fun choose(key: String, device: CastDevice) {
        if (chosen != null) return
        chosen = key
        setFragmentResult(REQUEST_KEY, bundleOf(TARGET to device.toTarget().toBundle()))
        // the player may refuse before connecting (video still loading, with its own message):
        // the rows are then free again
        handler.postDelayed(CHOICE_TIMEOUT_MS) {
            if (Stolm.state.value !is Stolm.State.Connecting && Stolm.state.value !is Stolm.State.Casting) {
                chosen = null
            }
        }
    }

    /** New rows rise into place one after the other. */
    private fun View.enter(order: Int) {
        if (!animationsEnabled()) return
        alpha = 0f
        translationY = ENTER_OFFSET_DP * resources.displayMetrics.density
        animate().alpha(1f).translationY(0f)
            .setStartDelay(order * STAGGER_MS)
            .setDuration(ENTER_MS)
            .setInterpolator(DecelerateInterpolator(2f))
    }

    private fun View.fadeIn() {
        if (isVisible) return
        isVisible = true
        if (!animationsEnabled()) return
        alpha = 0f
        animate().alpha(1f).setDuration(ENTER_MS).setInterpolator(DecelerateInterpolator())
    }

    /** Off when the user removed animations in the system settings. */
    private fun animationsEnabled() = ValueAnimator.areAnimatorsEnabled()

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        rows.clear()
        renderJob = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val REQUEST_KEY = "stolm_request"
        const val TARGET = "target"
        const val STOP = "stop"
        const val VOLUME = "volume"
        const val TRANSLATION = "translation"
        const val QUALITY = "quality"
        private const val ARG_VOLUME = "device_volume"
        private const val ARG_TRANSLATION = "translation_summary"
        private const val ARG_QUALITY = "quality_summary"

        /** Long enough for a device on the same Wi-Fi to answer the first query. */
        private const val EMPTY_HINT_DELAY_MS = 4_000L

        /** Long enough for the player to start connecting, or to say why it does not. */
        private const val CHOICE_TIMEOUT_MS = 1_500L

        private const val SECTION_MS = 300L
        private const val ENTER_MS = 220L
        private const val STAGGER_MS = 45L
        private const val ENTER_OFFSET_DP = 8f

        /** [translation] and [quality]: what the player plays, shown under those options. */
        fun newInstance(deviceVolume: Int, translation: String, quality: String) = StolmSheet().apply {
            arguments = bundleOf(
                ARG_VOLUME to deviceVolume,
                ARG_TRANSLATION to translation,
                ARG_QUALITY to quality
            )
        }
    }
}
// endregion
