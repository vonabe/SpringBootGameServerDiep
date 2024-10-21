package com.vonabe.server.config

import org.apache.catalina.connector.Connector
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WebServerConfig {
    @Bean
    fun servletContainer(): WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
        return WebServerFactoryCustomizer<TomcatServletWebServerFactory> { server: TomcatServletWebServerFactory ->
            // Добавляем дополнительный Connector для HTTP (без SSL)
            val connector = Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL)
            connector.port = 8989 // Порт для ws://
            server.addAdditionalTomcatConnectors(connector)
        }
    }
}
