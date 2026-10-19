package com.mulerun.bot.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import android.net.Uri
import com.mulerun.bot.service.BotAccessibilityService
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ActionExecutor(private val context: Context) {
    fun execute(action: BotAction): Result<String> = runCatching {
        when (action) {
            is BotAction.OpenApp -> openApp(action.appName)
            is BotAction.Search -> openSearch(action.destination, action.query)
            is BotAction.Call -> {
                val number = resolvePhone(action.number)
                launch(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
                "Dialer opened. You control the final call."
            }
            is BotAction.Message -> {
                val recipient = resolvePhone(action.recipient)
                val uri = Uri.fromParts("smsto", recipient, null)
                launch(Intent(Intent.ACTION_SENDTO, uri).putExtra("sms_body", action.body))
                "Message draft opened. Review it before sending."
            }
            is BotAction.WhatsApp -> {
                val recipient = resolvePhone(action.recipient).filter { it.isDigit() || it == '+' }
                require(recipient.count { it.isDigit() } >= 7) { "I couldn't resolve a valid phone number for ${action.recipient}. Use the full international number if needed." }
                val encoded = URLEncoder.encode(action.body, StandardCharsets.UTF_8.toString())
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${recipient.trimStart('+')}?text=$encoded")))
                "WhatsApp opened with your draft. Review the recipient and message before sending."
            }
            is BotAction.Email -> {
                val uri = Uri.fromParts("mailto", action.recipient, null)
                launch(Intent(Intent.ACTION_SENDTO, uri).apply {
                    putExtra(Intent.EXTRA_SUBJECT, action.subject)
                    putExtra(Intent.EXTRA_TEXT, action.body)
                })
                "Email draft opened. Review it before sending."
            }
            is BotAction.Schedule -> schedule(action)
            is BotAction.ScreenClick -> {
                val service = BotAccessibilityService.instance ?: error("Enable Bot's Accessibility service in Android Settings first.")
                if (service.clickVisibleText(action.targetText)) "Tapped ${action.targetText}." else error("I couldn't find a clickable visible item matching ${action.targetText}.")
            }
            is BotAction.ScreenScroll -> {
                val service = BotAccessibilityService.instance ?: error("Enable Bot's Accessibility service in Android Settings first.")
                if (service.scroll(action.direction)) "Scrolled ${action.direction}." else error("I couldn't find a scrollable area on this screen.")
            }
            is BotAction.ScreenType -> {
                val service = BotAccessibilityService.instance ?: error("Enable Bot's Accessibility service in Android Settings first.")
                if (service.typeIntoFocusedField(action.text)) "Entered the requested text. Review the field before submitting anything." else error("Focus a visible editable field first, then try again.")
            }
            is BotAction.ScreenBack -> {
                val service = BotAccessibilityService.instance ?: error("Enable Bot's Accessibility service in Android Settings first.")
                if (service.pressBack()) "Pressed Back." else error("Android couldn't perform Back on this screen.")
            }
        }
    }

    private fun openApp(requestedName: String): String {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = context.packageManager.queryIntentActivities(launcherIntent, 0)
        val match = matches.firstOrNull {
            it.loadLabel(context.packageManager).toString().contains(requestedName, ignoreCase = true)
        } ?: error("I couldn't find an installed app named $requestedName.")
        val intent = context.packageManager.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: error("$requestedName cannot be launched.")
        launch(intent)
        return "Opened ${match.loadLabel(context.packageManager)}."
    }

    private fun resolvePhone(value: String): String {
        if (value.any { it.isDigit() } && value.count { it.isDigit() } >= 7) return value
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            error("To find contacts by name, grant Bot the Contacts permission in Android Settings, then try again.")
        }
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$value%"),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                if (cursor.count > 1) {
                    val matches = mutableListOf<Pair<String, String>>()
                    do {
                        matches += cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) to
                            cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    } while (cursor.moveToNext())
                    val unique = matches.distinctBy { it.second }
                    if (unique.size > 1) error("More than one contact matches '$value': ${unique.take(5).joinToString { it.first }}. Use a more specific name or number.")
                }
                return number
            }
        }
        error("I couldn't find a phone number for '$value'. Try the full phone number or check Contacts permission.")
    }

    private fun openSearch(destination: Destination, query: String): String {
        val q = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val url = when (destination) {
            Destination.GOOGLE -> "https://www.google.com/search?q=$q"
            Destination.YOUTUBE -> "https://www.youtube.com/results?search_query=$q"
            Destination.INSTAGRAM -> "https://www.instagram.com/explore/search/keyword/?q=$q"
        }
        launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        return "Opened ${destination.label} results for $query."
    }

    private fun schedule(action: BotAction.Schedule): String {
        val id = (action.triggerAtMillis xor action.request.hashCode().toLong()).toInt()
        val item = StoredSchedule(id, action.triggerAtMillis, action.request)
        ScheduleStore.put(context, item)
        ScheduleStore.schedule(context, item)
        return "Scheduled and saved across device restarts. Bot will notify you when it is due; sensitive actions still need approval."
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) error("No compatible app is installed.")
        context.startActivity(intent)
    }
}
