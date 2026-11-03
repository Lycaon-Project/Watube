package com.watube.yard.extensions

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.watube.yard.constants.IntentData
import com.watube.yard.enums.PlayerCommand
import com.watube.yard.parcelable.PlayerData
import com.watube.yard.services.AbstractPlayerService

// region Video Player
/**
 * Plays [videoId] next to the current video. A [playerData] opened from another screen also
 * brings its playlist or channel, its start time and whether the queue it set up stays.
 */
@OptIn(UnstableApi::class)
fun MediaController.navigateVideo(videoId: String, playerData: PlayerData? = null) {
    sendCustomCommand(
        AbstractPlayerService.runPlayerActionCommand,
        bundleOf(
            PlayerCommand.PLAY_VIDEO_BY_ID.name to videoId,
            IntentData.playerData to playerData
        )
    )
}
// endregion
