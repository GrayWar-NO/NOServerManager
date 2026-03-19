package com.graywar.noServerManager.edge

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.io.BufferedWriter

@Serializable
@SerialName("ping")
data class PingPacket(
    val data: String,
) : GamePacket()

class PingPacketProcessor
{
    var pingPacketOutbound: Boolean = false
    var pingOk = true
    private val json = Json { encodeDefaults = true }


    fun processPacket(packet: PingPacket): PingPacket?{
        if (pingPacketOutbound) {
            pingPacketOutbound = false
            return null
        }
        return packet
    }


    fun sendNewPing(writer: BufferedWriter): Boolean{
        if (pingPacketOutbound) {
            pingOk = false
            return false
        }
        pingPacketOutbound = true
        val packet: GamePacket = PingPacket(data="TEST")
        writer.write(json.encodeToString(packet))
        writer.newLine()
        writer.flush()
        return true
    }
}



