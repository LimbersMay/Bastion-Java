package dev.limbers.bastion.logic.flow

import kotlin.math.floor

/**
 * Rejilla espacial: reparte puntos en casillas de `cellSize` bloques para encontrar al más cercano mirando solo las
 * casillas de alrededor, no a todos. Se vacía y se rellena cada vez (con 400 unidades cuesta muy poco).
 *
 * Es lo contrario de `reselect_targets` de Bedrock, que hacía que cada unidad mirase a todas las demás. Lógica pura:
 * sirve para cualquier cosa con posición.
 */
class SpatialGrid<T>(private val cellSize: Int = 8) {
	private class Entry<T>(val x: Double, val z: Double, val item: T)

	private val cells = HashMap<Long, ArrayList<Entry<T>>>()
	var size = 0
		private set

	private fun key(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
	private fun cellOf(v: Double) = floor(v / cellSize).toInt()

	fun clear() {
		cells.values.forEach { it.clear() }
		size = 0
	}

	fun add(x: Double, z: Double, item: T) {
		cells.getOrPut(key(cellOf(x), cellOf(z))) { ArrayList() }.add(Entry(x, z, item))
		size++
	}

	/** El más cercano a (x, z) a `radius` bloques o menos (en horizontal) que cumpla `accept`, o `null`. */
	fun nearest(x: Double, z: Double, radius: Double, accept: (T) -> Boolean = { true }): T? {
		val r2 = radius * radius
		var best: T? = null
		var bestD2 = Double.MAX_VALUE
		for (cx in cellOf(x - radius)..cellOf(x + radius)) {
			for (cz in cellOf(z - radius)..cellOf(z + radius)) {
				val list = cells[key(cx, cz)] ?: continue
				for (e in list) {
					val dx = e.x - x
					val dz = e.z - z
					val d2 = dx * dx + dz * dz
					if (d2 <= r2 && d2 < bestD2 && accept(e.item)) {
						best = e.item
						bestD2 = d2
					}
				}
			}
		}
		return best
	}

	/** Cuántos hay a `radius` bloques o menos de (x, z). */
	fun countWithin(x: Double, z: Double, radius: Double): Int {
		val r2 = radius * radius
		var n = 0
		for (cx in cellOf(x - radius)..cellOf(x + radius)) {
			for (cz in cellOf(z - radius)..cellOf(z + radius)) {
				val list = cells[key(cx, cz)] ?: continue
				for (e in list) {
					val dx = e.x - x
					val dz = e.z - z
					if (dx * dx + dz * dz <= r2) n++
				}
			}
		}
		return n
	}
}
