/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.view.View

interface KeyVisualEffectListener {
    fun onKeyPressStart(view: View, rawX: Float, rawY: Float) {}
    fun onKeyPressMove(view: View, rawX: Float, rawY: Float) {}
    fun onKeyPressEnd(view: View, rawX: Float, rawY: Float) {}
    fun onKeyReleased(view: View, rawX: Float, rawY: Float) {}
}
