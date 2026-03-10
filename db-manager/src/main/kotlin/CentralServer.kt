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
import io.grpc.ServerInterceptors
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import java.io.File
import java.time.Instant

data class HostConfig(val port: Int, val db: DataBaseConfig)


class CentralServer {
    private val config = ConfigLoaderBuilder.default()
        .addFileSource("central.conf")
        .build()
        .loadConfigOrThrow<HostConfig>()

    private val db = DB(config.db)

    private val server = NettyServerBuilder
        .forPort(config.port)
        .sslContext(
            GrpcSslContexts.forServer(
                File("CA/central.crt"),
                File("CA/central.key"),
            )
                .trustManager(File("CA/ca.crt"))
                .clientAuth(ClientAuth.REQUIRE)
                .build()
        )
        .addService(
            ServerInterceptors.intercept(
                EdgeAgentServiceImpl(db),
                AgentIdInterceptor()
            )
        )
        .build()

    fun start() = runBlocking {
        db.init()
        server.start()
        println("[Central] gRPC server started on ${config.port}")
        server.awaitTermination()
    }

    @Suppress("unused")
    fun stop() = server.shutdownNow()!!
}

class EdgeAgentServiceImpl(private val db: DB) : EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineImplBase() {
    private val subscribers = mutableMapOf<String, SendChannel<BanRequest>>()
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

            subscribers[source] = channel

            awaitClose {
                println("[Central] $source disconnected")
                db.endMission(
                    db.getCurrentMissionIDForServer(source),
                    Timestamp.newBuilder().setSeconds(Instant.now().epochSecond).setNanos(Instant.now().nano).build())
                subscribers.remove(source)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun sendBan(request: BanRequest): Ack {
        try {
            val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
            if (request.shouldBeBanned) {
                db.addBan(request.steamID.toULong(), request.reason, request.banStart, request.banEnd)
            } else {
                db.endBan(request.steamID.toULong(), request.banStart)
            }
            subscribers
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
                    request.time
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

fun main() = CentralServer().start()
