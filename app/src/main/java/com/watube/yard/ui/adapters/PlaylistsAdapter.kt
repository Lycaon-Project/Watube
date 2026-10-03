package com.watube.yard.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.watube.yard.extensions.bundleOf
import androidx.recyclerview.widget.ListAdapter
import com.watube.yard.R
import com.watube.yard.api.obj.Playlists
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.PlaylistsRowBinding
import com.watube.yard.enums.PlaylistType
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.ui.adapters.callbacks.DiffUtilItemCallback
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.sheets.PlaylistOptionsBottomSheet
import com.watube.yard.ui.sheets.PlaylistOptionsBottomSheet.Companion.PLAYLIST_OPTIONS_REQUEST_KEY
import com.watube.yard.ui.viewholders.PlaylistsViewHolder

class PlaylistsAdapter(
    private val playlistType: PlaylistType
) : ListAdapter<Playlists, PlaylistsViewHolder>(
    DiffUtilItemCallback(areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id })
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistsViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = PlaylistsRowBinding.inflate(layoutInflater, parent, false)
        return PlaylistsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistsViewHolder, position: Int) {
        val playlist = getItem(holder.bindingAdapterPosition)
        holder.binding.apply {
            // set imageview drawable as empty playlist if imageview empty
            if (playlist.thumbnail.orEmpty().split("/").size <= 4) {
                playlistThumbnail.setImageResource(R.drawable.ic_empty_playlist)
                playlistThumbnail
                    .setBackgroundColor(com.google.android.material.R.attr.colorSurface)
            } else {
                ImageHelper.loadImage(playlist.thumbnail, playlistThumbnail)
            }
            playlistTitle.text = playlist.name
            playlistDescription.text = playlist.shortDescription

            videoCount.text = playlist.videos.toString()

            root.setOnClickListener {
                NavigationHelper.navigatePlaylist(root.context, playlist.id, playlistType)
            }

            val fragmentManager = (root.context as BaseActivity).supportFragmentManager
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
                        // try to refresh the playlists in the library on deletion success
                        onDelete(position)
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

    private fun onDelete(position: Int) {
        val newList = currentList.toMutableList().also {
            it.removeAt(position)
        }
        submitList(newList)
    }
}
