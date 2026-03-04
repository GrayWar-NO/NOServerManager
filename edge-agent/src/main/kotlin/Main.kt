package com.graywar.noServerManager.edge

import com.google.protobuf.Timestamp
import com.graywar.noServerManager.proto.AgentInfo
import com.graywar.noServerManager.proto.ChatLog
import com.graywar.noServerManager.proto.EdgeAgentServiceGrpcKt
import com.graywar.noServerManager.proto.StatusRequest
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addResourceSource
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.selects.select
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.time.Instant


data class Remote (val host: String, val port: Int, val pingDelay: Int)
data class EdgeConfig (val name: String, val nuclearOption: Remote, val central: Remote)

fun main() = runBlocking {
    val config = ConfigLoaderBuilder.default()
        .addResourceSource("/edge-agent.conf")
        .build()
        .loadConfigOrThrow<EdgeConfig>()

    // ---------- 1️⃣  Open TCP connection to the game server ----------
    val socket = Socket(config.nuclearOption.host, config.nuclearOption.port)
    println("[Edge] Connected to game server at ${config.nuclearOption.host}:${config.nuclearOption.port}")
    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

    val pingProc = PingPacketProcessor()

    val serverJsonCommunicator = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    // ---------- 2️⃣  gRPC channel to central manager ----------
    val channel = ManagedChannelBuilder.forAddress(config.central.host, config.central.port)
        .usePlaintext()
        .build()

    val grpcStub = EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineStub(channel)

    val chatLogsFlow = MutableSharedFlow<ChatLog>()

    val jobs = mutableListOf<Job>()

    val cmdMgr = CommandManager(writer, scope = CoroutineScope(Dispatchers.Default))
    cmdMgr.start()

    jobs.add( launch(Dispatchers.IO){
        val banFlow = grpcStub.subscribeToBans(
            AgentInfo.newBuilder().setAgentID(config.name).build()
        )

        banFlow.collect { ban ->
            val banCommandPacket = CommandPacket(
                commandName = "ban",
                arguments = listOf(ban.steamID.toString(), ban.reason),
                result = false
            )
            cmdMgr.enqueueCommand(banCommandPacket)
        }
    })

    jobs.add(
        launch(Dispatchers.IO) {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null && isActive) {
                println("[Edge] Game says $line")
                if (line == null) continue
                var outPacket: GamePacket? = null
                when (val packet = serverJsonCommunicator.decodeFromString<GamePacket>(line)) {
                    is PingPacket -> { outPacket = pingProc.processPacket(packet) }
                    is ChatLogPacket -> emitChatLog(chatLogsFlow, packet)
                    is CommandPacket -> throw Exception("Command received; this is an outgoing-only packet for the agent.")
                    is LogEntryPacket -> { outPacket = logEntryProcessor(packet, grpcStub) }
                    is ResponsePacket -> cmdMgr.onReceivePacket(packet)
                }
                if (outPacket != null) {
                    writer.write(Json.encodeToString(outPacket))
                    writer.newLine()
                    writer.flush()
                }
            }
        }
    )


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

                delay(1000 * config.nuclearOption.pingDelay.toLong())
            }
        }
    )

    jobs.add(launch(Dispatchers.IO) {
        while (isActive) {
            try {
                println("[Edge] sending ping to game")
                if (!pingProc.sendNewPing(writer)) break
                delay(1000 * config.central.pingDelay.toLong())
            } catch (e: Exception) {
                println("[Edge] Ping failed: ${e.message}")
            }
        }
        println("[Edge] server stopped responding.")

    })

    // ---------- 4️⃣  Graceful shutdown ----------
    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking { cleanup(jobs, channel, socket, cmdMgr) }
    })

    // Wait until either one of the jobs fail.
    select<Unit> {
        jobs.forEach { job ->
            job.onJoin { }
        }
    }}

private suspend fun cleanup(
    jobs: List<Job>,
    channel: ManagedChannel,
    socket: Socket,
    cmdMgr: CommandManager
) {
    println("[Edge] Shutting down…")
    for (job in jobs) {
        job.cancelAndJoin()
    }
    cmdMgr.stop()
    channel.shutdownNow()
    if (!socket.isClosed) withContext(Dispatchers.IO) {
        socket.close()
    }
}
