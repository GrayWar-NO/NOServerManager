package com.graywar.noServerManager.edge

import kotlinx.coroutines.*
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.Socket
import kotlin.time.Duration.Companion.seconds

class NotConnectedException(message: String) : Exception(message)

class ManagedSocket(
    private val host: String,
    private val port: Int,
    private val onConnected: () -> Unit,
    private val onMessage: suspend (ManagedSocket, String) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null

    @Volatile
    private var currentWriter: BufferedWriter? = null

    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    val isConnected: Boolean
        get() = socket?.isConnected == true && !socket?.isClosed!!

    fun connect() {
        cancel()

        job = scope.launch {
            while (isActive) {
                try {
                    println("[Edge] Connecting to game server at $host:$port...")
                    val newSocket = Socket(host, port)
                    val newWriter = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream()))

                    socket = newSocket
                    writer = newWriter
                    currentWriter = newWriter

                    println("[Edge] Connected to game server at $host:$port")

                    onConnected()

                    readFromSocket(newSocket)

                } catch (t: Throwable) {
                    onError(t)
                    delay(10.seconds)
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        closeResources()
    }

    private suspend fun readFromSocket(socket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                val reader = socket.getInputStream().bufferedReader()
                while (!socket.isClosed && socket.isConnected) {
                    val line = reader.readLine() ?: break
                    onMessage(this@ManagedSocket, line)
                }
            } catch (t: Throwable) {
                if (socket.isConnected) {
                    println("[Edge] Read error: ${t.message}")
                } else {
                    println("[Edge] Disconnected: ${t.message}")
                }
            } finally {
                closeResources()
                onDisconnected()
            }
        }
    }

    private fun closeResources() {
        writer?.use { it.flush() }
        socket?.use { if (!it.isClosed) it.close() }
        writer = null
        socket = null
        currentWriter = null
    }

    fun sendWithWriter(line: String){
        if (!isConnected) throw NotConnectedException("Socket is not connected")
        currentWriter!!.write(line)
        currentWriter!!.newLine()
        currentWriter!!.flush()
    }

}