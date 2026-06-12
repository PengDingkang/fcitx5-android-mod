/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.clipboard.ClipboardManager
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardEntry
import org.fcitx.fcitx5.android.databinding.ActivityClipboardEditBinding
import org.fcitx.fcitx5.android.utils.clipboardManager
import org.fcitx.fcitx5.android.utils.inputMethodManager
import org.fcitx.fcitx5.android.utils.str

class ClipboardEditActivity : Activity() {

    private val scope: CoroutineScope = MainScope()

    private lateinit var binding: ActivityClipboardEditBinding
    private lateinit var editText: EditText

    private var entryId: Int = -1
    private var createPinned: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes.gravity = Gravity.TOP
        binding = ActivityClipboardEditBinding.inflate(layoutInflater).apply {
            editText = clipboardEditText
            clipboardEditCancel.setOnClickListener { finish() }
            clipboardEditOk.setOnClickListener { finishEditing() }
            clipboardEditCopy.setOnClickListener { finishEditing(copy = true) }
        }
        setContentView(binding.root)
        inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        processIntent(intent)
    }

    private fun finishEditing(copy: Boolean = false) {
        val str = editText.str
        if (createPinned && str.isBlank()) {
            finish()
            return
        }
        scope.launch {
            if (createPinned) {
                ClipboardManager.addPinned(str)
            } else {
                ClipboardManager.updateText(entryId, str)
            }
            if (copy) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("", str))
            }
        }
        finish()
    }

    private fun setEntry(entry: ClipboardEntry) {
        entryId = entry.id
        editText.setText(entry.text)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent) {
        createPinned = intent.getBooleanExtra(CREATE_PINNED, false)
        if (createPinned) {
            entryId = -1
            editText.setText("")
            editText.setHint(R.string.pinned_clipboard_text_hint)
            binding.clipboardEditOk.setText(R.string.save)
            binding.clipboardEditCopy.visibility = View.GONE
            setTitle(R.string.add_pinned_clipboard_item)
            return
        }
        editText.setHint(R.string.clipboard_edit_text_hint)
        binding.clipboardEditOk.setText(android.R.string.ok)
        binding.clipboardEditCopy.visibility = View.VISIBLE
        setTitle(R.string.edit_clipboard)
        scope.launch {
            intent.run {
                if (getBooleanExtra(LAST_ENTRY, false)) {
                    ClipboardManager.lastEntry
                } else {
                    ClipboardManager.get(getIntExtra(ENTRY_ID, -1))
                }
            }?.let { setEntry(it) }
        }
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        const val ENTRY_ID = "id"
        const val LAST_ENTRY = "last_entry"
        const val CREATE_PINNED = "create_pinned"
    }
}
