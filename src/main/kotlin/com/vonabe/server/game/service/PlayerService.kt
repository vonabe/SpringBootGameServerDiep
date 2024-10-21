package com.vonabe.server.game.service

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectMap
import com.badlogic.gdx.utils.Pool
import com.vonabe.server.data.EventType
import com.vonabe.server.data.receive.Move
import com.vonabe.server.data.send.*
import com.vonabe.server.utils.DirectionMove.toMove
import com.vonabe.server.utils.UtilsConverter.convert
import com.vonabe.server.websocket.NicknameHandler
import com.vonabe.server.websocket.WebSocketHandler
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class PlayerService(
    private val webSocketHandler: WebSocketHandler,
    private val nicknameHandler: NicknameHandler,
    @Lazy private val collisionService: CollisionService,
    @Lazy private val foodService: FoodService,
    private val eventService: EventService,
    private val worldSize: Rectangle,
) : GameService {

    private val playersMap = ObjectMap<String, Player>()
    private val movePlayerList = Array<Player>()

    private val playerFoodVisible = ObjectMap<String, Array<Food>>()

    private val pool = object : Pool<Player>(500) {
        override fun newObject(): Player {
            return Player()
        }
    }

    fun getPlayer(uid: String): Player {
        return playersMap.get(uid)
    }

    fun getPlayers() = playersMap.values().toList()

    fun createPlayer(uid: String) {
        val player = pool.obtain().apply {
            this.uid = uid
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
            this.cameraViewport.worldWidth = worldSize.width
            this.cameraViewport.worldHeight = worldSize.height
            this.cameraViewport.hasCrossedBorder(this.position)
        }
        playersMap.put(uid, player)
        playerFoodVisible.put(uid, Array())

        // Уведомляем всех пользователей о подключившемся пользователе
        webSocketHandler.sendOnly(uid, StateUser(EventType.USER, player)) // MY USER INFO
        webSocketHandler.sendOnly(uid, StateUsers(EventType.USERS, playersMap.values().toList()))
        webSocketHandler.broadcastMessage(ConnectUser(EventType.CONNECT, player))

//        val aroundUsers = player.getAroundPlayer(playersMap.values().toList()).map { it.uid }
//        if (aroundUsers.isNotEmpty()) {
//            webSocketHandler.broadcastMessage(
//                aroundUsers,
//                StateUser(EventType.USER, player)
//            )
//        }
    }

    fun disconnectPlayer(uid: String) {
        playersMap.remove(uid)?.let {
            pool.free(it)

            playerFoodVisible.get(uid).clear()
            playerFoodVisible.remove(uid)
            // Broadcast disconnect around users
//            it.getAroundPlayer(playersMap.values().toList()).map { it.uid },
//          Broadcast all users
            webSocketHandler.broadcastMessage(DisconnectUser(EventType.DISCONNECT, uid))
        }
    }

    fun move(uid: String, move: Move) {
        val player = playersMap[uid]
        if (player != null) {
            player.angle = move.angle
            player.angleChange = true
            player.velocity.set(move.direction.toMove(player.speed))
        }
    }

    override fun update(delta: Float) {
        movePlayerList.clear()
        val players = playersMap.values().toList()

        // Move players and filling buffer changes.
        players.forEach { player ->
            if (player.velocity.len() > 0 || player.angleChange) {
                player.angleChange = false
                player.position.add(player.velocity.x * delta, player.velocity.y * delta)

                // FOODS UPDATE
                if (player.cameraViewport.hasCrossedBorder(player.position)) {
                    player.cameraViewport.updatePosition()

                    val viewport = player.cameraViewport.getViewportGlobal()
                    val foodsVisible = playerFoodVisible.get(player.uid)
//                    foodsVisible.clear()
                    foodsVisible.removeAll { !viewport.contains(it.position) }

                    val sendingFoods = foodService.getFoods()
                        .filter { viewport.contains(it.position) }
                        .filter { !foodsVisible.contains(it) }
                        .toList()

                    if (sendingFoods.isNotEmpty()) {
                        foodsVisible.addAll(sendingFoods.convert())
                        webSocketHandler.sendOnly(player.uid, Foods(EventType.FOODS, sendingFoods))
                    }
                }
//                eventService.addEvent(player)
                movePlayerList.add(player)
            }
        }

        // Совмещаем два списки - измененные объекты коллизиями и переместившимися пользователями в один список.
        val collisionList = collisionService.collision()
        movePlayerList
            .filter { !collisionList.contains(it, false) }
            .apply {
                collisionList.addAll(this.convert())
            }

        // Разделяем список объектов на категории для группировки в пакеты.
        val onlyPlayers = collisionList.filterIsInstance<Player>().toList()
        val onlyBullets = collisionList.filterIsInstance<Bullet>().toList()
        val onlyFoods = collisionList.filterIsInstance<Food>().toList()

        // Проходим по всем пользователям и проверяем по их viewport кому отправлять измененные данные.
        players.forEach { player ->
            val viewport = player.cameraViewport.getViewportGlobal()

            val sendingPlayersList = onlyPlayers.filter { viewport.contains(it.position) }
            if (sendingPlayersList.isNotEmpty()) {
                val packet = MoveUsers(EventType.MOVES, sendingPlayersList.map { it.mapToMove() })
                webSocketHandler.sendOnly(player.uid, packet)
            }

            val sendingBulletList = onlyBullets.filter { viewport.contains(it.position) }
            if (sendingBulletList.isNotEmpty()) {
                val packet = Bullets(EventType.SHOTS, sendingBulletList)
                webSocketHandler.sendOnly(player.uid, packet)
            }

            val sendingFoodsList = onlyFoods.filter { viewport.contains(it.position) }
            if (sendingFoodsList.isNotEmpty()) {
                val packet = Foods(EventType.FOODS, sendingFoodsList)
                webSocketHandler.sendOnly(player.uid, packet)
            }
        }
    }

    private fun Player.mapToMove(): MoveUser {
        return MoveUser(
            EventType.MOVE, this.uid, this.position.x, this.position.y, this.angle
        )
    }

}
