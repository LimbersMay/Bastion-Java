package dev.limbers.bastion.director

import dev.limbers.bastion.Bastion
import dev.limbers.bastion.unit.Defender
import dev.limbers.bastion.unit.Invader
import dev.limbers.bastion.unit.SiegeUnit
import dev.limbers.bastion.core.flow.FlowField
import dev.limbers.bastion.core.flow.SpatialGrid
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerLevel
import kotlin.math.ceil

/**
 * Una batalla: sus unidades, el campo de flujo que siguen los invasores hacia su meta, y las rejillas de cada bando
 * para elegir enemigo. Las unidades de una batalla no buscan enemigo por su cuenta (`NearestAttackableTargetGoal` del
 * juego mira a todas las entidades cercanas): se lo asigna `BattleDirector`, por turnos.
 */
class Battle(val level: ServerLevel, val flow: FlowField) {
	val invaders = ArrayList<Invader>()
	val defenders = ArrayList<Defender>()

	internal val invaderGrid = SpatialGrid<Invader>(GRID_CELL)
	internal val defenderGrid = SpatialGrid<Defender>(GRID_CELL)
	internal var cursor = 0
	internal var ticks = 0L

	fun add(unit: SiegeUnit) {
		unit.battle = this
		when (unit) {
			is Invader -> invaders.add(unit)
			is Defender -> defenders.add(unit)
		}
	}

	val alive: Boolean get() = invaders.isNotEmpty() || defenders.isNotEmpty()

	companion object {
		/** Lado de las casillas de la rejilla, en bloques. */
		const val GRID_CELL = 8
	}
}

/**
 * El director de las batallas: cada tick rellena las rejillas y asigna enemigo a una décima parte de las unidades.
 * Así cada unidad revisa su objetivo cada 10 ticks (medio segundo) y el coste se reparte entre ticks.
 */
object BattleDirector {
	private val battles = ArrayList<Battle>()

	/** Las batallas en marcha. */
	val active: List<Battle> get() = battles

	/** A esta distancia o menos (en horizontal) se elige enemigo. */
	const val ENGAGE_RADIUS = 16.0

	/** Cada unidad revisa su objetivo una vez cada tantos ticks. */
	const val RETARGET_TICKS = 10

	fun start(battle: Battle) {
		battles.add(battle)
		Bastion.LOGGER.info("[bastion] Batalla: empieza con ${battle.invaders.size} invasores y ${battle.defenders.size} defensores")
	}

	/** Invasores y defensores en pie, sumando todas las batallas. */
	fun counts(): Pair<Int, Int> = battles.sumOf { it.invaders.size } to battles.sumOf { it.defenders.size }

	/** Termina todas las batallas (las unidades se quedan, ya sin director). */
	fun stopAll() {
		for (b in battles) (b.invaders + b.defenders).forEach { it.battle = null }
		battles.clear()
	}

	fun register() {
		ServerTickEvents.END_LEVEL_TICK.register { level ->
			val iterator = battles.iterator()
			while (iterator.hasNext()) {
				val battle = iterator.next()
				if (battle.level !== level) continue
				tick(battle)
				if (!battle.alive) {
					Bastion.LOGGER.info("[bastion] Batalla: terminada")
					iterator.remove()
				}
			}
		}
	}

	private fun tick(b: Battle) {
		b.ticks++
		b.invaders.removeIf { !it.isAlive || it.isRemoved }
		b.defenders.removeIf { !it.isAlive || it.isRemoved }

		b.invaderGrid.clear()
		b.defenderGrid.clear()
		for (u in b.invaders) b.invaderGrid.add(u.x, u.z, u)
		for (u in b.defenders) b.defenderGrid.add(u.x, u.z, u)

		// Una décima parte de las unidades por tick, por turnos.
		val all = b.invaders.size + b.defenders.size
		if (all == 0) return
		val slice = ceil(all / RETARGET_TICKS.toDouble()).toInt()
		repeat(slice) {
			val i = b.cursor++ % all
			if (i < b.invaders.size) {
				val u = b.invaders[i]
				u.target = b.defenderGrid.nearest(u.x, u.z, ENGAGE_RADIUS) { it.isAlive }
			} else {
				val u = b.defenders[i - b.invaders.size]
				u.target = b.invaderGrid.nearest(u.x, u.z, ENGAGE_RADIUS) { it.isAlive }
			}
		}

		if (b.ticks % 200 == 0L) {
			Bastion.LOGGER.info("[bastion] Batalla: ${b.invaders.size} invasores y ${b.defenders.size} defensores en pie")
		}
	}
}
