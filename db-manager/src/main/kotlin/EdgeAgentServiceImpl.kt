package com.graywar.noServerManager.dbManager

import com.google.protobuf.Empty
import com.graywar.noServerManager.dbManager.Discord.CallbackEvent
import com.graywar.noServerManager.dbManager.Discord.LoggedServerEvent
import com.graywar.noServerManager.proto.*
import io.grpc.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import java.util.*
import kotlin.time.Clock
import kotlin.time.Instant


class EdgeAgentServiceImpl(private val db: DB) : EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineImplBase() {
    private val banSubscribers = mutableMapOf<String, SendChannel<BanRequest>>()
    private val commandSubscribers = mutableMapOf<String, SendChannel<Command>>()
    internal var discordMessageFlows = mutableMapOf<Int, SendChannel<ChatBack>>()

    internal val serverStatuses = mutableMapOf<String, Pair<Instant, StatusResponse?>>()

    private var discordEventsCallback: (suspend (CallbackEvent) -> Unit)? = null

    private var permissionBreakdownGetter: (suspend () -> permissionBreakdown)? = null

    private val pendingCommands = mutableMapOf<String, CompletableDeferred<String>>()

    override fun sendChatLogsStream(requests: Flow<ChatLog>): Flow<ChatBack> = channelFlow {
        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
        @Suppress("KotlinPrintToLogpoint", "RedundantSuppression")
        println("[Central] $source connected to chat logs streaming.")
        val sID = db.getOrCreateServerIdFromName(source)
        discordMessageFlows[sID] = channel
        requests
            .collect { request ->
                db.storeMessage(
                    request.senderSteamID.toULong(),
                    request.messageSendTime,
                    request.messageChannel,
                    db.getCurrentMissionIDForServer(sID),
                    request.message
                )
                discordEventsCallback?.invoke(CallbackEvent.ServerEvent(LoggedServerEvent.ChatEvent(request), sID))
            }

        awaitClose {
            discordMessageFlows.remove(sID)
            println("[Central] $source disconnected from chat logs streaming.")
        }
    }

    override fun subscribeToBans(request: Empty): Flow<BanRequest> = channelFlow {
        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()

        @Suppress("KotlinPrintToLogpoint", "RedundantSuppression")
        println("[Central] $source subscribed to bans")

        banSubscribers.put(source, channel)?.close()

        try {
            awaitCancellation()
        } finally {
            @Suppress("KotlinPrintToLogpoint", "RedundantSuppression")
            println("[Central] $source disconnected")
            banSubscribers.remove(source)
        }
    }

    override fun subscribeToCommands(requests: Flow<CommandResult>): Flow<Command> = channelFlow {
        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
        @Suppress("KotlinPrintToLogpoint", "RedundantSuppression")
        println("[Central] Agent subscribed to commands: $source")

        commandSubscribers.put(source, channel)?.close()

        requests
            .onCompletion {
                @Suppress("KotlinPrintToLogpoint", "RedundantSuppression")
                println("[Central] Agent disconnected from commands: $source")
                commandSubscribers.remove(source)
            }
            .collect { result ->
                val requestId = result.requestID
                pendingCommands.remove(requestId)?.complete(result.result)
            }
    }


    override suspend fun statusStream(requests: Flow<StatusResponse>): Empty {
        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
        @Suppress("KotlinPrintToLogpoint", "RedundantSuppression")
        println("[Central] $source status set up")
        requests
            .onCompletion {
                @Suppress("KotlinPrintToLogpoint", "RedundantSuppression")
                println("[Central] $source disconnected from status stream")
            }
            .collect { result ->
                serverStatuses[source] = Pair(Clock.System.now(), result)
            }
        return Empty.getDefaultInstance()
    }

    suspend fun sendCommand(
        clientId: String,
        command: String,
        args: List<String>,
        result: Boolean,
        permLevel: PermissionLevel
    ): Deferred<String> {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()

        var requestBuilder = Command.newBuilder()
            .setRequestID(requestId)
            .setName(command)
            .setPermLevel(permLevel)

        for (arg in args) {
            requestBuilder = requestBuilder.addArguments(arg)
        }
        val request = requestBuilder.setResult(result).build()

        // here we’d track the deferred so we can complete it when response arrives
        pendingCommands[requestId] = deferred

        val sendChannel = commandSubscribers[clientId]
        if (sendChannel == null) {
            deferred.complete("The server $clientId is not online or has crashed.")
            return deferred
        }
        sendChannel.send(request)
        return deferred
    }

    override suspend fun sendBan(request: BanRequest): Ack {
        try {
            val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
            if (request.shouldBeBanned) {
                db.addBan(request.steamID.toULong(), request.reason, request.banStart, request.banEnd)
            } else {
                db.endBan(request.steamID.toULong(), request.banEnd)
            }
            sendBanBack(request, listOf(source))
        } catch (e: Exception) {
            e.printStackTrace()
            return Ack.newBuilder().setOk(false).build()
        }
        return Ack.newBuilder().setOk(true).build()
    }

    suspend fun sendBanBack(request: BanRequest, excludedServers: List<String>) {
        banSubscribers
            .filterKeys { key -> !excludedServers.contains(key) }
            .forEach { (key, channel) ->
                channel.trySend(request)
                println("[Ban] Sending ban for ${request.steamID} to server $key")
            }
        discordEventsCallback?.invoke(CallbackEvent.BanEvent(request, excludedServers.joinToString(", ")))
    }

    override suspend fun getBanList(request: Empty): BanList {
        val bans = db.getAllBans()
        val requestBuilder = BanList.newBuilder()
        for ((steamID, _, reason) in bans) {
            requestBuilder.addBans(Ban.newBuilder().setSteamID(steamID.toLong()).setReason(reason))
        }
        return requestBuilder.build()
    }

    override suspend fun sendKick(request: KickLog): Ack {
        try {
            db.addKick(request.steamID.toULong(), request.reason, request.time)
        } catch (e: Exception) {
            e.printStackTrace()
            return Ack.newBuilder().setOk(false).build()
        }
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendWarn(request: WarnLog): Ack {
        try {
            db.addWarn(request.steamID.toULong(), request.reason, request.time)
        } catch (e: Exception) {
            e.printStackTrace()
            return Ack.newBuilder().setOk(false).build()
        }
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendPlayerActivity(request: JoinLeaveLog): Ack {
        try {
            val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
            val serverID = db.getOrCreateServerIdFromName(source)
            discordEventsCallback?.invoke(
                CallbackEvent.ServerEvent(
                    LoggedServerEvent.PlayerEvent(request), db.getOrCreateServerIdFromName(source)
                )
            )

            if (request.isOn) {
                db.playerJoin(
                    request.steamID.toULong(),
                    db.getCurrentMissionIDForServer(serverID),
                    request.time,
                    request.name
                )
            } else {
                db.playerLeave(
                    request.steamID.toULong(),
                    request.score,
                    request.time
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Ack.newBuilder().setOk(false).build()
        }
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendPlayerJoinFac(request: FactionLog): Ack {
        db.playerJoinFaction(request.steamID.toULong(), request.faction)
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendMissionChange(request: missionStatus): Ack {
        try {
            val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
            val serverID = db.getOrCreateServerIdFromName(source)
            discordEventsCallback?.invoke(
                CallbackEvent.ServerEvent(
                    LoggedServerEvent.MissionEvent(request),
                    serverID
                )
            )

            if (request.ended) {
                val winnerName: String = if (request.hasWinnerName()) request.winnerName else "unknown"
                db.endMission(serverID, request.time, winnerName)
            }
            db.startMission(
                request.missionName,
                request.time,
                serverID
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return Ack.newBuilder().setOk(false).build()
        }
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendSortieChange(request: sortieStatus): Ack {
        try {
            if (request.start) {
                val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
                val serverID = db.getOrCreateServerIdFromName(source)
                db.startSortie(serverID, request.steamID.toULong(), request.planeName, request.time)
            } else {
                db.endSortie(request.steamID.toULong(), request.killed, request.time)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Ack.newBuilder().setOk(false).build()
        }
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendKill(request: KillLog): Ack {
        try {
            val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
            val serverID = db.getOrCreateServerIdFromName(source)
            db.addKill(db.getCurrentMissionIDForServer(serverID), request)
        } catch (e: Exception) {
            e.printStackTrace()
            return Ack.newBuilder().setOk(false).build()
        }
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendReport(request: serverReport): Ack {
        discordEventsCallback?.invoke(CallbackEvent.ReportEvent(request, AgentIdInterceptor.AGENT_ID_CTX_KEY.get()))
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendTeamKill(request: KillLog): Ack {
        try {
            val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
            val serverID = db.getOrCreateServerIdFromName(source)
            db.addTeamKill(db.getCurrentMissionIDForServer(serverID), request)
            discordEventsCallback?.invoke(
                CallbackEvent.TeamKillEvent(
                    Triple(
                        request,
                        db.getLastPlayerName(request.killer.toULong()),
                        db.getLastPlayerName(request.killed.toULong())
                    ),
                    source
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return Ack.newBuilder().setOk(false).build()
        }
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendLinkCode(request: LinkUser): Ack {
        discordEventsCallback?.invoke(CallbackEvent.LinkEvent(request))
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendDonation(request: DonationLog): Ack {
        db.addDonation(
            request.donatorSteamID.toULong(),
            request.receiverSteamID.toULong(),
            request.amountMillions,
            request.time
        )
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun getStaffList(request: Empty): permissionBreakdown {
        return permissionBreakdownGetter!!.invoke()
    }

    fun setEventCallback(cb: (suspend (CallbackEvent) -> Unit)) {
        if (discordEventsCallback != null) throw IllegalStateException("Tried to set a discord callback but it was already set.")
        discordEventsCallback = cb
    }

    fun setPermissionBreakdownGetter(cb: (suspend () -> permissionBreakdown)) {
        if (permissionBreakdownGetter != null) throw IllegalStateException("Tried to set a permission breakdown but it was already set.")
        permissionBreakdownGetter = cb
    }
}

class AgentIdInterceptor : ServerInterceptor {

    companion object {
        val AGENT_ID_CTX_KEY: Context.Key<String> =
            Context.key("agent-id")
    }

    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {

        val sslSession = call.attributes.get(Grpc.TRANSPORT_ATTR_SSL_SESSION)
        val principal = sslSession?.peerPrincipal
        val agentDn = principal?.name

        val agentId = extractCommonName(agentDn)

        val ctx = Context.current().withValue(AGENT_ID_CTX_KEY, agentId)

        return Contexts.interceptCall(ctx, call, headers, next)
    }

    private fun extractCommonName(dn: String?): String? {
        if (dn == null) return null
        val regex = Regex("CN=([^,]+)")
        return regex.find(dn)?.groupValues?.get(1)
    }

}
