package com.vonabe.server.websocket

import org.springframework.stereotype.Component
import org.springframework.util.ResourceUtils
import java.nio.file.Files

@Component
class NicknameHandler {

    private var listNickname: MutableList<String>? = null

    init {
        ResourceUtils.getFile("classpath:nicknames.csv").let {
            listNickname = Files.readAllLines(it.toPath())
    //            listNickname = Files.readAllLines(ClassPathResource("/nicknames.csv").file.toPath())
        }
    }

    fun randomNickname(): String {
        return listNickname?.random() ?: "Empty"
    }

}
