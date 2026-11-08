package dev.limbers.bastion.core.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

/** Los mapas reales del mod (`src/main/resources/data/bastion/maps`), leídos y comprobados en cada build. */
class RealMapsTest {
	private val dir = File(System.getProperty("bastion.maps") ?: error("falta la propiedad bastion.maps (la pone core/build.gradle.kts)"))

	@Test
	fun `todos los mapas del mod se leen y pasan las comprobaciones`() {
		val files = dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
		assertTrue(files.isNotEmpty(), "no hay mapas en $dir")
		for (f in files) MapFormat.parse(f.readText(), f.name)
	}

	@Test
	fun `Stonehill con los datos de Bedrock`() {
		val map = MapFormat.parse(File(dir, "stonehill.json").readText(), "stonehill.json")
		assertEquals("Stonehill Castle", map.name)
		val gate = map.gates.single()
		assertEquals(Pos(-289.5, 125.0, 207.5), gate.inside)
		assertEquals(3 * 6 * 12L, gate.walls.single().box.volume)
		assertEquals(12, map.allies.size)
		assertEquals(listOf(25, 46, 68), map.waves.map { w -> w.groups.sumOf { it.count } })
		assertEquals(4, map.invaderSpawns.size)
		// 2 de la puerta + 2 de brecha + 3 jugadores + 12 aliados + 4 invasores + 11 puestos + 3 patio + torre + salida + 4 refuerzos
		assertEquals(43, map.standingPoints().size)
		assertEquals("allies[8] (ally_pyromancer)", map.standingPoints().first { it.second == Pos(-284.5, 150.0, 212.5) }.first)
	}
}

class MapFormatTest {
	/** Un mapa mínimo válido; `extra` se añade dentro del objeto. */
	private fun map(extra: String = "", id: String = "prueba", wall: String = """{ "from": [0, 0, 0], "to": [2, 3, 0], "block": "minecraft:oak_planks" }""") = """
		// Comentario permitido
		{
			"id": "$id",
			"name": "Prueba",
			"area": { "from": [-10, -10, -10], "to": [10, 10, 10] },
			"gates": [{ "id": "g", "outside": [0, 0, -2], "inside": [0, 0, 2], "walls": [$wall] }],
			"playerSpawns": [[0, 5, 0]],
			"invaderSpawns": [[0, 0, -8]],
			"waves": [{ "groups": [{ "unit": "invader_soldier", "count": 3 }], "batchSize": 3, "batchIntervalTicks": 20 }],
			"lives": 1, "countdownSeconds": 0, "intermissionSeconds": 0,
			$extra
		}
	""".trimIndent()

	private fun problems(text: String, source: String = "prueba.json"): List<String> =
		assertThrows<MapException> { MapFormat.parse(text, source) }.problems

	@Test
	fun `un mapa minimo con comentarios y coma final se lee`() {
		val m = MapFormat.parse(map(), "prueba.json")
		assertEquals(Pos(0.0, 0.0, 2.0), m.gates[0].inside)
		assertEquals("minecraft:overworld", m.dimension)
	}

	@Test
	fun `un campo con errata es un error`() {
		assertTrue(problems(map(""""keeep": [0, 0, 0],""")).single().contains("keeep"))
	}

	@Test
	fun `un punto necesita 3 numeros`() {
		assertTrue(problems(map(""""keep": [0, 0],""")).single().contains("3 números"))
	}

	@Test
	fun `un punto fuera del area`() {
		assertTrue(problems(map(""""keep": [50, 0, 0],""")).single().startsWith("keep:"))
	}

	@Test
	fun `una unidad que no existe o del bando contrario`() {
		val p = problems(map(""""allies": [{ "unit": "dragon", "at": [0, 0, 0] }, { "unit": "invader_soldier", "at": [0, 0, 0] }],"""))
		assertEquals(2, p.size)
		assertTrue(p[0].contains("desconocida"))
		assertTrue(p[1].contains("bando ally"))
	}

	@Test
	fun `la meta dentro del muro`() {
		assertTrue(problems(map(wall = """{ "from": [-1, 0, 1], "to": [1, 3, 3], "block": "minecraft:oak_planks" }""")).any { it.contains("inside") })
	}

	@Test
	fun `un muro de un solo bloque de alto`() {
		assertTrue(problems(map(wall = """{ "from": [0, 0, 0], "to": [2, 0, 0], "block": "minecraft:oak_planks" }""")).any { it.contains("2 bloques de alto") })
	}

	@Test
	fun `flancos sin zona de picar`() {
		assertTrue(problems(map(""""flankSpawns": [[5, 0, 5]],""")).single().contains("digZones"))
	}

	@Test
	fun `el archivo debe llamarse como el id`() {
		assertTrue(problems(map(), "otro.json").single().contains("prueba.json"))
	}

	@Test
	fun `los puestos tienen sitios por defecto`() {
		assertEquals(3, DefensePost(Pos(0.0, 0.0, 0.0), PostKind.FRONT).slots)
		assertEquals(2, DefensePost(Pos(0.0, 0.0, 0.0), PostKind.HIGH).slots)
		assertEquals(5, DefensePost(Pos(0.0, 0.0, 0.0), PostKind.SIDE, 5).slots)
	}
}
