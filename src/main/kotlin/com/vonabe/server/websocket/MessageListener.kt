package com.vonabe.server.websocket

import org.springframework.web.socket.WebSocketSession

interface MessageListener {
    fun onMessage(session: WebSocketSession, message: String)
}
