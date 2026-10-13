package com.watube.yard.ui.interfaces

// region Video Player
interface CustomPlayerCallback {
    fun toggleFullscreen()
    fun getVideoId(): String
    fun isVideoShort(): Boolean
    fun isVideoLive(): Boolean
    fun onControllerVisibilityChanged(visible: Boolean) = Unit
}
// endregion
