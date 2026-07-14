package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.proto.KillLog
import com.graywar.noServerManager.proto.serverReport
import dev.kord.common.entity.Snowflake
import dev.kordex.core.extensions.Extension

class TeamKillExtension(val url: String, val pingRole: Snowflake): Extension(){
    override val name = "teamKills"

    private lateinit var sender: WebhookSender

    override suspend fun setup(){
        sender = WebhookSender(kord, url, "Teamkills Reporter")
    }

    suspend fun sendTeamKill(log: KillLog, killerName: String, killedName: String, server: String) {
        sender.send("```$killerName[${log.killerUnit}]:${log.killer.toULong()} teamkilled $killedName[${log.killedUnit}]:${log.killed.toULong()} with ${log.weapon} on server $server```")
    }

    suspend fun sendReport(log: serverReport, server: String) {
        sender.send{
            username = "${log.username} reported on $server"

            content = "<@${pingRole.value}> ${log.content}"
        }
    }

}
