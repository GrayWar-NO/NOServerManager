package com.graywar.noServerManager.edge

import com.google.protobuf.Empty
import com.google.protobuf.Timestamp
import com.graywar.noServerManager.proto.ChatLog
import com.graywar.noServerManager.proto.Command
import com.graywar.noServerManager.proto.CommandResult
import com.graywar.noServerManager.proto.EdgeAgentServiceGrpcKt
import com.graywar.noServerManager.proto.StatusRequest
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.selects.select
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.ConnectException
import java.time.Instant
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds


data class Remote (val host: String, val port: Int, val pingDelay: Int)
data class EdgeConfig (val name: String, val nuclearOption: Remote, val central: Remote)

@OptIn(ExperimentalAtomicApi::class)
fun main() = runBlocking {
    val config = ConfigLoaderBuilder.default()
        .addFileSource("edge-agent.conf")
        .build()
        .loadConfigOrThrow<EdgeConfig>()

    val pingProc = PingPacketProcessor()

    val serverJsonCommunicator = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    val sslContext = GrpcSslContexts.forClient()
        .trustManager(File("CA/ca.crt")) // trust server
        .keyManager(
            File("CA/${config.name}.crt"), // client certificate
            File("CA/${config.name}.key")  // client private key
        )
        .build()

    val channel = NettyChannelBuilder
        .forAddress(config.central.host, config.central.port)
        .sslContext(sslContext)
        .build()

    val grpcStub = EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineStub(channel)

    val chatLogsFlow = MutableSharedFlow<ChatLog>()

    val jobs = mutableListOf<Job>()

    lateinit var cmdMgr: CommandManager

    val ignoreNextBan = AtomicBoolean(false)

    val client = ManagedSocket(
        host = config.nuclearOption.host,
        port = config.nuclearOption.port,
        onConnected = {
            println("[Edge] Connected to game")
        },
        onMessage = {client, line ->
            println("[Edge] Game says $line")
            var outPacket: GamePacket? = null
            when (val packet = serverJsonCommunicator.decodeFromString<GamePacket>(line)) {
                is PingPacket ->outPacket = pingProc.processPacket(packet)
                is ChatLogPacket -> emitChatLog(chatLogsFlow, packet)
                is CommandPacket -> throw Exception("Command received; this is an outgoing-only packet for the agent.")
                is LogEntryPacket -> logEntryProcessor(packet, grpcStub, ignoreNextBan)
                is ResponsePacket -> cmdMgr.onReceivePacket(packet)
                is LinkPacket -> sendLink(packet, grpcStub)
            }
            if (outPacket != null) {
                try {
                    client.sendWithWriter(Json.encodeToString(outPacket))
                } catch (_: NotConnectedException) {}
            }
        },
        onDisconnected = {
            println("[Edge] Game disconnected")
        },
        onError = {e ->
            println("[Edge] Error in game Socket: ${e.message}")
            if (e is ConnectException) return@ManagedSocket
            e.printStackTrace()
        }
    )
    client.connect()

    cmdMgr = CommandManager(client, scope = CoroutineScope(Dispatchers.Default))
    cmdMgr.start()

    jobs.add (launch (Dispatchers.IO){
        retryWithBackoff {
            val ack = grpcStub.sendChatLogsStream(chatLogsFlow)
            println("Server flow ok: ${ack.ok}")
        }
    })

    jobs.add( launch(Dispatchers.IO){
        retryWithBackoff {
            val banFlow = grpcStub.subscribeToBans(
                Empty.getDefaultInstance()
            )

            banFlow.collect { ban ->
                val banCommandPacket = CommandPacket(
                    commandName = if (ban.shouldBeBanned) "ban" else "unban",
                    arguments = if (ban.shouldBeBanned) listOf(
                        ban.steamID.toString(),
                        ban.reason
                    ) else listOf(ban.steamID.toString()),
                    result = false
                )
                if (!ignoreNextBan.compareAndSet(expectedValue = false, newValue = true)){
                    throw Exception("Ban was forwarded when ignoreNextBan is true")
                }
                cmdMgr.enqueueCommand(banCommandPacket)
                println("[Edge] Game banned by Central request: $banCommandPacket")
            }
        }
    })

    jobs.add( launch(Dispatchers.IO){
        retryWithBackoff {
            val resultFlow = MutableSharedFlow<CommandResult>(extraBufferCapacity = 100)
            val commandFlow: Flow<Command> = grpcStub.subscribeToCommands(resultFlow)

            commandFlow.collect { command ->
                val commandPacket = CommandPacket(
                    commandName = command.name,
                    arguments = command.argumentsList,
                    result = command.result
                )

                println("Received command ${command.name} from central with arguments ${command.argumentsList}")

                val response: ResponsePacket? = cmdMgr.enqueueCommand(commandPacket)

                var resultBuilder = CommandResult.newBuilder()
                    .setRequestID(command.requestID)
                if (response != null) {
                    resultBuilder = resultBuilder.setResult(response.responseText).setOk(true)
                }
                resultFlow.tryEmit(resultBuilder.build())
            }
        }
    })

    jobs.add(
        launch {
            while (isActive) {

                val request = StatusRequest.newBuilder()
                    .setLastHeartbeat(Timestamp.newBuilder().setSeconds(Instant.now().epochSecond).setNanos(Instant.now().nano).build())
                    .build()

                try {
                    val response = grpcStub.reportStatus(request)
                    if (!response.ok) println("[Edge] Central is not ok")
//                    println("[Edge] Central replied: ${response.ok}")
                } catch (e: Exception) {
                    println("[Edge] Failed to report status: ${e.message}")
                }
                delay(config.nuclearOption.pingDelay.toLong().seconds)
            }
        }
    )

    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking { cleanup(jobs, client, channel, cmdMgr) }
    })

    select {
        jobs.forEach { job ->
            job.onJoin { }
        }
    }}

private suspend fun cleanup(
    jobs: List<Job>,
    client: ManagedSocket,
    channel: ManagedChannel,
    cmdMgr: CommandManager
) {
    println("[Edge] Shutting down…")
    for (job in jobs) {
        job.cancelAndJoin()
    }
    cmdMgr.stop()
    client.cancel()
    channel.shutdownNow()
}

suspend fun <T> retryWithBackoff(
    initialDelay: Int = 1,
    maxDelay: Int = 60,
    maxRetries: Int? = null,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    var currentRetries = 0

    while (currentCoroutineContext().isActive) {
        try {
            return block()
        } catch (e: CancellationException){
            throw e
        } catch (e: Exception) {
            if (maxRetries == null || currentRetries == maxRetries) {
                throw e
            }
            println("Retrying in $currentDelay seconds after error: ${e.message}")
//            e.printStackTrace()
            currentRetries++
        }
        delay(currentDelay.seconds)
        currentDelay = (currentDelay * factor).toInt().coerceAtMost(maxDelay)
    }
    throw CancellationException("Coroutine was cancelled")
}
