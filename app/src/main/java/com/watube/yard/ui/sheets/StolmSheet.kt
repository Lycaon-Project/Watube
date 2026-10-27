package com.watube.yard.ui.sheets

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.os.postDelayed
import androidx.core.view.isEmpty
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.watube.yard.R
import com.watube.yard.cast.CastDevice
import com.watube.yard.cast.CastDiscovery
import com.watube.yard.cast.DiscoveryFailedException
import com.watube.yard.cast.Stolm
import com.watube.yard.cast.StolmPlayer
import com.watube.yard.databinding.StolmDeviceItemBinding
import com.watube.yard.databinding.StolmSheetBinding
import com.watube.yard.extensions.bundleOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// region Stolm
/**
 * Stolm: picks the TV to cast to (where the system has no picker of its own), then controls the
 * cast. It holds no player: choices go to the player screen as fragment results. Devices are
 * only looked for while it shows the list.
 */
class StolmSheet : ExpandedBottomSheet(R.layout.stolm_sheet) {
    private var _binding: StolmSheetBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = StolmSheetBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        binding.volume.value = requireArguments().getInt(ARG_VOLUME).coerceIn(0, StolmPlayer.MAX_VOLUME).toFloat()
        binding.volume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) setFragmentResult(REQUEST_KEY, bundleOf(VOLUME to value.toInt()))
        }
        binding.stop.setOnClickListener {
            setFragmentResult(REQUEST_KEY, bundleOf(STOP to true))
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
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
                binding.title.text = getString(R.string.stolm_casting_to, state.deviceName)
                showSection(controls = true)
            }

            is Stolm.State.Connecting -> {
                binding.title.text = getString(R.string.stolm_picker_title)
                binding.progressText.text = getString(R.string.stolm_connecting, state.deviceName)
                showSection(controls = false)
                binding.devices.removeAllViews()
            }

            else -> searchDevices()
        }
    }

    private fun showSection(controls: Boolean) {
        binding.controls.isVisible = controls
        binding.progressRow.isGone = controls
        binding.devices.isGone = controls
        binding.emptyHint.isVisible = false
    }

    private suspend fun searchDevices() {
        binding.title.text = getString(R.string.stolm_picker_title)
        binding.progressText.text = getString(R.string.stolm_searching)
        showSection(controls = false)
        handler.postDelayed(EMPTY_HINT_DELAY_MS) {
            _binding?.let { it.emptyHint.isVisible = it.devices.isEmpty() }
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

    private fun showDevices(devices: List<CastDevice>) {
        binding.devices.removeAllViews()
        if (devices.isNotEmpty()) binding.emptyHint.isVisible = false
        devices.forEach { device ->
            val row = StolmDeviceItemBinding.inflate(layoutInflater, binding.devices, true)
            row.name.text = device.name
            row.model.text = device.model
            row.model.isGone = device.model.isEmpty()
            row.root.setOnClickListener {
                setFragmentResult(REQUEST_KEY, bundleOf(TARGET to device.toTarget().toBundle()))
            }
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val REQUEST_KEY = "stolm_request"
        const val TARGET = "target"
        const val STOP = "stop"
        const val VOLUME = "volume"
        private const val ARG_VOLUME = "device_volume"

        /** Long enough for a device on the same Wi-Fi to answer the first query. */
        private const val EMPTY_HINT_DELAY_MS = 4_000L

        fun newInstance(deviceVolume: Int) = StolmSheet().apply {
            arguments = bundleOf(ARG_VOLUME to deviceVolume)
        }
    }
}
// endregion
