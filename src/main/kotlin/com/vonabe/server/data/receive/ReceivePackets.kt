package com.vonabe.server.data.receive

import com.vonabe.server.data.EventType

enum class MoveType { UP, DOWN, LEFT, RIGHT }

data class Move(val eventType: EventType, val angle: Int, val direction: Int)
data class Shot(val eventType: EventType, val autoShot: Boolean)
