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
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.launch

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

    val generativeModel = remember {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = "AQ.Ab8RN6JX1wWlrztdxQpZ_eBqD00WHQKLAk6zoNbatypdpT7H9g"
        )
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
                            try {
                                val response = generativeModel.generateContent(userQuery)
                                chatLog[processingIndex] = "AI: ${response.text ?: "No response"}"
                            } catch (e: Exception) {
                                chatLog[processingIndex] = "AI: Error - ${e.localizedMessage}"
                            }
                        }
                    }
                }
            ) {
                Text("Send")
            }
        }
    }
}
