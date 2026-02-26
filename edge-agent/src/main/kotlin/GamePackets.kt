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
sealed class GamePacket

@Serializable
@SerialName("Response")
data class ResponsePacket(
    val ResponseText: String
) : GamePacket()

@Serializable
@SerialName("Command")
data class CommandPacket(
    val CommandName: String,
    val Arguments: List<String>
) : GamePacket()

@Serializable
@SerialName("LogEntry")
data class LogEntryPacket(
    val Channel: LogChannel,
    val LogText: String
) : GamePacket()
