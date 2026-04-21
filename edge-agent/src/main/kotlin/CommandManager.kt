package com.graywar.noServerManager.edge

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

@Serializable
@SerialName("command")
data class CommandPacket(
    val commandName: String,
    val arguments: List<String>,
    val result: Boolean = false
) : GamePacket()

data class QueuedCommand(
    val packet: CommandPacket,
    val result: CompletableDeferred<ResponsePacket>? = null
)

class CommandManager(
    private val client: ManagedSocket,
    private val scope: CoroutineScope
) {
    private var commandInFlight: QueuedCommand? = null

    private val commandQueue: Channel<QueuedCommand> = Channel(Channel.UNLIMITED)

    private var job: Job? = null

    private val json = Json { encodeDefaults = true }


    fun start() {
        job = scope.launch {
            for (queued in commandQueue) {
                commandInFlight = queued
                try {
                    sendCommand(queued.packet)
                } catch (_: NotConnectedException) {
                    queued.result?.complete(ResponsePacket("Could not execute command: Edge is up but not connected to game."))
                }
                queued.result?.await()
                commandInFlight = null
            }
        }
    }

    suspend fun enqueueCommand(packet: CommandPacket): ResponsePacket? {
        if (!packet.result){
            commandQueue.send(QueuedCommand(packet))
            return null
        }
        val deferred = CompletableDeferred<ResponsePacket>()
        commandQueue.send(QueuedCommand(packet, deferred))
        return deferred.await()
    }

    private suspend fun sendCommand(packet: GamePacket) {
        withContext(Dispatchers.IO) {
            client.sendWithWriter(json.encodeToString(packet))
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