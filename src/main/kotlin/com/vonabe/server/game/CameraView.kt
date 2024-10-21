package com.vonabe.server.game

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2

data class CameraView(
    var centerX: Float,
    var centerY: Float,
    val width: Int,
    val height: Int,
    var worldWidth: Float,
    var worldHeight: Float,
) {
    private val viewportCamera = Rectangle()
    private val viewportBuffer = Rectangle()
    private val viewportGlobal = Rectangle()

    init {
        viewportCamera.set(
            centerX - (width / 2 * 0.5f),
            centerY - (height / 2 * 0.5f),
            (width / 2).toFloat(),
            (height / 2).toFloat()
        )

        viewportBuffer.set(
            centerX - ((width / 2 + (width / 2 * 0.5f)) / 2),
            centerY - ((height / 2 + (height / 2 * 0.5f)) / 2),
            (width / 2 + (width / 2 * 0.5f)),
            (height / 2 + (height / 2 * 0.5f))
        )

        viewportGlobal.set(
            centerX - (width / 2),
            centerY - (height / 2),
            width.toFloat(),
            height.toFloat()
        )
    }

    fun hasCrossedBorder(newPosition: Vector2): Boolean {
        return this.hasCrossedBorder(newPosition.x, newPosition.y)
    }

    // Метод для проверки пересечения границы одного квадрата обзора
    fun hasCrossedBorder(newCenterX: Float, newCenterY: Float): Boolean {
        centerX = newCenterX
        centerY = newCenterY
        viewportCamera.set(
            MathUtils.clamp(
                centerX - (width / 2 * 0.5f),
                2f,
                worldWidth - viewportCamera.width - 2f
            ),
            MathUtils.clamp(
                centerY - (height / 2 * 0.5f),
                2f,
                worldHeight - viewportCamera.height - 2f
            ),
            (width / 2).toFloat(),
            (height / 2).toFloat()
        )
        // Проверяем, пересек ли игрок границы текущего квадрата (один квадрат обзора)
        return !viewportBuffer.contains(viewportCamera)
    }

    // Обновляем положение камеры
    fun updatePosition() {
        viewportBuffer.set(
            MathUtils.clamp(
                centerX - ((width / 2 + (width / 2 * 0.5f)) / 2),
                0f,
                worldWidth - viewportBuffer.width
            ),
            MathUtils.clamp(
                centerY - ((height / 2 + (height / 2 * 0.5f)) / 2),
                0f,
                worldHeight - viewportBuffer.height
            ),
            (width / 2 + (width / 2 * 0.5f)),
            (height / 2 + (height / 2 * 0.5f))
        )

        viewportGlobal.set(
            MathUtils.clamp(
                centerX - (width / 2),
                0f,
                worldWidth - viewportGlobal.width
            ),
            MathUtils.clamp(
                centerY - (height / 2),
                0f,
                worldHeight - viewportGlobal.height
            ),
            width.toFloat(),
            height.toFloat()
        )

    }

    fun getViewportCamera(): Rectangle {
        return viewportCamera
    }

    fun getViewportBuffer(): Rectangle {
        return viewportBuffer
    }

    // Получаем координаты углов видимой области
    fun getViewportGlobal(): Rectangle {
        return viewportGlobal
    }

}
