package com.mulerun.bot.automation

import java.time.LocalDateTime
import java.time.ZoneId

class CommandParser {
    fun parse(input: String): ParsedCommand {
        val text = input.trim()
        val lower = text.lowercase()

        parseSchedule(text, lower)?.let { return ParsedCommand.Action(it) }

        Regex("^(?:open|launch|start)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.let { return ParsedCommand.Action(BotAction.OpenApp(it.groupValues[1].trim())) }

        Regex("^(?:google|search google(?: for)?|search for)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.let { return actionSearch(Destination.GOOGLE, it.groupValues[1]) }

        Regex("^(?:youtube|search youtube(?: for)?)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.let { return actionSearch(Destination.YOUTUBE, it.groupValues[1]) }

        Regex("^(?:instagram|search instagram(?: for)?)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.let { return actionSearch(Destination.INSTAGRAM, it.groupValues[1]) }

        Regex("^(?:call|dial)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.let { return ParsedCommand.Action(BotAction.Call(it.groupValues[1].trim())) }

        Regex("^(?:whatsapp|send whatsapp|message on whatsapp)\\s+(.+?)\\s+(?:saying|say|that)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.let {
                return ParsedCommand.Action(BotAction.WhatsApp(it.groupValues[1].trim(), it.groupValues[2].trim()))
            }

        Regex("^(?:message|text|sms)\\s+(.+?)\\s+(?:saying|say|that)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.let {
                return ParsedCommand.Action(BotAction.Message(it.groupValues[1].trim(), it.groupValues[2].trim()))
            }

        Regex("^email\\s+(\\S+@\\S+)\\s+(?:about|subject)\\s+(.+?)(?:\\s+(?:saying|body)\\s+(.+))?$", RegexOption.IGNORE_CASE)
            .find(text)?.let {
                return ParsedCommand.Action(BotAction.Email(
                    it.groupValues[1], it.groupValues[2], it.groupValues.getOrElse(3) { "" }
                ))
            }

        return ParsedCommand.Conversation(text)
    }

    private fun actionSearch(destination: Destination, rawQuery: String) =
        ParsedCommand.Action(BotAction.Search(destination, rawQuery.trim()))

    private fun parseSchedule(original: String, lower: String): BotAction.Schedule? {
        val relative = Regex("^in\\s+(\\d+)\\s*(minute|minutes|hour|hours)\\s+(.+)$")
            .find(lower)
        if (relative != null) {
            val amount = relative.groupValues[1].toLong()
            val millis = if (relative.groupValues[2].startsWith("hour")) {
                amount * 3_600_000
            } else amount * 60_000
            val requestStart = relative.groups[3]!!.range.first
            return BotAction.Schedule(System.currentTimeMillis() + millis, original.substring(requestStart))
        }

        val atTime = Regex("^(?:schedule\\s+)?at\\s+(\\d{1,2}):(\\d{2})\\s+(.+)$").find(lower)
        if (atTime != null) {
            val hour = atTime.groupValues[1].toIntOrNull() ?: return null
            val minute = atTime.groupValues[2].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            var dateTime = LocalDateTime.now().withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (dateTime.isBefore(LocalDateTime.now())) dateTime = dateTime.plusDays(1)
            val requestStart = atTime.groups[3]!!.range.first
            return BotAction.Schedule(
                dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                original.substring(requestStart),
            )
        }
        return null
    }
}
