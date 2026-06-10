/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.translation

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class TranslationPreset(
    override val stringRes: Int,
    val promptInstruction: String,
) : ManagedPreferenceEnum {
    Balanced(
        R.string.translation_preset_balanced,
        "Use a balanced translation style: accurate, natural, and faithful to the source tone."
    ),
    Literal(
        R.string.translation_preset_literal,
        "Stay close to the source wording and structure. Prefer literal accuracy over rewriting."
    ),
    Natural(
        R.string.translation_preset_natural,
        "Rewrite naturally in the target language while preserving the source meaning and tone."
    ),
    Formal(
        R.string.translation_preset_formal,
        "Use a polished, formal, professional tone in the target language."
    ),
    Casual(
        R.string.translation_preset_casual,
        "Use a casual, conversational tone in the target language."
    ),
    Concise(
        R.string.translation_preset_concise,
        "Keep the translation concise. Remove unnecessary filler while preserving meaning."
    ),
    Custom(
        R.string.translation_preset_custom,
        ""
    );

    fun resolveInstruction(customInstruction: String): String {
        return if (this == Custom) {
            customInstruction.trim().ifEmpty { Balanced.promptInstruction }
        } else {
            promptInstruction
        }
    }
}
