package com.vonabe.server.utils

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Array
import com.vonabe.server.FastNoiseLite
import com.vonabe.server.FastNoiseLite.NoiseType
import org.springframework.context.annotation.ComponentScan
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min

@ComponentScan
class NoiseMapGenerator(
    private val worldWidth: Int,      // Полная ширина карты
    private val worldHeight: Int,     // Полная высота карты
    private val previewWidth: Int,
    private val previewHeight: Int,
) {

    private var noise: FastNoiseLite =
        FastNoiseLite()

    // Метод генерации карты шума и сохранения миниатюры
    fun generateMapPreview(
        dataList: Array<Vector2>,
        frequency: Float = 0.03f,
        noiseType: NoiseType = NoiseType.Perlin,
        reductionStep: Int = 10,
        noiseFreq: Float = 0.3f,
    ): BufferedImage {
        noise.SetNoiseType(noiseType)  // Выбираем тип шума Перлина
        noise.SetFrequency(frequency)

        // Создаем BufferedImage для миниатюры
        val image = BufferedImage(previewWidth, previewHeight, BufferedImage.TYPE_INT_RGB)

        // Проходим по каждому пикселю изображения
        for (i in 0 until previewWidth step reductionStep) {
            for (j in 0 until previewHeight step reductionStep) {
                // Преобразуем координаты миниатюры в мировые координаты
                val worldX = i * (worldWidth.toFloat() / previewWidth)
                val worldY = j * (worldHeight.toFloat() / previewHeight)

                // Получаем значение шума для текущей позиции
                val noiseValue = noise.GetNoise(worldX, worldY)

                if (noiseValue >= noiseFreq) {
                    // Добавляем объекты на карту по фактическим координатам
                    dataList.add(Vector2(worldX, worldY))

                    // Преобразуем значение шума в диапазон 0-255 для цвета
                    val colorValue = ((noiseValue + 1) * 0.5 * 255).toInt()
                    val finalColorValue = min(max(colorValue, 0), 255)

                    // Инвертируем ось Y для правильного отображения
                    val pixelY = previewHeight - j - 1

                    // Устанавливаем пиксель как градацию серого
                    val color = Color(finalColorValue, finalColorValue, finalColorValue)
                    image.setRGB(i, pixelY, color.rgb)
                }
            }
        }
        return image

        // Сохраняем изображение в файл
//        val outputFile = File(outputFilePath)
//        ImageIO.write(image, "png", outputFile)
//        println("Миниатюра карты шума сохранена: ${outputFile.absolutePath}")
    }

}

