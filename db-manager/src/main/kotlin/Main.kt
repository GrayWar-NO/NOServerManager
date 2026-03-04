package com.graywar.noServerManager.dbManager

import com.graywar.noServerManager.proto.Ack
import com.graywar.noServerManager.proto.AgentInfo
import com.graywar.noServerManager.proto.BanRequest
import com.graywar.noServerManager.proto.ChatLog
import io.grpc.ServerBuilder
import kotlinx.coroutines.runBlocking
import com.graywar.noServerManager.proto.StatusResponse
import com.graywar.noServerManager.proto.EdgeAgentServiceGrpcKt
import com.graywar.noServerManager.proto.StatusRequest
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class CentralServer(private val port: Int = 50051) {
    private val server = ServerBuilder.forPort(port)
        .addService(EdgeAgentServiceImpl())
        .build()

    fun start() = runBlocking {
        server.start()
        println("[Central] gRPC server started on $port")
        server.awaitTermination()
    }

    @Suppress("unused")
    fun stop() = server.shutdownNow()!!
}

class EdgeAgentServiceImpl: EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineImplBase() {
    private val subscribers = mutableMapOf<String, SendChannel<BanRequest>>()

    override suspend fun reportStatus(request: StatusRequest): StatusResponse {
        println("[Central] Received status from ${request.agentId}")
        // TODO You could persist the data, update a dashboard, etc.
        return StatusResponse.newBuilder().setOk(true).build()
    }


    override suspend fun sendChatLogsStream(requests: Flow<ChatLog>): Ack {
        requests.collect { request ->
                println(request.message)
                // TODO send to DB
        }
        return Ack.newBuilder().setOk(true).build()
    }

    override fun subscribeToBans(request: AgentInfo): Flow<BanRequest> = channelFlow {
        val agentId = request.agentID
        println("[Central] $agentId subscribed to bans")

        subscribers[agentId] = channel

        awaitClose {
            println("[Central] $agentId disconnected")
            subscribers.remove(agentId)
        }
    }

    override suspend fun sendBan(request: BanRequest): Ack {
        val source = request.agentID
        // TODO send to DB
        subscribers
            .filterKeys { key -> key != source }
            .forEach { (_, channel) -> channel.trySend(request) }
        return Ack.newBuilder().setOk(true).build()
    }
}

fun main() = CentralServer().start()
