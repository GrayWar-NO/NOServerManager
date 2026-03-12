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
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import java.time.Instant
import java.util.UUID


class EdgeAgentServiceImpl(private val db: DB) : EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineImplBase() {
    private val banSubscribers = mutableMapOf<String, SendChannel<BanRequest>>()
    private val commandSubscribers = mutableMapOf<String, SendChannel<Command>>()
    private val serversToMissionIDs = mutableMapOf<String, Long>()

    override suspend fun reportStatus(request: StatusRequest): StatusResponse {
        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
        println("[Central] Received status from $source")
        // TODO Handle no status received or something idk
        return StatusResponse.newBuilder().setOk(true).build()
    }


    override suspend fun sendChatLogsStream(requests: Flow<ChatLog>): Ack {
        try {
            requests.collect { request ->
                val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
                db.storeMessage(
                    request.senderSteamID.toULong(),
                    request.messageSendTime,
                    request.messageChannel,
                    serversToMissionIDs[source]!!,
                    request.message
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Ack.newBuilder().setOk(false).build()
        }
        return Ack.newBuilder().setOk(true).build()
    }

    override fun subscribeToBans(request: Empty): Flow<BanRequest> = channelFlow {
        try {
            val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
            println("[Central] $source subscribed to bans")

            banSubscribers[source] = channel

            awaitClose {
                println("[Central] $source disconnected")
                banSubscribers.remove(source)
                val missionId: Long
                try {
                    missionId = db.getCurrentMissionIDForServer(source)
                } catch (_: NullPointerException) {
                    return@awaitClose
                }
                db.endMission(
                    missionId,
                    Timestamp.newBuilder().setSeconds(Instant.now().epochSecond).setNanos(Instant.now().nano).build()
                )
                db.closeAllPlayers(missionId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun subscribeToCommands(requests: Flow<CommandResult>): Flow<Command> = channelFlow {
        try {
            val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()

            commandSubscribers[source] = channel

            requests.collect { result ->
                val requestId = result.requestID
                pendingRequests.remove(requestId)?.complete(result.result)
            }
        }catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun sendCommand(clientId: String, command: String, args: List<String>, result: Boolean): Deferred<String> {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()

        var requestBuilder = Command.newBuilder()
            .setRequestID(requestId)
            .setName(command)

        for (i in args.indices) {
            requestBuilder = requestBuilder.setArguments(i, args[i])
        }
        val request = requestBuilder.setResult(result).build()

        // here we’d track the deferred so we can complete it when response arrives
        pendingRequests[requestId] = deferred

        commandSubscribers[clientId]?.send(request)
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
                    serversToMissionIDs[source]!!,
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
                db.endMission(serversToMissionIDs[source]!!, request.time)
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

    override suspend fun sendKill(request: KillLog): Ack {
        try {
            val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
            db.addKill(serversToMissionIDs[source]!!, request)
        } catch (e: Exception) {
            e.printStackTrace()
            return Ack.newBuilder().setOk(false).build()
        }
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendTeamKill(request: KillLog): Ack {
        try {
            val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
            db.addTeamKill(serversToMissionIDs[source]!!, request)
        } catch (e: Exception) {
            e.printStackTrace()
            return Ack.newBuilder().setOk(false).build()
        }
        return Ack.newBuilder().setOk(true).build()
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
