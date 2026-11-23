package com.watube.yard.ui.adapters

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.inSpans
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.R
import com.watube.yard.helpers.LiveChatPolicy.LiveChatMessage
import com.watube.yard.helpers.ThemeHelper

// region Comments
/**
 * The messages of a live chat ([com.watube.yard.ui.fragments.LiveChatFragment]), plain text
 * only: the author in bold (the accent colour for the channel's owner, then the verified
 * mark), then the message. A screen reader reads each message whole, the owner and the
 * verified mark in words.
 */
class LiveChatAdapter : ListAdapter<LiveChatMessage, LiveChatAdapter.MessageViewHolder>(DIFF) {

    class MessageViewHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.live_chat_row, parent, false)
        return MessageViewHolder(view as TextView)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)
        val view = holder.text
        val context = view.context
        val authorColor = ThemeHelper.getThemeColor(
            context,
            if (message.isOwner) androidx.appcompat.R.attr.colorPrimary
            else com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        view.text = SpannableStringBuilder().apply {
            inSpans(StyleSpan(Typeface.BOLD), ForegroundColorSpan(authorColor)) { append(message.author) }
            if (message.isVerified) {
                ContextCompat.getDrawable(context, R.drawable.ic_verified)?.mutate()?.let { mark ->
                    val size = view.textSize.toInt()
                    mark.setBounds(0, 0, size, size)
                    mark.setTint(authorColor)
                    append(' ')
                    inSpans(ImageSpan(mark, ImageSpan.ALIGN_CENTER)) { append('✓') }
                }
            }
            append("  ")
            append(message.text)
        }
        view.contentDescription = buildString {
            append(message.author)
            if (message.isOwner) append(", ").append(context.getString(R.string.live_chat_owner))
            if (message.isVerified) append(", ").append(context.getString(R.string.comment_verified))
            append(", ").append(message.text)
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<LiveChatMessage>() {
            override fun areItemsTheSame(oldItem: LiveChatMessage, newItem: LiveChatMessage) =
                if (oldItem.id.isEmpty()) oldItem == newItem else oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: LiveChatMessage, newItem: LiveChatMessage) =
                oldItem == newItem
        }
    }
}
// endregion
