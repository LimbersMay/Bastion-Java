package dev.limbers.bastion.debug

import dev.limbers.bastion.director.BattleDirector
import dev.limbers.bastion.unit.BastionEntities
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.server.level.ServerLevel
import kotlin.math.floor
import kotlin.math.max

/**
 * Ver lo que piensan las unidades, con partículas que ven todos los jugadores (sin nada en el cliente):
 *
 * - **Rutas** (`/bastion ver rutas`): la ruta de cada invasor que planea la suya (`AssaultGoal`), desde el paso por el
 *   que va. Verde, andar; rojo, picar (y un punto rojo en cada bloque que va a quitar); azul, escalera que va a poner.
 * - **Campo** (`/bastion ver campo`): el campo de flujo de las batallas, alrededor de cada jugador (12 bloques). Cada
 *   casilla, un punto del color de su distancia a la meta (verde cerca, rojo lejos) y otro más pequeño hacia donde
 *   manda ir. Donde no hay puntos, el campo no llega.
 *
 * Se dibuja cada medio segundo. Es para desarrollar: cuesta poco, pero no está pensado para una partida.
 */
object DebugView {
	var routes = false
	var flow = false

	private const val EVERY_TICKS = 10L
	private const val FLOW_RADIUS = 12
	private const val WALK = 0x3CD03C
	private const val DIG = 0xE03030
	private const val LADDER = 0x3070F0

	fun register() {
		ServerTickEvents.END_LEVEL_TICK.register { level ->
			if (level.gameTime % EVERY_TICKS != 0L) return@register
			if (routes) drawRoutes(level)
			if (flow) drawFlow(level)
		}
	}

	private fun dot(level: ServerLevel, color: Int, scale: Float, x: Double, y: Double, z: Double) {
		level.sendParticles(DustParticleOptions(color, scale), x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
	}

	private fun drawRoutes(level: ServerLevel) {
		for (invader in level.getEntities(BastionEntities.INVADER_SOLDIER.type) { it.isAlive }) {
			val route = invader.assault.plannedRoute
			for (k in invader.assault.routeIndex until route.size) {
				val step = route[k]
				val color = when {
					step.ladder != null -> LADDER
					step.breaks.isNotEmpty() -> DIG
					else -> WALK
				}
				dot(level, color, 1.0f, step.x + 0.5, step.y + 0.2, step.z + 0.5)
				for (b in step.breaks) dot(level, DIG, 0.6f, b.x + 0.5, b.y + 0.5, b.z + 0.5)
			}
		}
	}

	private fun drawFlow(level: ServerLevel) {
		for (battle in BattleDirector.active) {
			if (battle.level !== level) continue
			val field = battle.flow
			for (player in level.players()) {
				val px = floor(player.x).toInt()
				val py = floor(player.y).toInt()
				val pz = floor(player.z).toInt()
				for (x in px - FLOW_RADIUS..px + FLOW_RADIUS) for (z in pz - FLOW_RADIUS..pz + FLOW_RADIUS) for (y in py - 6..py + 3) {
					val d = field.distance(x, y, z)
					if (d < 0) continue
					val next = field.next(x, y, z)
					dot(level, gradient(d), 0.9f, x + 0.5, y + 0.15, z + 0.5)
					if (next != null) dot(level, gradient(d), 0.5f, x + 0.5 + (next.x - x) * 0.35, y + 0.15 + (next.y - y) * 0.35, z + 0.5 + (next.z - z) * 0.35)
				}
			}
		}
	}

	/** De verde (en la meta) a rojo (a 80 pasos o más). */
	private fun gradient(distance: Int): Int {
		val t = (distance / 80.0).coerceIn(0.0, 1.0)
		val r = (255 * t).toInt()
		val g = (255 * (1 - t)).toInt()
		return (r shl 16) or (max(g, 40) shl 8) or 40
	}
}
