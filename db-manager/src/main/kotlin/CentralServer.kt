package com.graywar.noServerManager.dbManager

import io.grpc.ServerInterceptors
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth
import kotlinx.coroutines.runBlocking
import java.io.File

data class HostConfig(val port: Int, val db: DataBaseConfig, val discord: DiscordConfig)


class CentralServer {
    private val config = ConfigLoaderBuilder.default()
        .addFileSource("central.conf")
        .build()
        .loadConfigOrThrow<HostConfig>()

    private val db = DB(config.db)

    private val edgeAgent = EdgeAgentServiceImpl(db)

    private val discord = Discord(config.discord, config.db, edgeAgent)

    private val server = NettyServerBuilder
        .forPort(config.port)
        .sslContext(
            GrpcSslContexts.forServer(
                File("CA/central.crt"),
                File("CA/central.key"),
            )
                .trustManager(File("CA/ca.crt"))
                .clientAuth(ClientAuth.REQUIRE)
                .build()
        )
        .addService(
            ServerInterceptors.intercept(
                edgeAgent,
                AgentIdInterceptor()
            )
        )
        .build()

    fun start() = runBlocking {
        db.init()
        server.start()
        discord.main()
        println("[Central] gRPC server started on ${config.port}")
        server.awaitTermination()
    }

    @Suppress("unused")
    fun stop() {
        server.shutdownNow()!!
        discord.stop()
    }
}

fun main() = CentralServer().start()
