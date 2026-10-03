package com.watube.yard.ui.fragments

import android.os.Bundle
import android.view.View
import com.watube.yard.extensions.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.fragment.app.setFragmentResult
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.R
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.FragmentCommentsBinding
import com.watube.yard.extensions.formatShort
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.ui.adapters.CommentsPagingAdapter
import com.watube.yard.ui.models.CommentsViewModel
import com.watube.yard.ui.sheets.CommentsSheet

class CommentsMainFragment : Fragment(R.layout.fragment_comments) {

    private val viewModel: CommentsViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentCommentsBinding.bind(view)
        val layoutManager = LinearLayoutManager(requireContext())
        binding.commentsRV.layoutManager = layoutManager

        val commentsSheet = parentFragment as? CommentsSheet
        commentsSheet?.binding?.btnScrollToTop?.setOnClickListener {
            // scroll back to the top / first comment
            layoutManager.startSmoothScroll(LinearSmoothScroller(view.context).also {
                it.targetPosition = POSITION_START
            })
            viewModel.setCommentsPosition(POSITION_START)
        }

        binding.commentsRV.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return

                val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
                viewModel.setCommentsPosition(firstVisiblePosition)
            }
        })

        commentsSheet?.updateFragmentInfo(false, getString(R.string.comments))

        val commentPagingAdapter = CommentsPagingAdapter(
            false,
            requireArguments().getString(IntentData.channelAvatar),
            handleLink = {
                setFragmentResult(
                    CommentsSheet.HANDLE_LINK_REQUEST_KEY,
                    bundleOf(IntentData.url to it),
                )
            },
            saveToClipboard = { comment ->
                viewModel.saveToClipboard(view.context, comment)
            },
            navigateToChannel = { comment ->
                NavigationHelper.navigateChannel(view.context, comment.commentorUrl)
                setFragmentResult(CommentsSheet.DISMISS_SHEET_REQUEST_KEY, Bundle.EMPTY)
            },
            navigateToReplies = { comment, channelAvatar ->
                if (comment.repliesPage != null) {
                    val args = bundleOf(
                        IntentData.videoId to viewModel.videoIdLiveData.value,
                        IntentData.comment to comment,
                        IntentData.channelAvatar to channelAvatar
                    )
                    parentFragmentManager.commit {
                        viewModel.setLastOpenedCommentRepliesId(comment.commentId)
                        replace<CommentsRepliesFragment>(R.id.commentFragContainer, args = args)
                        addToBackStack(null)
                    }
                }
            },
        )
        binding.commentsRV.adapter = commentPagingAdapter

        commentPagingAdapter.addLoadStateListener { loadStates ->
            val refresh = loadStates.refresh
            binding.progress.isVisible = refresh is LoadState.Loading

            // A failed load used to be swallowed silently: the count (a side effect of the
            // paging source) was already displayed while the list stayed empty with no
            // message and no way out. Handle the error, and clear the message again once
            // a retry delivers data.
            val error = refresh as? LoadState.Error ?: loadStates.append as? LoadState.Error
            when {
                error != null -> {
                    binding.errorTV.text = errorLabel()
                    binding.errorTV.isVisible = true
                    binding.errorTV.setOnClickListener { commentPagingAdapter.retry() }
                }
                loadStates.append is LoadState.NotLoading
                        && loadStates.append.endOfPaginationReached
                        && commentPagingAdapter.itemCount == 0 -> {
                    binding.errorTV.text = getString(R.string.no_comments_available)
                    binding.errorTV.isVisible = true
                    binding.errorTV.setOnClickListener(null)
                }
                else -> {
                    binding.errorTV.isVisible = false
                    binding.errorTV.setOnClickListener(null)
                }
            }
        }

        viewModel.currentCommentsPosition.observe(viewLifecycleOwner) {
            // hide or show the scroll to top button
            commentsSheet?.binding?.btnScrollToTop?.isVisible = it != 0
        }

        viewModel.commentsLiveData.observe(viewLifecycleOwner) {
            commentPagingAdapter.submitData(lifecycle, it)
        }

        viewModel.commentCountLiveData.observe(viewLifecycleOwner) { commentCount ->
            if (commentCount == null) return@observe

            commentsSheet?.updateFragmentInfo(
                false,
                getString(R.string.comments_count, commentCount.formatShort())
            )
        }
    }

    private fun errorLabel(): String =
        "${getString(R.string.error_occurred)} – ${getString(R.string.retry)}"

    companion object {
        private const val POSITION_START = 0
    }
}
