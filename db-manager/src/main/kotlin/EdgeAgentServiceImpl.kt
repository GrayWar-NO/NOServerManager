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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import java.time.Instant
import java.util.UUID


class EdgeAgentServiceImpl(private val db: DB) : EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineImplBase() {
    private val banSubscribers = mutableMapOf<String, SendChannel<BanRequest>>()
    private val commandSubscribers = mutableMapOf<String, SendChannel<Command>>()
    private val serversToMissionIDs = mutableMapOf<String, Long>()

    private var discordMessageCallback: (suspend (ChatLog, Int) -> Unit)? = null
    private var discordTKCallback: (suspend (KillLog, String) -> Unit)? = null
    private var discordLinkCallback: (suspend (LinkUser) -> Unit)? = null

    override suspend fun reportStatus(request: StatusRequest): StatusResponse {
//        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
//        println("[Central] Received status from $source")
        // TODO Handle no status received or something idk
        return StatusResponse.newBuilder().setOk(true).build()
    }


    override suspend fun sendChatLogsStream(requests: Flow<ChatLog>): Ack {
        requests
            .onCompletion {
                println("[Central] Agent disconnected from chat logs streaming.")
            }
            .collect { request ->
                val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
                db.storeMessage(
                    request.senderSteamID.toULong(),
                    request.messageSendTime,
                    request.messageChannel,
                    serversToMissionIDs[source] ?: 1,
                    request.message
                )
                val sID = db.getServerIdFromName(source) ?: 1

                discordMessageCallback?.invoke(request, sID)
            }
        return Ack.newBuilder().setOk(true).build()
    }

    override fun subscribeToBans(request: Empty): Flow<BanRequest> = channelFlow {
        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
        println("[Central] $source subscribed to bans")

        banSubscribers.put(source, channel)?.close()

        try {
            awaitCancellation()
        } finally {
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
        println("[Central] Agent connected: $source")

        commandSubscribers.put(source, channel)?.close()

        requests
            .onCompletion {
                println("[Central] Agent disconnected from commands: $source")
                commandSubscribers.remove(source)
            }
            .collect { result ->
                val requestId = result.requestID
                pendingRequests.remove(requestId)?.complete(result.result)
            }
    }

    suspend fun sendCommand(clientId: String, command: String, args: List<String>, result: Boolean): Deferred<String> {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()

        var requestBuilder = Command.newBuilder()
            .setRequestID(requestId)
            .setName(command)

        for (arg in args) {
            requestBuilder = requestBuilder.addArguments(arg)
        }
        val request = requestBuilder.setResult(result).build()

        // here we’d track the deferred so we can complete it when response arrives
        pendingRequests[requestId] = deferred

        val sendChannel = commandSubscribers[clientId]
        if (sendChannel == null) {
            deferred.complete("The server $clientId is not online or has crashed.")
            return deferred
        }
        sendChannel.send(request)
        return deferred
    }

    // Pending requests map
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<String>>()


    override suspend fun sendBan(request: BanRequest): Ack {
        try {
            val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
            if (request.shouldBeBanned) {
                db.addBan(request.steamID.toULong(), request.reason, request.banStart, request.banEnd)
            } else {
                db.endBan(request.steamID.toULong(), request.banStart)
            }
            banSubscribers
                .filterKeys { key -> key != source }
                .forEach { (_, channel) -> channel.trySend(request) }
        } catch (e: Exception) {
            e.printStackTrace()
            return Ack.newBuilder().setOk(false).build()
        }
        return Ack.newBuilder().setOk(true).build()
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

    override suspend fun sendTeamKill(request: KillLog): Ack {
        try {
            val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
            db.addTeamKill(serversToMissionIDs[source] ?: 1, request)
            discordTKCallback?.invoke(request, db.getLastPlayerName(request.killer.toULong()))
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

    fun setMsgCallback(cb: (suspend (ChatLog, Int) -> Unit)){
        if (discordMessageCallback != null)  throw IllegalStateException("Tried to set a discord callback but it was already set.")
        discordMessageCallback = cb
    }

    fun setTKCallback(cb: (suspend (KillLog, String) -> Unit)){
        if (discordTKCallback != null)  throw IllegalStateException("Tried to set a discord callback but it was already set.")
        discordTKCallback = cb
    }

    fun setLinkCallback(cb: (suspend (LinkUser) -> Unit)){
        if (discordLinkCallback != null)  throw IllegalStateException("Tried to set a discord callback but it was already set.")
        discordLinkCallback = cb
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
