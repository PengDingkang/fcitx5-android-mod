/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager

private const val MinReadableKeyContrast = 4.5

@ColorInt
internal fun estimatedKeyboardBackdropColor(theme: Theme): Int {
    val customBackgroundColor = (theme as? Theme.Custom)
        ?.backgroundImage
        ?.estimatedBackgroundColor
    val base = ColorUtils.setAlphaComponent(customBackgroundColor ?: theme.keyboardColor, 255)
    val dimAmount = ThemeManager.prefs.keyboardBackgroundDimAmount.getValue()
    if (dimAmount <= 0) return base
    val dimOverlay = Color.argb((dimAmount * 255 / 100).coerceIn(0, 255), 0, 0, 0)
    return ColorUtils.compositeColors(dimOverlay, base)
}

@ColorInt
internal fun estimatedMaterialSurfaceColor(
    @ColorInt materialColor: Int,
    @ColorInt backdropColor: Int
): Int {
    val opaqueBackdrop = ColorUtils.setAlphaComponent(backdropColor, 255)
    return ColorUtils.compositeColors(materialColor, opaqueBackdrop)
}

@ColorInt
internal fun readableMaterialContentColor(
    @ColorInt preferredColor: Int,
    @ColorInt materialColor: Int,
    @ColorInt backdropColor: Int,
    minContrast: Double = MinReadableKeyContrast
): Int = readableContentColor(
    preferredColor,
    estimatedMaterialSurfaceColor(materialColor, backdropColor),
    minContrast
)

@ColorInt
private fun readableContentColor(
    @ColorInt preferredColor: Int,
    @ColorInt surfaceColor: Int,
    minContrast: Double
): Int {
    if (ColorUtils.calculateContrast(preferredColor, surfaceColor) >= minContrast) {
        return preferredColor
    }

    val opaquePreferred = ColorUtils.setAlphaComponent(preferredColor, 255)
    if (ColorUtils.calculateContrast(opaquePreferred, surfaceColor) >= minContrast) {
        return opaquePreferred
    }

    val blackContrast = ColorUtils.calculateContrast(Color.BLACK, surfaceColor)
    val whiteContrast = ColorUtils.calculateContrast(Color.WHITE, surfaceColor)
    return if (blackContrast >= whiteContrast) Color.BLACK else Color.WHITE
}
