/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.popup

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewOutlineProvider
import androidx.core.graphics.ColorUtils
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePrefs.KeyMaterial
import org.fcitx.fcitx5.android.input.AutoScaleTextView
import org.fcitx.fcitx5.android.input.keyboard.estimatedKeyboardBackdropColor
import org.fcitx.fcitx5.android.input.keyboard.KeyMaterialStyle
import org.fcitx.fcitx5.android.input.keyboard.materialKeyBackgroundDrawable
import org.fcitx.fcitx5.android.input.keyboard.readableMaterialContentColor
import org.fcitx.fcitx5.android.utils.alpha
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.gravityCenter

class PopupEntryUi(override val ctx: Context, theme: Theme, keyHeight: Int, radius: Float) : Ui {

    var lastShowTime = -1L
    private val materialStyle = when (ThemeManager.prefs.keyMaterial.getValue()) {
        KeyMaterial.Default -> null
        KeyMaterial.Glass -> KeyMaterialStyle.Glass
        KeyMaterial.FrostedGlass -> KeyMaterialStyle.FrostedGlass
        KeyMaterial.LiquidGlass -> KeyMaterialStyle.LiquidGlass
    }
    private val materialBackgroundColor = materialStyle?.let { popupMaterialBackgroundColor(theme, it) }

    val textView = view(::AutoScaleTextView) {
        textSize = 23f
        gravity = gravityCenter
        setTextColor(
            materialStyle?.let {
                readableMaterialContentColor(
                    theme.popupTextColor,
                    materialBackgroundColor ?: theme.popupBackgroundColor,
                    estimatedKeyboardBackdropColor(theme),
                    minContrast = if (it == KeyMaterialStyle.FrostedGlass) 4.5 else 3.0
                )
            } ?: theme.popupTextColor
        )
    }

    override val root = constraintLayout {
        background = materialStyle?.let {
            materialKeyBackgroundDrawable(
                materialBackgroundColor ?: theme.popupBackgroundColor,
                popupMaterialStrokeColor(theme, it),
                popupMaterialShadowColor(theme),
                radius,
                dp(1),
                0,
                0,
                it,
                pressBump = false,
                edgeStrength = if (it == KeyMaterialStyle.LiquidGlass) 0.18f else 1f
            )
        } ?: GradientDrawable().apply {
            cornerRadius = radius
            setColor(theme.popupBackgroundColor)
        }
        outlineProvider = ViewOutlineProvider.BACKGROUND
        elevation = if (materialStyle == KeyMaterialStyle.LiquidGlass) 0f else dp(2f)
        if (materialStyle == KeyMaterialStyle.LiquidGlass) {
            isPressed = true
        }
        add(textView, lParams(matchParent, keyHeight) {
            topOfParent()
            centerHorizontally()
        })
    }

    fun setText(text: String) {
        textView.text = text
    }

    private fun popupMaterialBackgroundColor(theme: Theme, style: KeyMaterialStyle): Int {
        val mixed = when (style) {
            KeyMaterialStyle.FrostedGlass ->
                ColorUtils.blendARGB(theme.popupBackgroundColor, Color.WHITE, if (theme.isDark) 0.10f else 0.18f)
            KeyMaterialStyle.LiquidGlass ->
                ColorUtils.blendARGB(theme.popupBackgroundColor, Color.WHITE, if (theme.isDark) 0.08f else 0.12f)
            else -> theme.popupBackgroundColor
        }
        val alpha = when (style) {
            KeyMaterialStyle.Glass -> if (theme.isDark) 0.38f else 0.56f
            KeyMaterialStyle.FrostedGlass -> if (theme.isDark) 0.52f else 0.68f
            KeyMaterialStyle.LiquidGlass -> if (theme.isDark) 0.28f else 0.40f
        }
        return mixed.alpha(alpha)
    }

    private fun popupMaterialStrokeColor(theme: Theme, style: KeyMaterialStyle): Int = when (style) {
        KeyMaterialStyle.Glass ->
            if (theme.isDark) Color.WHITE.alpha(0.24f) else Color.BLACK.alpha(0.12f)
        KeyMaterialStyle.FrostedGlass ->
            if (theme.isDark) Color.WHITE.alpha(0.34f) else Color.BLACK.alpha(0.16f)
        KeyMaterialStyle.LiquidGlass ->
            if (theme.isDark) Color.WHITE.alpha(0.62f) else Color.BLACK.alpha(0.18f)
    }

    private fun popupMaterialShadowColor(theme: Theme): Int =
        if (theme.isDark) Color.BLACK.alpha(0.65f) else Color.BLACK.alpha(0.28f)
}
