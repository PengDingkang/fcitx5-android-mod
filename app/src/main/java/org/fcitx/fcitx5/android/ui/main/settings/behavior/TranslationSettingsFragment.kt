/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.data.secrets.SecureSecretStore
import splitties.dimensions.dp

class TranslationSettingsFragment : ManagedPreferenceFragment(AppPrefs.getInstance().translation) {

    private var apiKeyPreference: Preference? = null

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        apiKeyPreference = Preference(screen.context).apply {
            key = SecureSecretStore.TranslationApiKey
            isIconSpaceReserved = false
            isSingleLineTitle = false
            setTitle(R.string.translation_api_key)
            setSummary(R.string.translation_api_key_summary)
            setOnPreferenceClickListener {
                showApiKeyDialog()
                true
            }
        }.also {
            screen.addPreference(it)
            updateApiKeySummary()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateApiKeySummary()
    }

    private fun updateApiKeySummary() {
        apiKeyPreference?.summary = if (SecureSecretStore.hasSecret(SecureSecretStore.TranslationApiKey)) {
            getString(R.string.translation_api_key_configured)
        } else {
            getString(R.string.translation_api_key_summary)
        }
    }

    private fun showApiKeyDialog() {
        val ctx = requireContext()
        val editText = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            hint = if (SecureSecretStore.hasSecret(SecureSecretStore.TranslationApiKey)) {
                ctx.getString(R.string.translation_api_key_replace_hint)
            } else {
                ""
            }
        }
        val container = FrameLayout(ctx).apply {
            val horizontalPadding = dp(24)
            val topPadding = dp(8)
            setPadding(horizontalPadding, topPadding, horizontalPadding, 0)
            addView(editText, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.translation_api_key)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = editText.text?.toString().orEmpty().trim()
                if (value.isNotEmpty()) {
                    SecureSecretStore.putSecret(SecureSecretStore.TranslationApiKey, value)
                }
                updateApiKeySummary()
            }
            .setNeutralButton(R.string.clear) { _, _ ->
                SecureSecretStore.clearSecret(SecureSecretStore.TranslationApiKey)
                updateApiKeySummary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
