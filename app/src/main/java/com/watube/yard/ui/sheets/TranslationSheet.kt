package com.watube.yard.ui.sheets

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.watube.yard.R
import com.watube.yard.databinding.BottomSheetItemBinding
import com.watube.yard.databinding.TranslationSheetBinding
import com.watube.yard.ui.extensions.onSystemInsets
import com.google.android.material.tabs.TabLayout
import java.util.Locale

// region Captions
/**
 * "Traduction" of the player options: the audio channel and the subtitles in one sheet, one tab
 * each. A tap applies the choice right away and moves the highlight, so both can be set in one go.
 */
class TranslationSheet : ExpandedBottomSheet(R.layout.translation_sheet) {

    class Choice(val title: String, val isSelected: Boolean, val onSelect: () -> Unit)

    private var audioChoices: List<Choice>? = null
    private var captionChoices: List<Choice>? = null

    fun setChoices(audio: List<Choice>, captions: List<Choice>) = apply {
        audioChoices = audio
        captionChoices = captions
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val audio = audioChoices
        val captions = captionChoices
        // recreated by the system (rotation, process death) without the player's tracks
        if (audio == null || captions == null) {
            dismiss()
            return
        }

        val binding = TranslationSheetBinding.bind(view)
        fillGroup(binding.audioOptions, audio)
        fillGroup(binding.captionOptions, captions)

        // one tab per list: twenty dubbed tracks no longer push the subtitles out of sight
        binding.translationTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.audioOptions.isVisible = tab.position == 0
                binding.captionOptions.isVisible = tab.position == 1
                binding.translationScroll.scrollTo(0, 0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        // the last row must not end up under the navigation bar
        binding.translationScroll.onSystemInsets { v, systemInsets ->
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemInsets.bottom)
        }
    }

    private fun fillGroup(group: LinearLayout, choices: List<Choice>) {
        val rows = choices.map { choice ->
            BottomSheetItemBinding.inflate(layoutInflater, group, true).root.apply {
                // "anglais" reads as a label once capitalised: "Anglais"
                text = choice.title.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                isSelected = choice.isSelected
            }
        }
        rows.zip(choices).forEach { (row, choice) ->
            row.setOnClickListener {
                choice.onSelect()
                // one choice per group
                rows.forEach { it.isSelected = it === row }
            }
        }
    }
}
// endregion
