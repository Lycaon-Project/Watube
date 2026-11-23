package com.watube.yard.ui.activities

import android.content.Intent
import android.os.Bundle
import com.watube.yard.constants.IntentData
import com.watube.yard.extensions.bundleOf
import com.watube.yard.helpers.IntentHelper
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.dialogs.AddToQueueDialog
import com.watube.yard.util.PlayingQueue

// region Playing Queue
/**
 * Receives a shared text and, once the user confirms in [AddToQueueDialog], adds its video to
 * the playing queue, or opens the video when nothing is playing. The share can come from any
 * installed app, so nothing happens before that tap.
 */
class AddToQueueActivity : BaseActivity() {
    override val isDialogActivity: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoId = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?.let { IntentHelper.resolveType(IntentHelper.sharedTextToUri(it)) }
            ?.getStringExtra(IntentData.videoId)

        if (videoId == null) {
            finish()
            return
        }

        supportFragmentManager.setFragmentResultListener(
            AddToQueueDialog.ADD_TO_QUEUE_CONFIRMED_KEY,
            this
        ) { _, _ -> queueOrOpen(videoId) }
        supportFragmentManager.setFragmentResultListener(
            AddToQueueDialog.ADD_TO_QUEUE_DIALOG_DISMISSED_KEY,
            this
        ) { _, _ -> finish() }

        // after a rotation the dialog is restored by the fragment manager
        if (savedInstanceState == null) {
            AddToQueueDialog().apply {
                arguments = bundleOf(
                    IntentData.videoId to videoId,
                    AddToQueueDialog.ADDS_TO_QUEUE to PlayingQueue.isNotEmpty()
                )
            }.show(supportFragmentManager, null)
        }
    }

    private fun queueOrOpen(videoId: String) {
        // the launcher entry is an activity-alias swapped by the icon picker: never null here
        val newIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)

        // if playing a video currently, the video will be added to the queue
        if (PlayingQueue.isNotEmpty()) {
            PlayingQueue.insertByVideoId(videoId)
        } else {
            newIntent.putExtra(IntentData.videoId, videoId)
        }

        startActivity(newIntent)
    }
}
// endregion
