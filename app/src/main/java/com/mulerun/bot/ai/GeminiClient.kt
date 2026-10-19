package com.mulerun.bot.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AiResponse(
    val text: String? = null,
    val toolName: String? = null,
    val arguments: JSONObject? = null,
)

class GeminiClient {
    suspend fun reply(
        apiKey: String,
        userText: String,
        relationship: String,
        screenText: String?,
        provider: String = "Gemini",
        model: String = "gemini-2.5-flash",
        baseUrl: String = "https://integrate.api.nvidia.com/v1",
        imageBase64: String? = null,
    ): Result<AiResponse> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(
            IllegalStateException("Add an API key in Settings first.")
        )
        runCatching {
            val systemPrompt = """
                You are Bot, a warm but honest Android voice companion. Your role is $relationship.
                Respond naturally and concisely for speech. Adapt your tone to the user's mood:
                encouraging, playful, calm, serious, or gently challenging when appropriate. Show
                empathy without pretending to be human, conscious, or to literally feel emotions.
                Do not encourage dependency. When safety, money, calls, emails,
                messages, permissions, or account changes are involved, explain that user confirmation
                is required. Never claim an action succeeded unless the Android app confirms it.
            """.trimIndent()
            val context = screenText?.take(4_000)?.let {
                "\nThe user explicitly allowed this visible-screen context:\n$it"
            }.orEmpty()
            val prompt = userText + context
            val raw: String
            val code: Int
            if (provider.equals("Gemini", ignoreCase = true)) {
                val body = JSONObject().apply {
                    put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                    val parts = JSONArray().put(JSONObject().put("text", prompt))
                    imageBase64?.let { parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", it))) }
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("parts", parts)
                    }))
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.75)
                        put("maxOutputTokens", 700)
                    })
                }
                val connection = URL("https://generativelanguage.googleapis.com/v1beta/models/${model.ifBlank { "gemini-2.5-flash" }}:generateContent").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 45_000
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("x-goog-api-key", apiKey)
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                code = connection.responseCode
                raw = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) error(apiError(raw, code))
                AiResponse(text = JSONObject(raw).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim())
            } else {
                // NVIDIA NIM and many other providers expose an OpenAI-compatible chat-completions API.
                val endpoint = baseUrl.trim().trimEnd('/')
                require(endpoint.startsWith("https://")) { "API base URL must use HTTPS." }
                val body = JSONObject().apply {
                    put("model", model.ifBlank { "meta/llama-3.1-8b-instruct" })
                    val userContent: Any = if (imageBase64 == null) prompt else JSONArray()
                        .put(JSONObject().put("type", "text").put("text", prompt))
                        .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageBase64")))
                    put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(JSONObject().put("role", "user").put("content", userContent)))
                    put("temperature", 0.7)
                    put("max_tokens", if (imageBase64 == null) 700 else 900)
                    // Vision analysis is deliberately read-only; don't expose action tools for screenshots.
                    if (imageBase64 == null) {
                        put("tools", toolDefinitions())
                        put("tool_choice", "auto")
                    }
                }
                val connection = URL("$endpoint/chat/completions").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 45_000
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                code = connection.responseCode
                raw = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) error(apiError(raw, code))
                val message = JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                val calls = message.optJSONArray("tool_calls")
                if (calls != null && calls.length() > 0) {
                    val function = calls.getJSONObject(0).getJSONObject("function")
                    AiResponse(
                        toolName = function.optString("name").takeIf { it.isNotBlank() },
                        arguments = runCatching { JSONObject(function.optString("arguments", "{}")) }.getOrDefault(JSONObject()),
                    )
                } else {
                    AiResponse(text = message.optString("content").trim().ifBlank { "I couldn't form a response. Try rephrasing that." })
                }
            }
        }
    }


    private fun toolDefinitions(): JSONArray {
        fun parameter(name: String, description: String, type: String = "string") = JSONObject()
            .put("type", type).put("description", description)
        fun tool(name: String, description: String, properties: JSONObject, required: List<String>) = JSONObject()
            .put("type", "function")
            .put("function", JSONObject()
                .put("name", name)
                .put("description", description)
                .put("parameters", JSONObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", JSONArray(required))))
        return JSONArray()
            .put(tool("open_app", "Open an installed Android app by its visible name.", JSONObject().put("app_name", parameter("app_name", "Installed app name")), listOf("app_name")))
            .put(tool("search_google", "Open Google search results.", JSONObject().put("query", parameter("query", "Search query")), listOf("query")))
            .put(tool("search_youtube", "Open YouTube search results.", JSONObject().put("query", parameter("query", "Search query")), listOf("query")))
            .put(tool("search_instagram", "Open Instagram keyword search.", JSONObject().put("query", parameter("query", "Search query")), listOf("query")))
            .put(tool("dial_number", "Open the phone dialer with a phone number. Never place a call directly.", JSONObject().put("number", parameter("number", "Phone number to dial")), listOf("number")))
            .put(tool("draft_sms", "Prepare an SMS draft. Never send it directly. Recipient may be a contact name or phone number.", JSONObject().put("recipient", parameter("recipient", "Contact name or phone number" )).put("message", parameter("message", "SMS body")), listOf("recipient", "message")))
            .put(tool("draft_whatsapp", "Open WhatsApp with a prefilled draft. Never send it directly. Recipient may be a contact name or international phone number.", JSONObject().put("recipient", parameter("recipient", "Contact name or phone number")).put("message", parameter("message", "WhatsApp message body")), listOf("recipient", "message")))
            .put(tool("dial_contact", "Open the phone dialer for a saved contact. Never place a call directly.", JSONObject().put("contact_name", parameter("contact_name", "Saved contact name")), listOf("contact_name")))
            .put(tool("draft_email", "Prepare an email draft. Never send it directly.", JSONObject().put("recipient", parameter("recipient", "Email address")).put("subject", parameter("subject", "Email subject")).put("body", parameter("body", "Email body")), listOf("recipient", "subject", "body")))
            .put(tool("schedule_reminder", "Schedule a reminder after a number of minutes. This must not automatically send messages or place calls.", JSONObject().put("minutes", parameter("minutes", "Delay in minutes", "integer")).put("request", parameter("request", "Reminder text")), listOf("minutes", "request")))
            .put(tool("click_screen_text", "Tap a visible clickable item by its text or accessibility description. This is a consequential UI action and requires user confirmation.", JSONObject().put("target", parameter("target", "Visible text or description to tap")), listOf("target")))
            .put(tool("scroll_screen", "Scroll the current app screen up or down.", JSONObject().put("direction", parameter("direction", "up or down; use down to move to later content")), listOf("direction")))
            .put(tool("type_screen_text", "Enter text into the currently focused editable field. Never submit the form automatically.", JSONObject().put("text", parameter("text", "Text to enter into the focused field")), listOf("text")))
            .put(tool("press_back", "Press Android Back in the current app. This changes the current UI state and requires confirmation.", JSONObject(), emptyList()))
    }

    private fun apiError(raw: String, code: Int): String = runCatching {
        val json = JSONObject(raw)
        json.optJSONObject("error")?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?: json.optString("message").takeIf { it.isNotBlank() }
            ?: "AI request failed ($code)"
    }.getOrDefault("AI request failed ($code)").take(240)
}
