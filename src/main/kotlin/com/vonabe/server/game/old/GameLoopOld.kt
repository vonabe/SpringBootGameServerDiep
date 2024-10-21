package com.vonabe.server.game.old

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.*
import com.badlogic.gdx.utils.Array
import com.fasterxml.jackson.databind.ObjectMapper
import com.vonabe.server.FastNoiseLite
import com.vonabe.server.data.EventType
import com.vonabe.server.data.receive.Move
import com.vonabe.server.data.receive.Shot
import com.vonabe.server.data.send.*
import com.vonabe.server.game.CameraView
import com.vonabe.server.utils.DirectionMove.calculateDirection
import com.vonabe.server.utils.DirectionMove.toMove
import com.vonabe.server.utils.JsonConverter.getType
import com.vonabe.server.utils.JsonConverter.toJson
import com.vonabe.server.utils.JsonConverter.toObject
import com.vonabe.server.utils.NoiseMapGenerator
import com.vonabe.server.utils.UtilsConverter.convert
import com.vonabe.server.websocket.*
import org.springframework.boot.web.servlet.server.Session
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.awt.image.BufferedImage
import kotlin.math.pow
import kotlin.random.Random

//@Component
class GameLoopOld(
    private var webSocketHandler: WebSocketHandler,
    private var nicknameHandler: NicknameHandler,
    private var mapper: ObjectMapper,
    private val worldSize: Rectangle,
) : ApplicationAdapter() {

    private val events = Array<String>()

    private val playersMap = ObjectMap<WebSocketSession, Player>()
    private val bulletList = Array<Bullet>()
    private val foodList = Array<Food>()

    // Мапа для хранения видимых объектов
    private val visibleFoodIdsMap: MutableMap<Session, Set<Int>> = mutableMapOf()

    // Мапа для хранения текущей области видимости игрока (камеры)
    private val playerCameraViews: MutableMap<Session, CameraView> = mutableMapOf()

    private var previewNoise: NoiseMapGenerator = NoiseMapGenerator(
        worldSize.width.toInt(), worldSize.height.toInt(),
        1920, 1080
    )

    private val bulletPool = object : Pool<Bullet>(5000) {
        override fun newObject(): Bullet {
            return Bullet(
                EventType.SHOT, -1, "", Vector2(), false,
                -1f, -1, -1f
            )
        }

        override fun reset(bullet: Bullet) {
            bullet.id = -1
            bullet.position.set(0f, 0f)
            bullet.direction = Vector2()
            bullet.remove = false
            bullet.speed = 0f
            bullet.radius = 0f
            bullet.timestamp = 0L
            bullet.penetration = 10f
            bullet.eventType = EventType.SHOT
            bullet.ownerUid = ""
            super.reset(bullet)
        }
    }

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

    private fun spawnBullet(it: Player) {
        val bullet = bulletPool.obtain()
        bullet.id = Random.nextInt(0, Int.MAX_VALUE)
        bullet.ownerUid = it.uid
        bullet.direction.set(it.angle.toFloat().calculateDirection())
        bullet.speed = 0.8f
        bullet.radius = 15f
        bullet.timestamp = TimeUtils.millis()
        bullet.remove = false

//        bullet.position.set(it.position) // Old
        // Вычисляем начальную позицию пули: добавляем смещение на конец ствола (playerRadius + bullet.radius)
        val spawnOffset = it.radius + bullet.radius
        val offsetX = MathUtils.cosDeg(it.angle.toFloat()) * spawnOffset
        val offsetY = MathUtils.sinDeg(it.angle.toFloat()) * spawnOffset
        // Устанавливаем начальную позицию пули
        bullet.position.set(it.position.x + offsetX, it.position.y + offsetY)

        bulletList.add(bullet)
        sendBulletList.add(bullet)
//        events.add(bullet.toJson(mapper))
    }

    private fun createPlayer(session: WebSocketSession) = Player().apply {
        this.uid = session.id
        this.name = nicknameHandler.randomNickname()
        this.position.set(
            Random.nextInt(25, 300).toFloat(),
            Random.nextInt(25, worldSize.height.toInt()).toFloat()
        )
        this.angle = Random.nextInt(0, 360)
        this.typeTank = TankType.PUSSY
        this.score = 0
        this.nextLevelScore = 1000
        this.level = 0
        this.radius = 30f
        this.autoShot = false
        this.health = 100f
    }

    fun generateFood(frequency: Float, reductionStep: Int, noiseFreq: Float): BufferedImage {
        foodList.forEach {
            foodPool.free(it)
            it.remove = true
        }
        events.add(Foods(EventType.FOOD, emptyList()).toJson(mapper))
        foodList.clear()

        val listPoint = Array<Vector2>()
        val image = previewNoise.generateMapPreview(
            listPoint,
            frequency,
            reductionStep = reductionStep,
            noiseFreq = noiseFreq,
            noiseType = FastNoiseLite.NoiseType.Perlin
        )

        listPoint.map {
            foodPool.obtain().apply {
                this.id = Random.nextInt(0, Int.MAX_VALUE)
                this.position.set(it.x, it.y)
                this.remove = false
            }
        }.convert()
            .apply {
                foodList.addAll(this)
            }

        println("Foods - ${foodList.size}")
        return image
    }

    override fun create() {
        Timer.schedule(object : Timer.Task() {
            override fun run() {
                println("FPS: ${Gdx.graphics.framesPerSecond}")
            }
        }, 1f, 1f)

//        Timer.schedule(object : Timer.Task() {
//            override fun run() {}
//        }, 5f, 30f)

        webSocketHandler.connectListener = object : ConnectListener {
            override fun onConnect(session: WebSocketSession) {
                println("Connect: ${session.id}")
                createPlayer(session).apply {
                    playersMap.put(session, this) // Добавляем юзера и сессию в список
                    sendOnly(
                        session, StateUser(EventType.USER, this).toJson(mapper)
                    ) // Отправляем USER лично подключившемуся пользователю
                    sendOnly(
                        session, StateUsers(EventType.USERS, playersMap.map { it.value }.toList()).toJson(mapper)
                    ) // Отправляем подключившемуся пользователю список всех юзеров
                    events.add(
                        ConnectUser(
                            EventType.CONNECT, this
                        ).toJson(mapper)
                    ) // Отправляем всем пользователям подключившегося пользователя
                }
            }
        }
        webSocketHandler.disconnectListener = object : DisconnectListener {
            override fun onDisconnect(session: WebSocketSession) {
                println("Disconnect: ${session.id}")
                val player = playersMap.get(session)
                events.add(DisconnectUser(EventType.DISCONNECT, player.uid).toJson(mapper))
                playersMap.remove(session)
            }
        }
        webSocketHandler.messageListener = object : MessageListener {
            override fun onMessage(session: WebSocketSession, message: String) {
                kotlin.runCatching {
                    when (message.getType(mapper)) {
                        EventType.MOVE -> {
                            playersMap.get(session).apply {
                                val movePacket = message.toObject(Move::class.java, mapper)

                                this.angle = movePacket.angle
                                this.position.add(movePacket.direction.toMove(15f))

                                checkBounds(this)

                                events.add(
                                    MoveUser(
                                        EventType.MOVE, session.id, this.position.x, this.position.y, this.angle
                                    ).toJson(mapper)
                                )

                                val checkFood = checkFood(this)
                                if (checkFood.isNotEmpty()) {
                                    events.add(Foods(EventType.FOODS, checkFood).toJson(mapper))
                                }
                            }
                        }

                        EventType.SHOT -> {
                            val shot = message.toObject(Shot::class.java, mapper)
                            playersMap.get(session).apply {
                                this.autoShot = shot.autoShot
                                spawnBullet(this)
                            }
                        }

                        else -> {
                            println("Type not found -> $message")
                        }
                    }
                }.onFailure {
                    it.printStackTrace()
                }
            }
        }
    }

    private fun checkFood(player: Player): List<Food> {
        // Создаем область видимости вокруг игрока
        val viewWidth = 1920f
        val viewHeight = 1080f

        // Определяем видимую область (прямоугольник) с центром на позиции игрока
        val viewRectangle = Rectangle(
            player.position.x - viewWidth / 2, player.position.y - viewHeight / 2, viewWidth, viewHeight
        )

        // Фильтруем список еды, проверяя, попадает ли объект в видимую область
        return foodList.filter { food ->
            viewRectangle.contains(food.position)
        }
    }

    private fun sendOnly(session: WebSocketSession, message: String, temp: Int = 3) {
        kotlin.runCatching {
            session.sendMessage(TextMessage(message))
        }.onFailure {
            it.printStackTrace()
            if (temp > 0) sendOnly(session, message, temp.dec())
        }
    }

    private var lastUpdateBullet = 0L
    private var autoShotLastUpdate = 0L
    private var sendBulletList = Array<Bullet>()
    private var sendPlayerList = Array<Player>()

    override fun render() {
        kotlin.runCatching {
            val deltaTime = Gdx.graphics.deltaTime

            val newTimestamp = TimeUtils.millis()

            // Auto spawn bullet
            if ((newTimestamp - autoShotLastUpdate) > 10) {
                autoShotLastUpdate = newTimestamp
                playersMap.forEach {
                    if (it.value.autoShot) spawnBullet(it.value)
                }
            }

            // Move Bullets 30 per second
            bulletList.filter { newTimestamp - it.timestamp > 10 }.forEach { bullet ->
                bullet.position.add(
                    bullet.direction.cpy().scl(bullet.speed * (newTimestamp - bullet.timestamp))
                )
                // Check for remove
                if (bullet.position.x < 0 || bullet.position.x > worldSize.width
                    || bullet.position.y < 0 || bullet.position.y > worldSize.height
                ) {
                    bullet.remove = true
                }
                bullet.timestamp = newTimestamp

                if (bullet.remove) sendBulletList.add(bullet)
            }

            // Check bullet collision
            val bulletsToCheck = bulletList.filter { !it.remove } // Фильтруем удаленные пули
            for (bullet1 in bulletsToCheck) {
                for (bullet2 in bulletsToCheck) {
                    if (bullet1 != bullet2 && !isFarApart(bullet1, bullet2)) {
                        if (checkCollision(bullet1, bullet2)) {
                            if (!sendBulletList.contains(bullet1, false)) sendBulletList.add(bullet1)
                            if (!sendBulletList.contains(bullet2, false)) sendBulletList.add(bullet2)
                        }
                    }
                }
            }

            // Check Collision player with bullet
            for (bullet in bulletsToCheck) {
                for (player in playersMap.values()) {
                    if (!isFarApart(bullet, player)) {
                        if (checkCollision(bullet, player)) {
                            if (!sendPlayerList.contains(player, false)) sendPlayerList.add(player)
                            if (!sendBulletList.contains(bullet, false)) sendBulletList.add(bullet)
                        }
                    }
                }
            }

            // Check player collision
            val it = playersMap.values()
            it.forEach { player1 ->
                it.forEach { player2 ->
                    if (player1 != player2 && !isFarApart(player1, player2)) {
                        if (checkCollision(player1, player2)) {
                            if (!sendPlayerList.contains(player1, false)) sendPlayerList.add(player1)
                            if (!sendPlayerList.contains(player2, false)) sendPlayerList.add(player2)
                        }
                    }
                }
            }

            // Check player border
            playersMap.values().forEach {
                if (checkBounds(it)) {
                    if (!sendPlayerList.contains(it, false)) sendPlayerList.add(it)
                }
            }

            if (sendBulletList.size > 0) {
                events.add(Bullets(EventType.SHOTS, sendBulletList.toList()).toJson(mapper))
                sendBulletList.clear()

                val remove = bulletList.filter { it.remove }.convert()
                bulletList.removeAll(remove, false)
                bulletPool.freeAll(remove)
            }

            if (sendPlayerList.size > 0) {
//                sendPlayerList.forEach {
//                    events.add(it.mapToMove())
//                }
                events.add(MoveUsers(EventType.MOVES, sendPlayerList.map { it.mapToMovePacket() }).toJson(mapper))
                sendPlayerList.clear()
            }

            // Remove bullet
//        val bulletsToRemove = bulletList.filter { it.remove }.convert()
//        bulletList.removeAll { it.remove }
//        bulletPool.freeAll(bulletsToRemove)

            // Send packets for users
            synchronized(events) {
                var value: String?
                while (!events.isEmpty) {
                    value = events.pop() ?: break
                    webSocketHandler.broadcastMessage(value)
                }
            }

        }.onFailure {
            it.printStackTrace()
        }

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

    fun isFarApart(bullet: Bullet, player: Player): Boolean {
        // Квадрат суммы радиусов
        val radiusSumSq = (bullet.radius + player.radius).pow(2)

        // Квадрат расстояния между игроками
        val distSq = bullet.position.dst2(player.position)

        // Если квадрат расстояния больше квадрата суммы радиусов — игроки далеко
        return distSq > radiusSumSq
    }

    fun isFarApart(bullet1: Bullet, bullet2: Bullet): Boolean {
        // Квадрат суммы радиусов
        val radiusSumSq = (bullet1.radius + bullet2.radius).pow(2)

        // Квадрат расстояния между игроками
        val distSq = bullet1.position.dst2(bullet2.position)

        // Если квадрат расстояния больше квадрата суммы радиусов — игроки далеко
        return distSq > radiusSumSq
    }

    fun checkCollision(bullet1: Bullet, bullet2: Bullet): Boolean {
        // Вычисляем вектор между позициями игроков
        val diff = bullet2.position.cpy().sub(bullet1.position)

        // Вычисляем расстояние между центрами
        val distance = diff.len()

        // Сумма радиусов игроков
        val radiusSum = bullet1.radius + bullet2.radius

        // Если расстояние меньше суммы радиусов, значит, есть столкновение
        if (distance < radiusSum) {
            // Находим, насколько сильно пересекаются игроки
            val overlap = radiusSum - distance

            // Нормализуем вектор diff, чтобы получить направление
            val direction = diff.nor()

            // Раздвигаем игроков в противоположные стороны, чтобы устранить пересечение
            bullet1.position.sub(direction.cpy().scl(overlap / 2))
            bullet2.position.add(direction.cpy().scl(overlap / 2))
            return true
//            events.addAll(Bullets(EventType.SHOTS, bulletList.toList()).toJson(mapper))
        }
        return false
    }

    fun isFarApart(player1: Player, player2: Player): Boolean {
        // Квадрат суммы радиусов
        val radiusSumSq = (player1.radius + player2.radius).pow(2)

        // Квадрат расстояния между игроками
        val distSq = player1.position.dst2(player2.position)

        // Если квадрат расстояния больше квадрата суммы радиусов — игроки далеко
        return distSq > radiusSumSq
    }

    fun checkCollision(bullet: Bullet, player: Player): Boolean {
        // Вычисляем вектор между позициями игроков
        val diff = player.position.cpy().sub(bullet.position)

        // Вычисляем расстояние между центрами
        val distance = diff.len()

        // Сумма радиусов игроков
        val radiusSum = bullet.radius + player.radius

        // Если расстояние меньше суммы радиусов, значит, есть столкновение
        if (distance < radiusSum) {
            // Находим, насколько сильно пересекаются игроки
            val overlap = radiusSum - distance

            // Нормализуем вектор diff, чтобы получить направление
            val direction = diff.nor()

            // Раздвигаем игроков в противоположные стороны, чтобы устранить пересечение
            bullet.position.sub(direction.cpy().scl(overlap / 2))
            player.position.add(direction.cpy().scl(overlap / 2))
            return true
//            events.add(Bullets(EventType.SHOTS, sendBulletList.toList()).toJson(mapper))
//            events.addAll(bullet.toJson(mapper), player.mapToMove())
//            events.add(player.mapToMove())
        }
        return false
    }

    fun checkCollision(player1: Player, player2: Player): Boolean {
        // Вычисляем вектор между позициями игроков
        val diff = player2.position.cpy().sub(player1.position)

        // Вычисляем расстояние между центрами
        val distance = diff.len()

        // Сумма радиусов игроков
        val radiusSum = player1.radius + player2.radius

        // Если расстояние меньше суммы радиусов, значит, есть столкновение
        if (distance < radiusSum) {
            // Находим, насколько сильно пересекаются игроки
            val overlap = radiusSum - distance

            // Нормализуем вектор diff, чтобы получить направление
            val direction = diff.nor()

            // Раздвигаем игроков в противоположные стороны, чтобы устранить пересечение
            player1.position.sub(direction.cpy().scl(overlap / 2))
            player2.position.add(direction.cpy().scl(overlap / 2))
            return true
//            events.addAll(player1.mapToMove(), player2.mapToMove())
        }
        return false
    }

    private fun Player.mapToMove(): String {
        return MoveUser(
            EventType.MOVE, this.uid, this.position.x, this.position.y, this.angle
        ).toJson(mapper)
    }

    private fun Player.mapToMovePacket(): MoveUser {
        return MoveUser(
            EventType.MOVE, this.uid, this.position.x, this.position.y, this.angle
        )
    }

}
