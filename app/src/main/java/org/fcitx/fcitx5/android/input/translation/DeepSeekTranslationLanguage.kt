/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.translation

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class DeepSeekTranslationLanguage(
    val promptName: String,
    override val stringRes: Int,
) : ManagedPreferenceEnum {
    English("English", R.string.translation_language_english),
    SimplifiedChinese("Simplified Chinese", R.string.translation_language_simplified_chinese),
    TraditionalChinese("Traditional Chinese", R.string.translation_language_traditional_chinese),
    Japanese("Japanese", R.string.translation_language_japanese),
    Korean("Korean", R.string.translation_language_korean),
    French("French", R.string.translation_language_french),
    German("German", R.string.translation_language_german),
    Spanish("Spanish", R.string.translation_language_spanish),
    Russian("Russian", R.string.translation_language_russian),
    Portuguese("Portuguese", R.string.translation_language_portuguese),
    Italian("Italian", R.string.translation_language_italian),
    Vietnamese("Vietnamese", R.string.translation_language_vietnamese),
    Thai("Thai", R.string.translation_language_thai),
    Indonesian("Indonesian", R.string.translation_language_indonesian),
    Arabic("Arabic", R.string.translation_language_arabic);
}
