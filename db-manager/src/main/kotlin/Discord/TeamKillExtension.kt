package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.proto.KillLog
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kordex.core.extensions.Extension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

class TeamKillExtension(val channel: ULong): Extension(){
    override val name = "teamKills"

    private lateinit var queue: MessageQueue

    override suspend fun setup(){
        queue = MessageQueue(kord.getChannel(Snowflake(channel)) as MessageChannelBehavior)
    }

    suspend fun sendTeamKill(log: KillLog, killerName: String, killedName: String, server: String) {
        queue.add("```$killerName[${log.killerUnit}]:${log.killer.toULong()} teamkilled $killedName[${log.killedUnit}]:${log.killed.toULong()} with ${log.weapon} on server $server```")
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startPeriodicSender(scope: CoroutineScope = GlobalScope) {
        scope.launch {
            while (isActive) {
                delay(1.minutes)
                queue.flush()
            }
        }
    }
}

class TeamKillWebhookExtension(val url: String): Extension(){
    override val name = "teamKills"

    private lateinit var sender: WebhookSender

    override suspend fun setup(){
        sender = WebhookSender(url, "Teamkills Reporter")
    }

    suspend fun sendTeamKill(log: KillLog, killerName: String, killedName: String, server: String) {
        sender.send("```$killerName[${log.killerUnit}]:${log.killer.toULong()} teamkilled $killedName[${log.killedUnit}]:${log.killed.toULong()} with ${log.weapon} on server $server```")
    }
}
