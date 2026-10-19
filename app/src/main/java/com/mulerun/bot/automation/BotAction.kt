package com.mulerun.bot.automation

sealed interface BotAction {
    val summary: String
    val needsConfirmation: Boolean

    data class OpenApp(val appName: String) : BotAction {
        override val summary = "Open $appName"
        override val needsConfirmation = false
    }

    data class Search(val destination: Destination, val query: String) : BotAction {
        override val summary = "Search ${destination.label} for “$query”"
        override val needsConfirmation = false
    }

    data class Call(val number: String) : BotAction {
        override val summary = "Open the dialer for $number"
        override val needsConfirmation = true
    }

    data class Message(val recipient: String, val body: String) : BotAction {
        override val summary = "Draft a message to $recipient: “$body”"
        override val needsConfirmation = true
    }

    data class WhatsApp(val recipient: String, val body: String) : BotAction {
        override val summary = "Prepare a WhatsApp message to $recipient: “$body”"
        override val needsConfirmation = true
    }

    data class Email(val recipient: String, val subject: String, val body: String) : BotAction {
        override val summary = "Draft an email to $recipient about “$subject”"
        override val needsConfirmation = true
    }

    data class Schedule(val triggerAtMillis: Long, val request: String) : BotAction {
        override val summary = "Schedule: $request"
        override val needsConfirmation = true
    }

    data class ScreenClick(val targetText: String) : BotAction {
        override val summary = "Tap the visible screen item “$targetText”"
        override val needsConfirmation = true
    }

    data class ScreenScroll(val direction: String) : BotAction {
        override val summary = "Scroll the current screen ${direction.lowercase()}"
        override val needsConfirmation = false
    }

    data class ScreenType(val text: String) : BotAction {
        override val summary = "Enter text into the currently focused field: “$text”"
        override val needsConfirmation = true
    }

    data object ScreenBack : BotAction {
        override val summary = "Press Back in the current app"
        override val needsConfirmation = true
    }
}

enum class Destination(val label: String) {
    GOOGLE("Google"), YOUTUBE("YouTube"), INSTAGRAM("Instagram")
}

sealed interface ParsedCommand {
    data class Action(val value: BotAction) : ParsedCommand
    data class Conversation(val text: String) : ParsedCommand
}
