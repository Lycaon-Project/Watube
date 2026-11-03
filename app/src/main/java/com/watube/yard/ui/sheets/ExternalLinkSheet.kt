package com.watube.yard.ui.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import com.watube.yard.R
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.ExternalLinkSheetBinding
import com.watube.yard.util.ExternalLink

// region Privacy
/**
 * Asked before a link of a description or a comment leaves the app: the real destination, in
 * plain ASCII and with the registered name set apart, whether the link is encrypted, whether
 * its name imitates another, and the trackers taken out. Opening is the caller's job, through
 * [REQUEST_KEY], with the link cleaned of its trackers.
 */
class ExternalLinkSheet : ExpandedBottomSheet(R.layout.external_link_sheet) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = ExternalLinkSheetBinding.bind(view)
        val link = requireArguments().getString(IntentData.url)?.let(ExternalLink::parse)
        if (link == null) {
            dismiss()
            return
        }

        showDestination(binding, link)
        showNotes(binding, link)
        binding.linkFull.text = link.url

        binding.linkOpen.setOnClickListener {
            // a double tap would open the link twice
            binding.linkOpen.isEnabled = false
            setFragmentResult(REQUEST_KEY, bundleOf(IntentData.url to link.url))
            dismiss()
        }
        binding.linkCopy.setOnClickListener {
            // Android shows its own confirmation of the copy
            requireContext().getSystemService<ClipboardManager>()?.setPrimaryClip(
                ClipData.newPlainText(getString(R.string.external_link_copy), link.url)
            )
            dismiss()
        }
        binding.linkCancel.setOnClickListener { dismiss() }
    }

    private fun showDestination(binding: ExternalLinkSheetBinding, link: ExternalLink) {
        val destination = binding.linkDestination
        val email = link.email
        if (email != null) {
            destination.text = email
            destination.contentDescription = getString(R.string.external_link_destination, email)
            return
        }
        // the whole name, all in one weight: without the public suffix list, setting a part
        // apart could stress a shared name (github.io) instead of its owner's
        destination.text = buildSpannedString { bold { append(link.host) } }
        destination.contentDescription = getString(R.string.external_link_destination, link.host)
    }

    private fun showNotes(binding: ExternalLinkSheetBinding, link: ExternalLink) {
        val web = link.email == null
        binding.linkSecure.isVisible = web && link.encrypted
        binding.linkUnencrypted.isVisible = web && !link.encrypted
        binding.linkLookalike.isVisible = link.lookalike
        if (link.lookalike) {
            binding.linkLookalike.text = getString(R.string.external_link_lookalike, link.unicodeHost)
        }
        val removed = link.removedParameters
        binding.linkTrackers.isVisible = removed.isNotEmpty()
        if (removed.isNotEmpty()) {
            binding.linkTrackers.text = resources.getQuantityString(
                R.plurals.external_link_trackers,
                removed.size,
                removed.size,
                removed.joinToString(", ")
            )
        }
    }

    companion object {
        const val REQUEST_KEY = "external_link_request"

        fun newInstance(link: String) = ExternalLinkSheet().apply {
            arguments = bundleOf(IntentData.url to link)
        }
    }
}
// endregion
