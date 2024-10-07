package com.vonabe.server.websocket

import org.springframework.web.socket.WebSocketSession

interface ConnectListener {
    fun onConnect(session: WebSocketSession)
}
