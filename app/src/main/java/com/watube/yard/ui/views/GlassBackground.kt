package com.watube.yard.ui.views

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import com.watube.yard.R
import kotlin.math.ceil

// region Navigation
/**
 * Frosted glass background for the surfaces floating over the screens: the bottom bar and the
 * docked mini player: Efecto, the design of this release (Appearance > Disable Efecto).
 *
 * The screens are drawn once, by [source]; the glass draws the same recording again, offset to
 * [host] and blurred by the GPU, then the surface's own [glaze] on top (its stroke, and for the
 * bar its sheen). Two layers of the glaze give way to the glass while it
 * shows: the plain fill (`watube_glass_fill`, from Android 13, where the smart tint replaces
 * it) and the gloss (`watube_glass_gloss`). With the glass off or hidden, the glaze is drawn
 * whole, the surface exactly as it was.
 *
 * The blur runs only on the frames where the screens or the surface change: an idle screen
 * draws no frame at all. [isShown] hides the glass when the surface is covered anyway (the
 * mini player's card under the full player).
 *
 * Watube's own glass, not a copy of any system look. On Android 13+ its tint is smart: each
 * point of the glass takes as much of the surface's tone as the colours under it need for its
 * ink to stay readable (dark rows by day, bright thumbnails at night get more; calm areas let
 * their colours glow through, slightly richer). Before 13 the plain fill stays over the blur.
 *
 * The glass follows [host] while it is attached and lets go of it for good as soon as the host
 * shows another background.
 */
class GlassBackground(
    private val source: BackdropFrameLayout,
    private val host: View,
    private val glaze: LayerDrawable,
    @ColorInt tone: Int,
    @ColorInt ground: Int,
    night: Boolean,
    blurRadius: Float,
    private val cornerRadius: Float,
    private val isShown: () -> Boolean = { true },
    private val fadeHeight: Float = 0f,
    style: Style = Style.CLEAR,
    /**
     * The host lies in another window than [source] (a sheet over the app): the two are
     * matched by their places on the screen, and the host is redrawn when the screens change.
     */
    private val acrossWindows: Boolean = false,
) : Drawable(), ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {

    /**
     * How the glass looks. [CLEAR] for the bars floating over the screens: a clear lens, the
     * colours under it a little richer. [FROSTED] for the sheets laid over the app: a deeper,
     * calmer frost (more of the surface's tone, the colours barely boosted) with a fine grain,
     * so a sheet reads as another, thicker pane than the bar it covers. The grain needs the
     * smart tint's Android 13.
     */
    enum class Style(
        val saturation: Float,
        val dayFloor: Float,
        val nightFloor: Float,
        val grain: Float
    ) {
        CLEAR(saturation = 1.25f, dayFloor = 0.58f, nightFloor = 0.62f, grain = 0f),
        FROSTED(saturation = 1.05f, dayFloor = 0.70f, nightFloor = 0.74f, grain = 0.035f)
    }

    /** Redraws a host in another window when the screens under it changed. */
    private val sourceWatch = ViewTreeObserver.OnPreDrawListener {
        if (source.isDirty && host.isShown) invalidateSelf()
        true
    }

    /**
     * The glass as it shows: a full-resolution node that only clips [frost] to the surface's
     * rounded outline, so its edge stays as sharp as the stroke drawn over it.
     */
    private val glass = RenderNode("WatubeGlass")

    /**
     * The screens under the surface, blurred and tinted, painted at 1/[DOWNSCALE] of the
     * resolution and stretched back by [glass]. A blur this wide keeps no detail finer than a
     * few pixels, so the smaller paint looks the same, and the GPU paints and blurs 16 times
     * fewer pixels each frame the screens move (Skia's blur scales down the same way inside).
     */
    private val frost = RenderNode("WatubeFrost")
    private val outline = Outline()

    private val fill: Drawable? = glaze.findDrawableByLayerId(R.id.watube_glass_fill)
    private val gloss: Drawable? = glaze.findDrawableByLayerId(R.id.watube_glass_gloss)
    private val smartTint = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private val hostLocation = IntArray(2)
    private val sourceLocation = IntArray(2)
    private var offsetX = 0
    private var offsetY = 0

    /** Whether the last frame showed the glass, to redraw when that changes. */
    private var showing = false

    /**
     * Fades the last [fadeHeight] pixels of the glass out, for a header over a full-width
     * list: the rows come out from under it softly instead of at a hard edge.
     */
    private val fadePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    init {
        val radius = blurRadius / DOWNSCALE
        val blur = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        frost.setRenderEffect(
            if (smartTint) {
                SmartTint.effect(blur, tone, ground, night, style)
            } else {
                val richer = ColorMatrix().apply { setSaturation(style.saturation) }
                RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(richer), blur)
            }
        )
        frost.setPivotX(0f)
        frost.setPivotY(0f)
        frost.setScaleX(DOWNSCALE)
        frost.setScaleY(DOWNSCALE)
        glass.clipToOutline = true
        // at full resolution, so each grain stays one pixel fine
        if (smartTint && style.grain > 0f) glass.setRenderEffect(Grain.effect(style.grain))
        host.addOnAttachStateChangeListener(this)
        if (host.isAttachedToWindow) onViewAttachedToWindow(host)
    }

    override fun onViewAttachedToWindow(v: View) {
        host.viewTreeObserver.addOnPreDrawListener(this)
        if (acrossWindows) source.viewTreeObserver.addOnPreDrawListener(sourceWatch)
    }

    override fun onViewDetachedFromWindow(v: View) {
        host.viewTreeObserver.removeOnPreDrawListener(this)
        source.viewTreeObserver.removeOnPreDrawListener(sourceWatch)
    }

    /** Lets go of the host: no more following it, no recording kept. */
    fun release() {
        host.removeOnAttachStateChangeListener(this)
        onViewDetachedFromWindow(host)
        glass.discardDisplayList()
        frost.discardDisplayList()
    }

    /** Whether the host still shows this glass, alone or as a layer of its background. */
    private fun isOnHost(): Boolean = when (val background = host.background) {
        this -> true
        is LayerDrawable -> (0 until background.numberOfLayers).any { background.getDrawable(it) === this }
        else -> false
    }

    /**
     * Before each frame: the glass is redrawn with the frame when the screens under it changed
     * (a scroll, an image loading), when it moved over them or when it shows or hides, never
     * on a frame of its own.
     */
    override fun onPreDraw(): Boolean {
        if (!isOnHost()) {
            release()
            return true
        }
        // An unseen window (app in the background, screen off) draws nothing, so the screens
        // stay dirty and the glass's own state never settles: an invalidation here would start
        // another empty traversal, frame after frame, a quarter of a core for nothing. The
        // window is redrawn whole as it shows again, the glass with it.
        if (host.windowVisibility != View.VISIBLE || !host.isShown) return true
        val shows = shouldShow()
        val moved = shows && updateOffset()
        if (shows != showing || moved || (shows && source.isDirty)) invalidateSelf()
        return true
    }

    private fun shouldShow() = source.isRecording && isShown()

    /** Where the host sits over the screens; true when that changed. */
    private fun updateOffset(): Boolean {
        if (acrossWindows) {
            host.getLocationOnScreen(hostLocation)
            source.getLocationOnScreen(sourceLocation)
        } else {
            host.getLocationInWindow(hostLocation)
            source.getLocationInWindow(sourceLocation)
        }
        val x = hostLocation[0] - sourceLocation[0]
        val y = hostLocation[1] - sourceLocation[1]
        if (x == offsetX && y == offsetY) return false
        offsetX = x
        offsetY = y
        return true
    }

    override fun draw(canvas: Canvas) {
        showing = canvas.isHardwareAccelerated && shouldShow() &&
            source.backdrop.hasDisplayList() && !bounds.isEmpty
        fill?.alpha = if (showing && smartTint) 0 else OPAQUE
        gloss?.alpha = if (showing) 0 else OPAQUE
        if (!showing) {
            glaze.draw(canvas)
        } else if (fadeHeight <= 0f) {
            drawGlass(canvas)
            glaze.draw(canvas)
        } else {
            val area = bounds
            val layer = canvas.saveLayer(
                area.left.toFloat(),
                area.top.toFloat(),
                area.right.toFloat(),
                area.bottom.toFloat(),
                null
            )
            drawGlass(canvas)
            glaze.draw(canvas)
            canvas.drawRect(
                area.left.toFloat(),
                area.bottom - fadeHeight,
                area.right.toFloat(),
                area.bottom.toFloat(),
                fadePaint
            )
            canvas.restoreToCount(layer)
        }
    }

    private fun drawGlass(canvas: Canvas) {
        updateOffset()
        val area = bounds
        glass.setPosition(area)
        outline.setRoundRect(0, 0, area.width(), area.height(), cornerRadius)
        glass.setOutline(outline)
        frost.setPosition(
            0,
            0,
            ceil(area.width() / DOWNSCALE).toInt(),
            ceil(area.height() / DOWNSCALE).toInt()
        )
        val small = frost.beginRecording()
        try {
            small.scale(1f / DOWNSCALE, 1f / DOWNSCALE)
            small.translate(-(offsetX + area.left).toFloat(), -(offsetY + area.top).toFloat())
            small.drawRenderNode(source.backdrop)
        } finally {
            frost.endRecording()
        }
        val recording = glass.beginRecording()
        try {
            recording.drawRenderNode(frost)
        } finally {
            glass.endRecording()
        }
        canvas.drawRenderNode(glass)
    }

    override fun onBoundsChange(bounds: Rect) {
        glaze.bounds = bounds
        if (fadeHeight > 0f) {
            fadePaint.shader = LinearGradient(
                0f,
                bounds.bottom - fadeHeight,
                0f,
                bounds.bottom.toFloat(),
                OPAQUE_BLACK,
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun setAlpha(alpha: Int) {
        glaze.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        glaze.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    /** The legibility-aware tint of the glass, run by the GPU on the blurred screens. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object SmartTint {
        /*
         * content: the blurred screens (premultiplied). Where they are see-through, the window
         * background shows, so it is laid under them first. The surface's ink is dark by day
         * and light at night: "clash" is how much the colour under a point fights that ink, and
         * the point takes from a floor of tone (the glass always reads as glass) up to nearly
         * the plain fill when the clash is full.
         */
        private const val AGSL = """
            uniform shader content;
            layout(color) uniform half4 tone;
            layout(color) uniform half4 ground;
            uniform half night;
            uniform half saturation;
            uniform half floorMix;
            uniform half ceilMix;

            half4 main(float2 p) {
                half4 c = content.eval(p);
                half3 rgb = c.rgb + ground.rgb * (1.0 - c.a);
                half l = dot(rgb, half3(0.2126, 0.7152, 0.0722));
                rgb = clamp(mix(half3(l), rgb, saturation), 0.0, 1.0);
                half clash = night > 0.5
                    ? smoothstep(0.12, 0.70, l)
                    : 1.0 - smoothstep(0.30, 0.88, l);
                rgb = mix(rgb, tone.rgb, mix(floorMix, ceilMix, clash));
                return half4(rgb, 1.0);
            }
        """

        fun effect(blur: RenderEffect, tone: Int, ground: Int, night: Boolean, style: Style): RenderEffect {
            val shader = RuntimeShader(AGSL).apply {
                setColorUniform("tone", tone or OPAQUE_BLACK)
                setColorUniform("ground", ground or OPAQUE_BLACK)
                setFloatUniform("night", if (night) 1f else 0f)
                setFloatUniform("saturation", style.saturation)
                setFloatUniform("floorMix", if (night) style.nightFloor else style.dayFloor)
                setFloatUniform("ceilMix", CEIL)
            }
            return RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(shader, "content"),
                blur
            )
        }
    }

    /**
     * The fine grain of the frosted glass ([Style.FROSTED]): each pixel a touch lighter or
     * darker, always the same for the same pixel, so it reads as a texture and never flickers.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object Grain {
        private const val AGSL = """
            uniform shader content;
            uniform half amount;

            half4 main(float2 p) {
                half4 c = content.eval(p);
                half n = half(fract(sin(dot(floor(p), float2(12.9898, 78.233))) * 43758.5453));
                return half4(clamp(c.rgb + (n - 0.5) * amount * c.a, 0.0, c.a), c.a);
            }
        """

        fun effect(amount: Float): RenderEffect {
            val shader = RuntimeShader(AGSL).apply { setFloatUniform("amount", amount) }
            return RenderEffect.createRuntimeShaderEffect(shader, "content")
        }
    }

    private companion object {
        const val OPAQUE = 255
        const val OPAQUE_BLACK = 0xFF000000.toInt()

        /** How much smaller the frost is painted than it shows (see [frost]). */
        const val DOWNSCALE = 4f

        /** Share of the surface's tone over a backdrop that fully fights its ink. */
        const val CEIL = 0.9f
    }
}
// endregion
