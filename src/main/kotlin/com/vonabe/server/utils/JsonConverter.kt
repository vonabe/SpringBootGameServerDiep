package com.vonabe.server.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonabe.server.data.EventType

object JsonConverter {

    fun Any.toJson(mapper: ObjectMapper): String {
        return mapper.writeValueAsString(this)
    }

    fun <T> String.toObject(clazz: Class<T>, mapper: ObjectMapper): T {
        return mapper.readValue(this, clazz)
    }

    fun String.getType(mapper: ObjectMapper): EventType {
        return mapper.readValue(mapper.readTree(this).get("eventType").toJson(mapper), EventType::class.java)
    }

}
