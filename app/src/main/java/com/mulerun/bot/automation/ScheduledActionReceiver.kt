package com.mulerun.bot.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.mulerun.bot.MainActivity
import com.mulerun.bot.R

class ScheduledActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val request = intent.getStringExtra(EXTRA_REQUEST) ?: return
        val scheduleId = intent.getIntExtra(EXTRA_ID, request.hashCode())
        ScheduleStore.remove(context, scheduleId)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Scheduled actions", NotificationManager.IMPORTANCE_HIGH)
        )
        val openBot = PendingIntent.getActivity(
            context,
            request.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(EXTRA_REQUEST, request),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bot)
            .setContentTitle("Bot · action due")
            .setContentText(request)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Ready to continue: $request"))
            .setContentIntent(openBot)
            .setAutoCancel(true)
            .build()
        manager.notify(request.hashCode(), notification)
    }

    companion object {
        const val EXTRA_REQUEST = "scheduled_request"
        const val EXTRA_ID = "scheduled_id"
        private const val CHANNEL_ID = "bot_scheduled_actions"
    }
}
