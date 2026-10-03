package com.watube.yard.ui.sheets

import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.View
import android.widget.Toast
import androidx.core.os.postDelayed
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import com.watube.yard.R
import com.watube.yard.databinding.SleepTimerSheetBinding
import com.watube.yard.ui.tools.SleepTimer
import com.google.android.material.chip.Chip

class SleepTimerSheet : ExpandedBottomSheet(R.layout.sleep_timer_sheet) {
    private var _binding: SleepTimerSheetBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = SleepTimerSheetBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        setupQuickSelectChips()
        updateTimeLeftText()

        binding.startSleepTimer.setOnClickListener {
            val time = binding.timeInput.text.toString().toLongOrNull()

            if (time == null) {
                Toast.makeText(context, R.string.invalid_input, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            SleepTimer.start(requireContext(), time)
            updateTimeLeftText()
        }

        binding.stopSleepTimer.setOnClickListener {
            SleepTimer.stop(requireContext())
            updateTimeLeftText()
        }
    }

    /**
     * Setup quick-select chips for common sleep timer durations.
     */
    private fun setupQuickSelectChips() {
        val chipDurations = listOf(10, 20, 30, 45, 60)

        chipDurations.forEach { duration ->
            val chip = layoutInflater.inflate(
                R.layout.assist_chip,
                binding.quickSelectChips,
                false
            ) as Chip
            chip.apply {
                text = resources.getQuantityString(
                    R.plurals.sleep_timer_chip_minutes,
                    duration,
                    duration
                )
                setOnClickListener {
                    binding.timeInput.apply {
                        setText(duration.toString())
                        clearFocus()

                        SleepTimer.start(requireContext(), duration.toLong())
                        updateTimeLeftText()
                    }
                }
            }

            binding.quickSelectChips.addView(chip)
        }
    }

    private fun updateTimeLeftText() {
        val binding = _binding ?: return

        val isTimerRunning = SleepTimer.timeLeftMillis > 0

        binding.timeLeft.isVisible = isTimerRunning
        binding.stopSleepTimer.isVisible = isTimerRunning
        binding.timeInputLayout.isGone = isTimerRunning
        binding.quickSelectContainer.isGone = isTimerRunning
        binding.startSleepTimer.isGone = isTimerRunning

        if (!isTimerRunning) return

        binding.timeLeft.text = DateUtils.formatElapsedTime(SleepTimer.timeLeftMillis / 1000)

        // only one countdown loop: every extra call (reopening the sheet, restarting the
        // timer) would otherwise stack another 1s chain
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            updateTimeLeftText()
        }, 1000)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // lets the settings screen refresh what it shows about the timer
        setFragmentResult(SLEEP_TIMER_REQUEST_KEY, Bundle.EMPTY)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }

    companion object {
        const val SLEEP_TIMER_REQUEST_KEY = "sleep_timer_request_key"
    }
}
