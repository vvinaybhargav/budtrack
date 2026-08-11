package com.vinay.fintrack.data

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal OpenAI chat client. Blocking on purpose — it runs on a worker thread
 * and the caller posts the result back — and built on HttpURLConnection so the
 * app gains no HTTP dependency for one endpoint.
 */
class OpenAi(private val apiKey: String, private val model: String = DEFAULT_MODEL) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    class Failure(message: String) : Exception(message)

    /**
     * One round trip. [messages] is the whole conversation including any tool
     * results; [tools] the functions the model may call.
     *
     * @return the assistant message, which either carries content or asks for
     *   tool calls.
     */
    fun chat(messages: JsonArray, tools: JsonArray): JsonObject {
        if (apiKey.isBlank()) throw Failure("No OpenAI key set — add one in Settings.")

        val body = buildJsonObject {
            put("model", model)
            put("messages", messages)
            put("tools", tools)
            put("tool_choice", "auto")
            put("temperature", 0.2)
            // A phone-sized answer. Generation is the slowest part of the wait,
            // and it is charged by the token.
            put("max_tokens", 500)
            // Several tool calls in one reply rather than a round trip each.
            put("parallel_tool_calls", true)
        }

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            // Keeps the TLS handshake out of the second call of a tool round trip.
            setRequestProperty("Connection", "keep-alive")
        }

        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (code !in 200..299) {
                Log.w(TAG, "OpenAI $code: ${text.take(400)}")
                throw Failure(readableError(code, text))
            }
            val root = json.parseToJsonElement(text) as? JsonObject
                ?: throw Failure("Unexpected reply from OpenAI.")
            val choices = root["choices"] as? JsonArray
            val first = choices?.firstOrNull() as? JsonObject
                ?: throw Failure("OpenAI returned no reply.")
            first["message"] as? JsonObject ?: throw Failure("OpenAI returned no message.")
        } finally {
            // Not disconnect(): that closes the socket, so the next call in a
            // tool round trip would pay for a fresh TLS handshake.
            runCatching { conn.inputStream?.close() }
        }
    }

    /** The raw error body is JSON and unhelpful on a phone screen. */
    private fun readableError(code: Int, body: String): String = when {
        code == 401 -> "That OpenAI key was rejected. Check it in Settings."
        code == 429 && body.contains("quota", true) ->
            "Your OpenAI account is out of credit."
        code == 429 -> "OpenAI is rate-limiting; try again in a moment."
        code == 404 -> "This key can't use $model."
        code >= 500 -> "OpenAI is having trouble; try again shortly."
        else -> runCatching {
            val obj = Json.parseToJsonElement(body) as JsonObject
            ((obj["error"] as? JsonObject)?.get("message")?.toString() ?: body).trim('"')
        }.getOrDefault("OpenAI request failed ($code).")
    }

    companion object {
        const val DEFAULT_MODEL = "gpt-4o-mini"
        private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        private const val TAG = "OpenAi"
    }
}
