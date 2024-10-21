package com.vonabe.server

import com.vonabe.server.game.CameraView
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test

@SpringBootTest
class CameraViewTest {

    private lateinit var cameraView: CameraView

    @BeforeEach
    fun setUp() {
        // Инициализация перед каждым тестом
        cameraView = CameraView(centerX = 0f, centerY = 0f, width = 1920, height = 1080, (1920 * 10).toFloat(), (1080 * 10).toFloat())
    }

    @Test
    fun testHasCrossedBorderWithinBounds() {
        // Проверим, что когда камера не пересекает границу, возвращается false
        val crossedBorder = cameraView.hasCrossedBorder(4f, 4f)
        assertFalse(crossedBorder)
    }

    @Test
    fun testHasCrossedBorderCrossedX() {
        // Проверим, что при пересечении границы по X возвращается true
        val crossedBorder = cameraView.hasCrossedBorder(960f, 0f)
        assertTrue(crossedBorder)
    }

    @Test
    fun testHasCrossedBorderCrossedY() {
        // Проверим, что при пересечении границы по Y возвращается true
        val crossedBorder = cameraView.hasCrossedBorder(0f, 540f)
        assertTrue(crossedBorder)
    }

    @Test
    fun testHasCrossedBorderCrossedBoth() {
        // Проверим, что при пересечении границы по X и Y возвращается true
        val crossedBorder = cameraView.hasCrossedBorder(960f, 6f)
        assertTrue(crossedBorder)
    }

    @Test
    fun testUpdatePosition() {
        // Проверим, что обновление позиции работает корректно
        cameraView.updatePosition()
        assertEquals(5f, cameraView.centerX, 0.001f)
        assertEquals(5f, cameraView.centerY, 0.001f)
    }

}
