package com.graywar.noServerManager.edge

import com.google.protobuf.Timestamp
import com.graywar.noServerManager.proto.BanRequest
import com.graywar.noServerManager.proto.EdgeAgentServiceGrpcKt
import kotlinx.serialization.*
import java.time.Instant

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

suspend fun logEntryProcessor(packet: LogEntryPacket,
                              grpcStub: EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineStub): GamePacket? {
    var rtPacket: GamePacket? = null
    when (packet.Channel) {
        LogChannel.JoinLeave -> TODO()
        LogChannel.Warn -> TODO()
        LogChannel.Teamkill -> rtPacket = TODO()
        LogChannel.Kill -> TODO()
        LogChannel.Kick -> TODO()
        LogChannel.Ban -> sendBan(packet, grpcStub)
    }
    return rtPacket
}

suspend fun sendBan(packet: LogEntryPacket, grpcStub: EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineStub) {
    if (packet.Channel != LogChannel.Ban) throw Exception("Send ban failed: Packet is not a ban packet.")
    val values = packet.LogText.split(':')
    if (values.size != 4) throw Exception("Send ban failed: Ban packet is invalid: " + packet.LogText)
    val timestampNow = Timestamp.newBuilder().setSeconds(Instant.now().epochSecond).build()
    val timestampEnd: Timestamp?
    if (values[4] == "") timestampEnd = null
    else if (!(values[4].endsWith('d', true) || values[4].endsWith('h', true))) {TODO()}
    else if (values[4].endsWith('d', true)){
        val nDays = values[4].removeSuffix("d").removeSuffix("D").toInt()
        timestampEnd = Timestamp.newBuilder().setSeconds(Instant.now().epochSecond + (nDays * 24 * 3600)).build()
    } else {
        val nHours = values[4].removeSuffix("h").removeSuffix("H").toInt()
        timestampEnd = Timestamp.newBuilder().setSeconds(Instant.now().epochSecond + (nHours * 3600)).build()
    }

    val request = BanRequest.newBuilder()
        .setShouldBeBanned(values[0].toInt() != 0)
        .setSteamID(values[1].toLong())
        .setReason(values[2])
        .setBanStart(timestampNow)
        .setBanEnd(timestampEnd)
        .build()

    val ack = grpcStub.sendBan(request)
    if (!ack.ok) println("[Ban] failed: ack invalid received back from central")

}
