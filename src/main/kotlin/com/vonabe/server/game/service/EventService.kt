package com.vonabe.server.game.service

import com.badlogic.gdx.utils.Array
import org.springframework.stereotype.Service

@Service
class EventService : GameService {

    private val mutableData = Array<Any>()

    fun addEvent(data: Any) {
        if (!mutableData.contains(data)) {
            mutableData.add(data)
        }
    }

    override fun update(delta: Float) {

    }
}
