package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.proto.KillLog
import com.graywar.noServerManager.proto.ServerReport
import dev.kord.common.entity.Snowflake
import dev.kord.rest.builder.message.allowedMentions
import dev.kordex.core.extensions.Extension

class TeamKillExtension(val url: String, val tkFormat: LogFormat, val reportFormat: LogFormat, val pingRole: Snowflake) : Extension() {
    override val name = "teamKills"

    private lateinit var sender: WebhookSender

    override suspend fun setup() {
        sender = WebhookSender(kord, url, "Teamkills Reporter")
    }

    suspend fun sendTeamKill(log: KillLog, killerName: String, killedName: String, server: String) {
        val string = TemplateString(tkFormat)

        val argMap: Map<String, String> = mapOf(
            "server" to server,
            "killerName" to killerName,
            "killedName" to killedName,
            "weapon" to log.weapon,
            "killerUnit" to log.killerUnit,
            "killedUnit" to log.killedUnit,
            "killerSteamID" to log.killer.toString(),
            "killedSteamID" to log.killed.toString()
        )

        sender.send {
            content = string.format(argMap)
        }
    }

    suspend fun sendReport(log: ServerReport, server: String) {
        val text = TemplateString(reportFormat)
        val argMap: Map<String, String> = mapOf(
            "server" to server,
            "roleID" to pingRole.value.toString(),
            "message" to log.content,
            "senderName" to log.username,
        )
        sender.send {
            username = "${log.username} reported on $server"
            content = text.format(argMap)
            allowedMentions {
                users.clear()
                roles.clear()
                roles.add(Snowflake(pingRole.value))
                repliedUser = false
            }
        }
    }

}
