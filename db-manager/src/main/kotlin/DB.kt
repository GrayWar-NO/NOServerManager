package com.graywar.noServerManager.dbManager

import com.google.protobuf.Timestamp
import com.graywar.noServerManager.proto.KillLog
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

data class DataBaseConfig(val host: String, val port: Int, val name: String, val user: String, val password: String)

data class Kill(val name: String?, val weapon: String, val unit: String, val isAircraft: Boolean)
data class Sortie(val aircraft: Aircraft, val start: Instant, val elapsed: Duration, val died: Boolean)
data class Mission(val name: String, val server: String, val start: Instant)
data class UserReasonTime(val steamID: ULong, val username: String?, val reason: String, val time: Instant)

class DB() {
    private lateinit var config: DataBaseConfig
    private lateinit var url: String

    constructor(config: DataBaseConfig) : this() {
        this.config = config
        this.url = "jdbc:postgresql://${config.host}:${config.port}/${config.name}"
        connect()
        init()
    }

    fun connect() {
        Database.connect(url, "org.postgresql.Driver", user = config.user, password = config.password)
    }

    fun init() {
        transaction {
            SchemaUtils.create(Servers)
            SchemaUtils.create(Missions)
            SchemaUtils.create(ServerMissions)
            SchemaUtils.create(Sorties)
            SchemaUtils.create(MissionPlayers)
            SchemaUtils.create(Bans)
            SchemaUtils.create(Kicks)
            SchemaUtils.create(Kills)
            SchemaUtils.create(TeamKills)
            SchemaUtils.create(Messages)
            SchemaUtils.create(NoTrack)
            SchemaUtils.create(Warns)
            SchemaUtils.create(DiscordPlayers)
            SchemaUtils.create(Donations)
        }
    }

    fun getAllServers(): Map<Int, String> {
        return transaction {
            Servers.selectAll()
                .associate { row ->
                    row[Servers.id] to row[Servers.name]
                }
        }
    }


    fun getServerIdFromName(name: String): Int? {
        val result = transaction {
            Servers
                .selectAll()
                .where { Servers.name eq name }
                .firstOrNull()
        }
        if (result == null) {
            return null
        }
        return result[Servers.id]
    }

    fun getServerNameFromId(id: Int): String? {
        val result = transaction {
            Servers
                .selectAll()
                .where { Servers.id eq id }
                .firstOrNull()
        }
        if (result == null) {
            return null
        }
        return result[Servers.name]
    }

    fun getMissionIdFromName(name: String): Int? {
        val result = transaction {
            Missions
                .selectAll()
                .where { Missions.name eq name }
                .firstOrNull()
        }
        if (result == null) {
            return null
        }
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
        if (result == null) {
            throw NullPointerException("Server $name not found or has no ongoing mission.")
        }
        return result[ServerMissions.id]
    }

    fun newServer(name: String, maxPlayers: Int) {
        transaction {
            Servers.insert {
                it[Servers.name] = name
                it[Servers.maxPlayers] = maxPlayers
            }
        }
    }

    fun transformTimestamp(timestamp: Timestamp): Instant {
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

    @Suppress("unused")
    fun deleteOldMessages() {
        val cutoff: Instant = Clock.System.now() - 90.days
        transaction {
            Messages.deleteWhere { Messages.time less cutoff }
        }
    }

    fun newMission(name: String, pvp: Boolean) {
        transaction {
            Missions.insert {
                it[Missions.name] = name
                it[Missions.pvp] = pvp
            }
        }
    }

    fun endMission(mission: Long, time: Timestamp) {
        endAllSorties(time)
        transaction {
            ServerMissions.update({ ServerMissions.id eq mission })
            { it[endTime] = transformTimestamp(time) }
        }
    }

    fun closeAllPlayers(mission: Long) {
        val currentTime = Clock.System.now()
        transaction {
            MissionPlayers.update({ MissionPlayers.mission eq mission and MissionPlayers.leaveTime.isNull() }) {
                it[MissionPlayers.leaveTime] = currentTime
            }
        }
    }

    fun startMission(name: String, time: Timestamp, serverName: String): Long {
        val serverID = getServerIdFromName(serverName)
        val missionID = getMissionIdFromName(name)
        if (serverID == null || missionID == null) {
            throw NullPointerException("Server $serverName or mission $name not found.")
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

    fun startSortie(steamID: ULong, aircraft: String, time: Timestamp): Long {
        return transaction {
            Sorties.insert {
                it[Sorties.steamID] = steamID
                it[Sorties.aircraft] = aircraft
                it[startTime] = transformTimestamp(time)
            } get Sorties.id
        }
    }

    fun endSortie(steamID: ULong, killed: Boolean, time: Timestamp) {
        transaction {
            Sorties.update({ Sorties.steamID eq steamID and Sorties.endTime.isNull() }) {
                it[endTime] = transformTimestamp(time)
                it[Sorties.killed] = killed
            }
        }
    }

    fun endAllSorties(time: Timestamp) {
        transaction {
            Sorties.update({ Sorties.endTime.isNull() }) {
                it[endTime] = transformTimestamp(time)
                it[Sorties.killed] = false
            }
        }
    }

    fun addBan(steamID: ULong, reason: String, startTime: Timestamp, endTime: Timestamp) {
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
            Bans.update({ Bans.steamID eq steamID and Bans.endTime.isNull() }) {
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

    fun playerJoin(steamID: ULong, mission: Long, time: Timestamp, name: String) {
        transaction {
            MissionPlayers.insert {
                it[MissionPlayers.steamID] = steamID
                it[MissionPlayers.mission] = mission
                it[MissionPlayers.joinTime] = transformTimestamp(time)
                it[MissionPlayers.name] = name
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

    fun playerJoinFaction(steamID: ULong, faction: String) {
        transaction {
            val row = MissionPlayers
                .selectAll()
                .where {
                    (MissionPlayers.steamID eq steamID) and
                            MissionPlayers.faction.isNull() and
                            MissionPlayers.leaveTime.isNull() and
                            MissionPlayers.score.isNull()
                }
                .orderBy(MissionPlayers.joinTime to SortOrder.DESC)
                .firstOrNull()

            if (row != null) {
                MissionPlayers.update({ MissionPlayers.id eq row[MissionPlayers.id] }) {
                    it[MissionPlayers.faction] = faction
                }
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

    fun addLink(steamID: ULong, discordID: String) {
        transaction {
            DiscordPlayers.insert {
                it[DiscordPlayers.steamID] = steamID
                it[DiscordPlayers.discordName] = discordID
            }
        }
    }

    fun addDonation(donorID: ULong, rcvId: ULong, amount: Int, time: Timestamp) {
        transaction {
            Donations.insert {
                it[donatorSteamID] = donorID
                it[receiverSteamID] = rcvId
                it[Donations.amount] = amount
                it[Donations.timestamp] = transformTimestamp(time)
            }
        }
    }


    fun isUserInDb(discordID: String): Boolean {
        val result = transaction {
            DiscordPlayers.selectAll().where {
                DiscordPlayers.discordName eq discordID
            }.count()
        }
        return (result == 1L)
    }

    fun getLinkedUsers(): Collection<String> {
        return transaction {
            DiscordPlayers.select(DiscordPlayers.discordName).asIterable().map { it[DiscordPlayers.discordName] }
        }
    }

    fun getLastPlayerName(player: ULong): String {
        val result = transaction {
            MissionPlayers
                .select(MissionPlayers.name)
                .where { MissionPlayers.steamID eq player }
                .orderBy(MissionPlayers.joinTime to SortOrder.DESC)
                .firstOrNull()
                ?.get(MissionPlayers.name)
        }
        return (result ?: "")
    }

    fun getSteamIDForDiscord(discordID: String): ULong? {
        return transaction {
            DiscordPlayers
                .select(DiscordPlayers.steamID)
                .where { DiscordPlayers.discordName eq discordID }
                .firstOrNull()?.get(DiscordPlayers.steamID)
        }
    }

    fun getKillsForUser(
        steamID: ULong,
        pageNumber: Int,
        pageLength: Int = 10,
        playerOnly: Boolean = false
    ): Pair<List<Kill>, Boolean> {
        val condition =
            if (playerOnly) (Kills.killerID eq steamID) and (Kills.killedName inList Aircraft.entries.map { it.craft }) else Kills.killerID eq steamID
        val result = transaction {
            Kills
                .select(Kills.killedID, Kills.killedName, Kills.weapon)
                .where { condition }
                .orderBy(Kills.time to SortOrder.DESC)
                .offset((pageNumber * pageLength).toLong())
                .limit(pageLength + 1)
                .toList()
        }
        val kills = result.map { kill ->
            val playerId = kill[Kills.killedID]
            val playerName = if (playerId != null) getLastPlayerName(playerId) else null
            Kill(playerName, kill[Kills.weapon]!!, kill[Kills.killedName], isAircraft(kill[Kills.killedName]))
        }
        val hasNext = kills.size > pageLength
        return Pair(kills.take(pageLength), hasNext)
    }

    fun getDeathsForUser(steamID: ULong, pageNumber: Int, pageLength: Int = 10): Pair<List<Kill>, Boolean> {
        val result = transaction {
            Kills
                .select(Kills.killerID, Kills.killerName, Kills.weapon)
                .where { Kills.killedID eq steamID and (Kills.killedName inList Aircraft.entries.map { it.craft }) }
                .orderBy(Kills.time to SortOrder.DESC)
                .offset((pageNumber * pageLength).toLong())
                .limit(pageLength + 1)
                .toList()
        }
        val kills = result.map { kill ->
            val playerId = kill[Kills.killerID]
            val playerName = if (playerId != null) getLastPlayerName(playerId) else null
            Kill(
                playerName,
                kill[Kills.weapon] ?: "the ground",
                kill[Kills.killerName] ?: "A crash",
                isAircraft(kill[Kills.weapon] ?: "the ground")
            )
        }
        val hasNext = kills.size > pageLength
        return Pair(kills.take(pageLength), hasNext)
    }

    fun getSortiesForUser(steamID: ULong, pageNumber: Int, pageLength: Int = 10): Pair<List<Sortie>, Boolean> {
        val result = transaction {
            Sorties
                .selectAll()
                .where { Sorties.steamID eq steamID }
                .orderBy(Sorties.startTime to SortOrder.DESC)
                .offset((pageNumber * pageLength).toLong())
                .limit(pageLength + 1)
                .toList()
        }
        val sorties = result.map { sortie ->
            val elapsed: Duration = (sortie[Sorties.endTime] ?: Clock.System.now()).minus(sortie[Sorties.startTime])

            Sortie(
                toAircraft(sortie[Sorties.aircraft]),
                sortie[Sorties.startTime],
                elapsed,
                sortie[Sorties.killed] ?: false
            )
        }
        val hasNext = sorties.size > pageLength
        return Pair(sorties, hasNext)
    }

    fun getWeaponsToKillsForUser(
        steamID: ULong,
        aircraftOnly: Boolean = false,
        playerOnly: Boolean = false
    ): Map<String, Long> {
        var condition = Kills.killerID eq steamID
        if (aircraftOnly || playerOnly) {
            condition = condition and (Kills.killedName inList Aircraft.entries.map { it.craft })
        }
        if (playerOnly) {
            condition = condition and (Kills.killedID.isNotNull())
        }
        val countExpr = Kills.weapon.count()
        return transaction {
            Kills
                .select(Kills.weapon, countExpr)
                .where { condition }
                .groupBy(Kills.weapon)
                .associate {
                    it[Kills.weapon] to it[countExpr]
                }
                .filterKeys { it != null }
                .mapKeys { it.key!! }
        }
    }

    fun getTargetsToKillsForUser(
        steamID: ULong,
        aircraftOnly: Boolean = false,
        playerOnly: Boolean = false
    ): Map<String, Long> {
        var condition = Kills.killerID eq steamID
        if (aircraftOnly || playerOnly) {
            condition = condition and (Kills.killedName inList Aircraft.entries.map { it.craft })
        }
        if (playerOnly) {
            condition = condition and (Kills.killedID.isNotNull())
        }
        val countExpr = Kills.killedName.count()
        return transaction {
            Kills
                .select(Kills.killedName, countExpr)
                .where { condition }
                .groupBy(Kills.killedName)
                .associate {
                    it[Kills.killedName] to it[countExpr]
                }
                .mapKeys { it.key }
        }
    }

    fun getKDForPlayer(steamID: ULong): Double {
        val kills: Long = transaction {
            Kills
                .selectAll()
                .where { Kills.killerID eq steamID and Kills.killedID.isNotNull() }
                .count()
        }
        val deaths: Long = transaction {
            Kills
                .selectAll()
                .where { Kills.killedID eq steamID and Kills.killerID.isNotNull() }
                .count()
        }
        return kills.toDouble() / deaths.toDouble()
    }

    fun getAllBans(): List<UserReasonTime> {
        val bans = transaction {
            Bans
                .leftJoin(MissionPlayers, { Bans.steamID }, { MissionPlayers.steamID })
                .select(Bans.steamID, Bans.reason, Bans.startTime, MissionPlayers.name)
                .where {
                    (Bans.endTime.isNull() or Bans.endTime.greater(Clock.System.now()))
                }
                .withDistinct()
                .orderBy(Bans.startTime to SortOrder.DESC)
                .toList()
        }
        return bans.map {
            @Suppress("USELESS_ELVIS")
            UserReasonTime(
                it[Bans.steamID],
                it[MissionPlayers.name] ?: "Unknown user",
                it[Bans.reason],
                it[Bans.startTime],
            )
        }
    }

    fun getMissions(user: ULong?): List<Mission> {
        val data = if (user == null) {
            transaction {
                (ServerMissions innerJoin Missions innerJoin Servers)
                    .select(ServerMissions.columns - ServerMissions.id + Missions.name - ServerMissions.server + Servers.name)
                    .orderBy(ServerMissions.startTime to SortOrder.DESC)
                    .toList()
            }
        } else {
            transaction {
                (ServerMissions innerJoin MissionPlayers innerJoin Missions innerJoin Servers)
                    .select(ServerMissions.columns - ServerMissions.id + Missions.name - ServerMissions.server + Servers.name)
                    .where { MissionPlayers.steamID eq user }
                    .orderBy(ServerMissions.startTime to SortOrder.DESC)
                    .distinct()
                    .toList()
            }
        }
        return data.map {
            Mission(
                it[Missions.name],
                it[Servers.name],
                it[ServerMissions.startTime]
            )
        }
    }

    fun getKicks(user: ULong?): List<UserReasonTime>{
        val kicks = if (user == null){
            transaction {
                Kicks
                    .leftJoin(MissionPlayers, { Kicks.steamID }, { MissionPlayers.steamID })
                    .select(Kicks.columns - Kicks.id + MissionPlayers.name)
                    .withDistinctOn(Kicks.id)
                    .orderBy(Kicks.id to SortOrder.DESC)
                    .toList()
            }
        } else {
            transaction {
                Kicks
                    .select(Kicks.columns - Kicks.id)
                    .where { Kicks.steamID eq user }
                    .orderBy(Kicks.time to SortOrder.DESC)
                    .toList()
            }
        }
        return kicks.map {
            UserReasonTime(
                it[Kicks.steamID],
                if (user == null) it[MissionPlayers.name] else null,
                it[Kicks.reason],
                it[Kicks.time]
            )
        }
    }

    fun getWarns(user: ULong?): List<UserReasonTime> {
        val warns = if (user == null) {
            transaction {
                Warns
                    .leftJoin(MissionPlayers, { Warns.steamID }, { MissionPlayers.steamID })
                    .select(Warns.columns - Warns.id + MissionPlayers.name)
                    .withDistinctOn(Warns.id)
                    .orderBy(Warns.id to SortOrder.DESC)
                    .toList()
            }
        } else {
            transaction {
                Warns
                    .select(Warns.columns - Warns.id)
                    .where { Warns.steamID eq user }
                    .orderBy(Warns.time to SortOrder.DESC)
                    .toList()
            }
        }
        return warns.map {
            UserReasonTime(
                it[Warns.steamID],
                if (user == null) it[MissionPlayers.name] else null,
                it[Warns.reason],
                it[Warns.time]
            )
        }
    }

}
