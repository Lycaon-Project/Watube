package com.watube.yard.helpers

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.core.content.getSystemService
import androidx.fragment.app.commitNow
import androidx.fragment.app.replace
import com.watube.yard.NavDirections
import com.watube.yard.R
import com.watube.yard.constants.IntentData
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.enums.PlaylistType
import com.watube.yard.extensions.TAG
import com.watube.yard.extensions.toID
import com.watube.yard.parcelable.PlayerData
import com.watube.yard.ui.activities.AbstractPlayerHostActivity
import com.watube.yard.ui.activities.MainActivity
import com.watube.yard.ui.activities.ZoomableImageActivity
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.fragments.AudioPlayerFragment
import com.watube.yard.ui.fragments.PlayerFragment
import com.watube.yard.util.PlayingQueue

object NavigationHelper {
    fun navigateChannel(context: Context, channelUrlOrId: String?) {
        if (channelUrlOrId == null) return

        // navigating to channels is only supported in the main activity, not in the no internet activity
        val activity = ContextHelper.tryUnwrapActivity<MainActivity>(context) ?: return
        activity.navController.navigate(NavDirections.openChannel(channelUrlOrId.toID()))
        try {
            // minimize player if currently expanded
            activity.runOnPlayerFragment {
                binding.playerMotionLayout.transitionToEnd()
                true
            }
            activity.minimizePlayerContainerLayout()
        } catch (e: Exception) {
            // Log the exception instead of printStackTrace for better debugging
            Log.e(TAG(), "Failed to minimize player while navigating to channel", e)
        }
    }

    /**
     * Navigate to the given video using the other provided parameters as well
     * If the audio only mode is enabled, play it in the background, else as a normal video
     */
    @SuppressLint("UnsafeOptInUsageError")
    fun navigateVideo(
        context: Context,
        playerData: PlayerData,
        alreadyStarted: Boolean = false,
        forceVideo: Boolean = false,
        audioOnlyPlayerRequested: Boolean = false,
    ) {
        // attempt to attach to the current media session first by using the corresponding
        // video/audio player instance
        val activity = ContextHelper.unwrapActivity<AbstractPlayerHostActivity>(context)
        val attachedToRunningPlayer = activity.runOnPlayerFragment {
            // can only continue using player if in same mode (online/offline)
            // otherwise, recreate the player
            if (playerData.isOffline != isOffline || playerData.videoId == null) return@runOnPlayerFragment false

            try {
                PlayingQueue.clearAfterCurrent()
                this.playVideo(playerData.videoId.toID())

                if (audioOnlyPlayerRequested) {
                    // switch to audio only player
                    this.switchToAudioMode()
                } else {
                    // maximize player
                    this.binding.playerMotionLayout.transitionToStart()
                }

                true
            } catch (e: Exception) {
                // Log the exception for debugging
                Log.e(TAG(), "Failed to reuse running player, recreating", e)
                this.onDestroy()
                false
            }
        }
        if (attachedToRunningPlayer) return

        val audioOnlyMode = PreferenceHelper.getBoolean(PreferenceKeys.AUDIO_ONLY_MODE, false)
        val attachedToRunningAudioPlayer = activity.runOnAudioPlayerFragment {
            // can only continue using player if in same mode (online/offline)
            // otherwise, recreate the player
            if (playerData.isOffline != isOffline || playerData.videoId == null) return@runOnAudioPlayerFragment false

            PlayingQueue.clearAfterCurrent()
            this.playNextVideo(playerData.videoId.toID())

            if (!audioOnlyPlayerRequested && !audioOnlyMode) {
                // switch to video only player
                this.switchToVideoMode(playerData.videoId.toID())
            } else {
                // maximize player
                this.binding.playerMotionLayout.transitionToStart()
            }

            true
        }
        if (attachedToRunningAudioPlayer) return

        if (audioOnlyPlayerRequested || (audioOnlyMode && !forceVideo)) {
            // in contrast to the video player, the audio player doesn't start a media service on
            // its own!
            BackgroundHelper.playOnBackground(context, playerData)

            openAudioPlayerFragment(context, offlinePlayer = playerData.isOffline, minimizeByDefault = true)
        } else {
            openVideoPlayerFragment(
                context,
                playerData,
                alreadyStarted
            )
        }
    }

    fun navigatePlaylist(context: Context, playlistUrlOrId: String?, playlistType: PlaylistType) {
        if (playlistUrlOrId == null) return

        val activity = ContextHelper.unwrapActivity<MainActivity>(context)
        activity.navController.navigate(
            NavDirections.openPlaylist(playlistUrlOrId.toID(), playlistType)
        )
    }

    /**
     * Start the audio player fragment
     */
    fun openAudioPlayerFragment(
        context: Context,
        offlinePlayer: Boolean = false,
        minimizeByDefault: Boolean = false
    ) {
        val activity = ContextHelper.unwrapActivity<BaseActivity>(context)
        activity.supportFragmentManager.commitNow {
            val args = Bundle().apply {
                putBoolean(IntentData.minimizeByDefault, minimizeByDefault)
                putBoolean(IntentData.offlinePlayer, offlinePlayer)
            }
            replace<AudioPlayerFragment>(R.id.container, args = args)
        }
    }

    /**
     * Starts the video player fragment for an already existing media session
     */
    fun openVideoPlayerFragment(
        context: Context,
        playerData: PlayerData,
        alreadyStarted: Boolean = false,
    ) {
        val activity = ContextHelper.unwrapActivity<BaseActivity>(context)

        val bundle = Bundle().apply {
            putParcelable(IntentData.playerData, playerData)
            putBoolean(IntentData.alreadyStarted, alreadyStarted)
        }
        activity.supportFragmentManager.commitNow {
            replace<PlayerFragment>(R.id.container, args = bundle)
        }
    }

    /**
     * Open a large, zoomable image preview
     */
    fun openImagePreview(context: Context, url: String) {
        val intent = Intent(context, ZoomableImageActivity::class.java)
        intent.putExtra(IntentData.bitmapUrl, url)
        context.startActivity(intent)
    }

    /**
     * Needed due to different MainActivity Aliases because of the app icons
     */
    fun restartMainActivity(context: Context) {
        // kill player notification
        context.getSystemService<NotificationManager>()!!.cancelAll()
        // start a new Intent of the app
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(context.packageName)
        intent?.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
        // kill the old application
        Process.killProcess(Process.myPid())
    }
}