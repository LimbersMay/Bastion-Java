package dev.limbers.bastion.core.flow

import dev.limbers.bastion.core.route.Cell
import dev.limbers.bastion.core.route.CellAt
import dev.limbers.bastion.core.route.RouteCell
import dev.limbers.bastion.core.route.RouteCell.Companion.FREE
import dev.limbers.bastion.core.route.RouteCell.Companion.HAZARD
import dev.limbers.bastion.core.route.RouteCell.Companion.SOLID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowFieldTest {
	/** Suelo en y = 0, aire encima, y los bloques que se indiquen. */
	private fun world(blocks: Map<Cell, RouteCell> = emptyMap()) =
		CellAt { x, y, z -> blocks[Cell(x, y, z)] ?: if (y <= 0) SOLID else FREE }

	private fun MutableMap<Cell, RouteCell>.fill(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int, cell: RouteCell = SOLID): MutableMap<Cell, RouteCell> {
		for (x in x0..x1) for (y in y0..y1) for (z in z0..z1) this[Cell(x, y, z)] = cell
		return this
	}

	private val box = Box(0, 1, 0, 20, 8, 20)

	/** Sigue las flechas desde `start` hasta que se acaban; devuelve el recorrido. */
	private fun follow(field: FlowField, start: Cell, max: Int = 500): List<Cell> {
		val path = mutableListOf(start)
		var c = start
		repeat(max) {
			val n = field.next(c.x, c.y, c.z) ?: return path
			path.add(n)
			c = n
		}
		return path
	}

	@Test
	fun `en campo abierto la distancia es la de manhattan`() {
		val field = FlowField.compute(box, listOf(Cell(10, 1, 10)), world())
		assertEquals(0, field.distance(10, 1, 10))
		assertEquals(10, field.distance(0, 1, 10))
		assertEquals(20, field.distance(0, 1, 0))
	}

	@Test
	fun `las flechas llevan a la meta por el camino mas corto`() {
		val field = FlowField.compute(box, listOf(Cell(10, 1, 10)), world())
		val path = follow(field, Cell(0, 1, 0))
		assertEquals(Cell(10, 1, 10), path.last())
		assertEquals(21, path.size)
	}

	@Test
	fun `un muro con una puerta - todos pasan por la puerta`() {
		// Muro en x = 10 de lado a lado, con una puerta en z = 15..16.
		val blocks = mutableMapOf<Cell, RouteCell>().fill(10, 1, 0, 10, 4, 20)
		blocks.remove(Cell(10, 1, 15)); blocks.remove(Cell(10, 2, 15))
		blocks.remove(Cell(10, 1, 16)); blocks.remove(Cell(10, 2, 16))
		val field = FlowField.compute(box, listOf(Cell(18, 1, 2)), world(blocks))
		val path = follow(field, Cell(2, 1, 2))
		assertEquals(Cell(18, 1, 2), path.last())
		assertTrue(path.any { it.x == 10 && it.z in 15..16 }, "no ha pasado por la puerta: $path")
	}

	@Test
	fun `sube un escalon y baja una caida corta`() {
		// Un bloque de alto en x = 5 (escalón) y una plataforma de 3 de alto en x = 12..20 que hay que bajar.
		val blocks = mutableMapOf<Cell, RouteCell>().fill(5, 1, 0, 8, 1, 20).fill(0, 1, 0, 0, 3, 20)
		val field = FlowField.compute(box, listOf(Cell(12, 1, 10)), world(blocks))
		assertTrue(field.distance(6, 2, 10) > 0, "no sube al escalón")
		assertTrue(field.distance(0, 4, 10) > 0, "no baja de la plataforma de 3")
	}

	@Test
	fun `no baja de mas de tres bloques`() {
		val blocks = mutableMapOf<Cell, RouteCell>().fill(0, 1, 0, 4, 5, 20)
		val field = FlowField.compute(box, listOf(Cell(12, 1, 10)), world(blocks))
		assertEquals(-1, field.distance(2, 6, 10))
	}

	@Test
	fun `no cruza el agua`() {
		val blocks = mutableMapOf<Cell, RouteCell>().fill(10, 0, 0, 10, 2, 20, HAZARD)
		val field = FlowField.compute(box, listOf(Cell(15, 1, 10)), world(blocks))
		assertEquals(-1, field.distance(5, 1, 10))
		assertNull(field.next(5, 1, 10))
	}

	@Test
	fun `una valla no se sube de un salto`() {
		val fence = RouteCell(cost = 4.0, ground = true, noStep = true)
		val blocks = mutableMapOf<Cell, RouteCell>().fill(10, 1, 0, 10, 1, 20, fence)
		val field = FlowField.compute(box, listOf(Cell(15, 1, 10)), world(blocks))
		assertEquals(-1, field.distance(5, 1, 10))
	}
}
