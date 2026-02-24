package com.graywar.noServerManager.dbManager

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
                println("   Active servers: ${request.activeServers}")
                // You could persist the data, update a dashboard, etc.
                return StatusResponse.newBuilder().setOk(true).build()
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
