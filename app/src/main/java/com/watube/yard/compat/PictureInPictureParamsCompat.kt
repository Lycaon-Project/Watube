package com.watube.yard.compat

import android.app.PictureInPictureParams
import android.util.Rational
import androidx.core.app.RemoteActionCompat
import androidx.media3.common.VideoSize

// region Picture-in-Picture
class PictureInPictureParamsCompat private constructor(
    private val autoEnterEnabled: Boolean,
    private val actions: List<RemoteActionCompat>,
    private val aspectRatio: Rational?
) {
    fun toPictureInPictureParams(): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .setActions(actions.map { it.toRemoteAction() })
            .setAspectRatio(aspectRatio)
            .setAutoEnterEnabled(autoEnterEnabled)
            .build()

    class Builder {
        private var autoEnterEnabled = false
        private var actions: List<RemoteActionCompat> = emptyList()
        private var aspectRatio: Rational? = null

        fun setAutoEnterEnabled(autoEnterEnabled: Boolean) = apply {
            this.autoEnterEnabled = autoEnterEnabled
        }

        fun setActions(actions: List<RemoteActionCompat>) = apply {
            this.actions = actions
        }

        fun setAspectRatio(aspectRatio: Rational?) = apply {
            this.aspectRatio = aspectRatio
        }

        // Additional function replacing the project's extension function for the platform builder.
        fun setAspectRatio(videoSize: VideoSize): Builder {
            val ratio = (videoSize.width.toFloat() / videoSize.height)
            val rational = when {
                ratio.isNaN() -> Rational(4, 3)
                ratio <= 0.418410 -> Rational(41841, 100000)
                ratio >= 2.390000 -> Rational(239, 100)
                else -> Rational(videoSize.width, videoSize.height)
            }
            return setAspectRatio(rational)
        }

        fun build(): PictureInPictureParamsCompat {
            return PictureInPictureParamsCompat(
                autoEnterEnabled,
                actions,
                aspectRatio
            )
        }
    }
}
// endregion
