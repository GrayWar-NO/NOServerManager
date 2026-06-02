package com.graywar.noServerManager.dbManager.Discord

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WebhookPayload(
    val username: String? = null,
    val content: String
)

class WebhookSender(
    private val webhookUrl: String,
    private val username: String
) {
    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun send(content: String) {
        val payload = WebhookPayload(
            username = username,
            content = content
        )
        send(payload)
    }

    suspend fun send(payload: WebhookPayload) {
        try {
            client.post(webhookUrl) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(payload))
            }
        } catch (e: Exception) {
            println("Failed to send webhook message: ${e.message}")
        }
    }
}
