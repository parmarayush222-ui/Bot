package com.mulerun.bot.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.mulerun.bot.MainActivity
import com.mulerun.bot.R
import com.mulerun.bot.voice.BotSpeaker

class WakeWordService : Service(), RecognitionListener {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private lateinit var speaker: BotSpeaker
    private var awaitingCommand = false
    private var shuttingDown = false

    override fun onCreate() {
        super.onCreate()
        speaker = BotSpeaker(this)
        createChannel()
        startForeground(NOTIFICATION_ID, baseNotification("Say “Bot” followed by a command"))
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecognition()
        } else stopSelf()
    }

    private fun startRecognition() {
        if (shuttingDown || !SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        })
    }

    private fun restart(delay: Long = 500) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ startRecognition() }, delay)
    }

    private fun handleFinal(text: String) {
        val cleaned = text.trim()
        if (awaitingCommand && cleaned.isNotBlank()) {
            awaitingCommand = false
            deliver(cleaned)
            return
        }
        val wakeMatch = Regex("(?:^|\\s)bot[,.!?]?\\s*(.*)$", RegexOption.IGNORE_CASE).find(cleaned)
            ?: return
        val command = wakeMatch.groupValues[1].trim()
        if (command.isBlank()) {
            awaitingCommand = true
            speaker.speak("I'm listening.")
        } else deliver(command)
    }

    private fun deliver(command: String) {
        BotCommandBus.commands.tryEmit(command)
        val openIntent = Intent(this, MainActivity::class.java).putExtra(EXTRA_COMMAND, command)
        val pending = PendingIntent.getActivity(
            this, command.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bot)
            .setContentTitle("Bot heard you")
            .setContentText(command)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(command.hashCode(), notification)
    }

    override fun onResults(results: Bundle?) {
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.let(::handleFinal)
        restart(350)
    }

    override fun onError(error: Int) = restart(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1_200 else 500)
    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    override fun onDestroy() {
        shuttingDown = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        speaker.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Wake word", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows when Bot is listening for its wake word"
            }
        )
    }

    private fun baseNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_bot)
        .setContentTitle("Bot is listening")
        .setContentText(text)
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ))
        .build()

    companion object {
        const val EXTRA_COMMAND = "voice_command"
        private const val CHANNEL_ID = "bot_wake_word"
        private const val NOTIFICATION_ID = 4101
    }
}
