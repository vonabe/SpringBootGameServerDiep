package com.vonabe.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonabe.server.data.EventType
import com.vonabe.server.data.send.ConnectUser
import com.vonabe.server.data.send.Player
import com.vonabe.server.utils.JsonConverter.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals

@SpringBootTest
class JsonTest {

    private lateinit var mapper: ObjectMapper

    @BeforeEach
    fun setup() {
        mapper = ObjectMapper()
    }

    @Test
    fun testJson() {
        val connectPacket = ConnectUser(EventType.CONNECT, Player())
        println(connectPacket.toJson(mapper))
        println(mapper.writeValueAsString(connectPacket))
        assertEquals(mapper.writeValueAsString(connectPacket), connectPacket.toJson(mapper))
    }

}
