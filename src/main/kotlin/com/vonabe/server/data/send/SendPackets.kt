package com.vonabe.server.data.send

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Pool.Poolable
import com.fasterxml.jackson.annotation.JsonIgnore
import com.vonabe.server.data.EventType
import com.vonabe.server.game.CameraView

data class StateUser(val eventType: EventType, val player: Player)
data class StateUsers(val eventType: EventType, val player: List<Player>)
data class MoveUser(val eventType: EventType, val uid: String, val x: Float, val y: Float, val a: Int)
data class MoveUsers(val eventType: EventType, val users: List<MoveUser>)

data class ConnectUser(val eventType: EventType, val player: Player)
data class DisconnectUser(val eventType: EventType, val uid: String)

enum class TankType { PUSSY, DOUBLE, TANK }

data class Player(
    var uid: String = "",
    var name: String = "",
    val velocity: Vector2 = Vector2(),
    var angle: Int = 0,
    @JsonIgnore
    var angleChange: Boolean = false,
    var speed: Float = 500f,
    var typeTank: TankType = TankType.PUSSY,
    var score: Int = 0,
    var nextLevelScore: Int = 1000,
    var level: Int = 0,
    var health: Float = 100f,
    @JsonIgnore
    var autoShot: Boolean = false,
    @JsonIgnore
    val cameraViewport: CameraView = CameraView(
        0f, 0f,
        1920 * 2, 1080 * 2,
        1920f, 1080f
    ),
) : Bounds(), Poolable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Player) return false
        return uid == other.uid// Сравнение по уникальным полям
    }

    override fun hashCode(): Int {
        return uid.hashCode() + uid.hashCode()
    }

    override fun reset() {
        uid = ""
        name = ""
        position.set(0f, 0f)
        angle = 0
        typeTank = TankType.PUSSY
        score = 0
        nextLevelScore = 1000
        level = 0
        radius = 15f
        autoShot = false
        health = 100f
    }
}

data class Bullets(var eventType: EventType, val bullets: List<Bullet>)

data class Bullet(
    var eventType: EventType,
    var id: Int,
//    @JsonIgnore
    var ownerUid: String,
    var direction: Vector2 = Vector2(),
    var remove: Boolean = false,
    var speed: Float,
    @JsonIgnore
    var timestamp: Long,
    @JsonIgnore
    var penetration: Float = 10f,
) : Bounds(), Poolable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Bullet) return false
        return id == other.id && ownerUid == other.ownerUid // Сравнение по уникальным полям
    }

    override fun hashCode(): Int {
        return id.hashCode() + ownerUid.hashCode()
    }

    override fun reset() {
        id = -1
        position.set(0f, 0f)
        direction.set(0f, 0f)
        remove = false
        speed = -1f
        radius = -1f
        timestamp = -1L
        penetration = -1f
        ownerUid = ""
        eventType = EventType.SHOT
    }
}

data class Food(val eventType: EventType, var id: Int, var remove: Boolean = false) : Poolable, Bounds() {
    override fun reset() {
        id = -1
        position.set(0f, 0f)
        remove = false
        position.set(0f, 0f)
        radius = 0f
    }

}

data class Foods(val eventType: EventType, val foods: List<Food>)

abstract class Bounds {
    val position: Vector2 = Vector2()
    var radius: Float = 0f
}
