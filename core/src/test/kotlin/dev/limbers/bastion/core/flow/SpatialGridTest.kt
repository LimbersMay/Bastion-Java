package dev.limbers.bastion.core.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SpatialGridTest {
	@Test
	fun `encuentra el mas cercano aunque este en otra casilla`() {
		val grid = SpatialGrid<String>(8)
		grid.add(1.0, 1.0, "lejos")
		grid.add(9.5, 0.5, "cerca")
		assertEquals("cerca", grid.nearest(8.0, 0.0, 10.0))
	}

	@Test
	fun `respeta el radio`() {
		val grid = SpatialGrid<String>(8)
		grid.add(30.0, 0.0, "a")
		assertNull(grid.nearest(0.0, 0.0, 16.0))
		assertEquals("a", grid.nearest(0.0, 0.0, 30.0))
	}

	@Test
	fun `filtra los que no valen`() {
		val grid = SpatialGrid<String>(8)
		grid.add(1.0, 0.0, "muerto")
		grid.add(3.0, 0.0, "vivo")
		assertEquals("vivo", grid.nearest(0.0, 0.0, 10.0) { it != "muerto" })
	}

	@Test
	fun `funciona con coordenadas negativas`() {
		val grid = SpatialGrid<String>(8)
		grid.add(-0.5, -0.5, "a")
		grid.add(-17.0, 3.0, "b")
		assertEquals("a", grid.nearest(0.5, 0.5, 4.0))
		assertEquals("b", grid.nearest(-15.0, 2.0, 4.0))
	}

	@Test
	fun `vaciar y contar`() {
		val grid = SpatialGrid<Int>(8)
		repeat(10) { grid.add(it.toDouble(), 0.0, it) }
		assertEquals(10, grid.size)
		assertEquals(3, grid.countWithin(0.0, 0.0, 2.0))
		grid.clear()
		assertEquals(0, grid.size)
		assertNull(grid.nearest(0.0, 0.0, 100.0))
	}
}
