package com.bot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                AiCompanionScreen()
            }
        }
    }
}

@Composable
fun AiCompanionScreen() {
    var textInput by remember { mutableStateOf("") }
    val chatLog = remember { mutableStateListOf("AI: Hello! I am your AI companion.") }
    val coroutineScope = rememberCoroutineScope()
    val apiKey = "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"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(chatLog) { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask something...") }
            )

            Button(
                onClick = {
                    val userQuery = textInput
                    if (userQuery.isNotBlank()) {
                        chatLog.add("You: $userQuery")
                        chatLog.add("AI: Processing...")
                        val processingIndex = chatLog.lastIndex
                        textInput = ""

                        coroutineScope.launch {
                            val result = callOpenRouter(userQuery, apiKey)
                            chatLog[processingIndex] = "AI: $result"
                        }
                    }
                }
            ) {
                Text("Send")
            }
        }
    }
}

suspend fun callOpenRouter(prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://openrouter.ai/api/v1/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.doOutput = true

        val jsonBody = JSONObject().apply {
            put("model", "meta-llama/llama-3.2-1b-instruct:free")
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
        }

        val writer = OutputStreamWriter(conn.outputStream)
        writer.write(jsonBody.toString())
        writer.flush()

        val responseCode = conn.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(responseText)
            val choices = jsonResponse.getJSONArray("choices")
            val message = choices.getJSONObject(0).getJSONObject("message")
            message.getString("content")
        } else {
            val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            "Error ($responseCode): $errorText"
        }
    } catch (e: Exception) {
        "Exception: ${e.localizedMessage}"
    }
}
