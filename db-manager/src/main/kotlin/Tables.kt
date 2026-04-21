package com.graywar.noServerManager.dbManager

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.timestamp

object Servers: Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 50)
    override val primaryKey = PrimaryKey(id)
}

object Missions: Table() {
    val id = integer("id").autoIncrement()
    val pvp = bool("pvp")
    val name = varchar("name", 50)
    override val primaryKey = PrimaryKey(id)
}

object ServerMissions: Table() {
    val id = long("id").autoIncrement()
    val server = integer("server").references(Servers.id)
    val mission = integer("mission").references(Missions.id)
    val startTime = timestamp("starttime")
    val endTime = timestamp("endtime").nullable()
    override val primaryKey = PrimaryKey(id)
}

object MissionPlayers: Table() {
    val id = long("id").autoIncrement()
    val steamID = ulong("steamid")
    val name = varchar("name", 50)
    val mission = long("mission").references(ServerMissions.id)
    val joinTime = timestamp("joinTime")
    val leaveTime = timestamp("leavetime").nullable()
    val score = float("score").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Sorties: Table(){
    val id = long("id").autoIncrement()
    val steamID = ulong("steamid")
    val aircraft = varchar("aircraft", 50)
    val startTime = timestamp("startTime")
    val endTime = timestamp("endtime").nullable()
    val killed = bool("killed").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Bans: Table() {
    val id = long("id").autoIncrement()
    val steamID = ulong("steamid")
    val reason = varchar("reason", 500)
    val startTime = timestamp("startTime")
    val endTime = timestamp("endtime").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Kicks: Table() {
    val id = long("id").autoIncrement()
    val steamID = ulong("steamid")
    val reason = varchar("reason", 500)
    val time = timestamp("time")
    override val primaryKey = PrimaryKey(id)
}

object Warns: Table() {
    val id = long("id").autoIncrement()
    val steamID = ulong("steamid")
    val reason = varchar("reason", 500)
    val time = timestamp("time")
    override val primaryKey = PrimaryKey(id)
}


object Kills: Table() {
    val id = long("id").autoIncrement()
    val mission = long("mission").references(ServerMissions.id)
    val killerID = ulong("killerid").nullable()
    val killerName = varchar("killername", 50).nullable()
    val killedID = ulong("killedID").nullable()
    val killedName = varchar("killedName", 50)
    val weapon = varchar("weapon",100).nullable()
    val time = timestamp("time")
    override val primaryKey = PrimaryKey(id)
}

object TeamKills: Table() {
    val id = long("id").autoIncrement()
    val mission = long("mission").references(ServerMissions.id)
    val killerID = ulong("killerid")
    val killerName = varchar("killername", 50)
    val killedID = ulong("killedID")
    val killedName = varchar("killedName", 50)
    val weapon = varchar("weapon", 100)
    val time = timestamp("time")
    override val primaryKey = PrimaryKey(id)
}

object Messages: Table() {
    val id = long("id").autoIncrement()
    val sender = ulong("sender")
    val time = timestamp("time")
    val channel = varchar("channel", 50)
    val mission = long("mission").references(ServerMissions.id)
    val text = varchar("text", 500)
    override val primaryKey = PrimaryKey(id)
}

object NoTrack: Table() {
    val user = ulong("user")
    override val primaryKey = PrimaryKey(user)
}

object DiscordPlayers: Table() {
    val steamID = ulong("steamid").uniqueIndex()
    val discordName = varchar("discord_name", 32).uniqueIndex()
}

object Donations: Table() {
    val id = long("id").autoIncrement()
    val donatorSteamID = ulong("donatorSteamId")
    val receiverSteamID = ulong("receiverSteamId")
    val amount = integer("amount")
    val timestamp = timestamp("timestamp")
    override val primaryKey = PrimaryKey(id)
}
