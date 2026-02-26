package com.graywar.noServerManager.edge

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.io.Writer

@Serializable
@SerialName("Ping")
data class PingPacket(
    val Data: String,
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


    fun sendNewPing(writer: Writer): Boolean{
        if (pingPacketOutbound) {
            pingOk = false
            return false
        }
        pingPacketOutbound = true
        val packet: GamePacket = PingPacket(Data="COCK")
        writer.write(json.encodeToString(packet) + "\n")
        writer.flush()
        return true
    }
}



