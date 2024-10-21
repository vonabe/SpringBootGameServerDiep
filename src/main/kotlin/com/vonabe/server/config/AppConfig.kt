package com.vonabe.server.config

import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.math.Rectangle
import com.vonabe.server.game.GameLoop
import com.vonabe.server.utils.NoiseMapGenerator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppConfig {
    @Bean
    fun getApplication(loop: GameLoop): HeadlessApplication {
        return HeadlessApplication(loop)
    }

    @Bean
    fun getNoiseMapGenerator(worldSize: Rectangle): NoiseMapGenerator {
        return NoiseMapGenerator(
            worldSize.width.toInt(), worldSize.height.toInt(),
            1920, 1080
        )
    }

    @Bean
    fun getSizeWorld(): Rectangle {
        return Rectangle(0f, 0f, 1920 * 10f, 1080 * 10f)
    }
}
