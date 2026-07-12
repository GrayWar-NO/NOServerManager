package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.dbManager.EdgeAgentServiceImpl
import com.graywar.noServerManager.proto.StatusResponse
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.EmbedBuilder.Companion.ZERO_WIDTH_SPACE
import dev.kord.rest.builder.message.embed
import dev.kordex.core.extensions.Extension
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

class Status(val db: DB, val channelID: Snowflake, val guildID: Snowflake, val grpc: EdgeAgentServiceImpl) :
    Extension() {
    override val name: String = "Server status"

    var message: Message? = null

    override suspend fun setup() {
        val guild = kord.getGuild(guildID)
        val channel = guild.getChannel(channelID) as? TextChannel ?: return
        val data = grpc.getAllServerStatuses()
        message = channel.createEmbed { serverStatus(data) }

        kord.launch {
            while (isActive) {
                delay(5.minutes)
                try {
                    update()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }


    suspend fun update() {
        val data = grpc.getAllServerStatuses()
        message = message?.edit { embed { serverStatus(data) } }
    }

    fun EmbedBuilder.serverStatus(data: Map<String, StatusResponse>) {
        title = "GrayWar servers status"

        for ((name, status) in data) {
            field {
                this.name = name
                if (status.ok)
                    this.value = "✅ ${status.playerNumber}/${status.maxPlayers}"
                else
                    this.value = "❌ 0/0"
                inline = false
            }
            field {
                this.name = "Current mission - started"
                this.value = if (status.missionName == null)
                    ZERO_WIDTH_SPACE
                else {
                    "${status.missionName} - <t:${status.missionStart.seconds}:R>"
                }
                inline = true
            }
            field {
                this.name = "last restarted"
                this.value = "<t:${status.lastRestart.seconds}:R>"
            }
        }
    }

}