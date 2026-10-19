package com.bot.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

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
    val chatLog = remember { mutableStateListOf("AI: Hello! Speak or type to chat with me.") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val apiKey = "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"

    var isListening by remember { mutableStateOf(false) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Text To Speech Setup
    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale.US
            }
        }
        ttsEngine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    // Function to process prompts
    fun processUserPrompt(query: String) {
        if (query.isNotBlank()) {
            chatLog.add("You: $query")
            chatLog.add("AI: Processing...")
            val processingIndex = chatLog.lastIndex

            coroutineScope.launch {
                val result = callOpenRouter(query, apiKey)
                chatLog[processingIndex] = "AI: $result"
                ttsEngine?.speak(result, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }

    // Speech Recognizer setup
    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
    }

    val startListening = {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer?.stopListening()
            speechRecognizer?.startListening(intent)
            isListening = true
        }
    }

    DisposableEffect(context) {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                }
                override fun onError(error: Int) {
                    isListening = false
                    // Automatically restart listening loop if needed
                    startListening()
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val spokenText = matches[0]
                        processUserPrompt(spokenText)
                    }
                    // Continue listening after handling result
                    startListening()
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        speechRecognizer = recognizer

        onDispose {
            recognizer.destroy()
        }
    }

    // Audio Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
        }
    }

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

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        textInput = ""
                        processUserPrompt(userQuery)
                    }
                ) {
                    Text("Send")
                }
            }

            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        if (isListening) {
                            speechRecognizer?.stopListening()
                            isListening = false
                        } else {
                            startListening()
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isListening) "Listening... (Tap to stop)" else "🎤 Speak")
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
            put("model", "openrouter/free")
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
