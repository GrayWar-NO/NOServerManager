package com.graywar.noServerManager.edge

import kotlinx.serialization.*

enum class LogChannel{
    Info,
    Chat,
    Teamkill,
    Kill,
    Kick,
    Ban,
    Warn
}


@Serializable
sealed class GamePacket {
    abstract val type: String
}

@Serializable
@SerialName("Ping")
data class PingPacket(
    override val type: String,
    val data: String,
) : GamePacket()

@Serializable
@SerialName("Response")
data class ResponsePacket(
    override val type: String,
    val responseText: String
) : GamePacket()

@Serializable
@SerialName("Command")
data class CommandPacket(
    override val type: String,
    val commandName: String,
    val arguments: List<String>
) : GamePacket()

@Serializable
@SerialName("LogEntry")
data class LogEntryPacket(
    override val type: String,
    val channel: LogChannel,
    val logText: String
) : GamePacket()

@Serializable
@SerialName("ChatLog")
data class ChatLogPacket(
    override val type: String,
    val channel: LogChannel,
    val logText: String,
    val chatName: String
) : GamePacket()


