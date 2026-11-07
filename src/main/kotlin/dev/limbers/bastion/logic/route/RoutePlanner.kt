package dev.limbers.bastion.logic.route

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.floor

/*
 * Buscador de rutas para unidades de 2 bloques de alto. Lógica pura: no toca el juego, lee el mundo a través de
 * `CellAt`, así que se prueba sin Minecraft (RoutePlannerTest).
 *
 * Traducido de `routeLogic.ts` del proyecto de Bedrock (v0.1.55), con sus pruebas. Una ruta puede pasar a través de
 * bloques: romperlos cuesta más que caminar, de modo que la unidad rodea si el rodeo es corto y atraviesa si no lo
 * es. También puede poner escaleras de mano para subir una pared o bajar un desnivel.
 *
 * Diferencia con Bedrock: allí el juego no seguía la ruta y el director llevaba a la unidad a mano. Aquí la ruta es
 * lo que la unidad ejecuta: camina, pica y coloca ella misma (AssaultGoal).
 */

/** Cómo cuenta un bloque para quien quiere pasar. */
data class RouteCell(
	/** Lo que cuesta quitarlo, en pasos de camino. 0 si se atraviesa sin más; infinito si no se puede. */
	val cost: Double,
	/** ¿Se puede estar de pie encima? */
	val ground: Boolean,
	/** Una escalera de mano: se sube y se baja por dentro. */
	val climb: Boolean = false,
	/** No se puede subir a él de un salto, como una valla. */
	val noStep: Boolean = false,
	/** No es un bloque entero (losa, escalón, vidrio fino...): una escalera de mano no se sostiene en él. */
	val partial: Boolean = false,
	/** Arde (madera, lana...): sostiene una escalera, pero se prefiere una cara que no arda. */
	val flammable: Boolean = false,
) {
	val breakable: Boolean get() = cost > 0 && cost.isFinite()

	companion object {
		val FREE = RouteCell(cost = 0.0, ground = false)
		val CLIMB = RouteCell(cost = 0.0, ground = true, climb = true)

		/** Sólido que no se rompe, pero que se puede pisar. */
		val SOLID = RouteCell(cost = Double.POSITIVE_INFINITY, ground = true)

		/** Ni se cruza ni se pisa: agua y lava. */
		val HAZARD = RouteCell(cost = Double.POSITIVE_INFINITY, ground = false)
	}
}

fun interface CellAt {
	fun at(x: Int, y: Int, z: Int): RouteCell
}

data class Cell(val x: Int, val y: Int, val z: Int)

/** Una casilla de una escalera de mano por poner, y hacia dónde mira (dx, dz): de la pared hacia fuera. */
data class LadderCell(val x: Int, val y: Int, val z: Int, val dx: Int, val dz: Int)

/** Una escalera de mano por poner. Las casillas van de abajo arriba. */
data class LadderMove(val up: Boolean, val cells: List<LadderCell>)

data class RouteStep(
	val x: Int,
	val y: Int,
	val z: Int,
	/** Bloques que hay que quitar para dar este paso, en orden. */
	val breaks: List<Cell> = emptyList(),
	/** El paso se da por una escalera de mano que hay que poner: se llega aquí desde el anterior por ella. */
	val ladder: LadderMove? = null,
)

data class Route(
	val steps: List<RouteStep>,
	/** `true` si llega a la meta; `false` si es lo más cerca que se pudo con el presupuesto. */
	val complete: Boolean,
	/** Casillas exploradas: mide lo que costó calcularla. */
	val nodes: Int,
)

data class RouteOptions(
	/** Tope de casillas a explorar. */
	val maxNodes: Int = 1500,
	/** A esta distancia horizontal de la meta ya se ha llegado. */
	val reach: Double = 1.5,
	/** Máxima altura de caída voluntaria: más que esto hace daño. */
	val maxFall: Int = 3,
	/**
	 * Puede poner escaleras de mano de hasta esta altura, para bajar donde la caída es mayor que `maxFall` o para
	 * subir una pared de 2 o más bloques. Solo en casillas libres y apoyadas en un bloque macizo. 0: ninguna.
	 */
	val ladders: Int = 0,
)

object RoutePlanner {
	/** Por encima de 1 busca más deprisa a cambio de rutas algo menos cortas. */
	private const val GREED = 1.2
	private const val STEP_UP_COST = 1.5

	/** Poner una escalera cuesta como unos pasos de rodeo, más un poco por bloque: se prefiere andar si se puede. */
	private const val LADDER_COST = 6.0
	private const val LADDER_COST_PER_BLOCK = 1.5

	/** De más por cada escalón apoyado en algo que arde: antes una cara de piedra, pero la madera vale. */
	private const val LADDER_FLAMMABLE_COST = 3.0

	private val DIRECTIONS = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))

	/** ¿Sostiene una escalera de mano? Un bloque entero que se pisa, que no es otra escalera ni una valla. */
	fun holdsLadder(c: RouteCell): Boolean = c.ground && c.cost != 0.0 && !c.climb && !c.noStep && !c.partial

	private class Node(
		val x: Int,
		val y: Int,
		val z: Int,
		val g: Double,
		val f: Double,
		val from: Node?,
		val breaks: List<Cell>,
		val ladder: LadderMove?,
	) {
		var closed = false
	}

	private fun keyOf(x: Int, y: Int, z: Int): Long =
		((x + 33_554_432L) shl 38) or ((z + 33_554_432L) shl 12) or (y + 2048L)

	/** Calcula la ruta de una vez. `null` si no hay ni un paso útil que dar. */
	fun plan(startX: Double, startY: Double, startZ: Double, goalX: Double, goalY: Double, goalZ: Double, cellAt: CellAt, options: RouteOptions = RouteOptions()): Route? {
		val maxFall = options.maxFall
		val reach = options.reach
		val gx = floor(goalX).toInt()
		val gy = floor(goalY).toInt()
		val gz = floor(goalZ).toInt()

		fun estimate(x: Int, y: Int, z: Int): Int = abs(x - gx) + abs(y - gy) + abs(z - gz)
		fun arrived(n: Node): Boolean {
			val dx = (n.x - gx).toDouble()
			val dz = (n.z - gz).toDouble()
			return dx * dx + dz * dz <= reach * reach && abs(n.y - gy) <= 1
		}

		val sx = floor(startX).toInt()
		val sy = floor(startY).toInt()
		val sz = floor(startZ).toInt()
		val first = Node(sx, sy, sz, 0.0, 0.0, null, emptyList(), null)
		val seen = HashMap<Long, Node>()
		seen[keyOf(sx, sy, sz)] = first
		val open = PriorityQueue<Node>(compareBy { it.f })
		open.add(first)

		var best = first
		var bestH = estimate(sx, sy, sz)
		var nodes = 0

		fun offer(from: Node, x: Int, y: Int, z: Int, cost: Double, breaks: List<Cell>, ladder: LadderMove? = null) {
			val g = from.g + cost
			val key = keyOf(x, y, z)
			val known = seen[key]
			if (known != null && known.g <= g) return
			val node = Node(x, y, z, g, g + GREED * estimate(x, y, z), from, breaks, ladder)
			known?.closed = true // la versión antigua queda descartada en la cola
			seen[key] = node
			open.add(node)
		}

		/** Altura a la que aterriza quien queda en (x, y, z) sin suelo. `null` si caería demasiado o en agua. */
		fun landing(x: Int, y: Int, z: Int): Int? {
			for (drop in 0..maxFall) {
				val below = cellAt.at(x, y - drop - 1, z)
				if (below.ground) return y - drop
				if (below.cost != 0.0) return null
			}
			return null
		}

		val ladderMax = options.ladders

		/** Casilla donde cabe una escalera: libre del todo, sin romper nada. */
		fun fits(c: RouteCell) = c.cost == 0.0 && !c.ground

		/** La pared para la casilla: primero la de detrás (bx, bz), si no cualquier otra; antes una que no arda. */
		fun wallFor(x: Int, y: Int, z: Int, bx: Int, bz: Int): IntArray? {
			val order = listOf(intArrayOf(bx, bz)) + DIRECTIONS.filter { it[0] != bx || it[1] != bz }
			var fallback: IntArray? = null
			for (w in order) {
				val c = cellAt.at(x + w[0], y, z + w[1])
				if (!holdsLadder(c)) continue
				if (!c.flammable) return w
				if (fallback == null) fallback = w
			}
			return fallback
		}

		fun burnCost(cells: List<LadderCell>): Double =
			LADDER_FLAMMABLE_COST * cells.count { cellAt.at(it.x - it.dx, it.y, it.z - it.dz).flammable }

		/**
		 * Bajar por una escalera puesta en la columna (cx, cz), desde quien está en el borde a la altura `y`. Cada
		 * casilla se apoya en la pared que tenga. `null` si no llega al suelo, termina en agua o le falta pared.
		 */
		fun ladderDown(cx: Int, y: Int, cz: Int, dx: Int, dz: Int): Pair<Int, LadderMove>? {
			val cells = ArrayList<LadderCell>()
			for (yy in y - 1 downTo y - ladderMax) {
				if (!fits(cellAt.at(cx, yy, cz))) return null
				val wall = wallFor(cx, yy, cz, -dx, -dz) ?: return null
				cells.add(LadderCell(cx, yy, cz, -wall[0], -wall[1]))
				val below = cellAt.at(cx, yy - 1, cz)
				if (below.ground) return yy to LadderMove(up = false, cells = cells.reversed())
				if (below.cost != 0.0) return null
			}
			return null
		}

		/**
		 * Subir por una escalera puesta en la propia casilla (x, z), apoyada en la pared de delante (dx, dz), hasta
		 * salir por encima. Solo para paredes de 2 o más: un bloque se sube de un salto.
		 */
		fun ladderUp(x: Int, y: Int, z: Int, dx: Int, dz: Int): Pair<Cell, LadderMove>? {
			val cells = ArrayList<LadderCell>()
			for (top in y until y + ladderMax) {
				if (!holdsLadder(cellAt.at(x + dx, top, z + dz)) || !fits(cellAt.at(x, top, z)) || !fits(cellAt.at(x, top + 1, z))) return null
				cells.add(LadderCell(x, top, z, -dx, -dz))
				if (fits(cellAt.at(x + dx, top + 1, z + dz)) && fits(cellAt.at(x + dx, top + 2, z + dz))) {
					if (top == y || !fits(cellAt.at(x, top + 2, z))) return null
					return Cell(x + dx, top + 1, z + dz) to LadderMove(up = true, cells = cells)
				}
			}
			return null
		}

		while (open.isNotEmpty() && nodes < options.maxNodes) {
			val n = open.poll()
			if (n.closed) continue
			n.closed = true
			nodes++

			if (arrived(n)) return Route(unwind(n), complete = true, nodes = nodes)
			val h = estimate(n.x, n.y, n.z)
			if (h < bestH) {
				best = n
				bestH = h
			}

			val x = n.x
			val y = n.y
			val z = n.z
			val headroom = cellAt.at(x, y + 2, z)

			for (d in DIRECTIONS) {
				val dx = d[0]
				val dz = d[1]
				val nx = x + dx
				val nz = z + dz
				val feet = cellAt.at(nx, y, nz)
				val head = cellAt.at(nx, y + 1, nz)

				// Al mismo nivel, o cayendo si no hay suelo.
				if (feet.cost.isFinite() && head.cost.isFinite()) {
					val land = landing(nx, y, nz)
					if (land != null) {
						val breaks = ArrayList<Cell>()
						if (head.cost > 0) breaks.add(Cell(nx, y + 1, nz))
						if (feet.cost > 0) breaks.add(Cell(nx, y, nz))
						offer(n, nx, land, nz, 1 + feet.cost + head.cost + (y - land), breaks)
					}
				}

				// Un escalón: lo de delante se queda para pisarlo.
				if (feet.ground && !feet.climb && !feet.noStep && headroom.cost.isFinite()) {
					val upperFeet = head
					val upperHead = cellAt.at(nx, y + 2, nz)
					if (upperFeet.cost.isFinite() && upperHead.cost.isFinite()) {
						val breaks = ArrayList<Cell>()
						if (headroom.cost > 0) breaks.add(Cell(x, y + 2, z))
						if (upperHead.cost > 0) breaks.add(Cell(nx, y + 2, nz))
						if (upperFeet.cost > 0) breaks.add(Cell(nx, y + 1, nz))
						offer(n, nx, y + 1, nz, STEP_UP_COST + headroom.cost + upperHead.cost + upperFeet.cost, breaks)
					}
				}

				if (ladderMax > 0) {
					// Por una escalera puesta: hacia abajo, donde la caída es demasiado alta; hacia arriba, por una pared.
					if (fits(feet) && fits(head) && landing(nx, y, nz) == null) {
						ladderDown(nx, y, nz, dx, dz)?.let { (land, move) ->
							offer(n, nx, land, nz, LADDER_COST + LADDER_COST_PER_BLOCK * (y - land) + burnCost(move.cells), emptyList(), move)
						}
					}
					if (holdsLadder(feet) && !fits(head)) {
						ladderUp(x, y, z, dx, dz)?.let { (exit, move) ->
							offer(n, exit.x, exit.y, exit.z, LADDER_COST + LADDER_COST_PER_BLOCK * (exit.y - y) + burnCost(move.cells), emptyList(), move)
						}
					}
				}
			}

			// Por la escalera de mano, hacia arriba.
			if ((cellAt.at(x, y, z).climb || cellAt.at(x, y + 1, z).climb) && headroom.cost.isFinite()) {
				offer(n, x, y + 1, z, 1 + headroom.cost, if (headroom.cost > 0) listOf(Cell(x, y + 2, z)) else emptyList())
			}

			// Hacia abajo: por la escalera de mano, o abriendo el suelo que pisa.
			val floorCell = cellAt.at(x, y - 1, z)
			if (floorCell.climb) {
				offer(n, x, y - 1, z, 1.0, emptyList())
			} else if (floorCell.ground && floorCell.cost.isFinite()) {
				val land = landing(x, y - 1, z)
				if (land != null) offer(n, x, land, z, 1 + floorCell.cost + (y - 1 - land), listOf(Cell(x, y - 1, z)))
			}
		}

		if (best === first) return null
		return Route(unwind(best), complete = false, nodes = nodes)
	}

	private fun unwind(last: Node): List<RouteStep> {
		val steps = ArrayList<RouteStep>()
		var n: Node? = last
		while (n?.from != null) {
			steps.add(RouteStep(n.x, n.y, n.z, n.breaks, n.ladder))
			n = n.from
		}
		return steps.reversed()
	}
}
