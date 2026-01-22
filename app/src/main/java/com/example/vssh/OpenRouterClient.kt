package com.example.vssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OpenRouterClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(15))
        .readTimeout(java.time.Duration.ofSeconds(25))
        .writeTimeout(java.time.Duration.ofSeconds(15))
        .callTimeout(java.time.Duration.ofSeconds(30))
        .build()
) {
    suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
        }

        val cleanBase = baseUrl.trim().trimEnd('/')
        val endpoint = if (cleanBase.isBlank()) "https://openrouter.ai/api/v1" else cleanBase
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$endpoint/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("X-Title", "vssh")
            .addHeader("HTTP-Referer", "https://localhost")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = extractErrorDetail(response.body?.string().orEmpty())
                val hint = when (response.code) {
                    401 -> "API-Key ungültig oder nicht aktiviert."
                    402 -> "Kein Guthaben oder Abrechnung nötig."
                    429 -> "Rate-Limit erreicht."
                    else -> null
                }
                val extra = listOfNotNull(hint, detail.takeIf { it.isNotBlank() }).joinToString(" ")
                val suffix = if (extra.isNotBlank()) " $extra" else ""
                throw IllegalStateException("OpenRouter Fehler: ${response.code}.$suffix")
            }

            val json = JSONObject(response.body?.string().orEmpty())
            val choices = json.optJSONArray("choices") ?: JSONArray()
            if (choices.length() == 0) {
                throw IllegalStateException("Leere Antwort von OpenRouter")
            }
            val message = choices.getJSONObject(0).optJSONObject("message")
            message?.optString("content")?.trim().orEmpty()
        }
    }

    private fun extractErrorDetail(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            val json = JSONObject(raw)
            val err = json.optJSONObject("error")
            when {
                err != null -> err.optString("message").trim()
                json.has("message") -> json.optString("message").trim()
                else -> raw.take(200)
            }
        } catch (_: Exception) {
            raw.take(200)
        }
    }
}

// role = system | user | assistant
data class ChatMessage(val role: String, val content: String)
