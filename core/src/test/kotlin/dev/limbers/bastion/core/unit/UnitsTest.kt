package dev.limbers.bastion.core.unit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UnitsTest {
	@Test
	fun `las 7 unidades de Bedrock con ids distintos`() {
		assertEquals(7, Units.ALL.size)
		assertEquals(Units.ALL.size, Units.ALL.map { it.id }.toSet().size)
		assertEquals(4, Units.ALL.count { it.side == Side.ALLY })
	}

	@Test
	fun `se buscan por id`() {
		assertSame(Units.INVADER_SOLDIER, Units.byId("invader_soldier"))
		assertNull(Units.byId("dragon"))
	}

	@Test
	fun `las cifras de la ficha de Bedrock`() {
		val s = Units.ALLY_SOLDIER
		assertEquals(listOf(60.0, 10.0, 0.29), listOf(s.health, s.damage, s.speed))
		val i = Units.INVADER_SOLDIER
		assertEquals(listOf(40.0, 9.0, 0.28), listOf(i.health, i.damage, i.speed))
		assertEquals(1.0, Units.ALLY_WARRIOR.knockbackResistance)
		assertEquals(24.0, Units.ALLY_ARCHER.ranged!!.radius)
		assertEquals(4.0, Units.INVADER_ARCHER.ranged!!.damage)
	}

	@Test
	fun `una ficha mal hecha no se acepta`() {
		assertThrows<IllegalArgumentException> { UnitDef("Soldado", Side.ALLY, Role.MELEE, 10.0, 1.0, 0.2, "minecraft:stick", 1) }
		assertThrows<IllegalArgumentException> { UnitDef("a", Side.ALLY, Role.MELEE, 10.0, 1.0, 0.2, "minecraft:stick", 0) }
		assertThrows<IllegalArgumentException> { UnitDef("a", Side.ALLY, Role.ARCHER, 10.0, 1.0, 0.2, "minecraft:bow", 1) }
	}
}

class VariationTest {
	@Test
	fun `el margen es el 10 por ciento redondeado`() {
		assertEquals(6, Variation.spread(60.0))
		assertEquals(4, Variation.spread(40.0))
		assertEquals(1, Variation.spread(9.0))
		assertEquals(1, Variation.spread(13.0))
	}

	@Test
	fun `las cifras pequenas no varian`() {
		assertEquals(0, Variation.spread(3.0))
		assertEquals(2.0, Variation.roll(2.0, 0.0))
		assertEquals(3.0, Variation.roll(3.0, 1.0))
	}

	@Test
	fun `va de menos a mas margen y la media es la ficha`() {
		assertEquals(54.0, Variation.roll(60.0, 0.0))
		assertEquals(60.0, Variation.roll(60.0, 0.5))
		assertEquals(66.0, Variation.roll(60.0, 1.0))
		assertEquals(8.0, Variation.roll(9.0, 0.0))
		assertEquals(10.0, Variation.roll(9.0, 1.0))
	}

	@Test
	fun `fuera de 0 a 1 es un error`() {
		assertThrows<IllegalArgumentException> { Variation.roll(10.0, 1.5) }
	}
}
