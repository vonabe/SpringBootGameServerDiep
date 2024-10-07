package com.vonabe.server.websocket

import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component

@Component
class NicknameHandler(resourceLoader: ResourceLoader) {

    private var listNickname: List<String> =
        resourceLoader.getResource("classpath:nicknames.csv").inputStream.bufferedReader().readLines()

    fun randomNickname(): String {
        return listNickname?.random() ?: "Empty"
    }

}
