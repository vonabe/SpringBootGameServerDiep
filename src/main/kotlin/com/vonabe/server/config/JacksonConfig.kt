package com.vonabe.server.config

import com.badlogic.gdx.math.Vector2
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vonabe.server.data.serializable.Vector2Serializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {

    @Bean
    fun objectMapper(): ObjectMapper {
        val objectMapper = jacksonObjectMapper()

        // Создаем модуль Jackson с кастомным сериализатором
        val module = SimpleModule()
        module.addSerializer(Vector2::class.java, Vector2Serializer())
//        module.addDeserializer(Move::class.java, MoveDeserializer())
        // Регистрируем модуль в ObjectMapper
        objectMapper.registerModules(module)

        return objectMapper
    }
}
