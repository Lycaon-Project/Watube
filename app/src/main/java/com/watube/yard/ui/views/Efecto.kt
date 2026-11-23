package com.watube.yard.ui.views

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.core.view.doOnLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.R as MaterialR
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.helpers.ContextHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.helpers.ThemeHelper

// region Navigation
/**
 * Efecto, the design of this release: the surfaces floating over the screens show what
 * scrolls under them through frosted glass ([GlassBackground]). Turned off (Appearance >
 * Disable Efecto), they keep a plain fill, slightly see-through (92 %), as the bottom bar does.
 *
 * This brings it to the headers floating over a screen's list (Subscriptions, History): the
 * list runs on under the header ([runUnder]) and the header shows it blurred ([applyToHeader]).
 */
object Efecto {
    /** Share of the page tone in the glaze of a header's glass, where no smart tint replaces it. */
    private const val GLASS_ALPHA = 0xD6

    /**
     * Share of the page tone in a header's plain fill, Efecto off (92 %, as the bottom bar's
     * plain fill): nothing is blurred any more, so less shows through, only faintly.
     */
    private const val PLAIN_ALPHA = 0xEB

    /** Whether Efecto is on (off only through Appearance > Disable Efecto). */
    val isOn: Boolean
        get() = !PreferenceHelper.getBoolean(PreferenceKeys.EFECTO_OFF, false)

    /**
     * Gives [header] the glass over [source] while Efecto is on, else the page tone a little
     * see-through. Call it each time the screen resumes: nothing is done while the setting
     * has not changed since the last call for this header.
     */
    fun applyToHeader(header: View, source: BackdropFrameLayout) {
        val on = isOn
        if (header.getTag(R.id.watube_efecto_state) == on) return
        header.setTag(R.id.watube_efecto_state, on)

        val context = header.context
        val page = ThemeHelper.getThemeColor(context, android.R.attr.colorBackground)
        // the rows come out from under the header softly, never at a hard edge
        val fade = context.resources.getDimension(R.dimen.watube_glass_header_fade)

        // the list is recorded for the glass only while the glass shows it
        source.isRecording = on
        header.background = if (on) {
            val glazeTone = ColorUtils.setAlphaComponent(page, GLASS_ALPHA)
            val glaze = LayerDrawable(arrayOf(ColorDrawable(glazeTone)))
                .apply { setId(0, R.id.watube_glass_fill) }
            GlassBackground(
                source = source,
                host = header,
                glaze = glaze,
                tone = page,
                ground = page,
                night = isNight(context),
                blurRadius = context.resources.getDimension(R.dimen.watube_glass_blur_radius),
                cornerRadius = 0f,
                fadeHeight = fade,
            )
        } else {
            val res = context.resources
            val plain = ColorUtils.setAlphaComponent(page, PLAIN_ALPHA)
            fadingFill(plain, res.getDimensionPixelSize(R.dimen.watube_plain_header_fade))
        }
    }

    /** The plain fill of a header, Efecto off, its last [fade] pixels fading out. */
    private fun fadingFill(color: Int, fade: Int): LayerDrawable {
        val edge = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(color, ColorUtils.setAlphaComponent(color, 0))
        )
        return LayerDrawable(arrayOf(ColorDrawable(color), edge)).apply {
            setLayerInsetBottom(0, fade)
            setLayerGravity(1, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL)
            setLayerHeight(1, fade)
        }
    }

    /**
     * Lets the list in [frame] (the coordinator's scrolling view, laid out under [header])
     * run on under the part of the header that stays on screen, [overlap] pixels high: the
     * frame moves up by that much and the [list] starts as far down, so its first row still
     * opens right under the header. Followed whenever the header's height changes.
     */
    fun runUnder(header: View, frame: View, list: View, overlap: () -> Int) {
        runFrameUnder(header, frame, overlap) { room -> padUnder(list, room) }
    }

    /**
     * [runUnder] for a frame holding several lists (the pages of a pager): the frame moves up
     * by [overlap] pixels and [fitRoom] gets that room, to give it to each list with
     * [padUnder]. Returns the fit itself, to run again when the overlap changes for another
     * reason than the header's height (the header moved in or out of the screen's top).
     */
    fun runFrameUnder(
        header: View,
        frame: View,
        overlap: () -> Int,
        fitRoom: (room: Int) -> Unit
    ): () -> Unit {
        fun fit() {
            val room = overlap()
            if ((frame.layoutParams as ViewGroup.MarginLayoutParams).topMargin != -room) {
                frame.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = -room }
            }
            fitRoom(room)
        }
        fit()
        // a new height lands during a layout pass: the frame follows on the next one, unless
        // the screen is gone by then (its views torn down: fitting them would read a cleared
        // binding)
        header.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                header.post { if (header.isAttachedToWindow) fit() }
            }
        }
        return ::fit
    }

    /**
     * Keeps [room] pixels free at the top of [list], on top of its own top padding. A list
     * still at its top stays there: a RecyclerView keeps its rows where they were when its
     * padding grows, which would leave the first row under the header once the room comes in
     * after the rows (a channel tab loaded from the cache before the header's first layout).
     */
    fun padUnder(list: View, room: Int) {
        val base = list.getTag(R.id.watube_efecto_base_padding) as? Int
            ?: list.paddingTop.also { list.setTag(R.id.watube_efecto_base_padding, it) }
        (list as? ViewGroup)?.clipToPadding = false
        if (list.paddingTop == base + room) return
        val atTop = !list.canScrollVertically(-1)
        list.updatePadding(top = base + room)
        if (atTop) (list as? RecyclerView)?.scrollToPosition(0)
    }

    /**
     * Takes back what [applyToHeader] gave [header], for a header that no longer floats over
     * [source] (the home header scrolling away with the feed): no background, no recording.
     */
    fun clearHeader(header: View, source: BackdropFrameLayout) {
        if (header.getTag(R.id.watube_efecto_state) == null) return
        header.setTag(R.id.watube_efecto_state, null)
        source.isRecording = false
        header.background = null
    }

    /**
     * Gives a bottom sheet its own pane of glass, deeper than the bottom bar's. The app behind
     * stays sharp, only set back ([SHEET_DIM]), and the sheet stands out by a drop shadow.
     * With Efecto on (Android 13 and up, the screens being recorded for the bar's glass), the
     * sheet shows those screens through frosted glass ([GlassBackground.Style.FROSTED]: wider
     * blur, calmer tint, fine grain); else the bar's plain fill (92 %). The glass reflection
     * ([SheetGloss]) lies over both.
     */
    fun applyToSheet(dialog: BottomSheetDialog) {
        val window = dialog.window ?: return
        val sheet = dialog.findViewById<View>(MaterialR.id.design_bottom_sheet) ?: return
        window.setDimAmount(SHEET_DIM)
        sheet.elevation = sheet.resources.getDimension(R.dimen.watube_sheet_elevation)
        // the glass needs the smart tint and the grain (13), and clips to the sheet's shape,
        // a path, which a view can clip to from 13 on
        val source = if (isOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextHelper.tryUnwrapActivity<Activity>(dialog.context)
                ?.findViewById<BackdropFrameLayout>(R.id.backdrop)
        } else {
            null
        }
        // the behavior gives the sheet its shape when it first lays it out
        sheet.doOnLayout { dressSheet(sheet, source) }
    }

    /**
     * Lays the sheet's material over the behavior's own shape, which keeps its corners
     * (rounded, squared once full screen), once: a sheet shown again keeps it.
     */
    private fun dressSheet(sheet: View, source: BackdropFrameLayout?) {
        val context = sheet.context
        val night = isNight(context)
        val plain = context.getColor(if (night) R.color.watube_nav_plain_night else R.color.watube_nav_plain)
        val shape = when (val background = sheet.background) {
            is MaterialShapeDrawable -> background
            is LayerDrawable -> return
            else -> {
                sheet.setBackgroundColor(plain)
                return
            }
        }
        val glossHeight = context.resources.getDimensionPixelSize(R.dimen.watube_sheet_gloss_height)
        val gloss = SheetGloss(shape, glossHeight)
        if (source == null) {
            shape.fillColor = ColorStateList.valueOf(plain)
            sheet.background = LayerDrawable(arrayOf(shape, gloss))
            return
        }
        // the shape only gives the outline now: the glass, clipped to it, is the fill, and
        // falls back to the plain fill while the screens are not recorded (full player open)
        shape.fillColor = ColorStateList.valueOf(Color.TRANSPARENT)
        val glaze = LayerDrawable(arrayOf(ColorDrawable(plain))).apply { setId(0, R.id.watube_glass_fill) }
        val glass = GlassBackground(
            source = source,
            host = sheet,
            glaze = glaze,
            tone = context.getColor(if (night) R.color.watube_nav_scrim_night else R.color.watube_nav_scrim),
            ground = ThemeHelper.getThemeColor(context, android.R.attr.colorBackground),
            night = night,
            blurRadius = context.resources.getDimension(R.dimen.watube_sheet_blur_radius),
            cornerRadius = 0f,
            style = GlassBackground.Style.FROSTED,
            acrossWindows = true,
        )
        sheet.clipToOutline = true
        sheet.background = LayerDrawable(arrayOf(shape, glass, gloss))
    }

    /** Dim of the app behind a sheet: set back, still sharp, the sheet's shadow over it. */
    private const val SHEET_DIM = 0.40f

    private fun isNight(context: Context) =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
}
// endregion
