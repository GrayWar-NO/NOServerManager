package com.graywar.noServerManager.dbManager.Discord

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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.isDistantPast

class Status(
    val config: StatusConfig,
    val guildID: Snowflake,
    val grpc: EdgeAgentServiceImpl,
    allServers: Map<Int, String>
) :
    Extension() {
    override val name: String = "Server status"
    val channelID = Snowflake(config.channel)

    val servers = allServers.asSequence()
        .filter { (key, _) -> key !in config.excludedServers }
        .sortedBy { (key, _) -> key }
        .map { (_, value) -> value }
        .toList()

    var message: Message? = null


    override suspend fun setup() {
        val guild = kord.getGuild(guildID)
        val channel = guild.getChannel(channelID) as? TextChannel ?: return
        message = channel.createEmbed { serverStatus() }

        kord.launch {
            while (isActive) {
                delay(config.updateRateSeconds.seconds)
                try {
                    update()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun update() {
        message = message?.edit { embed { serverStatus() } }
    }

    fun getData(): List<Triple<String, Instant, StatusResponse?>> = buildList(servers.size) {
        for (server in servers) {
            val serverData = grpc.serverStatuses[server]
            if (serverData == null) {
                add(Triple(server, Instant.DISTANT_PAST, null))
                continue
            }

            if (serverData.first < Clock.System.now()
                    .minus(config.statusTimeoutSeconds.seconds)
            ) { // Received the data before the current timeout window start
                val resp = StatusResponse.newBuilder().setOk(false)
                if (serverData.second != null) {
                    resp
                        .setMissionStart(serverData.second!!.missionStart)
                        .setLastRestart(serverData.second!!.lastRestart)
                        .setPlayerNumber(0)
                        .setMaxPlayers(serverData.second!!.maxPlayers)
                        .setMissionName(serverData.second!!.missionName)
                }
                add(Triple(server, serverData.first, resp.build()))
            } else
                add(Triple(server, serverData.first, serverData.second))
        }

    }

    fun EmbedBuilder.serverStatus() {
        title = "Servers status"

        for ((name, time, status) in getData()) {
            field {
                this.name = name
                if (status != null)
                    if (status.ok)
                        this.value = "✅ ${status.playerNumber}/${status.maxPlayers}"
                    else
                        this.value = "❌ ${status.playerNumber}/${status.maxPlayers}"
                else
                    this.value = "❌ 0/0"
                inline = true
            }
            field {
                this.name = "Current mission - started"
                this.value = if (status?.missionName == null)
                    ZERO_WIDTH_SPACE
                else {
                    "${status.missionName} - <t:${status.missionStart.seconds}:R>"
                }
                inline = true
            }
            field {
                val timeString = if (time.isDistantPast) "never" else "<t:${time.epochSeconds}:R>"
                val restartString = if (status == null) "never" else "<t:${status.lastRestart.seconds}:R>"

                this.name = "last restarted - last status received"
                this.value = "$restartString - $timeString"
                this.inline = true
            }
        }
    }

    override suspend fun unload() {
        message?.delete()
        super.unload()
    }

}