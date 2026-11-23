package com.watube.yard.ui.views

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.widget.ImageView
import androidx.core.graphics.createBitmap
import androidx.core.view.isVisible

// region Background Playback
/**
 * The picture of a video whose track is turned off while unseen (see PlayerFragment.onStop),
 * back the moment the screen returns.
 *
 * Unseen, the video is neither downloaded nor decoded: only its sound plays. Turned back on, the
 * video renderer joins the playing sound but shows nothing until the network has brought the
 * video back and the decoder has caught up with the position (half a second to a few seconds),
 * while the surface, which lost its picture with the window, stays black.
 *
 * So the last frame seen is copied from the surface as the screen leaves ([capture], while the
 * surface still holds it) and laid over the video as it comes back ([show]): it is there with the
 * window's very first frame, and fades out as soon as the video moves again ([reveal]). The copy
 * is taken at the surface's size, capped at [MAX_COPY_SIDE_PX] on its longest side (a 4K surface
 * would take 33 MB, at each pause), and kept only while the video is off.
 *
 * @param image the view over the video surface that shows the copy, hidden otherwise
 * @param surface the player's current video surface, if it is a SurfaceView
 */
class VideoReturnFrame(
    private val image: ImageView,
    private val surface: () -> SurfaceView?,
) {
    private val handler = Handler(Looper.getMainLooper())

    /** The last frame copied, waiting for the screen to come back. */
    private var frame: Bitmap? = null

    /** Numbers the copies, so that one finishing after [clear] or a newer copy is dropped. */
    private var copyId = 0

    /** Should the video never move again (an error, a lost network), the copy goes anyway. */
    private val fallback = Runnable { reveal() }

    /** Whether the copy is over the video right now. */
    val isShowing: Boolean get() = image.isVisible

    /**
     * Copies the frame the surface shows now, before the screen leaves. Nothing is copied while
     * the copy itself shows: the surface under it has no picture yet.
     */
    fun capture() {
        if (isShowing) return
        val view = surface() ?: return
        if (view.width <= 0 || view.height <= 0 || !view.holder.surface.isValid) return
        // PixelCopy scales the picture to the bitmap; the image view scales it back
        val scale = minOf(1f, MAX_COPY_SIDE_PX.toFloat() / maxOf(view.width, view.height))
        val width = (view.width * scale).toInt().coerceAtLeast(1)
        val height = (view.height * scale).toInt().coerceAtLeast(1)
        // without memory to spare, the screen comes back black for a moment, as without a copy
        val target = try {
            createBitmap(width, height)
        } catch (_: OutOfMemoryError) {
            return
        }
        val id = ++copyId
        PixelCopy.request(
            view,
            target,
            { result -> if (id == copyId && result == PixelCopy.SUCCESS) frame = target },
            handler
        )
    }

    /** Lays the last copy over the video, which is turned back on right after. */
    fun show() {
        val picture = frame ?: return
        image.animate().cancel()
        image.setImageBitmap(picture)
        image.alpha = 1f
        image.isVisible = true
        handler.removeCallbacks(fallback)
        handler.postDelayed(fallback, FALLBACK_DELAY_MS)
    }

    /** The video moves again: the copy fades out over it, then is dropped. */
    fun reveal() {
        handler.removeCallbacks(fallback)
        if (!isShowing) return
        image.animate()
            .alpha(0f)
            .setDuration(FADE_DURATION_MS)
            .withEndAction(::clear)
    }

    /** Drops the copy at once (another video, the view going away, a copy no longer needed). */
    fun clear() {
        copyId++
        handler.removeCallbacks(fallback)
        image.animate().cancel()
        image.isVisible = false
        image.setImageDrawable(null)
        frame = null
    }

    private companion object {
        /** Long enough to cover the first frame's way to the screen, short enough to read as one. */
        const val FADE_DURATION_MS = 150L

        /** Far beyond the slowest return measured (2.5 s), well before a still would puzzle. */
        const val FALLBACK_DELAY_MS = 8_000L

        /** Full HD: as sharp as the screen of a phone, at most 8 MB per copy. */
        const val MAX_COPY_SIDE_PX = 1920
    }
}
// endregion
