package com.mulerun.bot.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

data class StoredSchedule(val id: Int, val triggerAtMillis: Long, val request: String)

object ScheduleStore {
    private const val PREFS = "bot_scheduled_tasks"
    private const val KEY = "tasks"

    @Synchronized fun all(context: Context): List<StoredSchedule> = runCatching {
        val json = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            StoredSchedule(item.optInt("id"), item.optLong("at"), item.optString("request"))
        }
    }.getOrDefault(emptyList())

    @Synchronized fun put(context: Context, item: StoredSchedule) {
        val tasks = all(context).filterNot { it.id == item.id }.toMutableList()
        tasks += item
        save(context, tasks)
    }

    @Synchronized fun remove(context: Context, id: Int) = save(context, all(context).filterNot { it.id == id })

    @Synchronized private fun save(context: Context, tasks: List<StoredSchedule>) {
        val json = JSONArray()
        tasks.forEach { json.put(JSONObject().put("id", it.id).put("at", it.triggerAtMillis).put("request", it.request)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json.toString()).apply()
    }

    fun schedule(context: Context, item: StoredSchedule) {
        val intent = Intent(context, ScheduledActionReceiver::class.java).apply {
            putExtra(ScheduledActionReceiver.EXTRA_REQUEST, item.request)
            putExtra(ScheduledActionReceiver.EXTRA_ID, item.id)
        }
        val pending = PendingIntent.getBroadcast(context, item.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 1_000), pending)
    }
}
