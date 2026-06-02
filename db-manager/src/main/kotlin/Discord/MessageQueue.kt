package com.graywar.noServerManager.dbManager.Discord

import dev.kord.core.behavior.channel.MessageChannelBehavior
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MessageQueue(val channel: MessageChannelBehavior) {
    private val queue = mutableListOf<String>()
    private val mutex = Mutex()

    internal suspend fun flush() {
        var consolidated: String? = null

        mutex.withLock {
            while (queue.isNotEmpty()) {
                if (consolidated == null) {
                    consolidated = queue.removeFirst() + "\n"
                } else consolidated += queue.removeFirst() + "\n"
            }
        }
        if (consolidated != null)  {
            try {
                channel.createMessage(consolidated)
            } catch (e: Exception) {
                println("Failed to send message to channel ${channel.id}: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    internal suspend fun add(data: String) { mutex.withLock { queue.add(data) } }
}
