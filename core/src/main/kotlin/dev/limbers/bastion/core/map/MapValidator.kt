package dev.limbers.bastion.core.map

import dev.limbers.bastion.core.unit.Side
import dev.limbers.bastion.core.unit.Units

/**
 * Comprueba un mapa: devuelve la lista de problemas, vacía si está bien. Viene de `../Bastion/scripts/maps/validate.ts`,
 * sin lo que comprobaba de los parches de Bedrock, y con dos cosas nuevas: cada punto debe caer dentro de `area`, y
 * cada unidad debe ser del bando que toca (aliados en `allies`, invasores en las oleadas).
 */
object MapValidator {
	/** Lo más grande que se acepta para un muro, en bloques. */
	const val MAX_WALL_VOLUME = 32_768L

	private val ID = Regex("[a-z0-9_-]+")
	private val BLOCK_ID = Regex("[a-z0-9_.-]+(:[a-z0-9_./-]+)?")

	fun validate(map: MapDef): List<String> {
		val errors = ArrayList<String>()
		fun err(msg: String) = errors.add(msg)

		if (!map.id.matches(ID)) err("id: solo minúsculas, números, _ y -")
		if (map.name.isBlank()) err("name: falta el nombre")
		if (!map.dimension.matches(BLOCK_ID)) err("dimension: id no válido «${map.dimension}»")

		fun point(label: String, p: Pos) {
			if (!listOf(p.x, p.y, p.z).all { it.isFinite() }) err("$label: coordenadas no válidas")
			else if (!map.area.contains(p)) err("$label: [${p.x}, ${p.y}, ${p.z}] queda fuera de area")
		}

		fun points(label: String, list: List<Pos>, required: Boolean = false) {
			if (required && list.isEmpty()) err("$label: hace falta al menos un punto")
			list.forEachIndexed { i, p -> point("$label[$i]", p) }
		}

		fun unit(label: String, id: String, side: Side) {
			val def = Units.byId(id)
			if (def == null) err("$label: unidad desconocida «$id»")
			else if (def.side != side) err("$label: «$id» no es del bando ${side.name.lowercase()}")
		}

		fun count(label: String, value: Int, min: Int) {
			if (value < min) err("$label: debe ser $min o más (es $value)")
		}

		if (map.gates.isEmpty()) err("gates: hace falta al menos una puerta")
		val gateIds = HashSet<String>()
		map.gates.forEachIndexed { gi, g ->
			val label = "gates[$gi]"
			if (!g.id.matches(ID)) err("$label.id: solo minúsculas, números, _ y -")
			if (!gateIds.add(g.id)) err("$label.id: repetido «${g.id}»")
			point("$label.outside", g.outside)
			point("$label.inside", g.inside)
			if (g.walls.isEmpty()) err("$label.walls: hace falta al menos un tramo de muro")
			g.walls.forEachIndexed { i, w ->
				val wl = "$label.walls[$i]"
				point("$wl.from", w.from)
				point("$wl.to", w.to)
				if (!w.block.matches(BLOCK_ID)) err("$wl.block: bloque no válido «${w.block}» (id sin estados, como dark_oak_planks)")
				if (w.box.volume > MAX_WALL_VOLUME) err("$wl: demasiado grande (${w.box.volume} bloques; máximo $MAX_WALL_VOLUME)")
				if (w.box.maxY - w.box.minY + 1 < 2) err("$wl: el muro necesita al menos 2 bloques de alto")
				if (w.box.contains(g.inside)) err("$wl: la meta (inside) no puede quedar dentro del muro")
				if (w.box.contains(g.outside)) err("$wl: el punto de fuera (outside) no puede quedar dentro del muro")
			}
			points("$label.breachSpawns", g.breachSpawns, required = g.breachTroops.isNotEmpty())
			g.breachTroops.forEachIndexed { i, t ->
				unit("$label.breachTroops[$i]", t.unit, Side.INVADER)
				count("$label.breachTroops[$i].count", t.count, 1)
			}
		}

		points("playerSpawns", map.playerSpawns, required = true)
		points("invaderSpawns", map.invaderSpawns, required = true)
		points("flankSpawns", map.flankSpawns)
		if (map.flankSpawns.isNotEmpty() && map.digZones.isEmpty()) err("flankSpawns: requiere digZones, o esas unidades no tienen por dónde abrirse paso")
		map.digZones.forEachIndexed { i, z ->
			point("digZones[$i].from", z.from)
			point("digZones[$i].to", z.to)
		}
		map.allies.forEachIndexed { i, a ->
			unit("allies[$i].unit", a.unit, Side.ALLY)
			point("allies[$i].at", a.at)
		}
		map.defensePosts.forEachIndexed { i, p ->
			point("defensePosts[$i].at", p.at)
			p.capacity?.let { count("defensePosts[$i].capacity", it, 1) }
		}
		points("rallyPoints", map.rallyPoints)
		map.keep?.let { point("keep", it) }
		map.sortiePoint?.let { point("sortiePoint", it) }

		map.reinforcements?.let { r ->
			points("reinforcements.spawns", r.spawns, required = true)
			if (r.units.isEmpty()) err("reinforcements.units: hace falta al menos una unidad")
			r.units.forEachIndexed { i, u -> unit("reinforcements.units[$i]", u, Side.ALLY) }
			count("reinforcements.minField", r.minField, 0)
			count("reinforcements.reservePerWave", r.reservePerWave, 0)
			count("reinforcements.batch", r.batch, 1)
			if (r.everySeconds <= 0) err("reinforcements.everySeconds: debe ser mayor que 0")
		}

		if (map.waves.isEmpty()) err("waves: hace falta al menos una oleada")
		map.waves.forEachIndexed { i, w ->
			if (w.groups.isEmpty()) err("waves[$i]: sin grupos")
			count("waves[$i].batchSize", w.batchSize, 1)
			count("waves[$i].batchIntervalTicks", w.batchIntervalTicks, 0)
			w.groups.forEachIndexed { j, g ->
				unit("waves[$i].groups[$j].unit", g.unit, Side.INVADER)
				count("waves[$i].groups[$j].count", g.count, 1)
			}
		}

		count("lives", map.lives, 1)
		if (map.countdownSeconds < 0) err("countdownSeconds: no puede ser negativo")
		if (map.intermissionSeconds < 0) err("intermissionSeconds: no puede ser negativo")
		return errors
	}
}
