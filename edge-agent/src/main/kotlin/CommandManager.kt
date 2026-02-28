package com.graywar.noServerManager.edge

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.io.BufferedWriter

@Serializable
@SerialName("Command")
data class CommandPacket(
    val CommandName: String,
    val Arguments: List<String>,
    val Result: Boolean
) : GamePacket()

data class QueuedCommand(
    val packet: CommandPacket,
    val result: CompletableDeferred<ResponsePacket>? = null
)

class CommandManager(
    private val writer: BufferedWriter,
    private val scope: CoroutineScope
) {
    private var commandInFlight: QueuedCommand? = null

    private val commandQueue: Channel<QueuedCommand> = Channel(Channel.UNLIMITED)

    private var job: Job? = null

    fun start() {
        job = scope.launch {
            for (queued in commandQueue) {
                commandInFlight = queued
                sendCommand(queued.packet)

                // Wait for response if needed
                queued.result?.await()

                commandInFlight = null
            }
        }
    }

    suspend fun enqueueCommand(packet: CommandPacket): ResponsePacket? {
        if (!packet.Result){
            commandQueue.send(QueuedCommand(packet))
            return null
        }
        val deferred = CompletableDeferred<ResponsePacket>()
        commandQueue.send(QueuedCommand(packet, deferred))
        return deferred.await()
    }

    private suspend fun sendCommand(packet: CommandPacket) {
        withContext(Dispatchers.IO) {
            writer.write(Json.encodeToString(packet))
            writer.newLine()
            writer.flush()
        }
    }

    fun onReceivePacket(packet: ResponsePacket) {
        val command = commandInFlight
            ?: throw IllegalArgumentException("Cannot receive a response if no command was sent")

        command.result?.complete(packet)
    }

    fun stop() {
        job?.cancel()
        commandQueue.close()
    }
}