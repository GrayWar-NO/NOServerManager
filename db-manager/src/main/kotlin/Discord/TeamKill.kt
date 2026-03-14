package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.proto.KillLog
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kordex.core.extensions.Extension

class TeamKillExtension(val channel: ULong): Extension(){
    override val name = "teamKills"
    override suspend fun setup(){}
    suspend fun sendTeamKill(log: KillLog, playerName: String){
        val discordChannel = kord.getChannel(Snowflake(channel)) as MessageChannelBehavior
        val content = "`$playerName[${log.killerUnit}]:${log.killer.toULong()} teamkilled ${log.killedUnit} with ${log.weapon}`"
        discordChannel.createMessage(content)
    }
}
