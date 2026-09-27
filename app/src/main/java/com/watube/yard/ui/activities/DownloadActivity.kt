package com.watube.yard.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import com.watube.yard.constants.IntentData
import com.watube.yard.enums.PlaylistType
import com.watube.yard.helpers.IntentHelper
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.dialogs.DownloadDialog
import com.watube.yard.ui.dialogs.DownloadPlaylistDialog

class DownloadActivity : BaseActivity() {
    override val isDialogActivity: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentData = intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            IntentHelper.resolveType(it.toUri())
        }

        val videoId = intentData?.getStringExtra(IntentData.videoId)
        val playlistId = intentData?.getStringExtra(IntentData.playlistId)
        if (videoId != null) {
            supportFragmentManager.setFragmentResultListener(
                DownloadDialog.DOWNLOAD_DIALOG_DISMISSED_KEY,
                this
            ) { _, _ -> finish() }

            DownloadDialog().apply {
                arguments = Bundle().apply {
                    putString(IntentData.videoId, videoId)
                }
            }.show(supportFragmentManager, null)
        } else if (playlistId != null) {
            DownloadPlaylistDialog().apply {
                arguments = Bundle().apply {
                    putString(IntentData.playlistId, playlistId)
                    putSerializable(IntentData.playlistType, PlaylistType.PUBLIC)
                    putString(IntentData.playlistName, intent.getStringExtra(Intent.EXTRA_TITLE))
                }
            }.show(supportFragmentManager, null)
        } else {
            finish()
        }
    }
}