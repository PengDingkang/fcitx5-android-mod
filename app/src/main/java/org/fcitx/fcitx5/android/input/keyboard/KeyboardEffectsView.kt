/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePrefs.KeyMaterial
import splitties.dimensions.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class KeyboardEffectsView(context: Context, private val theme: Theme) : View(context) {

    private data class ReleaseRipple(
        val x: Float,
        val y: Float,
        var progress: Float = 0f,
        var animator: ValueAnimator? = null
    )

    private data class LiquidPress(
        val sourceView: View,
        val keyRect: RectF,
        val backdrop: Bitmap?,
        var touchX: Float,
        var touchY: Float,
        var progress: Float = 0f,
        var animator: ValueAnimator? = null
    )

    private val releaseRippleEffect by ThemeManager.prefs.keyReleaseRippleEffect
    private val disableAnimation by AppPrefs.getInstance().advanced.disableAnimation
    private val location = IntArray(2)
    private val viewLocation = IntArray(2)
    private val parentLocation = IntArray(2)
    private val ripples = mutableListOf<ReleaseRipple>()
    private var liquidPress: LiquidPress? = null
    private var capturingBackdrop = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val srcRect = Rect()
    private val path = Path()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    fun showLiquidPress(view: View, rawX: Float, rawY: Float) {
        if (ThemeManager.prefs.keyMaterial.getValue() != KeyMaterial.LiquidGlass) return
        if (width <= 0 || height <= 0) return
        val keyRect = keyRectInThisView(view) ?: return
        val (x, y) = rawToLocal(rawX, rawY)
        liquidPress?.let(::clearLiquidPress)
        val press = LiquidPress(
            sourceView = view,
            keyRect = keyRect,
            backdrop = captureBackdrop(view),
            touchX = x,
            touchY = y,
            progress = if (disableAnimation) 1f else 0f
        )
        liquidPress = press
        if (disableAnimation) {
            invalidate()
        } else {
            animateLiquidPress(press, 1f, 150L)
        }
    }

    fun moveLiquidPress(view: View, rawX: Float, rawY: Float) {
        val press = liquidPress ?: return
        if (press.sourceView !== view) return
        val (x, y) = rawToLocal(rawX, rawY)
        press.touchX = x
        press.touchY = y
        invalidate()
    }

    fun hideLiquidPress(view: View, rawX: Float, rawY: Float) {
        val press = liquidPress ?: return
        if (press.sourceView !== view) return
        val (x, y) = rawToLocal(rawX, rawY)
        press.touchX = x
        press.touchY = y
        if (disableAnimation) {
            clearLiquidPress(press)
            invalidate()
        } else {
            animateLiquidPress(press, 0f, 210L, clearOnEnd = true)
        }
    }

    fun showReleaseRipple(rawX: Float, rawY: Float) {
        if (!releaseRippleEffect || disableAnimation || width <= 0 || height <= 0) return
        getLocationOnScreen(location)
        val x = rawX - location[0]
        val y = rawY - location[1]
        if (x < 0f || y < 0f || x > width || y > height) return

        val ripple = ReleaseRipple(x, y)
        ripples += ripple
        ripple.animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                ripple.progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    ripples -= ripple
                    invalidate()
                }

                override fun onAnimationCancel(animation: Animator) {
                    ripples -= ripple
                    invalidate()
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (capturingBackdrop) return
        liquidPress?.let { drawLiquidPress(canvas, it) }
        drawReleaseRipples(canvas)
    }

    private fun drawReleaseRipples(canvas: Canvas) {
        if (ripples.isEmpty()) return

        val startRadius = dp(5f)
        val endRadius = dp(40f)
        val startStroke = dp(2.2f)
        val endStroke = dp(0.8f)
        val color = theme.keyPressHighlightColor
        val baseAlpha = max(Color.alpha(color), 96)

        ripples.forEach { ripple ->
            val p = ripple.progress
            val alpha = (baseAlpha * (1f - p)).roundToInt().coerceIn(0, 255)
            if (alpha <= 0) return@forEach
            paint.color = ColorUtils.setAlphaComponent(color, alpha)
            paint.strokeWidth = startStroke + (endStroke - startStroke) * p
            canvas.drawCircle(ripple.x, ripple.y, startRadius + (endRadius - startRadius) * p, paint)
        }
    }

    private fun drawLiquidPress(canvas: Canvas, press: LiquidPress) {
        val p = press.progress.coerceIn(0f, 1f)
        if (p <= 0.001f) return

        val key = press.keyRect
        val width = key.width()
        val height = key.height()
        val touchDx = ((press.touchX - key.centerX()) / width).coerceIn(-1f, 1f)
        val touchDy = ((press.touchY - key.centerY()) / height).coerceIn(-1f, 1f)
        val expandX = dp(2f) + width * lerp(0.05f, 0.14f, p)
        val expandY = dp(2f) + height * lerp(0.05f, 0.16f, p)
        rect.set(
            key.left - expandX + touchDx * dp(3f) * p,
            key.top - expandY + touchDy * dp(2f) * p,
            key.right + expandX + touchDx * dp(3f) * p,
            key.bottom + expandY + touchDy * dp(2f) * p
        )

        val radius = min(rect.width(), rect.height()) * 0.28f + dp(6f)
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)

        drawLiquidShadow(canvas, p)
        canvas.save()
        canvas.clipPath(path)
        drawLiquidBackdrop(canvas, press, p, touchDx, touchDy)
        drawLiquidSurface(canvas, p)
        drawLiquidRefractionBands(canvas, p)
        drawLiquidGlint(canvas, p, touchDx, touchDy)
        canvas.restore()
        drawLiquidInnerShadow(canvas, p)
        drawLiquidSpecular(canvas, p)
        drawLiquidChromaticEdge(canvas, p)
    }

    private fun drawLiquidShadow(canvas: Canvas, progress: Float) {
        fillPaint.resetForFill()
        fillPaint.shader = RadialGradient(
            rect.centerX(),
            rect.centerY() + rect.height() * 0.2f,
            max(rect.width(), rect.height()) * 0.68f,
            intArrayOf(alphaColor(Color.BLACK, (56 * progress).roundToInt()), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawOval(
            rect.left - dp(4f),
            rect.top - dp(2f),
            rect.right + dp(4f),
            rect.bottom + dp(8f),
            fillPaint
        )
    }

    private fun drawLiquidBackdrop(
        canvas: Canvas,
        press: LiquidPress,
        progress: Float,
        touchDx: Float,
        touchDy: Float
    ) {
        val backdrop = press.backdrop
        if (backdrop == null || backdrop.isRecycled) {
            fillPaint.resetForFill(alphaColor(theme.keyBackgroundColor, (90 * progress).roundToInt()))
            canvas.drawRoundRect(rect, min(rect.width(), rect.height()) * 0.33f, min(rect.width(), rect.height()) * 0.33f, fillPaint)
            return
        }

        val insetX = rect.width() * lerp(0.04f, 0.16f, progress)
        val insetY = rect.height() * lerp(0.04f, 0.16f, progress)
        srcRect.set(
            (rect.left + insetX + touchDx * dp(5f)).roundToInt().coerceIn(0, backdrop.width - 1),
            (rect.top + insetY + touchDy * dp(4f)).roundToInt().coerceIn(0, backdrop.height - 1),
            (rect.right - insetX + touchDx * dp(5f)).roundToInt().coerceIn(1, backdrop.width),
            (rect.bottom - insetY + touchDy * dp(4f)).roundToInt().coerceIn(1, backdrop.height)
        )
        if (srcRect.width() <= 1 || srcRect.height() <= 1) return
        bitmapPaint.alpha = (255 * progress).roundToInt().coerceIn(0, 255)
        canvas.drawBitmap(backdrop, srcRect, rect, bitmapPaint)
        bitmapPaint.alpha = 255
    }

    private fun drawLiquidSurface(canvas: Canvas, progress: Float) {
        fillPaint.resetForFill()
        fillPaint.shader = LinearGradient(
            0f,
            rect.top,
            0f,
            rect.bottom,
            intArrayOf(
                alphaColor(Color.WHITE, (105 * progress).roundToInt()),
                alphaColor(Color.WHITE, (24 * progress).roundToInt()),
                alphaColor(Color.BLACK, (42 * progress).roundToInt())
            ),
            floatArrayOf(0f, 0.46f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, min(rect.width(), rect.height()) * 0.33f, min(rect.width(), rect.height()) * 0.33f, fillPaint)

        fillPaint.resetForFill(
            alphaColor(
                if (theme.isDark) Color.WHITE else Color.BLACK,
                ((if (theme.isDark) 20 else 12) * progress).roundToInt()
            )
        )
        canvas.drawRoundRect(rect, min(rect.width(), rect.height()) * 0.33f, min(rect.width(), rect.height()) * 0.33f, fillPaint)
    }

    private fun drawLiquidRefractionBands(canvas: Canvas, progress: Float) {
        val count = 5
        val bandWidth = rect.width() * 0.08f
        repeat(count) { i ->
            val x = rect.left + rect.width() * (0.22f + i * 0.14f)
            fillPaint.resetForFill()
            fillPaint.shader = LinearGradient(
                x - bandWidth,
                rect.centerY(),
                x + bandWidth,
                rect.centerY(),
                intArrayOf(
                    Color.TRANSPARENT,
                    alphaColor(Color.WHITE, (40 * progress).roundToInt()),
                    alphaColor(Color.BLACK, (18 * progress).roundToInt()),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.42f, 0.62f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(x - bandWidth, rect.top, x + bandWidth, rect.bottom, fillPaint)
        }
    }

    private fun drawLiquidGlint(canvas: Canvas, progress: Float, touchDx: Float, touchDy: Float) {
        fillPaint.resetForFill()
        fillPaint.shader = RadialGradient(
            rect.right - rect.width() * (0.28f - touchDx * 0.04f),
            rect.top + rect.height() * (0.36f + touchDy * 0.06f),
            rect.height() * 0.72f,
            intArrayOf(
                alphaColor(Color.WHITE, (150 * progress).roundToInt()),
                alphaColor(Color.WHITE, (48 * progress).roundToInt()),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.38f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, min(rect.width(), rect.height()) * 0.33f, min(rect.width(), rect.height()) * 0.33f, fillPaint)
    }

    private fun drawLiquidInnerShadow(canvas: Canvas, progress: Float) {
        canvas.save()
        canvas.clipPath(path)
        canvas.clipRect(rect.left, rect.centerY(), rect.right, rect.bottom)
        paint.resetForStroke(alphaColor(Color.BLACK, (42 * progress).roundToInt()), dp(2f))
        canvas.drawRoundRect(rect, min(rect.width(), rect.height()) * 0.33f, min(rect.width(), rect.height()) * 0.33f, paint)
        canvas.restore()
    }

    private fun drawLiquidSpecular(canvas: Canvas, progress: Float) {
        paint.resetForStroke(alphaColor(Color.WHITE, (88 * progress).roundToInt()), dp(0.9f))
        paint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(
                alphaColor(Color.WHITE, (116 * progress).roundToInt()),
                alphaColor(Color.WHITE, (30 * progress).roundToInt()),
                alphaColor(Color.BLACK, (28 * progress).roundToInt())
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, min(rect.width(), rect.height()) * 0.33f, min(rect.width(), rect.height()) * 0.33f, paint)
        paint.shader = null
    }

    private fun drawLiquidChromaticEdge(canvas: Canvas, progress: Float) {
        val offset = dp(0.55f)
        paint.resetForStroke(alphaColor(0xFF7DDCFF.toInt(), (24 * progress).roundToInt()), dp(0.55f))
        canvas.save()
        canvas.translate(-offset, -offset)
        canvas.drawRoundRect(rect, min(rect.width(), rect.height()) * 0.33f, min(rect.width(), rect.height()) * 0.33f, paint)
        canvas.restore()

        paint.resetForStroke(alphaColor(0xFFFF9ACD.toInt(), (18 * progress).roundToInt()), dp(0.55f))
        canvas.save()
        canvas.translate(offset, offset)
        canvas.drawRoundRect(rect, min(rect.width(), rect.height()) * 0.33f, min(rect.width(), rect.height()) * 0.33f, paint)
        canvas.restore()
    }

    private fun animateLiquidPress(
        press: LiquidPress,
        target: Float,
        durationMillis: Long,
        clearOnEnd: Boolean = false
    ) {
        press.animator?.cancel()
        press.animator = ValueAnimator.ofFloat(press.progress, target).apply {
            duration = durationMillis
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                press.progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (clearOnEnd && liquidPress === press) {
                        clearLiquidPress(press)
                    }
                    invalidate()
                }

                override fun onAnimationCancel(animation: Animator) {
                    if (clearOnEnd && liquidPress === press) {
                        clearLiquidPress(press)
                    }
                    invalidate()
                }
            })
            start()
        }
    }

    private fun keyRectInThisView(view: View): RectF? {
        getLocationInWindow(location)
        val result = if (view is KeyView) {
            val bounds = view.bounds
            RectF(
                bounds.left - location[0] + view.hMargin.toFloat(),
                bounds.top - location[1] + view.vMargin.toFloat(),
                bounds.right - location[0] - view.hMargin.toFloat(),
                bounds.bottom - location[1] - view.vMargin.toFloat()
            )
        } else {
            view.getLocationInWindow(viewLocation)
            RectF(
                viewLocation[0] - location[0].toFloat(),
                viewLocation[1] - location[1].toFloat(),
                viewLocation[0] - location[0] + view.width.toFloat(),
                viewLocation[1] - location[1] + view.height.toFloat()
            )
        }
        if (!result.intersect(0f, 0f, width.toFloat(), height.toFloat())) return null
        return result.takeIf { it.width() > dp(8f) && it.height() > dp(8f) }
    }

    private fun rawToLocal(rawX: Float, rawY: Float): Pair<Float, Float> {
        getLocationOnScreen(location)
        return rawX - location[0] to rawY - location[1]
    }

    private fun captureBackdrop(pressedView: View): Bitmap? {
        val source = parent as? View ?: return null
        if (width <= 0 || height <= 0) return null
        return runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val captureCanvas = Canvas(bitmap)
                getLocationInWindow(location)
                source.getLocationInWindow(parentLocation)
                captureCanvas.translate(
                    (parentLocation[0] - location[0]).toFloat(),
                    (parentLocation[1] - location[1]).toFloat()
                )
                capturingBackdrop = true
                if (pressedView is KeyView) {
                    pressedView.drawWithVisualBackgroundHidden {
                        source.draw(captureCanvas)
                    }
                } else {
                    source.draw(captureCanvas)
                }
                capturingBackdrop = false
            }
        }.onFailure {
            capturingBackdrop = false
        }.getOrNull()
    }

    private fun clearLiquidPress(press: LiquidPress) {
        press.animator?.let {
            it.removeAllListeners()
            it.cancel()
        }
        press.animator = null
        press.backdrop?.recycle()
        if (liquidPress === press) {
            liquidPress = null
        }
    }

    private fun alphaColor(color: Int, alpha: Int): Int =
        ColorUtils.setAlphaComponent(color, alpha.coerceIn(0, 255))

    private fun lerp(start: Float, stop: Float, amount: Float): Float =
        start + (stop - start) * amount

    override fun onDetachedFromWindow() {
        ripples.toList().forEach { it.animator?.cancel() }
        ripples.clear()
        liquidPress?.let(::clearLiquidPress)
        super.onDetachedFromWindow()
    }
}

private fun Paint.resetForFill(color: Int = Color.TRANSPARENT) {
    reset()
    isAntiAlias = true
    style = Paint.Style.FILL
    this.color = color
}

private fun Paint.resetForStroke(color: Int, width: Float) {
    reset()
    isAntiAlias = true
    style = Paint.Style.STROKE
    strokeWidth = width
    this.color = color
}
