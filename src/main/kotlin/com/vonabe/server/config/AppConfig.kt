package com.vonabe.server.config

import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.vonabe.server.game.GameLoop
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppConfig {
    @Bean
    fun getApplication(loop: GameLoop): HeadlessApplication {
        return HeadlessApplication(loop)
    }
}
