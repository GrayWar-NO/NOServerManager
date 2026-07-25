package com.graywar.noServerManager.dbManager.Discord

import dev.kord.common.entity.DiscordWebhook
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.rest.builder.message.allowedMentions
import dev.kord.rest.builder.message.create.WebhookMessageCreateBuilder
import kotlin.require
import kotlinx.coroutines.runBlocking

class WebhookSender(
    private val kord: Kord,
    private val webhookUrl: String,
    private val username: String
) {

    var webhook: DiscordWebhook

    init {
        val parts = webhookUrl.split("/")
        require(parts.size == 7) { "Invalid webhook URL format: $webhookUrl" }

        val webhookId = parts[5].toLong()
        val token = parts[6]
        webhook = runBlocking { kord.rest.webhook.getWebhookWithToken(Snowflake(webhookId), token) }
    }

    suspend fun send(content: String) {
        val name = username
        send {
            this.username = name
            this.content = escapeDiscordMarkdown(content)
            allowedMentions {
                users.clear()
                roles.clear()
                repliedUser = false
            }
        }
    }

    suspend fun send(username: String, content: String) {
        send {
            this.username = username
            this.content = escapeDiscordMarkdown(content)
            allowedMentions {
                users.clear()
                roles.clear()
                repliedUser = false
            }
        }
    }

    suspend fun send(payload: WebhookMessageCreateBuilder.() -> Unit) {
        try {
            kord.rest.webhook.executeWebhook(webhook.id, webhook.token.value!!, builder = payload)
        } catch (e: Exception) {
            println("Failed to send webhook message: ${e.message}")
        }
    }
}
