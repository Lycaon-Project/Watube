package com.watube.yard.ui.views

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.core.view.marginLeft
import androidx.media3.common.util.UnstableApi
import com.watube.yard.api.obj.ChapterSegment
import com.watube.yard.extensions.dpToPx

/**
 * TimeBar that displays chapters as breaks in the progress bar.
 */
@UnstableApi
class ChapterTimeBar(
    context: Context,
    attributeSet: AttributeSet? = null,
) : MarkableTimeBar(context, attributeSet) {
    private var chapters = listOf<ChapterSegment>()
    private var length: Int = 0

    private val progressBarHeight = 2f.dpToPx()
    private val chapterBreakWidth = 4f.dpToPx()

    // reused on every draw, the time bar redraws while the video plays
    private val chapterRect = Rect()

    private val eraserPaint = Paint().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            blendMode = BlendMode.CLEAR
        } else {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val saveCount = canvas.saveLayer(null, null)
        super.onDraw(canvas)
        drawChapters(canvas)

        canvas.restoreToCount(saveCount)
    }


    private fun drawChapters(canvas: Canvas) {
        if (exoPlayer == null || chapters.size <= 1) return

        val horizontalOffset = (parent as View).marginLeft
        length = width - horizontalOffset * 2
        val marginY = (height - progressBarHeight) / 2

        // skip the first chapter as it always starts at 0:00
        for (i in 1 until chapters.size) {
            val center = chapters[i].start.toLength() + horizontalOffset

            chapterRect.set(
                center - (chapterBreakWidth / 2),
                marginY,
                center + (chapterBreakWidth / 2),
                marginY + progressBarHeight
            )
            canvas.drawRect(chapterRect, eraserPaint)
        }
    }

    private fun Long.toLength(): Int {
        return (this * 1000f / exoPlayer!!.duration * length).toInt()
    }

    fun setChapters(chapters: List<ChapterSegment>) {
        this.chapters = chapters
    }
}
