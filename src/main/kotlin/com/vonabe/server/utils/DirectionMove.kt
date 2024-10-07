package com.vonabe.server.utils

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2


object DirectionMove {
    const val UP = 1 shl 0    // 0001
    const val DOWN = 1 shl 1  // 0010
    const val LEFT = 1 shl 2  // 0100
    const val RIGHT = 1 shl 3 // 1000

    fun Int.toMove(speed: Float): Vector2 {
        val direction = Vector2()
        if ((this and UP) != 0) {
            // Двигаемся вверх
            direction.y = speed
        }
        if ((this and DOWN) != 0) {
            // Двигаемся вниз
            direction.y = -speed
        }
        if ((this and LEFT) != 0) {
            // Двигаемся влево
            direction.x = -speed
        }
        if ((this and RIGHT) != 0) {
            // Двигаемся вправо
            direction.x = speed
        }
        return direction
    }

    fun Float.calculateDirection(): Vector2 {
        // Используем MathUtils из LibGDX для конвертации угла в радианы и расчёта направления
        val directionX = MathUtils.cosDeg(this)
        val directionY = MathUtils.sinDeg(this)

        // Возвращаем нормализованный вектор направления
        return Vector2(directionX, directionY).nor()
    }

}
