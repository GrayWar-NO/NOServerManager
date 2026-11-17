package com.graywar.noServerManager.dbManager

import com.graywar.noServerManager.dbManager.Discord.Discord
import com.graywar.noServerManager.dbManager.Discord.DiscordConfig
import io.grpc.ServerInterceptors
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import java.io.File
import kotlin.time.Duration.Companion.minutes

data class HostConfig(val port: Int, val db: DataBaseConfig, val discord: DiscordConfig)


class CentralServer {
    private val logger = LogManager.getLogger(CentralServer::class.java)

    private val config = ConfigLoaderBuilder.default()
        .addFileSource("central.conf")
        .build()
        .loadConfigOrThrow<HostConfig>()

    private val db = DB(config.db)

    private val edgeAgent = EdgeAgentServiceImpl(db)

    private val discord = Discord(config.discord, db, edgeAgent)

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
        server.start()
        if (config.discord.enable){
            discord.start()
            edgeAgent.setEventCallback(discord::sendCallbackEvent)
            edgeAgent.setPermissionBreakdownGetter(discord.modListExt::get)
        }
        manageEndingBans()
        logger.info("[Central] gRPC server started on {}", config.port)
        server.awaitTermination()
    }

    @Suppress("unused")
    fun stop() {
        server.shutdownNow()!!
        discord.stop()
    }

    private fun manageEndingBans() = CoroutineScope(Dispatchers.Default).launch {
        while (isActive) {
            delay(30.minutes)
            for (ban in db.getAllEndedBans()) {
                edgeAgent.sendBanBack(ban, emptyList())
            }
        }
        logger.info("cancelled")
    }


}

fun main() = CentralServer().start()
