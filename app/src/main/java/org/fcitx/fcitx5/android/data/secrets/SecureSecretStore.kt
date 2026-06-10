/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.secrets

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import org.fcitx.fcitx5.android.utils.appContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureSecretStore {

    const val SharedPrefsName = "secure_secrets"
    const val TranslationApiKey = "translation_api_key"

    private const val AndroidKeyStore = "AndroidKeyStore"
    private const val KeyAlias = "org.fcitx.fcitx5.android.secure_secrets"
    private const val CipherTransformation = "AES/GCM/NoPadding"
    private const val GcmTagLength = 128
    private const val IvSuffix = ".iv"
    private const val CiphertextSuffix = ".ciphertext"

    private val lock = Any()

    private val prefs
        get() = appContext.getSharedPreferences(SharedPrefsName, Context.MODE_PRIVATE)

    fun hasSecret(key: String): Boolean = getSecret(key).isNotEmpty()

    fun getSecret(key: String): String = synchronized(lock) {
        val iv = prefs.getString(key + IvSuffix, null) ?: return@synchronized ""
        val ciphertext = prefs.getString(key + CiphertextSuffix, null) ?: return@synchronized ""
        runCatching {
            val cipher = Cipher.getInstance(CipherTransformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GcmTagLength, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrElse {
            if (it is GeneralSecurityException || it is IOException || it is IllegalArgumentException) {
                clearSecret(key)
                ""
            } else {
                throw it
            }
        }
    }

    fun putSecret(key: String, value: String) = synchronized(lock) {
        if (value.isEmpty()) {
            clearSecret(key)
            return@synchronized
        }
        val cipher = Cipher.getInstance(CipherTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit {
            putString(key + IvSuffix, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            putString(key + CiphertextSuffix, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        }
    }

    fun clearSecret(key: String) = synchronized(lock) {
        prefs.edit {
            remove(key + IvSuffix)
            remove(key + CiphertextSuffix)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        (keyStore.getEntry(KeyAlias, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
        val spec = KeyGenParameterSpec.Builder(
            KeyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
