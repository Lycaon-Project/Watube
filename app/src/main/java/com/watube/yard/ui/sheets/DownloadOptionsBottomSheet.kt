package com.watube.yard.ui.sheets

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.watube.yard.R
import com.watube.yard.api.obj.StreamItem
import com.watube.yard.constants.IntentData
import com.watube.yard.enums.ShareObjectType
import com.watube.yard.extensions.parcelable
import com.watube.yard.extensions.serializable
import com.watube.yard.extensions.toID
import com.watube.yard.helpers.BackgroundHelper
import com.watube.yard.helpers.ContextHelper
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.obj.ShareData
import com.watube.yard.parcelable.PlayerData
import com.watube.yard.ui.activities.NoInternetActivity
import com.watube.yard.ui.dialogs.ShareDialog
import com.watube.yard.ui.fragments.DownloadTab
import com.watube.yard.util.PlayingQueue
import com.watube.yard.util.PlayingQueueMode

class DownloadOptionsBottomSheet : BaseBottomSheet() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val streamItem = arguments?.parcelable<StreamItem>(IntentData.streamItem)!!
        val videoId = streamItem.url!!.toID()
        val downloadTab = arguments?.serializable<DownloadTab>(IntentData.downloadTab)!!
        val playlistId = arguments?.getString(IntentData.playlistId)

        val options = mutableListOf(
            R.string.playOnBackground,
            R.string.share,
            R.string.delete
        )

        // can't navigate to video while in offline activity
        if (ContextHelper.tryUnwrapActivity<NoInternetActivity>(requireContext()) == null) {
            options += R.string.go_to_video
        }

        val isSelectedVideoCurrentlyPlaying = PlayingQueue.getCurrent()?.url?.toID() == videoId
        if (!isSelectedVideoCurrentlyPlaying && PlayingQueue.isNotEmpty() && PlayingQueue.queueMode == PlayingQueueMode.OFFLINE) {
            options += R.string.play_next
            options += R.string.add_to_queue
        }

        setSimpleItems(options.map { getString(it) }) { which ->
            val playerData = PlayerData(
                videoId,
                playlistId = playlistId,
                downloadTab = downloadTab,
                isOffline = true
            )

            when (options[which]) {
                R.string.playOnBackground -> {
                    BackgroundHelper.playOnBackground(requireContext(), playerData)
                }

                R.string.go_to_video -> {
                    NavigationHelper.navigateVideo(requireContext(), playerData)
                }

                R.string.share -> {
                    val shareData = ShareData(currentVideo = videoId)
                    val bundle = bundleOf(
                        IntentData.id to videoId,
                        IntentData.shareObjectType to ShareObjectType.VIDEO,
                        IntentData.shareData to shareData
                    )
                    val newShareDialog = ShareDialog()
                    newShareDialog.arguments = bundle
                    newShareDialog.show(parentFragmentManager, null)
                }

                R.string.delete -> {
                    setFragmentResult(DELETE_DOWNLOAD_REQUEST_KEY, bundleOf())
                    dialog?.dismiss()
                }

                R.string.play_next -> {
                    PlayingQueue.addAsNext(streamItem)
                }

                R.string.add_to_queue -> {
                    PlayingQueue.add(streamItem)
                }
            }
        }

        super.onCreate(savedInstanceState)
    }

    companion object {
        const val DELETE_DOWNLOAD_REQUEST_KEY = "delete_download_request_key"
    }
}
