package com.vonabe.server.game.service

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectMap
import com.badlogic.gdx.utils.Pool
import com.badlogic.gdx.utils.TimeUtils
import com.vonabe.server.data.EventType
import com.vonabe.server.data.send.Bullet
import com.vonabe.server.data.send.Bullets
import com.vonabe.server.data.send.Player
import com.vonabe.server.utils.DirectionMove.calculateDirection
import com.vonabe.server.utils.UtilsConverter.convert
import com.vonabe.server.websocket.WebSocketHandler
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class BulletService(
    val playerService: PlayerService,
    val webSocketHandler: WebSocketHandler,
    val worldSize: Rectangle,
) : GameService {

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
            bullet.direction.set(0f, 0f)
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
    private var sendBulletList = Array<Bullet>()

    private val bullets = Array<Bullet>()

    fun getBullets() = bullets.toList()

    fun shot(player: Player) {
        val bullet = bulletPool.obtain()
        bullet.id = Random.nextInt(0, Int.MAX_VALUE)
        bullet.ownerUid = player.uid
        bullet.direction.set(player.angle.toFloat().calculateDirection())
        bullet.speed = 0.8f
        bullet.radius = 15f
        bullet.timestamp = TimeUtils.millis()
        bullet.remove = false

        // Вычисляем начальную позицию пули: добавляем смещение на конец ствола (playerRadius + bullet.radius)
        val spawnOffset = player.radius + bullet.radius
        val offsetX = MathUtils.cosDeg(player.angle.toFloat()) * spawnOffset
        val offsetY = MathUtils.sinDeg(player.angle.toFloat()) * spawnOffset
        // Устанавливаем начальную позицию пули
        bullet.position.set(player.position.x + offsetX, player.position.y + offsetY)

        bullets.add(bullet)
        sendBulletList.add(bullet)
    }

    private var autoShotLastUpdate = 0L

    override fun update(delta: Float) {
        val newTimestamp = TimeUtils.millis()

        val players = playerService.getPlayers()
        // Auto spawn bullet
        if ((newTimestamp - autoShotLastUpdate) > 30) {
            autoShotLastUpdate = newTimestamp
            players.forEach {
                if (it.autoShot) shot(it)
            }
        }

        bullets
            .filter { newTimestamp - it.timestamp > 25 }
            .forEach { bullet ->
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

                if (bullet.remove || players.any { it.cameraViewport.getViewportGlobal().contains(bullet.position) }) {
                    sendBulletList.add(bullet)
                }
            }

        if (sendBulletList.notEmpty()) {
            players.forEach { player ->
                val viewportGlobal = player.cameraViewport.getViewportGlobal()
                val sendBullets = sendBulletList.filter { viewportGlobal.contains(it.position) || it.remove }
                if (sendBullets.isNotEmpty()) {
                    webSocketHandler.sendOnly(player.uid, Bullets(EventType.SHOTS, sendBullets))
                }
            }
            sendBulletList.clear()
        }

        val removeList = bullets.filter { it.remove }.convert()
        bullets.removeAll(removeList, false)
        bulletPool.freeAll(removeList)
    }

    private val notifyBulletMap = ObjectMap<String, Array<Int>>()
    private fun Bullet.checkNotify(player: Player): Boolean {
        val viewport = player.cameraViewport.getViewportGlobal()
        return viewport.contains(this.position) && (notifyBulletMap.containsKey(player.uid) && !notifyBulletMap.get(player.uid).contains(this.id))
    }

}
