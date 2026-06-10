/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.translation

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class DeepSeekTranslationModel(
    val apiName: String,
    override val stringRes: Int,
) : ManagedPreferenceEnum {
    Flash("deepseek-v4-flash", R.string.translation_model_deepseek_v4_flash),
    Pro("deepseek-v4-pro", R.string.translation_model_deepseek_v4_pro);

    override fun toString() = apiName
}
