package com.watube.yard.ui.models

import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import com.watube.yard.api.MediaServiceRepository
import com.watube.yard.api.obj.Comment
import com.watube.yard.helpers.LiveChatPolicy
import com.watube.yard.helpers.LiveChatPolicy.LiveChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

// region Comments
/**
 * The live chat of a live stream, read while it is on screen ([follow]) by the rules of
 * [LiveChatPolicy]: plain text only, the newest messages only, in memory only, read less and
 * less often while nothing new comes. Scoped to the chat screen: closing it forgets the chat.
 */
class LiveChatViewModel : ViewModel() {

    sealed interface State {
        /** The first read has not answered yet. */
        data object Loading : State

        /** The stream has no live chat: its comments are shown instead. */
        data object Unavailable : State

        /**
         * The chat so far. [isEnded]: the chat closed, nothing more comes. [isFailing]: the last
         * read failed, the next one comes later.
         */
        data class Chat(
            val messages: List<LiveChatMessage>,
            val isEnded: Boolean = false,
            val isFailing: Boolean = false
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Where the next read starts: null before the first one. */
    private var continuation: String? = null
    private var delayMs = LiveChatPolicy.FIRST_DELAY_MS

    /**
     * Reads the chat of [videoId] for as long as the calling coroutine runs (the chat on
     * screen), picking up where the last call stopped. Returns once there is nothing to read.
     */
    suspend fun follow(videoId: String) {
        while (currentCoroutineContext().isActive) {
            when (val current = _state.value) {
                State.Unavailable -> return
                State.Loading -> Unit
                is State.Chat -> if (current.isEnded) return else delay(delayMs)
            }
            read(videoId)
        }
    }

    private suspend fun read(videoId: String) {
        val token = continuation
        val page = try {
            withContext(Dispatchers.IO) {
                if (token == null) {
                    MediaServiceRepository.instance.getComments(videoId)
                } else {
                    MediaServiceRepository.instance.getCommentsNextPage(videoId, token)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // offline or refused: try again later and later, the messages so far kept
            delayMs = LiveChatPolicy.nextDelay(delayMs, gotNew = false)
            _state.update { (it as? State.Chat)?.copy(isFailing = true) ?: State.Chat(emptyList(), isFailing = true) }
            return
        }
        if (token == null && !page.liveChat) {
            _state.value = State.Unavailable
            return
        }
        val fresh = withContext(Dispatchers.Default) { page.comments.map { it.toLiveChatMessage() } }
        val previous = (_state.value as? State.Chat)?.messages.orEmpty()
        val merged = LiveChatPolicy.merge(previous, fresh)
        delayMs = LiveChatPolicy.nextDelay(delayMs, gotNew = merged.size != previous.size || merged.lastOrNull() != previous.lastOrNull())
        continuation = page.nextpage
        _state.value = State.Chat(merged, isEnded = page.nextpage == null)
    }

    /**
     * A message as plain text: the extractor gives it HTML-escaped, so reading the HTML only
     * brings the characters back, never a link or a tag.
     */
    private fun Comment.toLiveChatMessage() = LiveChatMessage(
        id = commentId,
        author = LiveChatPolicy.oneLine(author),
        text = LiveChatPolicy.oneLine(
            HtmlCompat.fromHtml(commentText.orEmpty(), HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        ),
        isOwner = channelOwner,
        isVerified = verified
    )
}
// endregion
