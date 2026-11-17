package com.watube.yard.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import com.watube.yard.extensions.bundleOf
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import com.watube.yard.R
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.PlaylistCoverBinding
import com.watube.yard.db.DatabaseHolder
import com.watube.yard.db.obj.PlaylistBookmark
import com.watube.yard.enums.PlaylistType
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.ui.adapters.callbacks.DiffUtilItemCallback
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.extensions.nameLongPress
import com.watube.yard.ui.sheets.PlaylistOptionsBottomSheet
import com.watube.yard.ui.viewholders.PlaylistBookmarkViewHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// region Playlist Bookmarks
class PlaylistBookmarkAdapter: ListAdapter<PlaylistBookmark, PlaylistBookmarkViewHolder>(
    DiffUtilItemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.playlistId == newItem.playlistId }
    )
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistBookmarkViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return PlaylistBookmarkViewHolder(
            PlaylistCoverBinding.inflate(layoutInflater, parent, false)
        )
    }

    private fun showPlaylistOptions(context: Context, bookmark: PlaylistBookmark) {
        val sheet = PlaylistOptionsBottomSheet()
        sheet.arguments = bundleOf(
            IntentData.playlistId to bookmark.playlistId,
            IntentData.playlistName to bookmark.playlistName,
            IntentData.playlistType to PlaylistType.PUBLIC
        )
        sheet.show(
            (context as BaseActivity).supportFragmentManager
        )
    }

    /** The toggle's icon, and what it does next for a screen reader. */
    private fun showBookmarkState(toggle: ImageView, isBookmarked: Boolean) {
        toggle.setImageResource(
            if (isBookmarked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outlined
        )
        toggle.contentDescription = toggle.context.getString(
            if (isBookmarked) R.string.remove_bookmark else R.string.add_to_bookmarks
        )
    }

    override fun onBindViewHolder(holder: PlaylistBookmarkViewHolder, position: Int) {
        val bookmark = getItem(holder.bindingAdapterPosition)

        with(holder.binding) {
            var isBookmarked = true

            ImageHelper.loadImage(bookmark.thumbnailUrl, playlistThumbnail)
            playlistTitle.text = bookmark.playlistName
            playlistDescription.text = bookmark.uploader
            playlistDescription.isVisible = !bookmark.uploader.isNullOrBlank()
            videoCount.text = bookmark.videos.toString()

            // a recycled tile may still show the state another bookmark was left in
            showBookmarkState(bookmarkPlaylist, isBookmarked)
            bookmarkPlaylist.setOnClickListener {
                isBookmarked = !isBookmarked
                showBookmarkState(bookmarkPlaylist, isBookmarked)
                CoroutineScope(Dispatchers.IO).launch {
                    if (!isBookmarked) {
                        DatabaseHolder.Database.playlistBookmarkDao()
                            .deleteById(bookmark.playlistId)
                    } else {
                        DatabaseHolder.Database.playlistBookmarkDao().insert(bookmark)
                    }
                }
            }
            bookmarkPlaylist.isVisible = true

            root.setOnClickListener {
                NavigationHelper.navigatePlaylist(
                    root.context,
                    bookmark.playlistId,
                    PlaylistType.PUBLIC
                )
            }

            root.nameLongPress(R.string.tooltip_more)
            root.setOnLongClickListener {
                showPlaylistOptions(root.context, bookmark)
                true
            }
        }
    }
}
// endregion
