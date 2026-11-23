package com.watube.yard.ui.base

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentManager
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.helpers.ThemeHelper
import java.util.Collections
import java.util.WeakHashMap
import kotlin.concurrent.thread

// region Appearance
/** The old screen dissolving into the new one: short, and even over the whole screen. */
private const val FADE_DURATION_MS = 200L

/** The short-lived thread that takes the picture of the old screen. */
private const val SHOT_THREAD = "ThemeShot"

/** Longest wait for the picture of the old screen before the new one shows without a fade. */
private const val COPY_TIMEOUT_MS = 150L

/** Material 3 standard easing: the change starts at once and settles softly. */
private val STANDARD_EASING = PathInterpolator(0.2f, 0f, 0f, 1f)

/**
 * Switches the app between light, dark and OLED while it runs, with no restart, in a single
 * gentle movement: the very tap rebuilds every open screen in the new theme, while a picture
 * of the screen in sight is taken off the main thread; the rebuilt screen shows under that
 * picture, which dissolves into it.
 *
 * Nothing ever blanks out: the old screen stays in sight, every element included, until the
 * new one is ready under it. The light changes evenly over the whole screen, with no edge or
 * flash for the eyes to follow. With animations removed in the system settings, the new
 * theme shows at once.
 */
internal object ThemeTransition {

    private data class BarLook(val lightStatus: Boolean, val lightNavigation: Boolean)

    /** A picture of the old screen being taken, kept by the rebuilt screen until it dissolves. */
    private class Shot(
        val activityName: String,
        /** The status and navigation bar icons of the old screen, kept until the fade. */
        val oldBars: BarLook,
        val width: Int,
        val height: Int
    ) {
        /** The copy has been started (once only). */
        var taking = false

        /** The picture, once the copy has written it. */
        private var bitmap: Bitmap? = null

        /** The copy finished (well or not), or was given up. */
        var settled = false
            private set

        /** The picture is there to draw. */
        var ready = false
            private set

        /** Nobody draws the picture anymore: it goes as soon as the copy no longer writes it. */
        private var dropped = false

        /** The copy no longer writes into the bitmap: it can be recycled. */
        private var copyDone = false

        /** Called once, when the copy settles. */
        var onSettled: (() -> Unit)? = null

        val picture: Bitmap? get() = bitmap?.takeIf { ready && !it.isRecycled }

        /** On the main thread: the copy into [copy] is over, written when [success]. */
        fun copied(copy: Bitmap, success: Boolean) {
            copyDone = true
            bitmap = copy
            if (settled) {
                // given up meanwhile: the copy was the last one to touch the bitmap
                copy.recycle()
                return
            }
            settled = true
            ready = success && !dropped
            if (!ready) copy.recycle()
            onSettled?.invoke()
            onSettled = null
        }

        /** Shows the new screen without the picture, the copy being too slow. */
        fun giveUp() {
            if (settled) return
            settled = true
            dropped = true
            onSettled?.invoke()
            onSettled = null
        }

        fun drop() {
            dropped = true
            ready = false
            // still being written: the copy result recycles it
            if (copyDone) bitmap?.takeUnless { it.isRecycled }?.recycle()
        }
    }

    private var pending: Shot? = null

    /** Bumped at each switch: a screen built under an older one rebuilds itself. */
    var generation = 0
        private set

    private val live = Collections.newSetFromMap(WeakHashMap<BaseActivity, Boolean>())

    /**
     * The copy results come back as asynchronous messages: they are not held behind the
     * rebuilt screen's first layout, which waits for them.
     */
    private val copyHandler by lazy { Handler.createAsync(Looper.getMainLooper()) }

    fun onCreated(activity: BaseActivity) {
        live.add(activity)
        // the rebuild keeps the window, which still shows the last frame of the old screen:
        // the picture is taken now, on the render thread, while this thread builds the new
        // screen. Taken at the tap, it held up the last frames of the old screen (the render
        // thread busy reading the screen back) and the rebuild behind them
        pending?.takeIf { it.activityName == activity.javaClass.name && !it.taking }
            ?.let { takePicture(it, activity) }
    }

    fun onDestroyed(activity: BaseActivity) {
        live.remove(activity)
    }

    /**
     * Applies a theme change: [apply] writes the new preferences, then every open screen is
     * rebuilt at once in the new theme, the one in sight dissolving from the old look to the new.
     */
    @MainThread
    fun switchTheme(activity: BaseActivity, apply: () -> Unit) {
        val shot = startShot(activity)
        apply()
        rebuildAll(shot)
    }

    /**
     * The system went from day to night or back, and [activity] handles that change itself
     * (it keeps its views): rebuild it, dissolving from the old look to the new.
     */
    @MainThread
    fun followSystem(activity: BaseActivity) {
        replacePending(if (activity.hasWindowFocus()) startShot(activity) else null)
        activity.recreate()
    }

    /**
     * True when [config] brings a night mode other than [builtNight], the one [activity] was
     * built with, outside a switch of the app's own (that one rebuilds every screen already).
     */
    fun nightChanged(activity: BaseActivity, config: Configuration, builtNight: Int): Boolean =
        (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) != builtNight &&
            activity.themeGeneration == generation

    /**
     * The rebuilt screen lies under the picture of the old one, which dissolves as soon as the
     * new content has been laid out. Until the picture is there (it is taken off the main
     * thread, in a few milliseconds), the new screen holds its first frame, so the old one
     * stays in sight.
     */
    fun onPostCreated(activity: BaseActivity) {
        val shot = pending?.takeIf { it.activityName == activity.javaClass.name } ?: return
        pending = null
        val decor = activity.window.decorView as? ViewGroup
        if (decor == null) {
            shot.drop()
            return
        }
        // the system bars change at once, the screen only when it fades in: until then they
        // keep the old look, so their icons stay readable over the old picture. Read here,
        // after setContentView(), which applies the theme's own bar look to the window
        val newBars = barsOf(activity)
        setBars(activity, shot.oldBars)
        val cover = SnapshotView(activity, shot)
        decor.addView(cover, ViewGroup.LayoutParams(MATCH, MATCH))
        val giveUp = Runnable { shot.giveUp() }
        shot.onSettled = {
            decor.removeCallbacks(giveUp)
            cover.invalidate()
        }
        decor.postDelayed(giveUp, COPY_TIMEOUT_MS)
        cover.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                // no frame of the new screen before the picture that covers it
                if (!shot.settled) return false
                cover.viewTreeObserver.removeOnPreDrawListener(this)
                setBars(activity, newBars)
                if (shot.ready) {
                    cover.animate()
                        .alpha(0f)
                        .setDuration(FADE_DURATION_MS)
                        .setInterpolator(STANDARD_EASING)
                        .withEndAction { decor.removeView(cover) }
                } else {
                    decor.post { decor.removeView(cover) }
                }
                return true
            }
        })
    }

    /** Starts taking the picture of [activity]'s window, or null when it cannot fade. */
    private fun startShot(activity: BaseActivity): Shot? {
        val decor = activity.window.decorView
        if (!ValueAnimator.areAnimatorsEnabled() || !decor.isAttachedToWindow ||
            decor.width == 0 || decor.height == 0 || activity.isFinishing
        ) {
            return null
        }
        return Shot(activity.javaClass.name, barsOf(activity), decor.width, decor.height)
    }

    /**
     * Copies the last frame [activity]'s window showed into [shot]. Before Android 14 the copy
     * runs on the calling thread, a read-back of the whole screen: off the main thread, it
     * never holds back the building of the new screen.
     */
    private fun takePicture(shot: Shot, activity: BaseActivity) {
        shot.taking = true
        val window = activity.window
        thread(name = SHOT_THREAD) {
            val bitmap = Bitmap.createBitmap(shot.width, shot.height, Bitmap.Config.ARGB_8888)
            try {
                // the window's own surface: a dialog over it is not part of the picture
                PixelCopy.request(
                    window,
                    bitmap,
                    { result -> shot.copied(bitmap, result == PixelCopy.SUCCESS) },
                    copyHandler
                )
            } catch (_: IllegalArgumentException) {
                // the window lost its surface meanwhile: no picture, the new screen shows at once
                copyHandler.post { shot.copied(bitmap, false) }
            }
        }
    }

    private fun replacePending(next: Shot?) {
        pending?.drop()
        pending = next
    }

    private fun rebuildAll(next: Shot?) {
        generation++
        replacePending(next)
        // every screen is rebuilt, the OLED variant changing no configuration. A dialog closing
        // (the theme list) must be gone first, or the rebuild would bring it back
        val screens = live.toList()
        screens.forEach {
            it.supportFragmentManager.flushPending()
            it.recreate()
        }
        // each rebuilt screen applies the new night mode as it is created (BaseActivity
        // .attachBaseContext). Applied here, it made the screens about to be dropped lay
        // themselves out again first, holding the rebuild back (a third of a second on a slow
        // phone). With no screen to rebuild, it is applied now
        if (screens.isEmpty()) {
            val mode = ThemeHelper.getThemeMode(PreferenceHelper.getString(PreferenceKeys.THEME_MODE, "D"))
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    /** Runs the transactions still queued, such as a dialog being dismissed, in every level. */
    private fun FragmentManager.flushPending() {
        if (isDestroyed || isStateSaved) return
        executePendingTransactions()
        fragments.forEach { if (it.isAdded) it.childFragmentManager.flushPending() }
    }

    private fun barsOf(activity: BaseActivity): BarLook =
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).let {
            BarLook(it.isAppearanceLightStatusBars, it.isAppearanceLightNavigationBars)
        }

    private fun setBars(activity: BaseActivity, look: BarLook) {
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = look.lightStatus
            isAppearanceLightNavigationBars = look.lightNavigation
        }
    }

    /** The picture of the old screen, laid over the rebuilt one until it has dissolved. */
    private class SnapshotView(context: Context, private val shot: Shot) : View(context) {

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        // a single bitmap: fading it needs no offscreen pass
        override fun hasOverlappingRendering(): Boolean = false

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            shot.drop()
        }

        override fun onDraw(canvas: Canvas) {
            shot.picture?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        }
    }
}

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
// endregion
