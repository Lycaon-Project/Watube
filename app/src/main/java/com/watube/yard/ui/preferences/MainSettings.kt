package com.watube.yard.ui.preferences

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.watube.yard.BuildConfig
import com.watube.yard.R
import com.watube.yard.databinding.FragmentMainSettingsBinding
import com.watube.yard.databinding.SettingsCategoryRowBinding
import com.watube.yard.ui.activities.AboutActivity

/**
 * Settings hub index.
 *
 * One card, five rows — the "Paramètres" screen of the Solar mockup: Apparence,
 * Lecteur, Confidentialité, Données & maintenance and À propos. Every former top
 * level entry now lives inside one of those categories.
 */
class MainSettings : Fragment() {

    private var _binding: FragmentMainSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRow(
            binding.rowAppearance,
            R.drawable.ic_color,
            R.string.appearance,
            R.string.appearance_summary
        ) { navigate(R.id.action_global_appearanceSettings) }

        setupRow(
            binding.rowPlayer,
            R.drawable.ic_play_filled,
            R.string.player,
            R.string.player_summary
        ) { navigate(R.id.action_global_playerSettings) }

        setupRow(
            binding.rowPrivacy,
            R.drawable.ic_shield,
            R.string.privacy,
            R.string.privacy_summary
        ) { navigate(R.id.action_global_privacySettings) }

        setupRow(
            binding.rowData,
            R.drawable.ic_data_saver,
            R.string.settings_data_maintenance,
            R.string.general_summary
        ) { navigate(R.id.action_global_maintenanceSettings) }

        setupRow(
            binding.rowAbout,
            R.drawable.ic_info,
            R.string.about,
            R.string.about
        ) {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }
        // mockup: "Watube 26.9 · GPL-3.0 · crédits"
        binding.rowAbout.rowSummary.text = getString(R.string.version, BuildConfig.VERSION_NAME)
    }

    private fun setupRow(
        row: SettingsCategoryRowBinding,
        @DrawableRes icon: Int,
        title: Int,
        summary: Int,
        onClick: () -> Unit
    ) {
        row.rowIcon.setImageResource(icon)
        row.rowTitle.setText(title)
        row.rowSummary.setText(summary)
        row.root.setOnClickListener { onClick() }
    }

    private fun navigate(actionId: Int) {
        findNavController().navigate(actionId)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
