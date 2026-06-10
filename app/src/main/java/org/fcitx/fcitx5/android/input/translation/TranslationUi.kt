/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.translation

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.utils.alpha
import org.fcitx.fcitx5.android.utils.rippleDrawable
import splitties.dimensions.dp
import splitties.views.imageResource

class TranslationUi(
    private val ctx: Context,
    private val theme: Theme,
) {

    private val keyRadius = ctx.dp(ThemeManager.prefs.keyRadius.getValue().toFloat())
    private val controlRadius = if (keyRadius > 0f) keyRadius else ctx.dp(4f)
    private val keyBorder = ThemeManager.prefs.keyBorder.getValue()
    private val keyBorderStroke = ThemeManager.prefs.keyBorderStroke.getValue()

    private fun roundedBackground(color: Int, radius: Float = controlRadius) = GradientDrawable().apply {
        cornerRadius = radius
        setColor(color)
    }

    private fun keyBackground(color: Int): Drawable {
        val body = roundedBackground(color)
        if (!keyBorder) return body
        return if (keyBorderStroke) {
            body.apply {
                setStroke(ctx.dp(1), theme.keyShadowColor)
            }
        } else {
            LayerDrawable(arrayOf(
                roundedBackground(theme.keyShadowColor),
                body
            )).apply {
                val shadowWidth = ctx.dp(1)
                setLayerInset(1, 0, 0, 0, shadowWidth)
            }
        }
    }

    private fun applyPressFeedback(view: View) {
        view.foreground = rippleDrawable(theme.keyPressHighlightColor, roundedBackground(Color.WHITE))
    }

    private fun languageButton(text: String, active: Boolean = false) = TextView(ctx).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 14f
        setTextColor(if (active) theme.keyTextColor else theme.altKeyTextColor)
        background = keyBackground(if (active) theme.altKeyBackgroundColor else theme.keyBackgroundColor)
        includeFontPadding = false
        setPadding(ctx.dp(12), 0, ctx.dp(12), 0)
    }

    val backButton = ImageButton(ctx).apply {
        imageResource = R.drawable.ic_baseline_arrow_back_24
        imageTintList = ColorStateList.valueOf(theme.altKeyTextColor)
        background = keyBackground(theme.keyBackgroundColor)
        applyPressFeedback(this)
        contentDescription = ctx.getString(R.string.back_to_keyboard)
    }

    val presetChip = languageButton("").apply {
        isClickable = true
        isFocusable = true
        applyPressFeedback(this)
        contentDescription = ctx.getString(R.string.translation_preset)
    }

    val swapIcon = TextView(ctx).apply {
        text = "→"
        gravity = Gravity.CENTER
        textSize = 20f
        includeFontPadding = false
        setTextColor(theme.altKeyTextColor)
    }

    val targetChip = languageButton("", active = true).apply {
        isClickable = true
        isFocusable = true
        applyPressFeedback(this)
        contentDescription = ctx.getString(R.string.translation_target_language)
    }

    val input = EditText(ctx).apply {
        background = null
        minHeight = ctx.dp(56)
        maxLines = 3
        isSingleLine = false
        inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        showSoftInputOnFocus = false
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        hint = ctx.getString(R.string.translation_input_hint)
        setHintTextColor(theme.altKeyTextColor.alpha(0.7f))
        setTextColor(theme.keyTextColor)
        textSize = 16f
        includeFontPadding = false
        setPadding(ctx.dp(14), ctx.dp(8), ctx.dp(50), ctx.dp(8))
    }

    val sendButton = ImageButton(ctx).apply {
        imageResource = R.drawable.ic_baseline_send_24
        imageTintList = ColorStateList.valueOf(theme.genericActiveForegroundColor)
        background = keyBackground(theme.accentKeyBackgroundColor)
        applyPressFeedback(this)
        contentDescription = ctx.getString(R.string.translation_send)
    }

    val progress = ProgressBar(ctx).apply {
        indeterminateTintList = ColorStateList.valueOf(theme.genericActiveBackgroundColor)
        visibility = View.GONE
    }

    val statusText = TextView(ctx).apply {
        visibility = View.GONE
        setTextColor(theme.altKeyTextColor)
        textSize = 12f
        includeFontPadding = false
    }

    private val horizontalPadding = ctx.dp(6)
    private val topPadding = ctx.dp(6)
    private val bottomPadding = ctx.dp(8)

    private val inputContainer = FrameLayout(ctx).apply {
        background = keyBackground(theme.keyBackgroundColor)
        addView(input, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL
        ))
        addView(sendButton, FrameLayout.LayoutParams(ctx.dp(40), ctx.dp(40), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            marginEnd = ctx.dp(5)
        })
        addView(progress, FrameLayout.LayoutParams(ctx.dp(28), ctx.dp(28), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            marginEnd = ctx.dp(11)
        })
    }

    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        background = roundedBackground(theme.barColor, 0f)
        setPadding(horizontalPadding, topPadding, horizontalPadding, bottomPadding)
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(backButton, LinearLayout.LayoutParams(ctx.dp(40), ctx.dp(40)))
            addView(presetChip, LinearLayout.LayoutParams(0, ctx.dp(40), 1f).apply {
                marginStart = ctx.dp(8)
                marginEnd = ctx.dp(6)
            })
            addView(swapIcon, LinearLayout.LayoutParams(ctx.dp(32), ctx.dp(40)))
            addView(targetChip, LinearLayout.LayoutParams(0, ctx.dp(40), 1f).apply {
                marginStart = ctx.dp(6)
            })
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        addView(inputContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = ctx.dp(8)
        })
        addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = ctx.dp(6)
            marginStart = ctx.dp(12)
            marginEnd = ctx.dp(12)
        })
    }

    fun setPanelPadding(left: Int, right: Int) {
        root.setPadding(
            left + horizontalPadding,
            topPadding,
            right + horizontalPadding,
            bottomPadding
        )
    }

    fun setTargetLanguage(language: DeepSeekTranslationLanguage) {
        targetChip.text = ctx.getString(language.stringRes)
    }

    fun setPreset(preset: TranslationPreset) {
        presetChip.text = ctx.getString(preset.stringRes)
    }

    fun setLoading(loading: Boolean) {
        sendButton.isEnabled = !loading
        input.isEnabled = !loading
        sendButton.visibility = if (loading) View.GONE else View.VISIBLE
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    fun setStatus(message: CharSequence?) {
        statusText.text = message ?: ""
        statusText.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
    }
}
