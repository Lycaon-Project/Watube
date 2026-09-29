package com.watube.yard.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.watube.yard.R
import com.watube.yard.api.MediaServiceRepository
import com.watube.yard.api.obj.StreamItem
import com.watube.yard.constants.IntentData
import com.watube.yard.extensions.toastFromMainDispatcher
import com.watube.yard.helpers.IntentHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.dialogs.AddToPlaylistDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddToPlaylistActivity : BaseActivity() {
    override val isDialogActivity: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoId = intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            IntentHelper.resolveType(it.toUri())
        }?.getStringExtra(IntentData.videoId)

        if (videoId == null) {
            finish()
            return
        }

        supportFragmentManager.setFragmentResultListener(
            AddToPlaylistDialog.ADD_TO_PLAYLIST_DIALOG_DISMISSED_KEY,
            this
        ) { _, _ -> finish() }

        lifecycleScope.launch(Dispatchers.IO) {
            val videoInfo = if (PreferenceHelper.getToken().isEmpty()) {
                try {
                    MediaServiceRepository.instance.getStreams(videoId).toStreamItem(videoId)
                } catch (e: Exception) {
                    // Log the exception for better debugging
                    Log.e(TAG, "Failed to fetch stream info", e)
                    toastFromMainDispatcher(R.string.unknown_error)
                    withContext(Dispatchers.Main) {
                        finish()
                    }
                    return@launch
                }
            } else {
                StreamItem(videoId)
            }

            withContext(Dispatchers.Main) {
                AddToPlaylistDialog().apply {
                    arguments = Bundle().apply {
                        putParcelable(IntentData.videoInfo, videoInfo)
                    }
                }.show(supportFragmentManager, null)
            }
        }
    }

    companion object {
        private const val TAG = "AddToPlaylistActivity"
    }
}