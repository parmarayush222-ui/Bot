package com.mulerun.bot.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mulerun.bot.ai.GeminiClient
import com.mulerun.bot.ai.AiResponse
import com.mulerun.bot.automation.ActionExecutor
import com.mulerun.bot.automation.BotAction
import com.mulerun.bot.automation.CommandParser
import com.mulerun.bot.automation.ParsedCommand
import com.mulerun.bot.data.BotProfile
import com.mulerun.bot.data.BotSettings
import com.mulerun.bot.data.BotSettingsStore
import com.mulerun.bot.service.BotAccessibilityService
import com.mulerun.bot.service.BotCommandBus
import com.mulerun.bot.service.WakeWordService
import com.mulerun.bot.voice.BotSpeaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MessageAuthor { USER, BOT }
data class ChatMessage(val author: MessageAuthor, val text: String, val id: Long = System.nanoTime())

data class BotUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage(MessageAuthor.BOT, "I'm ready. Ask for advice, say what is on your mind, or give me a command.")
    ),
    val pendingAction: BotAction? = null,
    val isThinking: Boolean = false,
    val settings: BotSettings = BotSettings(),
    val profiles: List<BotProfile> = emptyList(),
    val voices: List<String> = emptyList(),
    val screenPreview: String = "",
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = BotSettingsStore(application)
    private val parser = CommandParser()
    private val executor = ActionExecutor(application)
    private val gemini = GeminiClient()
    private val speaker = BotSpeaker(application)
    private val _state = MutableStateFlow(BotUiState())
    val state: StateFlow<BotUiState> = _state.asStateFlow()

    init {
        refreshSettings()
        viewModelScope.launch {
            BotCommandBus.commands.collect(::submit)
        }
        viewModelScope.launch {
            BotAccessibilityService.visibleScreenText.collect { text ->
                _state.update { it.copy(screenPreview = text) }
            }
        }
    }

    fun submit(rawText: String) {
        val text = rawText.trim()
        if (text.isBlank() || _state.value.isThinking) return
        addMessage(MessageAuthor.USER, text)
        when (val parsed = parser.parse(text)) {
            is ParsedCommand.Action -> handleAction(parsed.value)
            is ParsedCommand.Conversation -> askGemini(parsed.text)
        }
    }

    private fun handleAction(action: BotAction) {
        if (action.needsConfirmation) {
            _state.update { it.copy(pendingAction = action) }
            reply("Please review this before I continue: ${action.summary}")
        } else execute(action)
    }

    fun confirmPendingAction() {
        val action = _state.value.pendingAction ?: return
        _state.update { it.copy(pendingAction = null) }
        execute(action)
    }

    fun cancelPendingAction() {
        _state.update { it.copy(pendingAction = null) }
        reply("Cancelled. Nothing was sent or changed.")
    }

    private fun execute(action: BotAction) {
        val result = executor.execute(action)
        reply(result.getOrElse { "I couldn't complete that: ${it.message ?: "unknown error"}" })
    }

    fun analyzeScreen() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            reply("Screenshot analysis requires Android 11 or newer. Screen text and accessible screen actions may still work.")
            return
        }
        val service = BotAccessibilityService.instance
        if (service == null) {
            reply("Enable Bot's Accessibility service first. Then tap Analyze screen again.")
            return
        }
        if (_state.value.isThinking) return
        addMessage(MessageAuthor.USER, "Analyze my current screen")
        _state.update { it.copy(isThinking = true) }
        viewModelScope.launch {
            val image = runCatching { service.captureScreenshotBase64() }.getOrNull()
            if (image.isNullOrBlank()) {
                _state.update { it.copy(isThinking = false) }
                reply("I couldn't capture the screen. Check that Bot's Accessibility service is enabled and try again.")
                return@launch
            }
            val settings = store.settings()
            val profile = store.activeProfile()
            val result = gemini.reply(
                apiKey = profile.apiKey,
                provider = profile.provider,
                model = profile.model,
                baseUrl = profile.baseUrl,
                userText = "Describe the visible screen, summarize useful information, and suggest what the user can do next. Do not click or submit anything.",
                relationship = settings.relationship,
                screenText = null,
                imageBase64 = image,
            )
            _state.update { it.copy(isThinking = false) }
            val response = result.getOrElse {
                reply("Screen analysis failed: ${it.message ?: "unknown error"}. Ensure the selected model supports image input.")
                return@launch
            }
            reply(response.text ?: "The selected model returned no screen description. Make sure it supports vision input.")
        }
    }

    private fun askGemini(text: String) {
        _state.update { it.copy(isThinking = true) }
        viewModelScope.launch {
            val settings = store.settings()
            val screen = if (settings.screenAwarenessEnabled && settings.includeScreenInAi) {
                _state.value.screenPreview.takeIf { it.isNotBlank() }
            } else null
            val result = gemini.reply(
                apiKey = store.activeProfile().apiKey,
                provider = store.activeProfile().provider,
                model = store.activeProfile().model,
                baseUrl = store.activeProfile().baseUrl,
                userText = text,
                relationship = settings.relationship,
                screenText = screen,
            )
            _state.update { it.copy(isThinking = false) }
            val response = result.getOrElse {
                reply("I couldn't reach ${store.activeProfile().provider}: ${it.message ?: "unknown error"}")
                return@launch
            }
            val action = response.toBotAction()
            if (action != null) {
                handleAction(action)
            } else {
                reply(response.text ?: "I couldn't form a response. Please try again.")
            }
        }
    }

    private fun AiResponse.toBotAction(): BotAction? {
        val args = arguments ?: return null
        return when (toolName) {
            "open_app" -> args.optString("app_name").takeIf { it.isNotBlank() }?.let { BotAction.OpenApp(it) }
            "search_google" -> args.optString("query").takeIf { it.isNotBlank() }?.let { BotAction.Search(com.mulerun.bot.automation.Destination.GOOGLE, it) }
            "search_youtube" -> args.optString("query").takeIf { it.isNotBlank() }?.let { BotAction.Search(com.mulerun.bot.automation.Destination.YOUTUBE, it) }
            "search_instagram" -> args.optString("query").takeIf { it.isNotBlank() }?.let { BotAction.Search(com.mulerun.bot.automation.Destination.INSTAGRAM, it) }
            "dial_number" -> args.optString("number").takeIf { it.isNotBlank() }?.let { BotAction.Call(it) }
            "dial_contact" -> args.optString("contact_name").takeIf { it.isNotBlank() }?.let { BotAction.Call(it) }
            "draft_sms" -> {
                val recipient = args.optString("recipient")
                val body = args.optString("message")
                if (recipient.isNotBlank() && body.isNotBlank()) BotAction.Message(recipient, body) else null
            }
            "draft_whatsapp" -> {
                val recipient = args.optString("recipient")
                val body = args.optString("message")
                if (recipient.isNotBlank() && body.isNotBlank()) BotAction.WhatsApp(recipient, body) else null
            }
            "draft_email" -> {
                val recipient = args.optString("recipient")
                val subject = args.optString("subject")
                val body = args.optString("body")
                if (recipient.contains("@") && subject.isNotBlank()) BotAction.Email(recipient, subject, body) else null
            }
            "schedule_reminder" -> {
                val minutes = args.optLong("minutes", -1)
                val request = args.optString("request")
                if (minutes > 0 && minutes <= 60L * 24L * 30L && request.isNotBlank()) {
                    BotAction.Schedule(System.currentTimeMillis() + minutes * 60_000L, request)
                } else null
            }
            "click_screen_text" -> args.optString("target").takeIf { it.isNotBlank() }?.let { BotAction.ScreenClick(it) }
            "scroll_screen" -> args.optString("direction").takeIf { it.equals("up", true) || it.equals("down", true) }?.let { BotAction.ScreenScroll(it) }
            "type_screen_text" -> args.optString("text").takeIf { it.isNotBlank() }?.let { BotAction.ScreenType(it) }
            "press_back" -> BotAction.ScreenBack
            else -> null
        }
    }

    private fun reply(text: String) {
        addMessage(MessageAuthor.BOT, text)
        speaker.selectVoice(store.settings().voiceName)
        speaker.speak(text)
    }

    private fun addMessage(author: MessageAuthor, text: String) {
        _state.update { it.copy(messages = (it.messages + ChatMessage(author, text)).takeLast(60)) }
    }

    fun saveProfile(name: String, apiKey: String, provider: String, model: String, baseUrl: String) {
        store.saveProfile(name, apiKey, provider, model, baseUrl)
        refreshSettings()
        reply("Profile saved. The API key is encrypted on this device.")
    }

    fun selectProfile(name: String) {
        store.selectProfile(name)
        refreshSettings()
    }

    fun setWakeWord(enabled: Boolean) {
        store.update(wakeWordEnabled = enabled)
        if (enabled) {
            ContextCompat.startForegroundService(
                getApplication(), Intent(getApplication(), WakeWordService::class.java)
            )
        } else getApplication<Application>().stopService(Intent(getApplication(), WakeWordService::class.java))
        refreshSettings()
    }

    fun setScreenAwareness(enabled: Boolean) {
        store.update(screenAwarenessEnabled = enabled)
        if (!enabled) BotAccessibilityService.visibleScreenText.value = ""
        refreshSettings()
    }

    fun setIncludeScreenInAi(enabled: Boolean) {
        store.update(includeScreenInAi = enabled)
        refreshSettings()
    }

    fun setRelationship(value: String) {
        store.update(relationship = value)
        refreshSettings()
    }

    fun setVoice(name: String?) {
        store.update(voiceName = name)
        speaker.selectVoice(name)
        refreshSettings()
    }

    fun refreshVoices() {
        _state.update { it.copy(voices = speaker.availableVoices()) }
    }

    private fun refreshSettings() {
        _state.update { it.copy(settings = store.settings(), profiles = store.profiles()) }
    }

    override fun onCleared() {
        speaker.shutdown()
        super.onCleared()
    }
}
