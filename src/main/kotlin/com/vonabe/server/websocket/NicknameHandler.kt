package com.vonabe.server.websocket

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.nio.file.Files

@Component
class NicknameHandler {

    private val listNickname = Files.readAllLines(ClassPathResource("nicknames.csv").file.toPath())

    fun randomNickname(): String {
        return listNickname.random()
    }

}
