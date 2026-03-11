package com.graywar.noServerManager.dbManager

import com.google.protobuf.Timestamp
import com.graywar.noServerManager.proto.KillLog
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

data class DataBaseConfig (val host: String, val port: Int, val name: String)

class DB() {
    private lateinit var config: DataBaseConfig
    private lateinit var url: String

    constructor(config: DataBaseConfig) : this() {
        this.config = config
        this.url = "jdbc:postgresql://${config.host}:${config.port}/${config.name}"
        connect()
    }

    fun connect() {
        Database.connect(url, "org.postgresql.Driver", user = "pauel") }

    fun init() { // To use only once to create the db.
        transaction {
            SchemaUtils.create(Servers)
            SchemaUtils.create(Missions)
            SchemaUtils.create(ServerMissions)
            SchemaUtils.create(MissionPlayers)
            SchemaUtils.create(Bans)
            SchemaUtils.create(Kicks)
            SchemaUtils.create(Kills)
            SchemaUtils.create(TeamKills)
            SchemaUtils.create(Messages)
            SchemaUtils.create(NoTrack)
            SchemaUtils.create(Warns)
        }
    }

    fun getServerIdFromName(name: String): Int? {
        val result = transaction {
            Servers
                .selectAll()
                .where {Servers.name eq name }
                .firstOrNull() }
        if (result == null) {return null}
        return result[Servers.id]
    }

    fun getMissionIdFromName(name: String): Int? {
        val result = transaction {
            Missions
                .selectAll()
                .where { Missions.name eq name }
                .firstOrNull() }
        if (result == null) {return null}
        return result[Missions.id]
    }

    fun getCurrentMissionIDForServer(name: String): Long {
        val result = transaction {
            Servers
                .join(
                    ServerMissions,
                    JoinType.INNER,
                    additionalConstraint = { Servers.id eq ServerMissions.server })
                .selectAll()
                .where { Servers.name eq name and ServerMissions.endTime.isNull() }
                .firstOrNull()
        }
        if (result == null) {throw NullPointerException("Server $name not found or has no ongoing mission.") }
        return result[ServerMissions.id]
    }

    fun newServer(name: String) {
        transaction {
            Servers.insert { it[Servers.name] = name }
        }
    }

    fun transformTimestamp(timestamp: Timestamp): Instant{
        return Instant.fromEpochSeconds(timestamp.seconds, timestamp.nanos.toLong())
    }

    fun storeMessage(sender: ULong, time: Timestamp, channel: String, mission: Long, text: String) {
        transaction {
            Messages.insert {
                it[Messages.sender] = sender
                it[Messages.time] = transformTimestamp(time)
                it[Messages.channel] = channel
                it[Messages.mission] = mission
                it[Messages.text] = text
            }
        }
    }

    fun deleteOldMessages(){
        val cutoff: Instant = Clock.System.now() - 90.days
        transaction {
            Messages.deleteWhere { Messages.time less cutoff }
        }
    }

    fun endMission(mission: Long, time: Timestamp) {
        transaction {
            ServerMissions.update ({ ServerMissions.id eq mission })
            { it[endTime] = transformTimestamp(time) }
        }
    }
    fun closeAllPlayers(mission: Long){
        val currentTime = Clock.System.now()
        transaction {
            MissionPlayers.update({ MissionPlayers.mission eq mission and MissionPlayers.leaveTime.isNull()}) {
                it[MissionPlayers.leaveTime] = currentTime
            }
        }
    }

    fun startMission(name: String, time: Timestamp, serverName: String): Long {
        val serverID = getServerIdFromName(serverName)
        val missionID = getMissionIdFromName(name)
        if (serverID == null || missionID == null){
            throw NullPointerException("Server $serverName not found or has no ongoing mission.")
        }
        val result = transaction {
            ServerMissions.insert {
                it[server] = serverID
                it[mission] = missionID
                it[startTime] = transformTimestamp(time)
            } get ServerMissions.id
        }
        return result
    }

    fun addBan(steamID: ULong, reason: String, startTime: Timestamp, endTime: Timestamp ) {
        val startInstant = transformTimestamp(startTime)
        val endInstant = transformTimestamp(endTime)
        transaction {
            Bans.insert {
                it[Bans.steamID] = steamID
                it[Bans.reason] = reason
                it[Bans.startTime] = startInstant
                it[Bans.endTime] = if (endInstant < startInstant) null else endInstant
            }
        }
    }

    fun endBan(steamID: ULong, endTime: Timestamp) {
        transaction {
            Bans.update({ Bans.steamID eq steamID and Bans.endTime.isNull()}) {
                it[Bans.endTime] = transformTimestamp(endTime)
            }
        }
    }

    fun addKick(steamID: ULong, reason: String, time: Timestamp) {
        transaction {
            Kicks.insert {
                it[Kicks.steamID] = steamID
                it[Kicks.reason] = reason
                it[Kicks.time] = transformTimestamp(time)
            }
        }
    }

    fun addWarn(steamID: ULong, reason: String, time: Timestamp) {
        transaction {
            Warns.insert {
                it[Warns.steamID] = steamID
                it[Warns.reason] = reason
                it[Warns.time] = transformTimestamp(time)
            }
        }
    }

    fun playerJoin(steamID: ULong, mission: Long, time: Timestamp) {
        transaction {
            MissionPlayers.insert {
                it[MissionPlayers.steamID] = steamID
                it[MissionPlayers.mission] = mission
                it[MissionPlayers.joinTime] = transformTimestamp(time)
            }
        }
    }

    fun playerLeave(steamID: ULong, score: Float, time: Timestamp) {
        transaction {
            MissionPlayers.update(
                where = { MissionPlayers.steamID eq steamID and MissionPlayers.leaveTime.isNull() }
            ) {
                it[MissionPlayers.score] = score
                it[MissionPlayers.leaveTime] = transformTimestamp(time)
            }
        }
    }

    fun addKill(mission: Long, kill: KillLog) {
        transaction {
            Kills.insert {
                it[Kills.mission] = mission
                it[killedID] = if (kill.killed.toULong() == 0UL) null else kill.killed.toULong()
                it[killerID] = if (kill.killer.toULong() == 0UL) null else kill.killer.toULong()
                it[killerName] = if (kill.killerUnit == "") null else kill.killerUnit
                it[killedName] = kill.killedUnit
                it[weapon] = if (kill.weapon == "") null else kill.weapon
                it[time] = transformTimestamp(kill.time)
            }
        }
    }

    fun addTeamKill(mission: Long, kill: KillLog) {
        transaction {
            TeamKills.insert {
                it[TeamKills.mission] = mission
                it[killedID] = kill.killed.toULong()
                it[killerID] = kill.killer.toULong()
                it[killerName] = kill.killerUnit
                it[killedName] = kill.killedUnit
                it[weapon] = kill.weapon
                it[time] = transformTimestamp(kill.time)
            }
        }
    }

}
