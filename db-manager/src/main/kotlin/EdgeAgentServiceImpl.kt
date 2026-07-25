package com.graywar.noServerManager.dbManager

import com.google.protobuf.Empty
import com.google.protobuf.Timestamp
import com.graywar.noServerManager.proto.*
import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Grpc
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


class EdgeAgentServiceImpl(private val db: DB) : EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineImplBase() {
    private val banSubscribers = mutableMapOf<String, SendChannel<BanRequest>>()
    private val commandSubscribers = mutableMapOf<String, SendChannel<Command>>()
    private val statusProviders = mutableMapOf<String, SendChannel<StatusRequest>>()
    private val serversToMissionIDs = mutableMapOf<String, Long>()
    internal var discordMessageFlows = mutableMapOf<Int, SendChannel<ChatBack>>()

    private var discordMessageCallback: (suspend (ChatLog, Int) -> Unit)? = null
    private var discordTKCallback: (suspend (KillLog, String, String, String) -> Unit)? = null
    private var discordLinkCallback: (suspend (LinkUser) -> Unit)? = null
    private var discordReportCallback: (suspend (serverReport, String) -> Unit)? = null
    private var discordBanCallback: (suspend (BanRequest, String) -> Unit)? = null

    private var permissionBreakdownCallback: (suspend () -> permissionBreakdown)? = null

    private val pendingCommands = mutableMapOf<String, CompletableDeferred<String>>()
    private val pendingStatusRequests = mutableMapOf<String, CompletableDeferred<StatusResponse>>()

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
                    serversToMissionIDs[source] ?: 1,
                    request.message
                )
                discordMessageCallback?.invoke(request, sID)
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

            val missionId = runCatching {
                db.getCurrentMissionIDForServer(source)
            }.getOrNull()

            if (missionId != null) {
                val now = Instant.now()

                db.endMission(
                    missionId,
                    Timestamp.newBuilder()
                        .setSeconds(now.epochSecond)
                        .setNanos(now.nano)
                        .build()
                )

                db.closeAllPlayers(missionId)
            }
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


    override fun statusStream(requests: Flow<StatusResponse>): Flow<StatusRequest> = channelFlow {
        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
        @Suppress("KotlinPrintToLogpoint", "RedundantSuppression")
        println("[Central] $source status set up")
        statusProviders.put(source, channel)?.close()
        requests
            .onCompletion {
                @Suppress("KotlinPrintToLogpoint", "RedundantSuppression")
                println("[Central] $source disconnected from status stream")
                statusProviders.remove(source)
            }
            .collect { result ->
                val requestId = result.requestID
                pendingStatusRequests.remove(requestId)?.complete(result)
            }
    }

    suspend fun getAllServerStatuses(): Map<String, StatusResponse> = coroutineScope {
        db.getAllServers().values
            .map { server ->
                async {
                    server to requestStatus(server)
                }
            }
            .awaitAll()
            .toMap()
    }

    suspend fun requestStatus(source: String, timeout: Duration = 30.seconds): StatusResponse {
        val outChannel = statusProviders[source] ?: return StatusResponse.newBuilder().setOk(false).build()
        val requestID = UUID.randomUUID().toString()
        val result = CompletableDeferred<StatusResponse>()
        pendingStatusRequests[requestID] = result
        outChannel.send(statusRequest { this.requestID = requestID })
        return try {
            withTimeout(timeout) {
                result.await()
            }
        } catch (_: TimeoutCancellationException) {
            StatusResponse.newBuilder().setOk(false).build()
        }
        finally {
            pendingStatusRequests.remove(requestID)
        }
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

    suspend fun sendBanBack(request: BanRequest, excludedServers: List<String>){
        banSubscribers
            .filterKeys { key -> !excludedServers.contains(key) }
            .forEach { (key, channel) ->
                channel.trySend(request)
                println("[Ban] Sending ban for ${request.steamID} to server $key")
            }
        discordBanCallback?.invoke(request, "all")
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
            if (request.isOn) {
                db.playerJoin(
                    request.steamID.toULong(),
                    serversToMissionIDs[source]?: 1,
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

            if (source in serversToMissionIDs.keys)
                db.endMission(serversToMissionIDs[source] ?: 1, request.time)
            if (request.missionName == "null") return Ack.newBuilder().setOk(true).build()
            serversToMissionIDs[source] = db.startMission(
                request.missionName,
                request.time,
                source
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return Ack.newBuilder().setOk(false).build()
        }
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendSortieChange(request: sortieStatus): Ack {
        try{
            if (request.start){
                db.startSortie(request.steamID.toULong(), request.planeName, request.time)
            }
            else
            {
                db.endSortie(request.steamID.toULong(), request.killed, request.time)
            }
        }catch (e: Exception) {
            e.printStackTrace()
            return Ack.newBuilder().setOk(false).build()
        }
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendKill(request: KillLog): Ack {
        try {
            val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
            db.addKill(serversToMissionIDs[source] ?: 1, request)
        } catch (e: Exception) {
            e.printStackTrace()
            return Ack.newBuilder().setOk(false).build()
        }
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendReport(request: serverReport): Ack {
        discordReportCallback?.invoke(request, AgentIdInterceptor.AGENT_ID_CTX_KEY.get())
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendTeamKill(request: KillLog): Ack {
        try {
            val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
            db.addTeamKill(serversToMissionIDs[source] ?: 1, request)
            discordTKCallback?.invoke(request, db.getLastPlayerName(request.killer.toULong()), db.getLastPlayerName(request.killed.toULong()), source)
        } catch (e: Exception) {
            e.printStackTrace()
            return Ack.newBuilder().setOk(false).build()
        }
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendLinkCode(request: LinkUser): Ack {
        discordLinkCallback?.invoke(request)
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendDonation(request: DonationLog): Ack {
        db.addDonation(request.donatorSteamID.toULong(), request.receiverSteamID.toULong(), request.amountMillions, request.time)
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun getStaffList(request: Empty): permissionBreakdown {
        return permissionBreakdownCallback!!.invoke()
    }

    fun setMsgCallback(cb: (suspend (ChatLog, Int) -> Unit)){
        if (discordMessageCallback != null)  throw IllegalStateException("Tried to set a discord callback but it was already set.")
        discordMessageCallback = cb
    }

    fun setTKCallback(cb: (suspend (KillLog, String, String, String) -> Unit)){
        if (discordTKCallback != null)  throw IllegalStateException("Tried to set a discord callback but it was already set.")
        discordTKCallback = cb
    }

    fun setLinkCallback(cb: (suspend (LinkUser) -> Unit)){
        if (discordLinkCallback != null)  throw IllegalStateException("Tried to set a discord callback but it was already set.")
        discordLinkCallback = cb
    }

    fun setReportCallback(cb: (suspend (serverReport, String) -> Unit)){
        if (discordReportCallback != null)  throw IllegalStateException("Tried to set a discord callback but it was already set.")
        discordReportCallback = cb
    }

    fun setPermissionBreakdownGetter(cb: (suspend () -> permissionBreakdown)){
        if (permissionBreakdownCallback != null)  throw IllegalStateException("Tried to set a permission breakdown but it was already set.")
        permissionBreakdownCallback = cb
    }

    fun setBanLoggerCallback(cb: (suspend (BanRequest, String) -> Unit)) {
        if (discordBanCallback != null)  throw IllegalStateException("Tried to set the ban callback but it was already set.")
        discordBanCallback = cb
    }

}

class AgentIdInterceptor : ServerInterceptor {

    companion object {
        val AGENT_ID_CTX_KEY: Context.Key<String> =
            Context.key("agent-id")
    }

    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: io.grpc.Metadata,
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
