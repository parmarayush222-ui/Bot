package com.mulerun.bot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BotApp(
    state: BotUiState,
    onSubmit: (String) -> Unit,
    onStartVoice: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onSaveProfile: (String, String, String, String, String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onWakeWord: (Boolean) -> Unit,
    onScreenAwareness: (Boolean) -> Unit,
    onIncludeScreen: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onRelationship: (String) -> Unit,
    onVoice: (String?) -> Unit,
    onRefreshVoices: () -> Unit,
    onAnalyzeScreen: () -> Unit,
    onRequestContacts: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    BotTheme {
        Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)
                    .background(Panel, RoundedCornerShape(30.dp)).padding(4.dp),
            ) {
                NavPill("Conversation", tab == 0, Modifier.weight(1f)) { tab = 0 }
                NavPill("Controls", tab == 1, Modifier.weight(1f)) { tab = 1 }
            }
            Box(Modifier.weight(1f)) {
                if (tab == 0) {
                    HomeScreen(state, onSubmit, onStartVoice, onConfirm, onCancel, onAnalyzeScreen)
                } else {
                    SettingsScreen(
                        state, onSaveProfile, onSelectProfile, onWakeWord,
                        onScreenAwareness, onIncludeScreen, onOpenAccessibility,
                        onRelationship, onVoice, onRefreshVoices, onRequestContacts, onRequestNotifications,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavPill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(if (selected) Lime else Panel, RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
    ) {
        Text(
            label.uppercase(),
            color = if (selected) Ink else Muted,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
