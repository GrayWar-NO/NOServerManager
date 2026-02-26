package com.graywar.noServerManager.dbManager

import com.graywar.noServerManager.proto.Ack
import com.graywar.noServerManager.proto.ChatLogs
import io.grpc.ServerBuilder
import kotlinx.coroutines.runBlocking
import com.graywar.noServerManager.proto.StatusResponse
import com.graywar.noServerManager.proto.EdgeAgentServiceGrpcKt
import com.graywar.noServerManager.proto.StatusRequest

class CentralServer(private val port: Int = 50051) {
    private val server = ServerBuilder.forPort(port)
        .addService(object : EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineImplBase() {
            override suspend fun reportStatus(request: StatusRequest): StatusResponse {
                println("[Central] Received status from ${request.agentId}")
                // TODO You could persist the data, update a dashboard, etc.
                return StatusResponse.newBuilder().setOk(true).build()
            }
        })
        .addService( object : EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineImplBase() {
            override suspend fun sendChatLogs(request: ChatLogs): Ack {
                for (log in request.logsList) {
                    println(log.message)
                }
                // TODO send to DB
                return Ack.newBuilder().setOk(true).build()
            }
        })
        .build()

    fun start() = runBlocking {
        server.start()
        println("[Central] gRPC server started on $port")
        server.awaitTermination()
    }

    fun stop() = server.shutdownNow()!!
}

fun main() = CentralServer().start()
