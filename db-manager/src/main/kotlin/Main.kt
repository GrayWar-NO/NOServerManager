package com.graywar.noServerManager.dbManager

import com.google.protobuf.Empty
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

data class Remote (val host: String, val port: Int, val pingDelay: Int)
data class HostConfig (val port: Int, val db: Remote)



class CentralServer() {
    private val config = ConfigLoaderBuilder.default()
        .addFileSource("central.conf")
        .build()
        .loadConfigOrThrow<HostConfig>()
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
                EdgeAgentServiceImpl(),
                AgentIdInterceptor()
            )
        )
        .build()

    fun start() = runBlocking {
        server.start()
        println("[Central] gRPC server started on ${config.port}")
        server.awaitTermination()
    }

    @Suppress("unused")
    fun stop() = server.shutdownNow()!!
}

class EdgeAgentServiceImpl: EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineImplBase() {
    private val subscribers = mutableMapOf<String, SendChannel<BanRequest>>()

    override suspend fun reportStatus(request: StatusRequest): StatusResponse {
        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
        println("[Central] Received status from $source")
        // TODO Handle no status received or something idk
        return StatusResponse.newBuilder().setOk(true).build()
    }


    override suspend fun sendChatLogsStream(requests: Flow<ChatLog>): Ack {
        requests.collect { request ->
                println(request.message)
                // TODO send to DB
        }
        return Ack.newBuilder().setOk(true).build()
    }

    override fun subscribeToBans(request: Empty): Flow<BanRequest> = channelFlow {
        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
        println("[Central] $source subscribed to bans")

        subscribers[source] = channel

        awaitClose {
            println("[Central] $source disconnected")
            subscribers.remove(source)
        }
    }

    override suspend fun sendBan(request: BanRequest): Ack {
        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
        // TODO send to DB
        subscribers
            .filterKeys { key -> key != source }
            .forEach { (_, channel) -> channel.trySend(request) }
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendKick(request: KickLog): Ack {
        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
        // TODO send to DB
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendWarn(request: WarnLog): Ack {
        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
        // TODO send to DB
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendPlayerActivity(request: JoinLeaveLog): Ack {
        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
        // TODO send to DB
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendMissionChange(request: missionStatus): Ack {
        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
        // TODO send to DB
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendKill(request: KillLog): Ack {
        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
        // TODO send to DB
        return Ack.newBuilder().setOk(true).build()
    }

    override suspend fun sendTeamKill(request: KillLog): Ack {
        val source = AgentIdInterceptor.AGENT_ID_CTX_KEY.get()
        // TODO send to DB
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
