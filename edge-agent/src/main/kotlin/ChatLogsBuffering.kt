package com.graywar.noServerManager.edge

import com.google.protobuf.Timestamp
import kotlinx.serialization.*
import com.graywar.noServerManager.proto.ChatLog
import com.graywar.noServerManager.proto.ChatLogs
import com.graywar.noServerManager.proto.EdgeAgentServiceGrpcKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

@Serializable
@SerialName("ChatLog")
data class ChatLogPacket(
    val MessageText: String,
    val ChatName: String
) : GamePacket()


class ChatLogsBuffer(val grpcStub: EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineStub) {
    val logs: MutableList<ChatLog> = mutableListOf()
    val maxLogsPerComm: Int = 100
    val maxLogsTimeInterval: Int = 120
    var waitMaxTimeExpired: Job? = null
    var currentFistLogTime: Timestamp? = null

    suspend fun addLog(logPacket: ChatLogPacket){
        val log = ChatLog.newBuilder()
            .setMessage(logPacket.MessageText)
            .setMessageChannel(logPacket.ChatName)
            .setMessageSendTime(Timestamp.newBuilder().setSeconds(Instant.now().epochSecond).build())
            .build()
        addLog(log)
    }

    private suspend fun addLog(log: ChatLog) {
        if (logs.isEmpty()){
            currentFistLogTime = Timestamp.newBuilder().setSeconds(Instant.now().epochSecond).build()
            waitMaxTimeExpired = CoroutineScope(Dispatchers.Default).launch {
                delay((maxLogsTimeInterval*1000).toLong())
                sendLogs()
            }
        }
        logs.add(log)
        if (logs.size >= maxLogsPerComm){
            waitMaxTimeExpired?.cancel()
            sendLogs()
        }
    }

    suspend fun sendLogs(){
        val grpcLogs = ChatLogs.newBuilder()
            .addAllLogs(logs)
            .build()
        grpcStub.sendChatLogs(grpcLogs)
        logs.clear()
    }
}