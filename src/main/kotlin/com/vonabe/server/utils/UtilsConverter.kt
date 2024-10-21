package com.vonabe.server.utils

import com.badlogic.gdx.utils.Array
import com.vonabe.server.data.send.Bounds
import com.vonabe.server.data.send.Player

object UtilsConverter {

    inline fun <reified T> List<T>.convert() = Array(this.toTypedArray())

    fun <T : Bounds> Player.getAroundPlayer(items: List<T>): List<T> {
        return items.filter { this.cameraViewport.getViewportGlobal().contains(it.position) }
    }

}
