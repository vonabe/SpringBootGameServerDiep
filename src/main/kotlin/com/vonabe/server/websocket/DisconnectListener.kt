package com.vonabe.server.websocket

import org.springframework.web.socket.WebSocketSession

interface DisconnectListener {
    fun onDisconnect(session: WebSocketSession)
}
