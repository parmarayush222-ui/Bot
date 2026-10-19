package com.mulerun.bot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    state: BotUiState,
    onSaveProfile: (String, String, String, String, String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onWakeWord: (Boolean) -> Unit,
    onScreenAwareness: (Boolean) -> Unit,
    onIncludeScreen: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onRelationship: (String) -> Unit,
    onVoice: (String?) -> Unit,
    onRefreshVoices: () -> Unit,
    onRequestContacts: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    var profileName by remember { mutableStateOf(state.settings.activeProfile) }
    var apiKey by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("NVIDIA NIM") }
    var model by remember { mutableStateOf("meta/llama-3.1-8b-instruct") }
    var baseUrl by remember { mutableStateOf("https://integrate.api.nvidia.com/v1") }
    LaunchedEffect(Unit) { onRefreshVoices() }
    LaunchedEffect(state.settings.activeProfile, state.profiles) {
        val active = state.profiles.firstOrNull { it.name == state.settings.activeProfile }
        if (active != null) {
            provider = active.provider
            model = active.model
            baseUrl = active.baseUrl
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
    ) {
        Text("SETTINGS", style = MaterialTheme.typography.titleMedium, color = Lime)
        Text("Private by design.", style = MaterialTheme.typography.displayLarge)
        Text(
            "Microphone, screen context, and cloud sharing are controlled separately.",
            color = Muted,
        )

        SettingCard("VOICE ACTIVATION", "Hands-free wake word") {
            ToggleRow(
                title = "Listen for “Bot”",
                detail = "Runs a visible foreground service. Android may pause it under battery restrictions.",
                checked = state.settings.wakeWordEnabled,
                onChecked = onWakeWord,
            )
        }

        SettingCard("REMINDERS", "Allow notifications so scheduled reminders can reach you") {
            Text("Scheduled tasks are saved across restarts, but Android notification permission and battery settings affect delivery.", color = Muted)
            Button(onClick = onRequestNotifications) { Text("Grant notification permission") }
        }

        SettingCard("CONTACTS & PHONE ACTIONS", "Permission is only used to resolve the contact name you request") {
            Text("Bot can look up a saved contact to prepare a call, SMS, or WhatsApp draft. It will not send a message automatically.", color = Muted)
            Button(onClick = onRequestContacts) { Text("Grant Contacts permission") }
        }

        SettingCard("SCREEN AWARENESS", "Accessibility permission") {
            ToggleRow(
                title = "Read visible screen text",
                detail = "Password fields are excluded. Data stays on-device unless cloud sharing is enabled.",
                checked = state.settings.screenAwarenessEnabled,
                onChecked = onScreenAwareness,
            )
            TextButton(onClick = onOpenAccessibility) {
                Text("Open Android accessibility settings", color = Lime)
            }
            ToggleRow(
                title = "Share screen context with the selected AI provider",
                detail = "Only use this when you understand what visible text may be uploaded.",
                checked = state.settings.includeScreenInAi,
                enabled = state.settings.screenAwarenessEnabled,
                onChecked = onIncludeScreen,
            )
            if (state.screenPreview.isNotBlank()) {
                Text("CURRENT LOCAL PREVIEW", style = MaterialTheme.typography.labelSmall, color = Muted)
                Text(
                    state.screenPreview.take(350),
                    modifier = Modifier.fillMaxWidth().background(Ink, RoundedCornerShape(12.dp)).padding(12.dp),
                    color = Cream,
                )
            }
        }

        SettingCard("AI PROFILES", "Gemini, NVIDIA NIM, or another OpenAI-compatible API") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                state.profiles.forEach { profile ->
                    val selected = profile.name == state.settings.activeProfile
                    Text(
                        profile.name,
                        color = if (selected) Ink else Cream,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(if (selected) Lime else Panel, RoundedCornerShape(30.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(30.dp))
                            .clickable { onSelectProfile(profile.name) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it },
                label = { Text("Profile name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("AI PROVIDER", color = Muted, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("NVIDIA NIM", "Gemini", "OpenAI-compatible").forEach { option ->
                    Text(
                        option,
                        color = if (provider == option) Ink else Cream,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(if (provider == option) Lime else Panel, RoundedCornerShape(24.dp))
                            .clickable {
                                provider = option
                                when (option) {
                                    "Gemini" -> { model = "gemini-2.5-flash"; baseUrl = "https://integrate.api.nvidia.com/v1" }
                                    "NVIDIA NIM" -> { model = "meta/llama-3.1-8b-instruct"; baseUrl = "https://integrate.api.nvidia.com/v1" }
                                    else -> { model = ""; baseUrl = "https://api.openai.com/v1" }
                                }
                            }.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model ID") },
                supportingText = { Text("Use the exact model ID enabled for your provider account.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (provider != "Gemini") {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API base URL") },
                    supportingText = { Text("Must be HTTPS and provide /chat/completions. NVIDIA NIM uses integrate.api.nvidia.com/v1.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(if (provider == "Gemini") "Gemini API key" else if (provider == "NVIDIA NIM") "NVIDIA API key" else "Provider API key") },
                supportingText = { Text("Saved encrypted on this device. Never share your API key.") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onSaveProfile(profileName, apiKey, provider, model, baseUrl)
                    apiKey = ""
                },
                enabled = profileName.isNotBlank() && apiKey.isNotBlank(),
            ) { Text("Save encrypted profile") }
        }

        SettingCard("PERSONALITY", "How Bot supports you") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                listOf("Mentor + friend", "Direct mentor", "Calm companion").forEach { option ->
                    val selected = state.settings.relationship == option
                    TextButton(
                        onClick = { onRelationship(option) },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (selected) Lime else Ink,
                            contentColor = if (selected) Ink else Cream,
                        ),
                    ) { Text(option) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text("VOICE", style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(
                state.settings.voiceName ?: "System default voice",
                maxLines = 1,
                color = Cream,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onVoice(null) }) { Text("Default", color = Lime) }
                state.voices.take(3).forEach { voice ->
                    TextButton(onClick = { onVoice(voice) }) {
                        Text(voice.substringAfterLast('-').take(12), color = Lime)
                    }
                }
                if (state.voices.isEmpty()) Text("Voices are loading…", color = Muted)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Rust),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("MVP SAFETY MODEL", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Calls open the dialer. Messages and emails open drafts. Scheduled sensitive actions notify you when due. Android or the target app keeps the final send step under your control.",
                    color = Ink,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingCard(label: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(18.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Lime)
            Text(subtitle, style = MaterialTheme.typography.headlineSmall)
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}
