package com.bot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    var chatLog by remember { mutableStateOf(listOf("Hello! I am your AI companion.")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Chat History
        Column(modifier = Modifier.weight(1f)) {
            for (message in chatLog) {
                Text(text = message, modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        // Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask something...") }
            )
            Button(onClick = {
                if (textInput.isNotBlank()) {
                    chatLog = chatLog + "You: $textInput"
                    chatLog = chatLog + "AI: Processing..."
                    textInput = ""
                }
            }) {
                Text("Send")
            }
        }
    }
}

