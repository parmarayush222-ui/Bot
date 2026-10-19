package com.mulerun.bot.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    state: BotUiState,
    onSubmit: (String) -> Unit,
    onStartVoice: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onAnalyzeScreen: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.pendingAction) {
        val count = state.messages.size + if (state.pendingAction != null) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 18.dp),
    ) {
        HeroStatus(state.settings.wakeWordEnabled)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onAnalyzeScreen, modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)) {
                Text("ANALYZE SCREEN", color = Lime)
            }
            Text("Uploads a screenshot to your selected AI provider", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterVertically))
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            items(listOf(
                "Help me plan my day",
                "Open YouTube",
                "Google latest Android news",
                "What is visible on my screen?",
            )) { suggestion ->
                TextButton(
                    onClick = { onSubmit(suggestion) },
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                ) { Text(suggestion, color = Cream) }
            }
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            items(state.messages, key = { it.id }) { MessageBubble(it) }
            if (state.isThinking) item { ThinkingBubble() }
            state.pendingAction?.let { action ->
                item { ConfirmationCard(action.summary, onConfirm, onCancel) }
            }
            item { Spacer(Modifier.height(6.dp)) }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Talk to Bot…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (input.isNotBlank()) onSubmit(input).also { input = "" }
                }),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onStartVoice,
                shape = CircleShape,
                contentPadding = ButtonDefaults.ContentPadding,
                modifier = Modifier.size(58.dp),
            ) { Text("TALK", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun HeroStatus(alwaysListening: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("BOT", style = MaterialTheme.typography.titleMedium, color = Lime)
            Text("Here with you.", style = MaterialTheme.typography.displayLarge, color = Cream)
            Text(
                if (alwaysListening) "WAKE WORD ACTIVE · SAY “BOT”" else "TAP TALK OR TYPE A REQUEST",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
        VoiceOrb(alwaysListening)
    }
}

@Composable
private fun VoiceOrb(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "voice")
    val pulse by transition.animateFloat(
        initialValue = .25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_000), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(68.dp).clip(CircleShape)
            .background(if (active) Lime else Panel),
    ) {
        Canvas(Modifier.size(38.dp)) {
            repeat(5) { index ->
                val x = size.width * (.18f + index * .16f)
                val factor = if (active) pulse else .35f
                val half = size.height * factor * (if (index % 2 == 0) .24f else .4f)
                drawLine(
                    color = if (active) Ink else Muted,
                    start = Offset(x, center.y - half),
                    end = Offset(x, center.y + half),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val user = message.author == MessageAuthor.USER
    Row(
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(.88f)
                .clip(RoundedCornerShape(
                    topStart = 20.dp, topEnd = 20.dp,
                    bottomStart = if (user) 20.dp else 4.dp,
                    bottomEnd = if (user) 4.dp else 20.dp,
                ))
                .background(if (user) Lime else Panel)
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            Text(message.text, color = if (user) Ink else Cream)
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Lime)
        Spacer(Modifier.width(10.dp))
        Text("Thinking with care…", color = Muted)
    }
}

@Composable
private fun ConfirmationCard(summary: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Rust),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("CONFIRM ACTION", style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(summary, style = MaterialTheme.typography.headlineSmall, color = Ink)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                    Text("Continue", color = Cream)
                }
                TextButton(onClick = onCancel) { Text("Cancel", color = Ink) }
            }
        }
    }
}
