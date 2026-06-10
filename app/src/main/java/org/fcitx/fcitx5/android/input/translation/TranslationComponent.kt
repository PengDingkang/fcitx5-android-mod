/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.translation

import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.secrets.SecureSecretStore
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.dependency.UniqueViewComponent
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import kotlin.math.max
import kotlin.math.min

class TranslationComponent :
    UniqueViewComponent<TranslationComponent, ViewGroup>(),
    FcitxInputMethodService.InputTextConsumer {

    private val context by manager.context()
    private val service by manager.inputMethodService()
    private val theme by manager.theme()
    private val prefs = AppPrefs.getInstance().translation

    private var translateJob: Job? = null
    private var targetLanguage = prefs.targetLanguage.getValue()
    private var preset = prefs.preset.getValue()

    private val ui by lazy {
        TranslationUi(context, theme).apply {
            backButton.setOnClickListener { hide() }
            presetChip.setOnClickListener { showPresetMenu() }
            targetChip.setOnClickListener { showTargetLanguageMenu() }
            sendButton.setOnClickListener { submitTranslation() }
        }
    }

    override val view: ViewGroup by lazy { ui.root }

    val isActive: Boolean
        get() = view.visibility == View.VISIBLE

    fun toggle() {
        if (isActive) {
            hide()
        } else {
            show()
        }
    }

    fun show() {
        targetLanguage = prefs.targetLanguage.getValue()
        preset = prefs.preset.getValue()
        ui.setTargetLanguage(targetLanguage)
        ui.setPreset(preset)
        ui.setStatus(null)
        view.visibility = View.VISIBLE
        service.setInputTextConsumer(this)
        ui.input.requestFocus()
        ui.input.setSelection(ui.input.text?.length ?: 0)
    }

    fun hide() {
        translateJob?.cancel()
        translateJob = null
        ui.setLoading(false)
        ui.setStatus(null)
        view.visibility = View.GONE
        service.setInputTextConsumer(null)
        ui.input.clearFocus()
    }

    fun onDetachedFromInputView() {
        if (isActive) {
            service.setInputTextConsumer(null)
        }
        translateJob?.cancel()
        translateJob = null
    }

    fun setSidePadding(left: Int, right: Int) {
        ui.setPanelPadding(left, right)
    }

    override fun commitText(text: String, cursor: Int) {
        val editable = ui.input.text ?: return
        val start = min(ui.input.selectionStart, ui.input.selectionEnd).coerceAtLeast(0)
        val end = max(ui.input.selectionStart, ui.input.selectionEnd).coerceAtLeast(0)
        editable.replace(start, end, text)
        val target = if (cursor == -1) {
            start + text.length
        } else {
            start + cursor
        }.coerceIn(0, editable.length)
        ui.input.setSelection(target)
    }

    override fun deleteBackspace() {
        val editable = ui.input.text ?: return
        val start = min(ui.input.selectionStart, ui.input.selectionEnd).coerceAtLeast(0)
        val end = max(ui.input.selectionStart, ui.input.selectionEnd).coerceAtLeast(0)
        when {
            start != end -> editable.delete(start, end)
            start > 0 -> {
                val previous = Character.offsetByCodePoints(editable, start, -1)
                editable.delete(previous, start)
            }
        }
    }

    override fun deleteSurrounding(before: Int, after: Int) {
        val editable = ui.input.text ?: return
        val cursor = ui.input.selectionStart.coerceIn(0, editable.length)
        val start = offsetByCodePoints(cursor, -before.coerceAtLeast(0))
        val end = offsetByCodePoints(cursor, after.coerceAtLeast(0))
        if (start != end) {
            editable.delete(start, end)
        }
    }

    override fun performEnter() {
        commitText("\n", -1)
    }

    override fun moveCursor(offset: Int) {
        val editable = ui.input.text ?: return
        if (editable.isEmpty()) return
        val cursor = ui.input.selectionEnd.coerceIn(0, editable.length)
        ui.input.setSelection(offsetByCodePoints(cursor, offset))
    }

    private fun offsetByCodePoints(index: Int, offset: Int): Int {
        val text = ui.input.text ?: return 0
        return runCatching {
            Character.offsetByCodePoints(text, index, offset)
        }.getOrElse {
            if (offset < 0) 0 else text.length
        }.coerceIn(0, text.length)
    }

    private fun submitTranslation() {
        val sourceText = ui.input.text?.toString().orEmpty()
        if (sourceText.isBlank()) {
            Toast.makeText(context, R.string.translation_empty_input, Toast.LENGTH_SHORT).show()
            return
        }
        val apiKey = SecureSecretStore.getSecret(SecureSecretStore.TranslationApiKey).trim()
        if (apiKey.isBlank()) {
            Toast.makeText(context, R.string.translation_api_key_missing, Toast.LENGTH_SHORT).show()
            return
        }

        translateJob?.cancel()
        ui.setLoading(true)
        ui.setStatus(context.getString(R.string.translation_in_progress))
        translateJob = service.lifecycleScope.launch {
            val result = runCatching {
                DeepSeekTranslator(
                    baseUrl = prefs.apiBaseUrl.getValue(),
                    apiKey = apiKey,
                    model = prefs.model.getValue().apiName,
                    enableThinking = prefs.enableThinking.getValue()
                ).translate(
                    text = sourceText,
                    targetLanguage = targetLanguage.promptName,
                    presetInstruction = preset.resolveInstruction(prefs.customPresetInstruction.getValue())
                )
            }
            ui.setLoading(false)
            result.onSuccess {
                service.commitText(it)
                ui.input.text?.clear()
                if (prefs.keepPanelOpenAfterSend.getValue()) {
                    ui.setStatus(context.getString(R.string.translation_committed))
                } else {
                    hide()
                }
            }.onFailure {
                ui.setStatus(context.getString(R.string.translation_failed))
                Toast.makeText(
                    context,
                    context.getString(R.string.translation_failed_with_reason, it.message.orEmpty()),
                    Toast.LENGTH_LONG
                ).show()
            }
            translateJob = null
        }
    }

    private fun showTargetLanguageMenu() {
        PopupMenu(context, ui.targetChip).apply {
            DeepSeekTranslationLanguage.entries.forEachIndexed { index, language ->
                menu.add(0, index, index, context.getString(language.stringRes)).apply {
                    isChecked = language == targetLanguage
                }
            }
            menu.setGroupCheckable(0, true, true)
            setOnMenuItemClickListener { item ->
                val language = DeepSeekTranslationLanguage.entries[item.itemId]
                targetLanguage = language
                prefs.targetLanguage.setValue(language)
                ui.setTargetLanguage(language)
                true
            }
        }.show()
    }

    private fun showPresetMenu() {
        PopupMenu(context, ui.presetChip).apply {
            TranslationPreset.entries.forEachIndexed { index, item ->
                menu.add(0, index, index, context.getString(item.stringRes)).apply {
                    isChecked = item == preset
                }
            }
            menu.setGroupCheckable(0, true, true)
            setOnMenuItemClickListener { item ->
                val selected = TranslationPreset.entries[item.itemId]
                preset = selected
                prefs.preset.setValue(selected)
                ui.setPreset(selected)
                true
            }
        }.show()
    }
}
