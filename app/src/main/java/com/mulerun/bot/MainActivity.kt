package com.mulerun.bot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.mulerun.bot.automation.ScheduledActionReceiver
import com.mulerun.bot.service.WakeWordService
import com.mulerun.bot.ui.BotApp
import com.mulerun.bot.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var enableWakeAfterPermission = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (enableWakeAfterPermission) viewModel.setWakeWord(audioGranted)
        enableWakeAfterPermission = false
    }

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.let(viewModel::submit)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsState()
            BotApp(
                state = state,
                onSubmit = viewModel::submit,
                onStartVoice = ::startVoiceInput,
                onConfirm = viewModel::confirmPendingAction,
                onCancel = viewModel::cancelPendingAction,
                onSaveProfile = viewModel::saveProfile,
                onSelectProfile = viewModel::selectProfile,
                onWakeWord = ::changeWakeWord,
                onScreenAwareness = viewModel::setScreenAwareness,
                onIncludeScreen = viewModel::setIncludeScreenInAi,
                onOpenAccessibility = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onRequestContacts = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                },
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                },
                onRelationship = viewModel::setRelationship,
                onVoice = viewModel::setVoice,
                onRefreshVoices = viewModel::refreshVoices,
                onAnalyzeScreen = viewModel::analyzeScreen,
            )
        }
        consumeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent) {
        val command = intent.getStringExtra(WakeWordService.EXTRA_COMMAND)
            ?: intent.getStringExtra(ScheduledActionReceiver.EXTRA_REQUEST)
        command?.takeIf { it.isNotBlank() }?.let(viewModel::submit)
        intent.removeExtra(WakeWordService.EXTRA_COMMAND)
        intent.removeExtra(ScheduledActionReceiver.EXTRA_REQUEST)
    }

    private fun changeWakeWord(enabled: Boolean) {
        if (!enabled) {
            viewModel.setWakeWord(false)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.setWakeWord(true)
            return
        }
        enableWakeAfterPermission = true
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        permissionLauncher.launch(permissions)
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Bot")
        }
        runCatching { speechLauncher.launch(intent) }
            .onFailure { viewModel.submit("I want to use voice, but speech recognition is unavailable.") }
    }
}
