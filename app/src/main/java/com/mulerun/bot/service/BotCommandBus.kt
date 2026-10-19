package com.mulerun.bot.service

import kotlinx.coroutines.flow.MutableSharedFlow

object BotCommandBus {
    val commands = MutableSharedFlow<String>(extraBufferCapacity = 8)
}
