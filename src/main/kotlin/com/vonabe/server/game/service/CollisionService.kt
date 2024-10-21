package com.vonabe.server.game.service

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Array
import com.vonabe.server.data.EventType
import com.vonabe.server.data.send.*
import com.vonabe.server.websocket.WebSocketHandler
import org.springframework.stereotype.Service
import kotlin.math.pow

@Service
class CollisionService(
    private val playerService: PlayerService,
    private val bulletService: BulletService,
    private val foodService: FoodService,
    private var messageHandler: WebSocketHandler,
    private val worldSize: Rectangle,
) : GameService {

    private val changePositionList = Array<Bounds>()
    private val collisionList = Array<Bounds>()

    fun collision(): Array<Bounds> {
        changePositionList.clear()

        val players = playerService.getPlayers()
        val foods = foodService.getFoods()
        val bullets = bulletService.getBullets()
        /*
        * TODO
        *   Доделать проверку коллизий и возвращать список измененных объектов для дальнейшей отправки клиентам
        */
        players.parallelStream().forEach { player1 ->

            players.parallelStream().forEach { player2 ->
                if (player1 != player2 && !isFarApart(player1, player2) && checkCollision(player1, player2)) {
                    if (!changePositionList.contains(player1, false)) changePositionList.add(player1)
                    if (!changePositionList.contains(player2, false)) changePositionList.add(player2)
                }
            }

            bullets.parallelStream().forEach { bullet ->
                if (!isFarApart(player1, bullet) && checkCollision(player1, bullet)) {
                    if (!changePositionList.contains(player1, false)) changePositionList.add(player1)
                    if (!changePositionList.contains(bullet, false)) changePositionList.add(bullet)
                }
            }

            foods.parallelStream().forEach { food ->
                if (!isFarApart(player1, food) && checkCollision(player1, food)) {
                    if (!changePositionList.contains(player1, false)) changePositionList.add(player1)
                    if (!changePositionList.contains(food, false)) changePositionList.add(food)
                }
            }

            checkBounds(player1)
        }

        return changePositionList
    }

    fun isFarApart(value1: Bounds, value2: Bounds): Boolean {
        // Квадрат суммы радиусов
        val radiusSumSq = (value1.radius + value2.radius).pow(2)

        // Квадрат расстояния между игроками
        val distSq = value1.position.dst2(value2.position)

        // Если квадрат расстояния больше квадрата суммы радиусов — игроки далеко
        return distSq > radiusSumSq
    }

    fun checkCollision(value1: Bounds, value2: Bounds): Boolean {
        // Вычисляем вектор между позициями игроков
        val diff = value2.position.cpy().sub(value1.position)

        // Вычисляем расстояние между центрами
        val distance = diff.len()

        // Сумма радиусов игроков
        val radiusSum = value1.radius + value2.radius

        // Если расстояние меньше суммы радиусов, значит, есть столкновение
        if (distance < radiusSum) {
            // Находим, насколько сильно пересекаются игроки
            val overlap = radiusSum - distance

            // Нормализуем вектор diff, чтобы получить направление
            val direction = diff.nor()

            // Раздвигаем игроков в противоположные стороны, чтобы устранить пересечение
            value1.position.sub(direction.cpy().scl(overlap / 2))
            value2.position.add(direction.cpy().scl(overlap / 2))
            return true
//            events.addAll(Bullets(EventType.SHOTS, bulletList.toList()).toJson(mapper))
        }
        return false
    }

    fun checkBounds(player: Player): Boolean {
        var state = false
        // Левая граница (x >= радиуса игрока)
        if (player.position.x < player.radius) {
            player.position.x = player.radius
            state = true
        }
        // Правая граница (x <= 1920 - радиус игрока)
        if (player.position.x > worldSize.width - player.radius) {
            player.position.x = worldSize.width - player.radius
            state = true
        }

        // Верхняя граница (y >= радиуса игрока)
        if (player.position.y < player.radius) {
            player.position.y = player.radius
            state = true
        }
        // Нижняя граница (y <= 1080 - радиус игрока)
        if (player.position.y > worldSize.height - player.radius) {
            player.position.y = worldSize.height - player.radius
            state = true
        }
        return state
    }

    override fun update(delta: Float) {
        collisionList.clear()
        val foods = foodService.getFoods()
        val bullets = bulletService.getBullets()
        bullets.parallelStream().forEach { bullet ->
            foods.parallelStream().forEach { food ->
                if (!isFarApart(food, bullet) && checkCollision(food, bullet)) {
                    if (!collisionList.contains(food, false)) collisionList.add(food)
                    if (!collisionList.contains(bullet, false)) collisionList.add(bullet)
                }
            }
        }

        if (collisionList.notEmpty()) {
            val players = playerService.getPlayers()
            players.forEach { player ->
                val viewportGlobal = player.cameraViewport.getViewportGlobal()
                val sendingList = collisionList.filter { viewportGlobal.contains(it.position) }
                if (sendingList.isNotEmpty()) {
                    val foodList = sendingList.filterIsInstance<Food>()
                    val bulletList = sendingList.filterIsInstance<Bullet>()
                    if (foodList.isNotEmpty())
                        messageHandler.sendOnly(player.uid, Foods(EventType.FOODS, foodList))
                    if (bulletList.isNotEmpty())
                        messageHandler.sendOnly(player.uid, Bullets(EventType.SHOTS, bulletList))
                }
            }
        }
    }

}
