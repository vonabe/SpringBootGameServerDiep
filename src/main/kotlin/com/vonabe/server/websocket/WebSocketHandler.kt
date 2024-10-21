package com.vonabe.server.websocket

import com.badlogic.gdx.utils.ObjectMap
import com.fasterxml.jackson.databind.ObjectMapper
import com.vonabe.server.utils.JsonConverter.toJson
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.AbstractWebSocketHandler
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Component
class WebSocketHandler(
    private val mapper: ObjectMapper,
) : AbstractWebSocketHandler() {

    private val sessions = ObjectMap<String, WebSocketSession>()

    lateinit var connectListener: ConnectListener
    lateinit var messageListener: MessageListener
    lateinit var disconnectListener: DisconnectListener

    private val executorService: ExecutorService = Executors.newFixedThreadPool(10) // Пул на 50 потоков

    fun broadcastMessage(usersUid: List<String>, value: Any) {
        val textMessage = TextMessage(value.toJson(mapper))
        synchronized(sessions) {
            sessions.filter { usersUid.contains(it.key) }.forEach { session ->
                kotlin.runCatching {
                    if (session.value.isOpen) {
                        executorService.submit {
                            session.value.sendMessage(textMessage)
                        }
                    }
                }
            }
        }
    }

    fun broadcastMessage(value: Any) {
        val textMessage = TextMessage(value.toJson(mapper))
        synchronized(sessions) {
            sessions.values().forEach { session ->
                kotlin.runCatching {
                    // Проверяем, открыта ли сессия перед отправкой
                    if (session.isOpen) {
                        executorService.submit {
                            session.sendMessage(textMessage)
                        }
                    }
                }.onFailure { ex ->
                    // Логгирование ошибок
                    ex.printStackTrace()
                }
            }
        }
    }

    fun sendOnly(uid: String, value: Any) {
        synchronized(sessions) {
            kotlin.runCatching {
                sessions[uid]?.apply { if (isOpen) sendMessage(TextMessage(value.toJson(mapper))) }
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        synchronized(sessions) {
            sessions.put(session.id, session)
        }
        connectListener.onConnect(session)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        messageListener.onMessage(session, message.payload)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        synchronized(sessions) {
            sessions.remove(session.id)
        }
        disconnectListener.onDisconnect(session)
    }

}
