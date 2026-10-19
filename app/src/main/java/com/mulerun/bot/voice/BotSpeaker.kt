package com.mulerun.bot.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class BotSpeaker(context: Context) : TextToSpeech.OnInitListener {
    private var ready = false
    private var pending: String? = null
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.getDefault()
            pending?.let(::speak)
            pending = null
        }
    }

    fun availableVoices(): List<String> = if (ready) {
        tts.voices.orEmpty().map { it.name }.sorted()
    } else emptyList()

    fun selectVoice(name: String?) {
        if (!ready || name == null) return
        tts.voices?.firstOrNull { it.name == name }?.let { tts.voice = it }
    }

    fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        tts.speak(text.take(2_500), TextToSpeech.QUEUE_FLUSH, null, "bot_reply")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
