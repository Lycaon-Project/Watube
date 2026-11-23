package com.watube.yard.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.extensions.bundleOf
import androidx.recyclerview.widget.ListAdapter
import com.watube.yard.R
import com.watube.yard.api.obj.Playlists
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.PlaylistCoverBinding
import com.watube.yard.enums.PlaylistType
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.ui.adapters.callbacks.DiffUtilItemCallback
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.extensions.nameLongPress
import com.watube.yard.ui.sheets.PlaylistOptionsBottomSheet
import com.watube.yard.ui.sheets.PlaylistOptionsBottomSheet.Companion.PLAYLIST_OPTIONS_REQUEST_KEY
import com.watube.yard.ui.viewholders.PlaylistsViewHolder

// region Playlists
class PlaylistsAdapter(
    private val playlistType: PlaylistType
) : ListAdapter<Playlists, PlaylistsViewHolder>(
    DiffUtilItemCallback(areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id })
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistsViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = PlaylistCoverBinding.inflate(layoutInflater, parent, false)
        return PlaylistsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistsViewHolder, position: Int) {
        val playlist = getItem(holder.bindingAdapterPosition)
        holder.binding.apply {
            // a playlist without a thumbnail shows the empty playlist mark, small and centred
            // on the cover's own surface; a recycled tile goes back to a cropped thumbnail
            if (playlist.thumbnail.orEmpty().split("/").size <= 4) {
                playlistThumbnail.tag = null
                playlistThumbnail.scaleType = ImageView.ScaleType.CENTER_INSIDE
                playlistThumbnail.setPadding(emptyMarkInset(playlistThumbnail))
                playlistThumbnail.setImageResource(R.drawable.ic_empty_playlist)
            } else {
                playlistThumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
                playlistThumbnail.setPadding(0)
                ImageHelper.loadImage(playlist.thumbnail, playlistThumbnail)
            }
            playlistTitle.text = playlist.name
            playlistDescription.text = playlist.shortDescription
            // a playlist without a description keeps its tile tight under the title
            playlistDescription.isVisible = !playlist.shortDescription.isNullOrBlank()

            videoCount.text = playlist.videos.toString()
            // read as one: the name first, then the description and the count (which sits first on screen)
            root.contentDescription = listOfNotNull(
                playlist.name,
                playlist.shortDescription?.takeIf { it.isNotBlank() },
                root.resources.getQuantityString(R.plurals.a11y_video_count, playlist.videos.toInt(), playlist.videos)
            ).joinToString(", ")

            root.setOnClickListener {
                NavigationHelper.navigatePlaylist(root.context, playlist.id, playlistType)
            }

            val fragmentManager = (root.context as BaseActivity).supportFragmentManager
            root.nameLongPress(R.string.tooltip_more)
            root.setOnLongClickListener {
                fragmentManager.setFragmentResultListener(
                    PLAYLIST_OPTIONS_REQUEST_KEY,
                    (root.context as BaseActivity)
                ) { _, resultBundle ->
                    val newPlaylistDescription =
                        resultBundle.getString(IntentData.playlistDescription)
                    val newPlaylistName =
                        resultBundle.getString(IntentData.playlistName)
                    val isPlaylistToBeDeleted =
                        resultBundle.getBoolean(IntentData.playlistTask)

                    newPlaylistDescription?.let {
                        playlistDescription.text = it
                        playlist.shortDescription = it
                    }

                    newPlaylistName?.let {
                        playlistTitle.text = it
                        playlist.name = it
                    }

                    if (isPlaylistToBeDeleted) {
                        // try to refresh the playlists in the library on deletion success;
                        // the tile's current place, not the one it had when it was bound
                        holder.bindingAdapterPosition
                            .takeIf { it != RecyclerView.NO_POSITION }
                            ?.let(::onDelete)
                    }
                }

                val playlistOptionsDialog = PlaylistOptionsBottomSheet()
                playlistOptionsDialog.arguments = bundleOf(
                    IntentData.playlistId to playlist.id!!,
                    IntentData.playlistName to playlist.name!!,
                    IntentData.playlistType to playlistType
                )
                playlistOptionsDialog.show(
                    fragmentManager,
                    PlaylistOptionsBottomSheet::class.java.name
                )
                true
            }
        }
    }

    /** Room around the empty playlist mark, so it stays small on the cover. */
    private fun emptyMarkInset(view: View) =
        (view.resources.displayMetrics.density * EMPTY_MARK_INSET_DP).toInt()

    private fun onDelete(position: Int) {
        val newList = currentList.toMutableList().also {
            it.removeAt(position)
        }
        submitList(newList)
    }

    private companion object {
        const val EMPTY_MARK_INSET_DP = 14
    }
}
// endregion
