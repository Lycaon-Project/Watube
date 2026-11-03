package com.watube.yard.enums

// region Video Player
enum class PlayerCommand {
    SKIP_SILENCE,
    SET_VIDEO_TRACK_TYPE_DISABLED,
    SET_AUDIO_ROLE_FLAGS,
    SET_RESOLUTION,
    SET_AUDIO_LANGUAGE,
    SET_CAPTION_TRACK,
    SET_SB_AUTO_SKIP_ENABLED,
    PLAY_NEXT_VIDEO,
    PLAY_VIDEO_BY_ID,
    SET_AUTOPLAY_COUNTDOWN_ENABLED,
    TOGGLE_AUDIO_ONLY_MODE,

    /** Stolm: casts the current video to the receiver in the bundle (a CastTarget). */
    CAST_TO,

    /** Stolm: brings the video back to the phone. */
    STOP_CAST
}
// endregion
