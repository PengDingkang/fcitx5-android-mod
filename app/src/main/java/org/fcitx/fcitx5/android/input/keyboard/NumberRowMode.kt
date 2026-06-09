/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class NumberRowMode(override val stringRes: Int) : ManagedPreferenceEnum {
    Disabled(R.string.keyboard_number_row_disabled),
    Password(R.string.keyboard_number_row_password),
    Always(R.string.keyboard_number_row_always);
}
