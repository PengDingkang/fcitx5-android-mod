/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class DeepSeekTranslator(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val enableThinking: Boolean,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun translate(
        text: String,
        targetLanguage: String,
        presetInstruction: String,
    ): String = withContext(Dispatchers.IO) {
        val endpoint = "${baseUrl.trimEnd('/')}/chat/completions"
        val systemPrompt = buildString {
            append(
                "You are a translation engine. Translate the user's text into the requested target language. " +
                        "Output only the translated text. Preserve line breaks. Do not add explanations."
            )
            val instruction = presetInstruction.trim()
            if (instruction.isNotEmpty()) {
                append('\n')
                append("Translation preset: ")
                append(instruction)
            }
        }
        val request = ChatRequest(
            model = model,
            thinking = Thinking(if (enableThinking) "enabled" else "disabled"),
            reasoningEffort = if (enableThinking) "high" else null,
            temperature = if (enableThinking) null else 0.2,
            messages = listOf(
                Message(
                    role = "system",
                    content = systemPrompt
                ),
                Message(
                    role = "user",
                    content = "Target language: $targetLanguage\n\nText:\n$text"
                )
            )
        )
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use {
                it.write(json.encodeToString(ChatRequest.serializer(), request).toByteArray(Charsets.UTF_8))
            }
            val responseCode = connection.responseCode
            val body = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (responseCode !in 200..299) {
                throw IOException("HTTP $responseCode: $body")
            }
            json.decodeFromString(ChatResponse.serializer(), body)
                .choices
                .firstOrNull()
                ?.message
                ?.content
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IOException("Empty translation response")
        } finally {
            connection.disconnect()
        }
    }

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<Message>,
        val thinking: Thinking,
        val stream: Boolean = false,
        val temperature: Double? = 0.2,
        @SerialName("reasoning_effort")
        val reasoningEffort: String? = null,
        @SerialName("max_tokens")
        val maxTokens: Int = 2048,
    )

    @Serializable
    private data class Thinking(
        val type: String,
    )

    @Serializable
    private data class Message(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class ChatResponse(
        val choices: List<Choice> = emptyList(),
    )

    @Serializable
    private data class Choice(
        val message: Message,
    )
}
