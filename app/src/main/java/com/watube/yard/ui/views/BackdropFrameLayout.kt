package com.watube.yard.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.RenderNode
import android.util.AttributeSet
import android.widget.FrameLayout

// region Navigation
/**
 * Holds the screens and keeps what they draw in [backdrop], so the glass of the bottom bar can
 * show it blurred (see [GlassBackground]).
 *
 * Nothing is drawn twice: while [isRecording], the children are recorded once into [backdrop]
 * and the layout then draws that node, which the glass draws again by reference. A node holds
 * the drawing commands of the children and points at their own nodes, so recording it costs a
 * few operations per frame and no pixel. Off, it is a plain FrameLayout.
 */
class BackdropFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** What the screens drew last, in this layout's coordinates. */
    val backdrop = RenderNode("WatubeBackdrop")

    /** Records the screens into [backdrop]; only while a glass shows them. */
    var isRecording = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) backdrop.discardDisplayList()
            invalidate()
        }

    override fun dispatchDraw(canvas: Canvas) {
        if (!isRecording || !canvas.isHardwareAccelerated) {
            super.dispatchDraw(canvas)
            return
        }
        backdrop.setPosition(0, 0, width, height)
        val recording = backdrop.beginRecording()
        try {
            super.dispatchDraw(recording)
        } finally {
            backdrop.endRecording()
        }
        canvas.drawRenderNode(backdrop)
    }
}
// endregion
