package com.watube.yard.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.R
import com.watube.yard.api.obj.Comment
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.FragmentCommentsBinding
import com.watube.yard.extensions.formatShort
import com.watube.yard.extensions.parcelable
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.ui.adapters.CommentsPagingAdapter
import com.watube.yard.ui.models.CommentRepliesViewModel
import com.watube.yard.ui.models.CommentsViewModel
import com.watube.yard.ui.sheets.CommentsSheet

class CommentsRepliesFragment : Fragment(R.layout.fragment_comments) {

    private val viewModel by viewModels<CommentRepliesViewModel> { CommentRepliesViewModel.Factory }
    private val sharedModel by activityViewModels<CommentsViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentCommentsBinding.bind(view)

        val commentsSheet = parentFragment as? CommentsSheet
        commentsSheet?.binding?.btnScrollToTop?.isGone = true

        val repliesAdapter = CommentsPagingAdapter(
            true,
            requireArguments().getString(IntentData.channelAvatar),
            handleLink = {
                setFragmentResult(
                    CommentsSheet.HANDLE_LINK_REQUEST_KEY,
                    bundleOf(IntentData.url to it),
                )
            },
            saveToClipboard = { replyComment ->
                viewModel.saveToClipboard(view.context, replyComment)
            },
            navigateToChannel = { comment ->
                NavigationHelper.navigateChannel(view.context, comment.commentorUrl)
                setFragmentResult(CommentsSheet.DISMISS_SHEET_REQUEST_KEY, Bundle.EMPTY)
            }
        )
        commentsSheet?.updateFragmentInfo(
            true,
            "${getString(R.string.replies)} (${requireArguments().parcelable<Comment>(IntentData.comment)!!.replyCount.formatShort()})"
        )

        binding.commentsRV.updatePadding(top = 0)

        val layoutManager = LinearLayoutManager(context)
        binding.commentsRV.layoutManager = layoutManager

        binding.commentsRV.adapter = repliesAdapter

        commentsSheet?.binding?.btnScrollToTop?.setOnClickListener {
            // scroll back to the top / first comment
            layoutManager.startSmoothScroll(LinearSmoothScroller(view.context).also {
                it.targetPosition = POSITION_START
            })
            sharedModel.setRepliesPosition(POSITION_START)
        }

        binding.commentsRV.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return

                val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()

                sharedModel.setRepliesPosition(firstVisiblePosition)

                super.onScrollStateChanged(recyclerView, newState)
            }
        })

        repliesAdapter.addLoadStateListener { loadStates ->
            val refresh = loadStates.refresh
            binding.progress.isVisible = refresh is LoadState.Loading

            // same as the main comments list: surface a failed load instead of leaving
            // the list blank, and clear the message once the retry succeeds
            val error = refresh as? LoadState.Error ?: loadStates.append as? LoadState.Error
            when {
                error != null -> {
                    binding.errorTV.text =
                        "${getString(R.string.error_occurred)} – ${getString(R.string.retry)}"
                    binding.errorTV.isVisible = true
                    binding.errorTV.setOnClickListener { repliesAdapter.retry() }
                }
                else -> {
                    binding.errorTV.isVisible = false
                    binding.errorTV.setOnClickListener(null)
                }
            }

            val refreshState = loadStates.source.refresh
            if (refreshState is LoadState.NotLoading && repliesAdapter.itemCount > 0) {
                sharedModel.currentRepliesPosition.value?.let { currentRepliesPosition ->
                    layoutManager.scrollToPosition(maxOf(currentRepliesPosition, POSITION_START))
                }
            }
        }

        sharedModel.currentRepliesPosition.observe(viewLifecycleOwner) {
            commentsSheet?.binding?.btnScrollToTop?.isVisible = it != 0
        }

        viewModel.commentRepliesLiveData.observe(viewLifecycleOwner) {
            repliesAdapter.submitData(lifecycle, it)
        }
    }

    companion object {
        const val POSITION_START = 0
    }
}
