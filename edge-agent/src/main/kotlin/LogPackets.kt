package com.graywar.noServerManager.edge

import kotlinx.serialization.*

enum class LogChannel{
    JoinLeave,
    Teamkill,
    Kill,
    Kick,
    Ban,
    Warn
}


@Serializable
@SerialName("LogEntry")
data class LogEntryPacket(
    val Channel: LogChannel,
    val LogText: String
) : GamePacket()

fun logEntryProcessor(packet: LogEntryPacket): GamePacket? {
    when (packet.Channel) {
        LogChannel.JoinLeave -> TODO()
        LogChannel.Warn -> TODO()
        LogChannel.Teamkill -> TODO()
        LogChannel.Kill -> TODO()
        LogChannel.Kick -> TODO()
        LogChannel.Ban -> TODO()
    }

}
