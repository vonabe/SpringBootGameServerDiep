package com.vonabe.server.data.send

import com.badlogic.gdx.math.Vector2
import com.fasterxml.jackson.annotation.JsonIgnore
import com.vonabe.server.data.EventType

data class StateUser(val eventType: EventType, val player: Player)
data class StateUsers(val eventType: EventType, val player: List<Player>)
data class MoveUser(val eventType: EventType, val uid: String, val x: Float, val y: Float, val a: Int)

data class ConnectUser(val eventType: EventType, val player: Player)
data class DisconnectUser(val eventType: EventType, val uid: String)

enum class TankType { PUSSY, DOUBLE, TANK }

data class Player(
    val uid: String = "",
    val name: String = "",
    val position: Vector2 = Vector2(),
    var angle: Int = 0,
    var typeTank: TankType,
    var score: Int,
    var nextLevelScore: Int,
    var level: Int,
    var radius: Float,
    @JsonIgnore
    var autoShot: Boolean = false,
    var health: Float = 100f,
)

data class Bullets(var eventType: EventType, val bullets: List<Bullet>)

data class Bullet(
    var eventType: EventType,
    var id: Int,
//    @JsonIgnore
    var ownerUid: String,
    var position: Vector2,
    var direction: Vector2,
    var remove: Boolean = false,
    var speed: Float,
    var radius: Float,
    @JsonIgnore
    var timestamp: Long,
    @JsonIgnore
    var penetration: Float = 10f,
)
