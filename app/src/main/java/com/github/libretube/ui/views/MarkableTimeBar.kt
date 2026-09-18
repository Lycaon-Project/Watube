package com.github.libretube.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.view.marginLeft
import androidx.media3.common.util.UnstableApi
import com.github.libretube.api.obj.Segment
import com.github.libretube.extensions.dpToPx
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.helpers.ThemeHelper
import com.google.android.material.R

/**
 * TimeBar that can be marked with SponsorBlock Segments
 */
@UnstableApi
open class MarkableTimeBar(
    context: Context,
    attributeSet: AttributeSet? = null
) : DismissableTimeBar(context, attributeSet) {
    private var segments = listOf<Segment>()
    private var length: Int = 0

    private val progressBarHeight = 2f.dpToPx()

    // reused on every draw, the time bar redraws while the video plays
    private val segmentRect = Rect()
    private val segmentPaint = Paint()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawSegments(canvas)
    }

    private fun drawSegments(canvas: Canvas) {
        if (exoPlayer == null || segments.isEmpty()) return

        canvas.save()
        val horizontalOffset = (parent as View).marginLeft
        length = canvas.width - horizontalOffset * 2
        val marginY =  (canvas.height - progressBarHeight) / 2
        val themeColor = ThemeHelper.getThemeColor(context, R.attr.colorOnSecondary)
        val useCustomColors = PreferenceHelper.getBoolean("sb_enable_custom_colors", false)

        segments.forEach {
            val (start, end) = it.segmentStartAndEnd

            segmentPaint.color = if (useCustomColors) {
                PreferenceHelper.getInt(it.category + "_color", themeColor)
            } else {
                themeColor
            }

            segmentRect.set(
                start.toLength() + horizontalOffset,
                marginY,
                end.toLength() + horizontalOffset,
                marginY + progressBarHeight
            )
            canvas.drawRect(segmentRect, segmentPaint)
        }
        canvas.restore()
    }

    private fun Float.toLength(): Int {
        return (this * 1000 / exoPlayer!!.duration * length).toInt()
    }

    fun setSegments(segments: List<Segment>) {
        this.segments = segments
    }

    fun clearSegments() {
        segments = listOf()
    }
}
