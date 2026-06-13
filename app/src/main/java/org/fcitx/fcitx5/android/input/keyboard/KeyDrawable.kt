/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.keyboard

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class KeyMaterialStyle {
    Glass,
    FrostedGlass,
    LiquidGlass
}

fun radiusDrawable(
    r: Float, @ColorInt
    color: Int = Color.WHITE
): Drawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = r
}

fun insetRadiusDrawable(
    hInset: Int,
    vInset: Int,
    r: Float = 0f,
    @ColorInt color: Int = Color.WHITE
): Drawable = InsetDrawable(
    radiusDrawable(r, color),
    hInset, vInset, hInset, vInset
)

fun insetOvalDrawable(
    hInset: Int,
    vInset: Int,
    @ColorInt color: Int = Color.WHITE
): Drawable = InsetDrawable(
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    },
    hInset, vInset, hInset, vInset
)

fun shadowedKeyBackgroundDrawable(
    @ColorInt bkgColor: Int,
    @ColorInt shadowColor: Int,
    radius: Float,
    shadowWidth: Int,
    hMargin: Int,
    vMargin: Int
): Drawable = LayerDrawable(
    arrayOf(
        radiusDrawable(radius, shadowColor),
        radiusDrawable(radius, bkgColor),
    )
).apply {
    setLayerInset(0, hMargin, vMargin, hMargin, vMargin - shadowWidth)
    setLayerInset(1, hMargin, vMargin, hMargin, vMargin)
}

fun borderedKeyBackgroundDrawable(
    @ColorInt bkgColor: Int,
    @ColorInt shadowColor: Int,
    radius: Float,
    strokeWidth: Int,
    hMargin: Int,
    vMargin: Int
): Drawable = LayerDrawable(
    arrayOf(
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(bkgColor)
            setStroke(strokeWidth, shadowColor)
        }
    )
).apply {
    setLayerInset(0, hMargin, vMargin, hMargin, vMargin)
}

fun materialKeyBackgroundDrawable(
    @ColorInt bkgColor: Int,
    @ColorInt strokeColor: Int,
    @ColorInt shadowColor: Int,
    radius: Float,
    strokeWidth: Int,
    hMargin: Int,
    vMargin: Int,
    style: KeyMaterialStyle,
    pressBump: Boolean = false,
    edgeStrength: Float = 1f
): Drawable = LayerDrawable(
    arrayOf(
        KeyMaterialDrawable(
            bkgColor, strokeColor, shadowColor, radius, strokeWidth.toFloat(), style,
            pressBump = pressBump,
            edgeStrength = edgeStrength
        )
    )
).apply {
    setLayerInset(0, hMargin, vMargin, hMargin, vMargin)
}

fun insetMaterialRadiusDrawable(
    hInset: Int,
    vInset: Int,
    r: Float,
    @ColorInt bkgColor: Int,
    @ColorInt strokeColor: Int,
    @ColorInt shadowColor: Int,
    strokeWidth: Int,
    style: KeyMaterialStyle,
    edgeStrength: Float = 1f
): Drawable = InsetDrawable(
    KeyMaterialDrawable(
        bkgColor, strokeColor, shadowColor, r, strokeWidth.toFloat(), style,
        edgeStrength = edgeStrength
    ),
    hInset, vInset, hInset, vInset
)

fun insetMaterialOvalDrawable(
    hInset: Int,
    vInset: Int,
    @ColorInt bkgColor: Int,
    @ColorInt strokeColor: Int,
    @ColorInt shadowColor: Int,
    strokeWidth: Int,
    style: KeyMaterialStyle,
    edgeStrength: Float = 1f
): Drawable = InsetDrawable(
    KeyMaterialDrawable(
        bkgColor, strokeColor, shadowColor, 0f, strokeWidth.toFloat(), style, oval = true,
        edgeStrength = edgeStrength
    ),
    hInset, vInset, hInset, vInset
)

private class KeyMaterialDrawable(
    @ColorInt private val bkgColor: Int,
    @ColorInt private val strokeColor: Int,
    @ColorInt private val shadowColor: Int,
    private val radius: Float,
    private val strokeWidth: Float,
    private val style: KeyMaterialStyle,
    private val oval: Boolean = false,
    private val pressBump: Boolean = false,
    edgeStrength: Float = 1f
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private var drawableAlpha = 255
    private var drawableColorFilter: ColorFilter? = null
    private var pressAnimator: ValueAnimator? = null
    private var pressProgress = 0f
    private var pressed = false
    private val edgeStrength = edgeStrength.coerceIn(0f, 1f)

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        if (rect.isEmpty) return
        val inset = max(0.5f, strokeWidth / 2f)
        rect.inset(inset, inset)
        updatePath()

        drawBase(canvas)
        when (style) {
            KeyMaterialStyle.Glass -> drawGlass(canvas)
            KeyMaterialStyle.FrostedGlass -> drawFrostedGlass(canvas)
            KeyMaterialStyle.LiquidGlass -> drawLiquidGlass(canvas)
        }
        drawOuterStroke(canvas)
    }

    private fun updatePath() {
        path.reset()
        if (oval) {
            path.addOval(rect, Path.Direction.CW)
        } else {
            path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        }
    }

    private fun drawBase(canvas: Canvas) {
        resetPaintForFill(colorWithAlpha(bkgColor))
        drawShape(canvas)
    }

    private fun drawGlass(canvas: Canvas) {
        drawClippedGradient(
            canvas,
            intArrayOf(
                alphaColor(Color.WHITE, 26),
                Color.TRANSPARENT,
                alphaColor(shadowColor, 14)
            )
        )
        drawClearGlassReflections(canvas)
        drawInnerStroke(canvas, alphaColor(Color.WHITE, 42), strokeWidth * 0.9f)
    }

    private fun drawFrostedGlass(canvas: Canvas) {
        drawClippedGradient(
            canvas,
            intArrayOf(
                alphaColor(Color.WHITE, 104),
                alphaColor(Color.WHITE, 68),
                alphaColor(shadowColor, 20)
            )
        )
        drawFrostedVeil(canvas)
        drawFrostedTexture(canvas)
        drawInnerStroke(canvas, alphaColor(Color.WHITE, 26), strokeWidth * 1.1f)
        drawBottomShadow(canvas, alphaColor(shadowColor, 22), strokeWidth * 1.4f)
    }

    private fun drawClearGlassReflections(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(path)

        resetPaintForFill()
        paint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            gradientColors(
                alphaColor(Color.WHITE, 72),
                alphaColor(Color.WHITE, 12),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.32f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.48f, paint)

        resetPaintForFill()
        paint.shader = LinearGradient(
            rect.left,
            rect.bottom,
            rect.right,
            rect.top,
            gradientColors(
                Color.TRANSPARENT,
                alphaColor(Color.WHITE, 44),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.36f, 0.5f, 0.64f),
            Shader.TileMode.CLAMP
        )
        drawShape(canvas)
        canvas.restore()
    }

    private fun drawFrostedVeil(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(path)
        resetPaintForFill()
        paint.shader = RadialGradient(
            rect.centerX(),
            rect.top + rect.height() * 0.22f,
            max(rect.width(), rect.height()) * 0.82f,
            gradientColors(
                alphaColor(Color.WHITE, 72),
                alphaColor(Color.WHITE, 28),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        drawShape(canvas)
        canvas.restore()
    }

    private fun drawFrostedTexture(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(path)
        resetPaintForFill()
        paint.shader = null
        val dotCount = (rect.width() / 8f).roundToInt().coerceIn(8, 18)
        repeat(dotCount) { index ->
            val seed = index * 37 + rect.width().roundToInt() * 3 + rect.height().roundToInt()
            val x = rect.left + rect.width() * (((seed * 17) % 100) / 100f)
            val y = rect.top + rect.height() * (((seed * 29) % 100) / 100f)
            val radius = strokeWidth * (0.45f + ((seed % 5) * 0.08f))
            paint.color = alphaColor(Color.WHITE, 10 + seed % 12)
            canvas.drawCircle(x, y, radius, paint)
        }

        val lineCount = (rect.height() / 18f).roundToInt().coerceIn(2, 5)
        repeat(lineCount) { index ->
            val y = rect.top + rect.height() * ((index + 1f) / (lineCount + 1f))
            val inset = rect.width() * (0.18f + index * 0.025f)
            paint.color = alphaColor(Color.WHITE, 9)
            paint.strokeWidth = strokeWidth * 0.55f
            canvas.drawLine(rect.left + inset, y, rect.right - inset, y + strokeWidth * 0.45f, paint)
        }
        canvas.restore()
    }

    private fun drawLiquidGlass(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(path)
        resetPaintForFill()
        paint.shader = LinearGradient(
            0f,
            rect.top,
            0f,
            rect.bottom,
            gradientColors(
                alphaColor(Color.WHITE, 82),
                Color.TRANSPARENT,
                alphaColor(shadowColor, 52)
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        drawShape(canvas)

        resetPaintForFill()
        paint.shader = RadialGradient(
            rect.left + rect.width() * 0.22f,
            rect.top + rect.height() * 0.12f,
            max(rect.width(), rect.height()) * 0.68f,
            gradientColors(
                alphaColor(Color.WHITE, 104),
                alphaColor(Color.WHITE, 24),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.34f, 1f),
            Shader.TileMode.CLAMP
        )
        drawShape(canvas)
        canvas.restore()

        if (pressBump && pressProgress > 0f) {
            drawLiquidPressBump(canvas, pressProgress)
        }
        drawInnerStroke(canvas, alphaColor(Color.WHITE, edgeAlpha(42)), strokeWidth * 1.1f)
        drawBottomShadow(canvas, alphaColor(shadowColor, edgeAlpha(34)), strokeWidth * 1.8f)
        if (edgeStrength > 0.2f) {
            drawChromaticEdge(canvas)
        }
    }

    private fun drawLiquidPressBump(canvas: Canvas, progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        val width = rect.width()
        val height = rect.height()
        val cx = rect.centerX()
        val cy = rect.centerY()
        val bumpRect = RectF(
            cx - width * lerp(0.42f, 0.50f, p),
            cy - height * lerp(0.35f, 0.49f, p),
            cx + width * lerp(0.42f, 0.50f, p),
            cy + height * lerp(0.35f, 0.49f, p),
        )
        val bumpRadius = if (oval) {
            min(bumpRect.width(), bumpRect.height()) / 2f
        } else {
            max(radius, min(bumpRect.width(), bumpRect.height()) * 0.34f)
        }
        val bumpPath = Path().apply {
            if (oval) {
                addOval(bumpRect, Path.Direction.CW)
            } else {
                addRoundRect(bumpRect, bumpRadius, bumpRadius, Path.Direction.CW)
            }
        }

        canvas.save()
        canvas.clipPath(bumpPath)
        resetPaintForFill(pressColor(if (isDarkSurface()) Color.WHITE else Color.BLACK, 22, p))
        drawRounded(canvas, bumpRect, bumpRadius)

        resetPaintForFill()
        paint.shader = LinearGradient(
            0f,
            bumpRect.top,
            0f,
            bumpRect.bottom,
            intArrayOf(
                pressColor(Color.WHITE, 118, p),
                pressColor(Color.WHITE, 22, p),
                pressColor(shadowColor, 76, p),
            ),
            floatArrayOf(0f, 0.46f, 1f),
            Shader.TileMode.CLAMP
        )
        drawRounded(canvas, bumpRect, bumpRadius)

        drawLiquidStreaks(canvas, bumpRect, p)
        drawLiquidGlint(canvas, bumpRect, bumpRadius, p)
        drawLiquidEdgeSlots(canvas, bumpRect, p)
        canvas.restore()

        drawLiquidSpecular(canvas, bumpRect, bumpRadius, p)
    }

    private fun drawLiquidStreaks(canvas: Canvas, bumpRect: RectF, progress: Float) {
        val bandWidth = bumpRect.width() * 0.07f
        repeat(7) { index ->
            val x = bumpRect.left + bumpRect.width() * (0.18f + index * 0.105f)
            resetPaintForFill()
            paint.shader = LinearGradient(
                x - bandWidth,
                bumpRect.centerY(),
                x + bandWidth,
                bumpRect.centerY(),
                intArrayOf(
                    Color.TRANSPARENT,
                    pressColor(Color.WHITE, 42, progress),
                    pressColor(shadowColor, 22, progress),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.42f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(
                x - bandWidth,
                bumpRect.top,
                x + bandWidth,
                bumpRect.bottom,
                paint
            )
        }
    }

    private fun drawLiquidGlint(
        canvas: Canvas,
        bumpRect: RectF,
        bumpRadius: Float,
        progress: Float
    ) {
        resetPaintForFill()
        paint.shader = RadialGradient(
            bumpRect.right - bumpRect.width() * 0.24f,
            bumpRect.centerY() - bumpRect.height() * 0.02f,
            bumpRect.height() * 0.58f,
            intArrayOf(
                pressColor(Color.WHITE, 150, progress),
                pressColor(Color.WHITE, 52, progress),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.38f, 1f),
            Shader.TileMode.CLAMP
        )
        drawRounded(canvas, bumpRect, bumpRadius)
    }

    private fun drawLiquidEdgeSlots(canvas: Canvas, bumpRect: RectF, progress: Float) {
        val slotWidth = bumpRect.width() * 0.34f
        val slotHeight = max(strokeWidth * 2.2f, bumpRect.height() * 0.09f)
        val slotRadius = slotHeight / 2f
        resetPaintForFill(pressColor(Color.BLACK, 100, progress))
        canvas.drawRoundRect(
            bumpRect.centerX() - slotWidth / 2f,
            bumpRect.top + slotHeight * 0.34f,
            bumpRect.centerX() + slotWidth / 2f,
            bumpRect.top + slotHeight * 1.34f,
            slotRadius,
            slotRadius,
            paint
        )
        resetPaintForFill(pressColor(Color.BLACK, 72, progress))
        canvas.drawRoundRect(
            bumpRect.centerX() - slotWidth / 2f,
            bumpRect.bottom - slotHeight * 1.34f,
            bumpRect.centerX() + slotWidth / 2f,
            bumpRect.bottom - slotHeight * 0.34f,
            slotRadius,
            slotRadius,
            paint
        )
    }

    private fun drawLiquidSpecular(
        canvas: Canvas,
        bumpRect: RectF,
        bumpRadius: Float,
        progress: Float
    ) {
        resetPaintForStroke(
            pressColor(Color.WHITE, edgeAlpha(76), progress),
            strokeWidth * lerp(0.9f, 1.8f, progress)
        )
        paint.shader = LinearGradient(
            bumpRect.left,
            bumpRect.top,
            bumpRect.right,
            bumpRect.bottom,
            intArrayOf(
                pressColor(Color.WHITE, edgeAlpha(98), progress),
                pressColor(0xFF7DDCFF.toInt(), edgeAlpha(34), progress),
                pressColor(0xFFFF9ACD.toInt(), edgeAlpha(26), progress),
                pressColor(Color.BLACK, edgeAlpha(34), progress),
            ),
            floatArrayOf(0f, 0.42f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        drawRounded(canvas, bumpRect, bumpRadius)
    }

    private fun drawClippedGradient(canvas: Canvas, colors: IntArray) {
        canvas.save()
        canvas.clipPath(path)
        resetPaintForFill()
        paint.shader = LinearGradient(
            0f,
            rect.top,
            0f,
            rect.bottom,
            colors.map(::colorWithAlpha).toIntArray(),
            null,
            Shader.TileMode.CLAMP
        )
        drawShape(canvas)
        canvas.restore()
    }

    private fun drawInnerStroke(canvas: Canvas, @ColorInt color: Int, width: Float) {
        resetPaintForStroke(colorWithAlpha(color), width)
        drawShape(canvas)
    }

    private fun drawBottomShadow(canvas: Canvas, @ColorInt color: Int, width: Float) {
        canvas.save()
        canvas.clipRect(rect.left, rect.centerY(), rect.right, rect.bottom)
        resetPaintForStroke(colorWithAlpha(color), width)
        drawShape(canvas)
        canvas.restore()
    }

    private fun drawChromaticEdge(canvas: Canvas) {
        val offset = strokeWidth.coerceAtLeast(1f)
        val edgeRect = RectF(rect)
        resetPaintForStroke(colorWithAlpha(alphaColor(0xFF7DDCFF.toInt(), edgeAlpha(24))), strokeWidth * 0.72f)
        canvas.save()
        canvas.translate(-offset, -offset)
        drawShape(canvas, edgeRect)
        canvas.restore()

        resetPaintForStroke(colorWithAlpha(alphaColor(0xFFFF9ACD.toInt(), edgeAlpha(18))), strokeWidth * 0.72f)
        canvas.save()
        canvas.translate(offset, offset)
        drawShape(canvas, edgeRect)
        canvas.restore()
    }

    private fun drawOuterStroke(canvas: Canvas) {
        val color = when (style) {
            KeyMaterialStyle.FrostedGlass -> withAlphaScale(strokeColor, 0.55f)
            KeyMaterialStyle.LiquidGlass -> withAlphaScale(strokeColor, edgeStrength)
            else -> strokeColor
        }
        if (Color.alpha(color) == 0 || strokeWidth <= 0f) return
        resetPaintForStroke(colorWithAlpha(color), strokeWidth)
        drawShape(canvas)
    }

    private fun drawShape(canvas: Canvas, shapeRect: RectF = rect) {
        if (oval) {
            canvas.drawOval(shapeRect, paint)
        } else {
            canvas.drawRoundRect(shapeRect, radius, radius, paint)
        }
    }

    private fun drawRounded(canvas: Canvas, shapeRect: RectF, shapeRadius: Float) {
        if (oval) {
            canvas.drawOval(shapeRect, paint)
        } else {
            canvas.drawRoundRect(shapeRect, shapeRadius, shapeRadius, paint)
        }
    }

    private fun colorWithAlpha(@ColorInt color: Int): Int {
        val alpha = (Color.alpha(color) * drawableAlpha / 255f).roundToInt().coerceIn(0, 255)
        return ColorUtils.setAlphaComponent(color, alpha)
    }

    private fun alphaColor(@ColorInt color: Int, alpha: Int): Int =
        ColorUtils.setAlphaComponent(color, alpha.coerceIn(0, 255))

    private fun pressColor(@ColorInt color: Int, alpha: Int, progress: Float): Int =
        colorWithAlpha(
            ColorUtils.setAlphaComponent(
                color,
                (alpha * progress).roundToInt().coerceIn(0, 255)
            )
        )

    private fun edgeAlpha(alpha: Int): Int =
        (alpha * edgeStrength).roundToInt().coerceIn(0, 255)

    private fun withAlphaScale(@ColorInt color: Int, scale: Float): Int =
        ColorUtils.setAlphaComponent(
            color,
            (Color.alpha(color) * scale).roundToInt().coerceIn(0, 255)
        )

    private fun gradientColors(vararg colors: Int): IntArray =
        colors.map(::colorWithAlpha).toIntArray()

    private fun isDarkSurface(): Boolean {
        val red = Color.red(bkgColor) / 255.0
        val green = Color.green(bkgColor) / 255.0
        val blue = Color.blue(bkgColor) / 255.0
        val luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
        return luminance < 0.5
    }

    private fun lerp(start: Float, stop: Float, amount: Float): Float =
        start + (stop - start) * amount

    private fun resetPaintForFill(@ColorInt color: Int = Color.TRANSPARENT) {
        paint.resetForFill(color)
        paint.colorFilter = drawableColorFilter
    }

    private fun resetPaintForStroke(@ColorInt color: Int, width: Float) {
        paint.resetForStroke(color, width)
        paint.colorFilter = drawableColorFilter
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        drawableColorFilter = colorFilter
        invalidateSelf()
    }

    override fun isStateful(): Boolean = pressBump

    override fun onStateChange(stateSet: IntArray): Boolean {
        if (!pressBump) return false
        val nextPressed = stateSet.any { it == android.R.attr.state_pressed }
        if (pressed == nextPressed) return false
        pressed = nextPressed
        animatePress(if (nextPressed) 1f else 0f)
        return true
    }

    override fun jumpToCurrentState() {
        if (!pressBump) return
        pressAnimator?.cancel()
        pressProgress = if (pressed) 1f else 0f
        invalidateSelf()
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        if (!visible) {
            pressAnimator?.cancel()
        }
        return super.setVisible(visible, restart)
    }

    private fun animatePress(target: Float) {
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(pressProgress, target).apply {
            duration = if (target > pressProgress) 120L else 180L
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                pressProgress = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private fun Paint.resetForFill(@ColorInt color: Int = Color.TRANSPARENT) {
    reset()
    isAntiAlias = true
    style = Paint.Style.FILL
    this.color = color
}

private fun Paint.resetForStroke(@ColorInt color: Int, width: Float) {
    reset()
    isAntiAlias = true
    style = Paint.Style.STROKE
    strokeWidth = width
    this.color = color
}
