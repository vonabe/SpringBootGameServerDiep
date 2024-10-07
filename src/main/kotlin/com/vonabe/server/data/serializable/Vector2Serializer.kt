package com.vonabe.server.data.serializable

import com.badlogic.gdx.math.Vector2
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import java.io.IOException

class Vector2Serializer : JsonSerializer<Vector2>() {
    @Throws(IOException::class)
    override fun serialize(value: Vector2, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        gen.writeNumberField("x", value.x)
        gen.writeNumberField("y", value.y)
        gen.writeEndObject()
    }
}
