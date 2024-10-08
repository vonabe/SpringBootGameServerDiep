package com.vonabe.server.websocket

import com.badlogic.gdx.utils.Array
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.AbstractWebSocketHandler

@Component
class WebSocketHandler : AbstractWebSocketHandler() {
    private val sessions = Array<WebSocketSession>()

    lateinit var connectListener: ConnectListener
    lateinit var messageListener: MessageListener
    lateinit var disconnectListener: DisconnectListener

    fun broadcastMessageAsync(value: String) {
        synchronized(sessions) {
            sessions.forEach { session ->
                kotlin.runCatching {
                    // Проверяем, открыта ли сессия перед отправкой
                    if (session.isOpen) {
                        session.sendMessage(TextMessage(value))
                    }
                }.onFailure { ex ->
                    // Логгирование ошибок
                    ex.printStackTrace()
                }
            }
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions.add(session)
        connectListener.onConnect(session)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        messageListener.onMessage(session, message.payload)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.removeValue(session, true)
        disconnectListener.onDisconnect(session)
    }

}
