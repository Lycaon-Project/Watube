package com.watube.yard.ui.interfaces

// region Background Playback
interface AudioPlayerOptions {

    fun onSingleTap()

    fun onLongTap()

    fun onSwipe(distanceY: Float)

    fun onSwipeEnd()
}
// endregion
