package dev.limbers.bastion.core.route

import dev.limbers.bastion.core.route.RouteCell.Companion.FREE
import dev.limbers.bastion.core.route.RouteCell.Companion.HAZARD
import dev.limbers.bastion.core.route.RouteCell.Companion.SOLID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Traducción de `tests/routeLogic.test.ts` y `tests/ladderRoute.test.ts` del proyecto de Bedrock. Las pruebas de
 * `classifyBlock` no se traducen: en Java la clasificación sale de las propiedades del bloque (LevelCells) y se
 * prueba dentro del juego.
 */
class RoutePlannerTest {
	private val stone = RouteCell(cost = 6.0, ground = true)
	private val fence = RouteCell(cost = 4.0, ground = true, noStep = true)
	private val slab = RouteCell(cost = 6.0, ground = true, partial = true)

	/** Un mundo de prueba: suelo (irrompible por defecto) en y <= 0, aire encima, y los bloques que se indiquen. */
	private fun world(blocks: Map<Cell, RouteCell> = emptyMap(), floor: RouteCell = SOLID) =
		CellAt { x, y, z -> blocks[Cell(x, y, z)] ?: if (y <= 0) floor else FREE }

	/** Rellena una caja, con los dos extremos incluidos. */
	private fun MutableMap<Cell, RouteCell>.fill(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int, cell: RouteCell = SOLID): MutableMap<Cell, RouteCell> {
		for (x in x0..x1) for (y in y0..y1) for (z in z0..z1) this[Cell(x, y, z)] = cell
		return this
	}

	/** Un muro de piedra a lo largo del eje z en esa x, de `height` bloques de alto. */
	private fun wallAt(x: Int, height: Int, zFrom: Int = -30, zTo: Int = 30) =
		mutableMapOf<Cell, RouteCell>().fill(x, 1, zFrom, x, height, zTo, stone)

	private fun plan(from: Triple<Double, Double, Double>, to: Triple<Double, Double, Double>, cellAt: CellAt, options: RouteOptions = RouteOptions()) =
		RoutePlanner.plan(from.first, from.second, from.third, to.first, to.second, to.third, cellAt, options)

	private fun p(x: Double, y: Double, z: Double) = Triple(x, y, z)
	private fun breaksOf(route: Route?) = route?.steps?.flatMap { it.breaks } ?: emptyList()
	private fun ladders(route: Route?) = route?.steps?.mapNotNull { it.ladder } ?: emptyList()

	/** Cada casilla de escalera está libre y apoyada en un bloque macizo, detrás de hacia donde mira. */
	private fun assertHeld(route: Route?, cellAt: CellAt) {
		for (l in ladders(route)) for (c in l.cells) {
			assertEquals(FREE, cellAt.at(c.x, c.y, c.z))
			assertTrue(cellAt.at(c.x - c.dx, c.y, c.z - c.dz).ground)
		}
	}

	@Nested
	inner class Basico {
		@Test
		fun `en terreno libre camina sin romper nada`() {
			val route = plan(p(0.5, 1.0, 0.5), p(8.5, 1.0, 0.5), world())
			assertEquals(true, route?.complete)
			assertEquals(emptyList<Cell>(), breaksOf(route))
		}

		@Test
		fun `atraviesa un muro largo abriendo un hueco de dos bloques`() {
			val route = plan(p(0.5, 1.0, 0.5), p(8.5, 1.0, 0.5), world(wallAt(4, 4)))
			assertEquals(true, route?.complete)
			assertEquals(listOf(Cell(4, 2, 0), Cell(4, 1, 0)), breaksOf(route))
		}

		@Test
		fun `rodea el muro si el rodeo es corto`() {
			val route = plan(p(0.5, 1.0, 0.5), p(8.5, 1.0, 0.5), world(wallAt(4, 4, -2, 2)))
			assertEquals(true, route?.complete)
			assertEquals(emptyList<Cell>(), breaksOf(route))
		}

		@Test
		fun `abre el suelo para bajar hasta quien esta debajo`() {
			val blocks = mutableMapOf<Cell, RouteCell>().fill(-30, 5, -30, 30, 5, 30, stone)
			val route = plan(p(0.5, 6.0, 0.5), p(0.5, 1.0, 0.5), world(blocks), RouteOptions(maxFall = 4))
			assertEquals(true, route?.complete)
			assertTrue(Cell(0, 5, 0) in breaksOf(route))
		}

		@Test
		fun `no cae mas de lo permitido`() {
			val pit = CellAt { x, y, _ -> if (x == 1) (if (y <= -20) SOLID else FREE) else if (y <= 0) SOLID else FREE }
			val route = plan(p(0.5, 1.0, 0.5), p(1.5, -19.0, 0.5), pit, RouteOptions(maxNodes = 50))
			assertFalse(route?.complete ?: false)
		}

		@Test
		fun `no pisa agua ni la cruza`() {
			val blocks = mutableMapOf<Cell, RouteCell>().fill(4, 0, -30, 4, 2, 30, HAZARD)
			val route = plan(p(0.5, 1.0, 0.5), p(8.5, 1.0, 0.5), world(blocks), RouteOptions(maxNodes = 300))
			assertFalse(route?.complete ?: false)
		}

		@Test
		fun `no rompe lo que no se puede romper`() {
			val blocks = mutableMapOf<Cell, RouteCell>().fill(4, 1, -30, 4, 8, 30, SOLID)
			val route = plan(p(0.5, 1.0, 0.5), p(8.5, 1.0, 0.5), world(blocks), RouteOptions(maxNodes = 300))
			assertFalse(route?.complete ?: false)
			assertEquals(emptyList<Cell>(), breaksOf(route))
		}

		@Test
		fun `si se agota el presupuesto devuelve lo mas cerca que pudo`() {
			val route = plan(p(0.5, 1.0, 0.5), p(500.5, 1.0, 0.5), world(), RouteOptions(maxNodes = 40))
			assertEquals(false, route?.complete)
			assertTrue(route!!.steps.size > 5)
		}

		@Test
		fun `sube un peldano natural sin romperlo`() {
			val route = plan(p(0.5, 1.0, 0.5), p(4.5, 2.0, 0.5), world(wallAt(3, 1)))
			assertEquals(true, route?.complete)
			assertEquals(emptyList<Cell>(), breaksOf(route))
		}
	}

	@Nested
	inner class Escaleras {
		private val opts = RouteOptions(ladders = 24, maxNodes = 20000)

		/** Torre maciza de 5x5 y 10 de alto: se está de pie arriba en y = 11. */
		private fun tower() = mutableMapOf<Cell, RouteCell>().fill(-2, 1, -2, 2, 10, 2)

		@Test
		fun `sin la opcion no pone escaleras - desde lo alto de la torre no hay ruta`() {
			val route = plan(p(0.5, 11.0, 0.5), p(8.5, 1.0, 0.5), world(tower()), RouteOptions(maxNodes = 20000))
			assertFalse(route?.complete ?: false)
		}

		@Test
		fun `baja de la torre por una escalera de toda su altura, pegada a la pared`() {
			val cellAt = world(tower())
			val route = plan(p(0.5, 11.0, 0.5), p(8.5, 1.0, 0.5), cellAt, opts)
			assertEquals(true, route?.complete)
			val all = ladders(route)
			assertEquals(1, all.size)
			assertFalse(all[0].up)
			assertEquals((1..10).toList(), all[0].cells.map { it.y })
			assertHeld(route, cellAt)
		}

		@Test
		fun `sube a la torre por una escalera y sale por encima`() {
			val cellAt = world(tower())
			val route = plan(p(8.5, 1.0, 0.5), p(0.5, 11.0, 0.5), cellAt, opts)
			assertEquals(true, route?.complete)
			val all = ladders(route)
			assertEquals(1, all.size)
			assertTrue(all[0].up)
			assertEquals((1..10).toList(), all[0].cells.map { it.y })
			assertEquals(11, route!!.steps.first { it.ladder != null }.y)
			assertHeld(route, cellAt)
		}

		@Test
		fun `una caida corta se salta, sin escalera`() {
			val route = plan(p(0.5, 4.0, 0.5), p(8.5, 1.0, 0.5), world(mutableMapOf<Cell, RouteCell>().fill(-2, 1, -2, 2, 3, 2)), opts)
			assertEquals(true, route?.complete)
			assertEquals(emptyList<LadderMove>(), ladders(route))
		}

		@Test
		fun `acantilado con un saliente a media altura - dos escaleras con un tramo a pie entre ellas`() {
			val blocks = mutableMapOf<Cell, RouteCell>().fill(-10, 1, -30, 2, 12, 30).fill(3, 1, -30, 5, 6, 30)
			val cellAt = world(blocks)
			val route = plan(p(0.5, 13.0, 0.5), p(12.5, 1.0, 0.5), cellAt, opts)
			assertEquals(true, route?.complete)
			val all = ladders(route)
			assertEquals(2, all.size)
			assertTrue(all.none { it.up })
			assertHeld(route, cellAt)
		}

		@Test
		fun `y al reves - sube al saliente y despues a la meseta`() {
			val blocks = mutableMapOf<Cell, RouteCell>().fill(-10, 1, -30, 2, 12, 30).fill(3, 1, -30, 5, 6, 30)
			val cellAt = world(blocks)
			val route = plan(p(12.5, 1.0, 0.5), p(-2.5, 13.0, 0.5), cellAt, opts)
			assertEquals(true, route?.complete)
			val all = ladders(route)
			assertEquals(2, all.size)
			assertTrue(all.all { it.up })
			assertHeld(route, cellAt)
		}

		@Test
		fun `bajo una cornisa que sobresale no hay pared - busca otra columna donde si la haya`() {
			val blocks = mutableMapOf<Cell, RouteCell>().fill(-10, 1, -30, 2, 12, 30).fill(3, 12, -30, 3, 12, 4).fill(3, 12, 8, 3, 12, 30)
			val cellAt = world(blocks)
			val route = plan(p(0.5, 13.0, 0.5), p(10.5, 1.0, 0.5), cellAt, opts)
			assertEquals(true, route?.complete)
			assertTrue(ladders(route)[0].cells.all { it.z in 5..7 })
			assertHeld(route, cellAt)
		}

		@Test
		fun `si la cornisa sobresale por todas partes, no hay escalera posible`() {
			val blocks = mutableMapOf<Cell, RouteCell>()
				.fill(-10, 1, -30, 2, 12, 30)
				.fill(-11, 12, -31, 3, 12, -31)
				.fill(-11, 12, 31, 3, 12, 31)
				.fill(-11, 12, -31, -11, 12, 31)
				.fill(3, 12, -31, 3, 12, 31)
			val route = plan(p(0.5, 13.0, 0.5), p(10.5, 1.0, 0.5), world(blocks), opts.copy(maxNodes = 6000))
			assertFalse(route?.complete ?: false)
		}

		@Test
		fun `no baja una escalera hasta el agua`() {
			val blocks = mutableMapOf<Cell, RouteCell>().fill(-10, 1, -30, 2, 12, 30).fill(3, 0, -30, 30, 0, 30, HAZARD)
			val route = plan(p(0.5, 13.0, 0.5), p(10.5, 1.0, 0.5), world(blocks), opts.copy(maxNodes = 6000))
			assertFalse(ladders(route).any { !it.up })
		}

		@Test
		fun `muralla con almenas - sube por un hueco entre almenas`() {
			val blocks = mutableMapOf<Cell, RouteCell>().fill(4, 1, -30, 6, 6, 30)
			for (z in -30..30 step 2) blocks[Cell(4, 7, z)] = SOLID
			val cellAt = world(blocks)
			val route = plan(p(0.5, 1.0, 0.5), p(5.5, 7.0, 0.5), cellAt, opts)
			assertEquals(true, route?.complete)
			val l = ladders(route)[0]
			assertTrue(l.up)
			assertEquals(1, abs(l.cells[0].z) % 2)
			assertHeld(route, cellAt)
		}

		@Test
		fun `una valla en lo alto de la muralla impide salir por encima`() {
			val blocks = mutableMapOf<Cell, RouteCell>()
				.fill(4, 1, -30, 6, 6, 30)
				.fill(4, 7, -30, 4, 7, 30, fence)
				.fill(6, 7, -30, 6, 7, 30, fence)
				.fill(4, 7, -30, 6, 7, -30, fence)
				.fill(4, 7, 30, 6, 7, 30, fence)
			val route = plan(p(0.5, 1.0, 0.5), p(5.5, 7.0, 0.5), world(blocks), opts.copy(maxNodes = 6000))
			assertFalse(ladders(route).any { it.up })
		}

		@Test
		fun `no pone una escalera mas alta que el maximo`() {
			val route = plan(p(0.5, 11.0, 0.5), p(8.5, 1.0, 0.5), world(tower()), RouteOptions(ladders = 6, maxNodes = 6000))
			assertFalse(route?.complete ?: false)
		}
	}

	@Nested
	inner class DondeSeSostienen {
		private val opts = RouteOptions(ladders = 24, maxNodes = 20000)

		@Test
		fun `no baja por una cara de losas - busca otra cara entera`() {
			val blocks = mutableMapOf<Cell, RouteCell>().fill(-2, 1, -2, 1, 10, 2).fill(2, 1, -2, 2, 10, 2, slab)
			val cellAt = world(blocks)
			val route = plan(p(0.5, 11.0, 0.5), p(8.5, 1.0, 0.5), cellAt, opts)
			assertEquals(true, route?.complete)
			for (l in ladders(route)) for (c in l.cells) assertFalse(cellAt.at(c.x - c.dx, c.y, c.z - c.dz).partial)
		}

		@Test
		fun `si toda la torre es de losas, no hay escalera posible`() {
			val cellAt = world(mutableMapOf<Cell, RouteCell>().fill(-2, 1, -2, 2, 10, 2, slab))
			val route = plan(p(0.5, 11.0, 0.5), p(8.5, 1.0, 0.5), cellAt, opts)
			assertEquals(emptyList<LadderMove>(), ladders(route))
		}
	}

	@Nested
	inner class Fuego {
		private val opts = RouteOptions(ladders = 24, maxNodes = 20000)

		/** Tablones que no se rompen y arden. */
		private val planks = SOLID.copy(flammable = true)

		@Test
		fun `baja por la cara de piedra aunque la de tablones quede mas cerca`() {
			val cellAt = world(mutableMapOf<Cell, RouteCell>().fill(-2, 1, -2, 1, 10, 2).fill(2, 1, -2, 2, 10, 2, planks))
			val route = plan(p(0.5, 11.0, 0.5), p(8.5, 1.0, 0.5), cellAt, opts)
			assertEquals(true, route?.complete)
			val l = ladders(route).firstOrNull()
			assertNotNull(l)
			for (c in l!!.cells) assertFalse(cellAt.at(c.x - c.dx, c.y, c.z - c.dz).flammable)
		}

		@Test
		fun `si toda la torre es de madera, la usa igual`() {
			val cellAt = world(mutableMapOf<Cell, RouteCell>().fill(-2, 1, -2, 2, 10, 2, planks))
			val route = plan(p(0.5, 11.0, 0.5), p(8.5, 1.0, 0.5), cellAt, opts)
			assertEquals(true, route?.complete)
			assertTrue(ladders(route).isNotEmpty())
		}
	}
}
