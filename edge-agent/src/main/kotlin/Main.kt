package com.graywar.noServerManager.edge

import com.google.protobuf.Timestamp
import com.graywar.noServerManager.proto.EdgeAgentServiceGrpcKt
import com.graywar.noServerManager.proto.StatusRequest
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.time.Instant

//TODO make configurable
private const val GAME_SERVER_HOST = "localhost"   // name on the Docker network
private const val GAME_SERVER_PORT = 10042            // whatever your game server listens to
private const val CENTRAL_GRPC_HOST = "localhost"
private const val CENTRAL_GRPC_PORT = 50051

fun main() = runBlocking {
    // ---------- 1️⃣  Open TCP connection to the game server ----------
    val socket = Socket(GAME_SERVER_HOST, GAME_SERVER_PORT)
    println("[Edge] Connected to game server at $GAME_SERVER_HOST:$GAME_SERVER_PORT")
    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

    val pingProc = PingPacketProcessor()

    val serverJsonCommunicator = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "Type"
    }

    val channel = ManagedChannelBuilder.forAddress(CENTRAL_GRPC_HOST, CENTRAL_GRPC_PORT)
        .usePlaintext()
        .build()

    val grpcStub = EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineStub(channel)

    val logsBuffer = ChatLogsBuffer(grpcStub)

    val jobs = mutableListOf<Job>()

    // Launch a coroutine that simply forwards whatever the game server sends
    jobs.add(
        launch(Dispatchers.IO) {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                println("[Edge] Game says $line")
                if (line == null) continue
                var outPacket: GamePacket? = null
                when (val packet = serverJsonCommunicator.decodeFromString<GamePacket>(line)) {
                    is PingPacket -> { outPacket = pingProc.processPacket(packet) }
                    is ChatLogPacket -> logsBuffer.addLog(packet)
                    is CommandPacket -> throw Exception("Command received; this is an outgoing-only packet for the agent.")
                    is LogEntryPacket -> { outPacket = logEntryProcessor(packet) }
                    is ResponsePacket -> TODO()
                }
                if (outPacket != null) {
                    writer.write(Json.encodeToString(outPacket) + "\n")
                    writer.flush()
                }
            }
        }
    )

    // ---------- 2️⃣  gRPC channel to central manager ----------

    // ---------- 3️⃣  Periodic status reporting ----------
    jobs.add(
        launch {
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

                delay(30_000L) // TODO make configurable
            }
        }
    )

    jobs.add(launch(Dispatchers.IO) {
        while (isActive) {
            try {
                println("[Edge] sending ping to game")
                if (!pingProc.sendNewPing(writer)) break
                delay(30_000L) //TODO make configurable
            } catch (e: Exception) {
                println("[Edge] Ping failed: ${e.message}")
            }
        }
        println("[Edge] server stopped responding.")

    })

    // ---------- 4️⃣  Graceful shutdown ----------
    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking { cleanup(jobs, channel, socket) }
    })

    // Wait until either one of the jobs fail.
    select<Unit> {
        jobs.forEach { job ->
            job.onJoin { }
        }
    }}

private suspend fun cleanup(
    jobs: List<Job>,
    channel: io.grpc.ManagedChannel,
    socket: Socket
) {
    println("[Edge] Shutting down…")
    for (job in jobs) {
        job.cancel("Shutting down")
    }
    channel.shutdownNow()
    if (!socket.isClosed) withContext(Dispatchers.IO) {
        socket.close()
    }
}
