package com.vonabe.server.game.service

import com.badlogic.gdx.utils.Array
import com.vonabe.server.data.send.Bounds
import org.springframework.context.annotation.ComponentScan

@ComponentScan
class SpatialGrid(private val cellSize: Float) {
    private val grid = mutableMapOf<Pair<Int, Int>, MutableList<Bounds>>()

    // Получить ключ ячейки для объекта
    private fun getCellKey(x: Float, y: Float): Pair<Int, Int> {
        return Pair((x / cellSize).toInt(), (y / cellSize).toInt())
    }

    // Добавление объекта в сетку
    fun addObject(obj: Bounds) {
        val cellKey = getCellKey(obj.position.x, obj.position.y)
        grid.computeIfAbsent(cellKey) { mutableListOf() }.add(obj)
    }

    // Добавление объекта в сетку
    fun addObjects(obj: Array<out Bounds>) {
        obj.forEach { addObject(it) }
    }

    // Метод для обновления позиции объекта в пространственной сетке
    fun updatePosition(bounds: Bounds) {
        // Удаляем объект из старой позиции
        removeFromGrid(bounds)

        // Обновляем позицию объекта
        // Например, если ваш объект имеет поля position.x и position.y
        val newKey = getGridKey(bounds.position.x, bounds.position.y)
        grid.computeIfAbsent(newKey) { mutableListOf() }.add(bounds)
    }

    // Метод для удаления объекта из старой позиции
    private fun removeFromGrid(bounds: Bounds) {
        // Определяем ключ сетки на основе текущей позиции объекта
        val oldKey = getGridKey(bounds.position.x, bounds.position.y)
        grid[oldKey]?.remove(bounds)
    }

    // Метод для получения ключа сетки (можно настроить по своему усмотрению)
    private fun getGridKey(x: Float, y: Float): Pair<Int, Int> {
        // Например, предположим, что размер ячеек сетки 100x100
        val cellSize = 100
        return Pair((x / cellSize).toInt(), (y / cellSize).toInt())
    }

    // Получить объекты в радиусе
    fun getObjectsInRadius(x: Float, y: Float, radius: Float): List<Bounds> {
        val cellKey = getCellKey(x, y)
        val result = mutableListOf<Bounds>()
        val searchRadius = (radius / cellSize).toInt() + 1 // количество ячеек для поиска вокруг объекта

        for (dx in -searchRadius..searchRadius) {
            for (dy in -searchRadius..searchRadius) {
                val nearbyCellKey = Pair(cellKey.first + dx, cellKey.second + dy)
                grid[nearbyCellKey]?.let { objects ->
                    result.addAll(objects.filter {
                        val distance = it.position.dst(x, y)
                        distance <= radius + it.radius // проверка с учетом радиуса объекта
                    })
                }
            }
        }
        return result
    }

    fun clear() {
        grid.clear()
    }
}
