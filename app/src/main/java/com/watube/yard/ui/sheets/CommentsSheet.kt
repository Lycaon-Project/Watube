package com.watube.yard.ui.sheets

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.fragment.app.setFragmentResult
import com.watube.yard.R
import com.watube.yard.databinding.CommentsSheetBinding
import com.watube.yard.constants.IntentData
import com.watube.yard.ui.fragments.CommentsMainFragment
import com.watube.yard.ui.fragments.LiveChatFragment
import com.watube.yard.ui.models.CommonPlayerViewModel

// region Comments
class CommentsSheet : ExpandablePlayerSheet(R.layout.comments_sheet) {
    private var _binding: CommentsSheetBinding? = null
    val binding get() = _binding!!

    private val commonPlayerViewModel: CommonPlayerViewModel by activityViewModels()

    private val backPressedCallback by lazy(LazyThreadSafetyMode.NONE) {
        (dialog as ComponentDialog?)?.onBackPressedDispatcher?.addCallback(owner = viewLifecycleOwner, enabled = false) {
            if (childFragmentManager.backStackEntryCount > 0) {
                childFragmentManager.popBackStack()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = CommentsSheetBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        childFragmentManager.setFragmentResultListener(DISMISS_SHEET_REQUEST_KEY, viewLifecycleOwner) { _, _ ->
            dismiss()
        }

        // forward requests to open links to the parent fragment
        childFragmentManager.setFragmentResultListener(HANDLE_LINK_REQUEST_KEY, viewLifecycleOwner) { key, result ->
            parentFragment?.setFragmentResult(key, result)
        }

        binding.btnBack.setOnClickListener {
            if (childFragmentManager.backStackEntryCount > 0) {
                childFragmentManager.popBackStack()
            }
        }

        binding.btnClose.setOnClickListener { dismiss() }

        // a live stream shows its live chat, which falls back to the comments when it has none
        childFragmentManager.commit {
            if (arguments?.getBoolean(IntentData.isLive) == true) {
                replace<LiveChatFragment>(R.id.commentFragContainer, args = arguments)
            } else {
                replace<CommentsMainFragment>(R.id.commentFragContainer, args = arguments)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // remove internal padding from the bottomsheet
        // https://github.com/material-components/material-components-android/issues/3389#issuecomment-2049028605
        dialog?.window?.apply {
            setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getSheetMaxHeightPx() = commonPlayerViewModel.maxSheetHeightPx

    override fun getDragHandle() = binding.dragHandle

    override fun getBottomSheet() = binding.standardBottomSheet

    fun updateFragmentInfo(showBackButton: Boolean, title: String) {
        binding.btnBack.isVisible = showBackButton
        binding.commentsTitle.text = title
        backPressedCallback?.isEnabled = showBackButton
    }

    companion object {
        /**
         * Keeps the end of [list] above the navigation bar: the sheet is laid out under it
         * (FLAG_LAYOUT_NO_LIMITS), so the last row would sit behind the bar, out of reach.
         */
        fun padAboveNavigationBar(list: View) {
            list.doOnAttach { attached ->
                val bar = ViewCompat.getRootWindowInsets(attached)
                    ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
                attached.updatePadding(bottom = bar)
            }
        }

        const val HANDLE_LINK_REQUEST_KEY = "handle_link_request_key"
        const val DISMISS_SHEET_REQUEST_KEY = "dismiss_sheet_request_key"
    }
}
// endregion
