package com.watube.yard.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.watube.yard.R
import com.watube.yard.databinding.FragmentCommentsBinding
import com.watube.yard.ui.adapters.LiveChatAdapter
import com.watube.yard.ui.models.CommentsViewModel
import com.watube.yard.ui.models.LiveChatViewModel
import com.watube.yard.ui.models.LiveChatViewModel.State
import com.watube.yard.ui.sheets.CommentsSheet
import kotlinx.coroutines.launch

// region Comments
/**
 * The live chat of a live stream, in the comments sheet in place of the comments. It is read
 * only while it is on screen and the app in the foreground (see [LiveChatViewModel]); the
 * newest message stays in view unless the list was scrolled up. A stream without a live chat
 * shows its comments instead.
 */
class LiveChatFragment : Fragment(R.layout.fragment_comments) {

    private val viewModel: LiveChatViewModel by viewModels()
    private val commentsViewModel: CommentsViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val videoId = commentsViewModel.videoIdLiveData.value ?: return
        val binding = FragmentCommentsBinding.bind(view)
        val commentsSheet = parentFragment as? CommentsSheet
        commentsSheet?.updateFragmentInfo(false, getString(R.string.live_chat))
        commentsSheet?.binding?.btnScrollToTop?.isVisible = false

        val adapter = LiveChatAdapter()
        binding.commentsRV.layoutManager = LinearLayoutManager(view.context).apply { stackFromEnd = true }
        binding.commentsRV.adapter = adapter
        CommentsSheet.padAboveNavigationBar(binding.commentsRV)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.follow(videoId) }
                viewModel.state.collect { render(binding, adapter, it) }
            }
        }
    }

    private fun render(binding: FragmentCommentsBinding, adapter: LiveChatAdapter, state: State) {
        binding.progress.isVisible = state == State.Loading
        when (state) {
            State.Loading -> binding.errorTV.isVisible = false
            State.Unavailable -> parentFragmentManager.commit {
                replace<CommentsMainFragment>(R.id.commentFragContainer, args = arguments)
            }
            is State.Chat -> {
                val list = binding.commentsRV
                // follow the newest message only while it was in view
                val atNewest = !list.canScrollVertically(1)
                adapter.submitList(state.messages) {
                    if (atNewest && state.messages.isNotEmpty()) list.scrollToPosition(state.messages.lastIndex)
                }
                val note = when {
                    state.isEnded -> R.string.live_chat_ended
                    state.isFailing && state.messages.isEmpty() -> R.string.error_occurred
                    state.messages.isEmpty() -> R.string.live_chat_empty
                    else -> null
                }
                binding.errorTV.isVisible = note != null
                note?.let(binding.errorTV::setText)
            }
        }
    }
}
// endregion
