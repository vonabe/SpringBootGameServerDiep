package com.vonabe.server.game

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Timer
import com.fasterxml.jackson.databind.ObjectMapper
import com.vonabe.server.data.EventType
import com.vonabe.server.data.receive.Message
import com.vonabe.server.data.receive.Move
import com.vonabe.server.data.receive.Shot
import com.vonabe.server.game.service.*
import com.vonabe.server.utils.JsonConverter.getType
import com.vonabe.server.utils.JsonConverter.toObject
import com.vonabe.server.websocket.ConnectListener
import com.vonabe.server.websocket.DisconnectListener
import com.vonabe.server.websocket.MessageListener
import com.vonabe.server.websocket.WebSocketHandler
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.system.measureTimeMillis

@Component
class GameLoop(
    private val playerService: PlayerService,
    private val bulletService: BulletService,
    private val collisionService: CollisionService,
    private val foodService: FoodService,
    private val eventService: EventService,
    private val mapper: ObjectMapper,
    private var webSocketHandler: WebSocketHandler,
) : ApplicationAdapter(), ConnectListener, DisconnectListener, MessageListener {

    private var globalTime: Long = 0
    private var timeLoggerService: Float = 0.0f
    private val services = Array<GameService>()

    override fun create() {
        webSocketHandler.apply {
            connectListener = this@GameLoop
            disconnectListener = this@GameLoop
            messageListener = this@GameLoop
        }

        Timer.schedule(object : Timer.Task() {
            override fun run() {
                println("FPS: ${Gdx.graphics.framesPerSecond}")
            }
        }, 1f, 1f)

        services.addAll(playerService, bulletService, foodService, collisionService, eventService)
    }

    override fun render() {
        val deltaTime = Gdx.graphics.deltaTime
        globalTime += (deltaTime * 1000L).toLong()
        timeLoggerService += deltaTime

        services.forEach {
            val measureTimeMillis = measureTimeMillis {
                it.update(deltaTime)
            }
            if (timeLoggerService > 1) {
                println("${it.javaClass.simpleName}: $measureTimeMillis ms.")
            }
        }

        if (timeLoggerService > 1) {
            timeLoggerService = 0.0f
        }
    }

    override fun onConnect(session: WebSocketSession) {
        playerService.createPlayer(session.id)
    }

    override fun onDisconnect(session: WebSocketSession) {
        playerService.disconnectPlayer(session.id)
    }

    override fun onMessage(session: WebSocketSession, message: String) {
        val eventType = message.getType(mapper)
        when (eventType) {
            EventType.MOVE -> {
                val move = message.toObject(Move::class.java, mapper)
                playerService.move(session.id, move)
            }

            EventType.SHOT -> {
                val shot = message.toObject(Shot::class.java, mapper)
                val player = playerService.getPlayer(session.id)
                player.autoShot = shot.autoShot
                bulletService.shot(player)
            }

            EventType.MESSAGE -> {
                webSocketHandler.broadcastMessage(message.toObject(Message::class.java, mapper))
            }

            else -> {
                println("Type not found $message")
            }
        }
    }

}
