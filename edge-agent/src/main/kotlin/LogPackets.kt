package com.graywar.noServerManager.edge

import com.google.protobuf.Timestamp
import com.graywar.noServerManager.proto.*
import kotlinx.serialization.*
import java.time.Instant

enum class LogChannel{
    JoinLeave,
    MissionStatus,
    Teamkill,
    Kill,
    Kick,
    Ban,
    Warn
}


@Serializable
@SerialName("logentry")
data class LogEntryPacket(
    val channel: LogChannel,
    val logText: String
) : GamePacket()

suspend fun logEntryProcessor(packet: LogEntryPacket,
                              grpcStub: EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineStub){
    val result = when (packet.channel) {
        LogChannel.JoinLeave -> sendPlayerAct(packet, grpcStub)
        LogChannel.MissionStatus -> sendMission(packet, grpcStub)
        LogChannel.Warn -> sendWarn(packet, grpcStub)
        LogChannel.Teamkill -> grpcStub.sendTeamKill(genKillLog(packet))
        LogChannel.Kill -> grpcStub.sendKill(genKillLog(packet))
        LogChannel.Kick -> sendKick(packet, grpcStub)
        LogChannel.Ban -> sendBan(packet, grpcStub)
    }
    if (result == null || !result.ok) {println("[Edge] Failed processing log packet with text ${packet.logText} as ${packet.channel}")}
}

suspend fun sendPlayerAct(packet: LogEntryPacket,
                          grpcStub: EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineStub): Ack {
    val values = packet.logText.split(":")
    val timestampNow = Timestamp.newBuilder().setSeconds(Instant.now().epochSecond).setNanos(Instant.now().nano).build()
    val isOn = values[0] == "1"
    val request = JoinLeaveLog.newBuilder()
        .setTime(timestampNow)
        .setSteamID(values[1].toULong().toLong())
        .setIsOn(isOn)
        .setScore(if (!isOn) values[2].toFloat() else 0f)
        .setName(if (isOn) values.drop(2).joinToString(":") else "")
        .build()
    return grpcStub.sendPlayerActivity(request)
}

suspend fun sendMission(packet: LogEntryPacket,
                        grpcStub: EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineStub): Ack {
    val timestampNow = Timestamp.newBuilder().setSeconds(Instant.now().epochSecond).setNanos(Instant.now().nano).build()
    val request = missionStatus.newBuilder()
        .setTime(timestampNow)
        .setMissionName(packet.logText)
        .build()
    return grpcStub.sendMissionChange(request)
}

suspend fun sendBan(packet: LogEntryPacket,
                    grpcStub: EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineStub): Ack {
    if (packet.channel != LogChannel.Ban) throw Exception("Send ban failed: Packet is not a ban packet.")
    val values = packet.logText.split(':')
    val shouldBeBanned = values[0].toInt() != 0
    if ((shouldBeBanned && values.size < 3) || values.size < 4) throw Exception("Send ban failed: Ban packet is invalid: " + packet.logText)
    val timestampNow = Timestamp.newBuilder().setSeconds(Instant.now().epochSecond).setNanos(Instant.now().nano).build()
    val timestampEnd: Timestamp?
    if (values[2] == "") timestampEnd = null
    else if (!(values[2].endsWith('d', true) || values[2].endsWith('h', true))) {
        timestampEnd = null
    }
    else if (values[2].endsWith('d', true)){
        val nDays = values[2].removeSuffix("d").removeSuffix("D").toInt()
        timestampEnd = Timestamp.newBuilder().setSeconds(Instant.now().epochSecond + (nDays * 24 * 3600)).build()
    } else {
        val nHours = values[2].removeSuffix("h").removeSuffix("H").toInt()
        timestampEnd = Timestamp.newBuilder().setSeconds(Instant.now().epochSecond + (nHours * 3600)).build()
    }

    val request = BanRequest.newBuilder()
        .setShouldBeBanned(shouldBeBanned)
        .setSteamID(values[1].toULong().toLong())
        .setReason(if (shouldBeBanned) values.drop(3).joinToString(":") else null)
        .setBanStart(timestampNow)
        .setBanEnd(timestampEnd)
        .build()

    return grpcStub.sendBan(request)
}

suspend fun sendKick(packet: LogEntryPacket, grpcStub: EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineStub): Ack? {

    if (packet.channel != LogChannel.Kick) throw Exception("Send Kick failed: Packet is not a kick packet.")
    val values = packet.logText.split(':')
    val timestampNow = Timestamp.newBuilder().setSeconds(Instant.now().epochSecond).setNanos(Instant.now().nano).build()
    if (values[0] == "0") return null

    val request = KickLog
        .newBuilder()
        .setTime(timestampNow)
        .setSteamID(values[1].toULong().toLong())
        .setReason(values.drop(2).joinToString(":"))
        .build()

    return grpcStub.sendKick(request)
}

suspend fun sendWarn(packet: LogEntryPacket, grpcStub: EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineStub): Ack? {

    if (packet.channel != LogChannel.Warn) throw Exception("Send Warn failed: Packet is not a warn packet.")
    val values = packet.logText.split(':')
    val timestampNow = Timestamp.newBuilder().setSeconds(Instant.now().epochSecond).setNanos(Instant.now().nano).build()
    if (values[0] == "0") return null

    val request = WarnLog
        .newBuilder()
        .setTime(timestampNow)
        .setSteamID(values[1].toULong().toLong())
        .setReason(values.drop(2).joinToString(":"))
        .build()

    return grpcStub.sendWarn(request)
}

fun genKillLog(packet: LogEntryPacket): KillLog{
    // text format: "killer:killerUnit:weapon:killed:killedUnit"
    val timestampNow = Timestamp.newBuilder().setSeconds(Instant.now().epochSecond).setNanos(Instant.now().nano).build()
    val values = packet.logText.split(':')
    val killerID: ULong = values[0].toULongOrNull() ?: 0UL
    val killedID = values[3].toULongOrNull() ?: 0UL

    val request = KillLog.newBuilder()
        .setTime(timestampNow)
        .setKiller(killerID.toLong())
        .setKillerUnit(values[1])
        .setWeapon(values[2])
        .setKilled(killedID.toLong())
        .setKilledUnit(values[4])
        .build()
    return request
}
