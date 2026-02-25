package com.graywar.noServerManager.edge

import com.google.protobuf.Timestamp
import com.graywar.noServerManager.proto.EdgeAgentServiceGrpcKt
import com.graywar.noServerManager.proto.StatusRequest
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.time.Instant

/**
 * Configuration – in real life you’d read this from env vars or a config file.
 */
private const val GAME_SERVER_HOST = "localhost"   // name on the Docker network
private const val GAME_SERVER_PORT = 10042            // whatever your game server listens to
private const val CENTRAL_GRPC_HOST = "localhost"
private const val CENTRAL_GRPC_PORT = 50051

/**
 * The Edge Agent is a thin wrapper that:
 * 1. Connects to the local Game Server via TCP and forwards everything we see.
 * 2. Periodically reports its health/status back to the Central Manager over gRPC.
 */
fun main() = runBlocking {
    // ---------- 1️⃣  Open TCP connection to the game server ----------
    val socket = Socket(GAME_SERVER_HOST, GAME_SERVER_PORT)
    println("[Edge] Connected to game server at $GAME_SERVER_HOST:$GAME_SERVER_PORT")

    val serverJsonCommunicator = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    // Launch a coroutine that simply forwards whatever the game server sends
    // (for demo purposes we just print it; in production you’d forward or transform it).
    val receiveFromGameJob = launch(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            println("[Edge] Game says $line")
            if (line == null) continue
            val packet = serverJsonCommunicator.decodeFromString<GamePacket>(line)
            when (packet) {
                is PingPacket -> TODO()
                is ChatLogPacket -> TODO()
                is CommandPacket -> TODO()
                is LogEntryPacket -> TODO()
                is ResponsePacket -> TODO()
            }
            // TODO: Process
        }
    }

    // ---------- 2️⃣  gRPC channel to central manager ----------
    val channel = ManagedChannelBuilder.forAddress(CENTRAL_GRPC_HOST, CENTRAL_GRPC_PORT)
        .usePlaintext()
        .build()

    val grpcStub = EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineStub(channel)

    // ---------- 3️⃣  Periodic status reporting ----------
    val reportJob = launch {
        while (isActive) {
            val request = StatusRequest.newBuilder()
                .setAgentId(socket.inetAddress.hostName)
                .setLastHeartbeat(Timestamp.newBuilder().setSeconds(Instant.now().epochSecond).build())
                .build()

            try {
                val response = grpcStub.reportStatus(request)
                println("[Edge] Central replied: ${response.ok}")
            } catch (e: Exception) {
                println("[Edge] Failed to report status: ${e.message}")
            }

            delay(30_000L) // every 30s
        }
    }

    // ---------- 4️⃣  Graceful shutdown ----------
    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking { cleanup(receiveFromGameJob, reportJob, channel, socket) }
    })

    // Wait until either the forward or report job fails.
    select<Unit> {
        receiveFromGameJob.onJoin {}
        reportJob.onJoin {}
    }
}

private suspend fun cleanup(
    receiveFromGameJob: Job,
    reportJob: Job,
    channel: io.grpc.ManagedChannel,
    socket: Socket
) {
    println("[Edge] Shutting down…")
    receiveFromGameJob.cancelAndJoin()
    reportJob.cancelAndJoin()
    channel.shutdownNow()
    if (!socket.isClosed) withContext(Dispatchers.IO) {
        socket.close()
    }
}
