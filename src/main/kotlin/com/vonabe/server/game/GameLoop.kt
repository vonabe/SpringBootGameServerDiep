package com.vonabe.server.game

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.*
import com.badlogic.gdx.utils.Array
import com.fasterxml.jackson.databind.ObjectMapper
import com.vonabe.server.data.EventType
import com.vonabe.server.data.receive.Move
import com.vonabe.server.data.receive.Shot
import com.vonabe.server.data.send.*
import com.vonabe.server.utils.DirectionMove.calculateDirection
import com.vonabe.server.utils.DirectionMove.toMove
import com.vonabe.server.utils.JsonConverter.getType
import com.vonabe.server.utils.JsonConverter.toJson
import com.vonabe.server.utils.JsonConverter.toObject
import com.vonabe.server.utils.UtilsConverter.convert
import com.vonabe.server.websocket.*
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import kotlin.math.pow
import kotlin.random.Random

@Component
class GameLoop(
    private var webSocketHandler: WebSocketHandler,
    private var nicknameHandler: NicknameHandler,
    private var mapper: ObjectMapper,
) : ApplicationAdapter() {

    private val events = Array<String>()

    private val playersMap = ObjectMap<WebSocketSession, Player>()
    private val bulletList = Array<Bullet>()

    private val bulletPool = object : Pool<Bullet>(50) {
        override fun newObject(): Bullet {
            return Bullet(EventType.SHOT, -1, "", Vector2(), Vector2(), false, -1f, 0f, 0L)
        }

        override fun reset(bullet: Bullet) {
            bullet.id = -1
            bullet.position = Vector2()
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

    @Synchronized
    private fun spawnBullet(it: Player) {
        val bullet = bulletPool.obtain()
        bullet.id = (TimeUtils.millis() and 0xFFFFFF).toInt() + Random.nextInt(0, Int.MAX_VALUE)
        bullet.ownerUid = it.uid
        bullet.position.set(it.position)
        bullet.direction.set(it.angle.toFloat().calculateDirection())
        bullet.speed = 0.5f
        bullet.radius = 15f
        bullet.timestamp = TimeUtils.millis()
        bullet.remove = false
        bulletList.add(bullet)
//            events.add(bullet.toJson(mapper))
    }

    private fun createPlayer(session: WebSocketSession) = Player(
        uid = session.id,
        nicknameHandler.randomNickname(),
        Vector2(Random.nextInt(50, 1280).toFloat(), Random.nextInt(50, 720).toFloat()),
        Random.nextInt(0, 360),
        TankType.PUSSY,
        0,
        1000,
        1,
        30f
    )

    override fun create() {
        Timer.schedule(object : Timer.Task() {
            override fun run() {
                println("FPS: ${Gdx.graphics.framesPerSecond}")
            }
        }, 1f, 1f)

        webSocketHandler.connectListener = object : ConnectListener {
            override fun onConnect(session: WebSocketSession) {
                println("Connect: ${session.id}")
                createPlayer(session).apply {
                    playersMap.put(session, this) // Добавляем юзера и сессию в список
                    sendOnly(
                        session,
                        StateUser(EventType.USER, this).toJson(mapper)
                    ) // Отправляем USER лично подключившемуся пользователю
                    sendOnly(
                        session,
                        StateUsers(EventType.USERS, playersMap.map { it.value }.toList()).toJson(mapper)
                    ) // Отправляем подключившемуся пользователю список всех юзеров
                    events.add(
                        ConnectUser(
                            EventType.CONNECT,
                            this
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
                                        EventType.MOVE,
                                        session.id,
                                        this.position.x,
                                        this.position.y,
                                        this.angle
                                    ).toJson(mapper)
                                )
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

    private fun sendOnly(session: WebSocketSession, message: String, temp: Int = 3) {
        kotlin.runCatching {
            session.sendMessage(TextMessage(message))
        }.onFailure {
            it.printStackTrace()
            if (temp > 0)
                sendOnly(session, message, temp.dec())
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
                    if (it.value.autoShot)
                        spawnBullet(it.value)
                }
            }

            bulletList
                .filter { newTimestamp - it.timestamp > 50 }
                .forEach { bullet ->
                    bullet.position.add(
                        bullet.direction.cpy().scl(bullet.speed * (newTimestamp - bullet.timestamp))
                    )
                    // Check for remove
                    if (bullet.position.x < 0 || bullet.position.x > 1920 || bullet.position.y < 0 || bullet.position.y > 1080) {
                        bullet.remove = true
                    }
                    bullet.timestamp = newTimestamp

                    sendBulletList.add(bullet)
                }

            // Check bullet collision
            val bulletsToCheck = sendBulletList.filter { !it.remove } // Фильтруем удаленные пули
            for (bullet1 in bulletsToCheck) {
                for (bullet2 in bulletsToCheck) {
                    if (bullet1 != bullet2 && !isFarApart(bullet1, bullet2)) {
                        checkCollision(bullet1, bullet2)
                    }
                }
            }

            // Check Collision player with bullet
            for (bullet in bulletsToCheck) {
                for (player in playersMap.values()) {
                    if (!isFarApart(bullet, player)) {
                        if (checkCollision(bullet, player)) {
                            sendPlayerList.add(player)
                        }
                    }
                }
            }

            // Check player collision
            // Оптимизированная проверка столкновений:
            val playerValues = playersMap.values()
            playerValues.forEach { player1 ->
                playerValues.forEach { player2 ->
                    if (player1 != player2 && !isFarApart(player1, player2)) {
                        if (checkCollision(player1, player2)) {
//                            sendPlayerList.addAll(player1, player2)
                            if (!sendPlayerList.contains(player1, true)) sendPlayerList.add(player1)
                            if (!sendPlayerList.contains(player2, true)) sendPlayerList.add(player2)
                        }
                    }
                }
            }

            // Check player border
            playerValues.forEach {
                checkBounds(it)
            }

            if (sendBulletList.size > 0) {
                events.add(Bullets(EventType.SHOTS, sendBulletList.toList()).toJson(mapper))
                sendBulletList.clear()

                val remove = bulletList.filter { it.remove }.convert()
                bulletList.removeAll(remove, true)
                bulletPool.freeAll(remove)
            }

            if (sendPlayerList.size > 0) {
                sendPlayerList.forEach {
                    events.add(it.mapToMove())
                }
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
                    webSocketHandler.broadcastMessageAsync(value)
                }
            }

        }.onFailure {
            it.printStackTrace()
        }

    }

    fun checkBounds(player: Player) {
        // Левая граница (x >= радиуса игрока)
        if (player.position.x < player.radius) {
            player.position.x = player.radius
        }
        // Правая граница (x <= 1920 - радиус игрока)
        if (player.position.x > 1920 - player.radius) {
            player.position.x = 1920 - player.radius
        }

        // Верхняя граница (y >= радиуса игрока)
        if (player.position.y < player.radius) {
            player.position.y = player.radius
        }
        // Нижняя граница (y <= 1080 - радиус игрока)
        if (player.position.y > 1080 - player.radius) {
            player.position.y = 1080 - player.radius
        }
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
            EventType.MOVE,
            this.uid,
            this.position.x,
            this.position.y,
            this.angle
        ).toJson(mapper)
    }

}
