package com.vonabe.server.utils

import com.badlogic.gdx.utils.Array

object UtilsConverter {

    inline fun <reified T> List<T>.convert() = Array<T>(this.toTypedArray())

}
