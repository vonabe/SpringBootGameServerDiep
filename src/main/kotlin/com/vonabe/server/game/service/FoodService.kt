package com.vonabe.server.game.service

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Pool
import com.vonabe.server.FastNoiseLite
import com.vonabe.server.data.EventType
import com.vonabe.server.data.send.Food
import com.vonabe.server.data.send.Foods
import com.vonabe.server.utils.NoiseMapGenerator
import com.vonabe.server.utils.UtilsConverter.convert
import com.vonabe.server.websocket.WebSocketHandler
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import kotlin.random.Random

@Service
class FoodService(
    private val noiseGenerator: NoiseMapGenerator,
    private var messageHandler: WebSocketHandler,
    private val spatialGrid: SpatialGrid,
) : GameService {

    private val foodList = Array<Food>()

    private val foodPool = object : Pool<Food>(5000) {
        override fun newObject(): Food {
            return Food(EventType.FOOD, -1, false)
        }

        override fun reset(food: Food?) {
            food?.position?.set(0f, 0f)
            food?.remove = false
            food?.id = -1
        }
    }

    fun getFoods() = synchronized(foodList) { foodList.toList() }

    fun generateFood(frequency: Float, reductionStep: Int, noiseFreq: Float): BufferedImage {
        synchronized(foodList) {
            foodList.forEach {
                foodPool.free(it)
                it.remove = true
            }
            foodList.clear()
            spatialGrid.clear()
            messageHandler.broadcastMessage(Foods(EventType.FOODS, emptyList()))

            val points = Array<Vector2>()
            val previewBuffer = noiseGenerator.generateMapPreview(
                points,
                frequency,
                FastNoiseLite.NoiseType.Perlin,
                reductionStep,
                noiseFreq
            )
            points.map {
                foodPool.obtain().apply {
                    this.id = Random.nextInt(0, Int.MAX_VALUE)
                    this.position.set(it.x, it.y)
                    this.remove = false
                    this.radius = MathUtils.random(10f, 50f)
                }
            }.convert()
                .let {
                    foodList.addAll(it)
                    spatialGrid.addObjects(it)
                }
            return previewBuffer
        }
    }

    override fun update(delta: Float) {

    }

}
