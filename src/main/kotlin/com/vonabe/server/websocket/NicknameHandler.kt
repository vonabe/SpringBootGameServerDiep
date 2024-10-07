package com.vonabe.server.websocket

import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component

@Component
class NicknameHandler(private val resourceLoader: ResourceLoader) {

    private var listNickname: MutableList<String>? = null

    init {
        val resource = resourceLoader.getResource("classpath:nicknames.csv")
        val inputStream = resource.inputStream

        // Now you can read from the input stream as needed
        // For example, if you want to read it as a string
        val nicknames = inputStream.bufferedReader().use { it.readText() }
        listNickname = nicknames.lines().toMutableList()

//        ResourceUtils.getFile("classpath:nicknames.csv").let {
//            listNickname = Files.readAllLines(it.toPath())
        //            listNickname = Files.readAllLines(ClassPathResource("/nicknames.csv").file.toPath())
//        }
    }

    fun randomNickname(): String {
        return listNickname?.random() ?: "Empty"
    }

}
