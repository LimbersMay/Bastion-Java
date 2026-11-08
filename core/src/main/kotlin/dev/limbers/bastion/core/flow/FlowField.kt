package dev.limbers.bastion.core.flow

import dev.limbers.bastion.core.route.Cell
import dev.limbers.bastion.core.route.CellAt
import dev.limbers.bastion.core.route.RouteCell
import java.util.PriorityQueue

/** Caja de casillas, con los dos extremos incluidos. */
data class Box(val minX: Int, val minY: Int, val minZ: Int, val maxX: Int, val maxY: Int, val maxZ: Int) {
	val sizeX get() = maxX - minX + 1
	val sizeY get() = maxY - minY + 1
	val sizeZ get() = maxZ - minZ + 1

	fun contains(x: Int, y: Int, z: Int) = x in minX..maxX && y in minY..maxY && z in minZ..maxZ
}

/**
 * Campo de flujo: la distancia a pie de cada casilla de una zona a la meta (la brecha, la puerta, el patio), calculada
 * **una vez** para todas las unidades. Cada unidad solo mira la flecha de su casilla (`next`): con 200 invasores no
 * hay 200 rutas. Ver docs/ARQUITECTURA.md, «Muchas unidades: campos de flujo».
 *
 * Lógica pura: lee el mundo con `CellAt`, como `RoutePlanner`. Una casilla es transitable si una unidad de 2 de alto
 * cabe de pie en ella. Movimientos: andar en 4 direcciones, subir un escalón y bajar hasta `maxFall` bloques. De
 * momento no pica ni pone escaleras: eso queda para las rutas individuales (`RoutePlanner`).
 */
class FlowField private constructor(
	val box: Box,
	private val dist: IntArray,
	private val next: IntArray,
) {
	private fun index(x: Int, y: Int, z: Int) = ((x - box.minX) * box.sizeY + (y - box.minY)) * box.sizeZ + (z - box.minZ)

	private fun cellOf(i: Int): Cell {
		val z = i % box.sizeZ
		val rest = i / box.sizeZ
		val y = rest % box.sizeY
		val x = rest / box.sizeY
		return Cell(x + box.minX, y + box.minY, z + box.minZ)
	}

	/** Pasos hasta la meta desde la casilla, o -1 si desde ahí no se llega (o está fuera de la zona). */
	fun distance(x: Int, y: Int, z: Int): Int = if (box.contains(x, y, z)) dist[index(x, y, z)] else -1

	/** La casilla siguiente hacia la meta, o `null` si ya está en la meta o desde ahí no se llega. */
	fun next(x: Int, y: Int, z: Int): Cell? {
		if (!box.contains(x, y, z)) return null
		val n = next[index(x, y, z)]
		return if (n < 0) null else cellOf(n)
	}

	/** Casillas con distancia conocida: para medir y para las pruebas. */
	val reachable: Int get() = dist.count { it >= 0 }

	companion object {
		private val DIRECTIONS = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))

		/**
		 * Calcula el campo hacia `goals` dentro de `box`. Recorre el grafo al revés desde la meta (Dijkstra): para cada
		 * casilla busca desde qué casillas vecinas se llega a ella andando, subiendo o cayendo.
		 */
		fun compute(box: Box, goals: Collection<Cell>, cellAt: CellAt, maxFall: Int = 3): FlowField {
			val size = box.sizeX * box.sizeY * box.sizeZ
			val dist = IntArray(size) { -1 }
			val next = IntArray(size) { -1 }
			val field = FlowField(box, dist, next)

			// Lectura del mundo, con caché: cada casilla se pregunta muchas veces.
			val cache = arrayOfNulls<RouteCell>(size)
			fun cell(x: Int, y: Int, z: Int): RouteCell {
				if (!box.contains(x, y, z)) return cellAt.at(x, y, z)
				val i = field.index(x, y, z)
				return cache[i] ?: cellAt.at(x, y, z).also { cache[i] = it }
			}
			fun free(c: RouteCell) = c.cost == 0.0 && !c.ground
			fun standable(x: Int, y: Int, z: Int) =
				box.contains(x, y, z) && free(cell(x, y, z)) && free(cell(x, y + 1, z)) && cell(x, y - 1, z).ground

			// Cola con (distancia << 32 | índice).
			val open = PriorityQueue<Long>()
			for (g in goals) {
				if (!standable(g.x, g.y, g.z)) continue
				val i = field.index(g.x, g.y, g.z)
				if (dist[i] == 0) continue
				dist[i] = 0
				open.add(i.toLong())
			}

			fun relax(fromX: Int, fromY: Int, fromZ: Int, toIndex: Int, cost: Int) {
				if (!standable(fromX, fromY, fromZ)) return
				val i = field.index(fromX, fromY, fromZ)
				val d = dist[toIndex] + cost
				if (dist[i] >= 0 && dist[i] <= d) return
				dist[i] = d
				next[i] = toIndex
				open.add((d.toLong() shl 32) or i.toLong())
			}

			while (open.isNotEmpty()) {
				val item = open.poll()
				val bi = (item and 0xFFFFFFFFL).toInt()
				val bd = (item ushr 32).toInt()
				if (bd != dist[bi]) continue // versión antigua en la cola
				val (bx, by, bz) = field.cellOf(bi)
				for (d in DIRECTIONS) {
					val ax = bx - d[0]
					val az = bz - d[1]
					// Andando al mismo nivel: de A a B sin más.
					relax(ax, by, az, bi, 1)
					// Subiendo un escalón: A está un bloque más abajo, pisa el bloque de B como escalón y necesita aire
					// encima de la cabeza.
					cell(bx, by - 1, bz).let { step ->
						if (step.ground && !step.noStep && !step.climb && free(cell(ax, by + 1, az))) relax(ax, by - 1, az, bi, 2)
					}
					// Cayendo: A está más arriba y sale andando hacia la columna de B, que debe estar libre hasta caer en B.
					var clear = true
					for (h in 1..maxFall) {
						val ay = by + h
						if (!free(cell(bx, ay, bz)) || !free(cell(bx, ay + 1, bz))) clear = false
						if (!clear) break
						relax(ax, ay, az, bi, 1 + h)
					}
				}
			}
			return field
		}
	}
}
