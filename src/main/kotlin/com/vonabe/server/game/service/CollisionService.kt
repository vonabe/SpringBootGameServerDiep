package com.vonabe.server.game.service

import com.badlogic.gdx.math.Rectangle
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
    private val spatialGrid: SpatialGrid,
) : GameService {

    private val changePositionList = HashSet<Bounds>()
    private val collisionList = HashSet<Bounds>()
    private val checkRect = Rectangle()

    fun collision(): Set<Bounds> {
        changePositionList.clear()

        val players = playerService.getPlayers()
        val foods = foodService.getFoods()
        val bullets = bulletService.getBullets()
        /*
        * TODO
        *   Доделать проверку коллизий и возвращать список измененных объектов для дальнейшей отправки клиентам
        */
        players.parallelStream().forEach { player1 ->
            players.forEach { player2 ->
                if (player1 != player2
                    && !isFarApart(player1, player2)
                    && checkCollision(player1, player2)
                ) {
                    changePositionList.add(player1)
                    changePositionList.add(player2)
                    checkBounds(player2)
                }
            }

            bullets.forEach { bullet ->
                if (!isFarApart(player1, bullet) && checkCollision(player1, bullet)) {
                    changePositionList.add(player1)
                    changePositionList.add(bullet)
                    checkBounds(bullet)
                }
            }

//            resolveAllCollisions(player1).apply {
//                changePositionList.add(player1)
//                changePositionList.addAll(this)
//                this.forEach { checkBounds(it) }
//            }

            foods.forEach { food ->
                if (!isFarApart(player1, food) && checkCollision(player1, food)) {
                    resolveAllCollisions(food).apply { changePositionList.addAll(this) }
                    changePositionList.add(player1)
                    changePositionList.add(food)
                    checkBounds(food)
                }
            }

            checkBounds(player1)
        }

        return changePositionList
    }

    private fun previewCheck(value1: Bounds, value2: Bounds): Boolean {
        val minX1 = value1.position.x
        val maxX1 = value1.position.x + value1.radius

        val minY1 = value1.position.y
        val maxY1 = value1.position.y + value1.radius

        val minX2 = value2.position.x
        val maxX2 = value2.position.x + value2.radius

        val minY2 = value2.position.y
        val maxY2 = value2.position.y + value2.radius

        // Проверка пересекаются ли объекты по осям X и Y
        if (maxX1 < minX2 || minX1 > maxX2 ||
            maxY1 < minY2 || minY1 > maxY2
        ) {
            return true // объекты далеко друг от друга
        }
        return false
    }

    private fun isFarApart(value1: Bounds, value2: Bounds): Boolean {
        // Квадрат суммы радиусов
        val radiusSumSq = (value1.radius + value2.radius).pow(2)

        // Квадрат расстояния между игроками
        val distSq = value1.position.dst2(value2.position)

        // Если квадрат расстояния больше квадрата суммы радиусов — игроки далеко
        return distSq > radiusSumSq
    }

    private fun resolveAllCollisions(initialCircle: Bounds): List<Bounds> {
        val objectsToCheck = mutableListOf(initialCircle)
        val checkedPairs = mutableSetOf<Pair<Bounds, Bounds>>()  // Отслеживание уже проверенных пар
        val collisionObjects = mutableSetOf<Bounds>()  // Хранение всех объектов, участвовавших в коллизиях

        while (objectsToCheck.isNotEmpty()) {
            val currentCircle = objectsToCheck.removeAt(0)
            val nearbyObjects =
                spatialGrid.getObjectsInRadius(currentCircle.position.x, currentCircle.position.y, currentCircle.radius)

            nearbyObjects.forEach { otherCircle ->
                // Убедимся, что это не тот же объект и пара еще не проверялась
                if (currentCircle != otherCircle &&
                    checkedPairs.add(Pair(currentCircle, otherCircle)) &&
                    checkedPairs.add(Pair(otherCircle, currentCircle))
                ) {
                    // Проверяем коллизию и при необходимости смещаем
                    if (checkCollision(currentCircle, otherCircle)) {
                        collisionObjects.add(currentCircle)   // Добавляем текущий объект
                        collisionObjects.add(otherCircle)     // Добавляем объект, с которым произошла коллизия

                        if (!objectsToCheck.contains(otherCircle)) objectsToCheck.add(otherCircle)
                        if (!objectsToCheck.contains(currentCircle)) objectsToCheck.add(currentCircle)

                        // Обновляем позицию в пространственной сетке
                        spatialGrid.updatePosition(otherCircle)
                        spatialGrid.updatePosition(currentCircle)
                    }
                }
            }
        }
        return collisionObjects.toList()
    }

    private fun checkCollision(value1: Bounds, value2: Bounds): Boolean {
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

    private fun checkBounds(player: Bounds): Boolean {
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

        // Collision bullets with foods
        bullets.forEach { bullet ->
            foods.forEach { food ->
                if (!isFarApart(food, bullet) && checkCollision(food, bullet)) {
                    collisionList.add(food)
                    collisionList.add(bullet)
                }
            }
        }

        // Collect foods from collision list
        collisionList.filterIsInstance<Food>().forEach { resolveAllCollisions(it).apply { collisionList.addAll(this) } }

        foods.forEach {
            if (checkBounds(it)) collisionList.add(it)
        }

        if (collisionList.isNotEmpty()) {
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

//    fun Set<Bounds>.recursiveCheck(checkedFoods: MutableSet<Bounds> = mutableSetOf()) {
//        this.forEach { food1 ->
//            // Получаем объекты в радиусе food1
//            spatialGrid.getObjectsInRadius(food1.position.x, food1.position.y, food1.radius).forEach { food2 ->
//                // Проверяем, не обрабатывались ли уже эти объекты
//                if (food1 != food2 && !checkedFoods.contains(food2)) {
//                    // Если обнаружена коллизия
//                    if (checkCollision(food1, food2)) {
//
//                        // Обновляем позиции в spatialGrid, так как checkCollision смещает объекты
//                        spatialGrid.updatePosition(food1)
//                        spatialGrid.updatePosition(food2)
//
//                        // Добавляем объекты в список коллизий
//                        collisionList.add(food1)
//                        collisionList.add(food2)
//
//                        // Добавляем их в множество проверенных
//                        checkedFoods.add(food1)
//                        checkedFoods.add(food2)
//
//                        // Рекурсивно проверяем новые цепочки коллизий
//                        checkedFoods.recursiveCheck(checkedFoods)
//                    }
//                }
//            }
//        }
//    }

    fun List<Bounds>.recursiveCheck() {
        this.forEach { food1 ->
            val foodsAround = spatialGrid.getObjectsInRadius(food1.position.x, food1.position.y, food1.radius)
            foodsAround.forEach { food2 ->
                if (food1 != food2) {
                    if (!isFarApart(food1, food2) && checkCollision(food1, food2)) {
                        // Разрешаем коллизию
//                        resolveCollision(food1, food2)
//                        collisionList.add(food1)
                        collisionList.add(food2)
                        // Поскольку мы разрешили коллизию, снова проверяем на близлежащие объекты
                        collisionList.toList().recursiveCheck()
                    }
                }
            }
        }
    }

    fun resolveCollision(bounds1: Bounds, bounds2: Bounds) {
        val overlapX = (bounds1.radius + bounds2.radius) - bounds1.position.dst(bounds2.position)
        val direction = (bounds2.position.cpy().sub(bounds1.position)).nor() // Вектор направления
        val adjustment = direction.scl(overlapX * 0.5f) // Половина перекрытия

        bounds1.position.add(adjustment)
        bounds2.position.sub(adjustment)

        // Обновите позиции в пространственной сетке
        spatialGrid.updatePosition(bounds1)
        spatialGrid.updatePosition(bounds2)

        collisionList.add(bounds1)
        collisionList.add(bounds2)
    }

    fun checkRectangle(value1: Bounds, value2: Bounds): Boolean {
        return checkRect.set(
            value1.position.x - value1.radius * 2.5f,
            value1.position.y - value1.radius * 2.5f,
            value1.radius * 5,
            value1.radius * 5
        ).contains(value2.position)
    }

}
