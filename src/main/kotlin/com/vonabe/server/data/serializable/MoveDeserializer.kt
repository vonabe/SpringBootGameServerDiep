package com.vonabe.server.data.serializable

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.vonabe.server.data.EventType
import com.vonabe.server.data.receive.Move
import java.io.IOException

class MoveDeserializer : JsonDeserializer<Move>() {
    @Throws(IOException::class)
    override fun deserialize(json: JsonParser?, ctx: DeserializationContext?): Move {
        // Получаем корневой объект JSON
        val node: JsonNode = json!!.codec.readTree(json)

        // Десериализуем поля JSON вручную
        val eventType = EventType.valueOf(node.get("eventType").asText())  // Преобразуем строку в enum
        val angle = node.get("angle").asInt()
        val direction = node.get("direction").asInt()

        // Возвращаем новый объект Move
        return Move(eventType, angle, direction)
    }
}
