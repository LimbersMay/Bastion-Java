package dev.limbers.bastion.entity.ai

import dev.limbers.bastion.Bastion
import dev.limbers.bastion.entity.Invader
import dev.limbers.bastion.logic.route.Cell
import dev.limbers.bastion.logic.route.LadderCell
import dev.limbers.bastion.logic.route.LadderMove
import dev.limbers.bastion.logic.route.RouteOptions
import dev.limbers.bastion.logic.route.RoutePlanner
import dev.limbers.bastion.logic.route.RouteStep
import dev.limbers.bastion.world.Digging
import dev.limbers.bastion.world.LevelCells
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.Vec3
import java.util.EnumSet
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * El asalto: el invasor planea su ruta (`RoutePlanner`) y la ejecuta él mismo, paso a paso.
 *
 * - **Caminar:** con la navegación del juego, por tramos.
 * - **Picar:** golpes visibles, grieta por fases, partículas y sonido; el tiempo sale de la dureza del bloque y de
 *   su herramienta (`Digging`). Como el zombi que rompe puertas (`BreakDoorGoal`).
 * - **Escalera hacia arriba:** se pone al pie de la pared con las escaleras en la mano, coloca el primer tramo, sube
 *   empujando contra la pared (así trepa el juego) y coloca el siguiente por encima de la cabeza hasta arriba.
 * - **Escalera hacia abajo:** desde el borde coloca los tramos de arriba, se mete en la columna y baja colocando.
 *
 * Nada de teletransportes ni bloques puestos por el juego: si se queda sin escaleras o algo falla, vuelve a planear.
 * Cuando su objetivo está a su alcance, deja paso a `MeleeAttackGoal`.
 */
class AssaultGoal(private val unit: Invader) : Goal() {
	init {
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK))
	}

	private sealed interface Phase
	private data object Walk : Phase
	private class Dig(val pos: BlockPos, val total: Int) : Phase {
		var ticks = 0
		var stage = -1
	}
	private class Ladder(val move: LadderMove, val exit: RouteStep, val timeout: Int) : Phase {
		var ticks = 0
		var cooldown = 0
		var climbing = false

		/** Tramos que han estado puestos (los suyos o los que ya había). Si uno desaparece, se la han roto. */
		val held = BooleanArray(move.cells.size)
	}

	private var route: List<RouteStep> = emptyList()
	private var complete = false
	private var index = 0
	private var phase: Phase = Walk
	private var replanIn = 0
	private var walkTarget: RouteStep? = null
	private var lastProgressPos: Vec3 = Vec3.ZERO
	private var attackCooldown = 0
	private var stillTicks = 0

	private val level get() = unit.level() as ServerLevel

	/** A quién o adónde va: su objetivo si lo tiene; si no, su destino. */
	private fun goal(): Vec3? {
		val target = unit.target
		if (target != null && target.isAlive) return target.position()
		return unit.destination
	}

	/** Ya está: con un enemigo, a distancia de pelea en su mismo piso; con un destino, encima de él. */
	private fun arrived(goal: Vec3): Boolean {
		val horizontal = hypot(goal.x - unit.x, goal.z - unit.z)
		val vertical = abs(goal.y - unit.y)
		return if (unit.target?.isAlive == true) horizontal <= FIGHT_DISTANCE && vertical <= 1.5 else horizontal <= 1.0 && vertical <= 1.0
	}

	override fun canUse(): Boolean {
		val goal = goal() ?: return false
		return !arrived(goal)
	}

	/**
	 * En mitad de una escalera no se deja: se termina de subir o bajar, y se pelea al llegar. Si se suelta para pelear,
	 * desde la escalera no alcanza, resbala y acaba planeando desde media altura.
	 */
	override fun canContinueToUse(): Boolean {
		if (phase is Ladder) return true
		val goal = goal() ?: return false
		return !arrived(goal)
	}

	override fun requiresUpdateEveryTick(): Boolean = true

	override fun start() {
		plan()
	}

	override fun stop() {
		(phase as? Dig)?.let { level.destroyBlockProgress(unit.id, it.pos, -1) }
		phase = Walk
		route = emptyList()
		unit.navigation.stop()
		unit.holdWeapon()
	}

	override fun tick() {
		if (attackCooldown > 0) attackCooldown--
		if (DEBUG && unit.tickCount % 10 == 0) {
			log("depuración: fase ${phase::class.simpleName}, paso $index/${route.size} ${route.getOrNull(index)?.let { "${it.x},${it.y},${it.z}" }}, en ${unit.position()}, suelo ${unit.onGround()}, escalera ${unit.onClimbable()}")
		}
		when (val p = phase) {
			Walk -> walk()
			is Dig -> dig(p)
			is Ladder -> if (p.move.up) ladderUp(p) else ladderDown(p)
		}
	}

	// --- Planear ---

	private fun plan() {
		val goal = goal() ?: return
		if (!steady()) {
			// Cayendo: se planea al aterrizar, no desde el aire.
			route = emptyList()
			phase = Walk
			replanIn = 5
			return
		}
		val options = RouteOptions(
			maxNodes = 4000,
			reach = if (unit.target != null) FIGHT_DISTANCE - 1.0 else 1.0,
			ladders = unit.ladders.coerceAtMost(MAX_LADDER),
		)
		val found = RoutePlanner.plan(unit.x, unit.y, unit.z, goal.x, goal.y, goal.z, LevelCells(level, unit.weapon), options)
		route = found?.steps ?: emptyList()
		complete = found?.complete ?: false
		index = 0
		phase = Walk
		walkTarget = null
		replanIn = REPLAN_TICKS
		stillTicks = 0
		lastProgressPos = unit.position()
		unit.holdWeapon()
		// Ruta vacía y completa: ya está en la casilla de la meta, solo falta el último acercamiento (no se anota).
		if (route.isEmpty() && complete) return
		val breaks = route.sumOf { it.breaks.size }
		val ladders = route.mapNotNull { it.ladder }
		log("Ruta: ${route.size} pasos, ${if (complete) "completa" else "parcial"}, $breaks bloques por picar, " +
			"${ladders.size} escaleras (${ladders.sumOf { it.cells.size }} tramos; lleva ${unit.ladders}), ${found?.nodes ?: 0} nodos")
	}

	// --- Caminar ---

	private fun walk() {
		if (route.isEmpty() || index >= route.size) {
			// Ruta acabada: si era completa, está en la casilla de la meta y se acerca del todo a pie (el buscador cuenta
			// por casillas, `arrived` por posición). Si era parcial o no hay ruta, se vuelve a planear con calma.
			val goal = goal()
			if (complete && goal != null && unit.navigation.isDone) unit.navigation.moveTo(goal.x, goal.y, goal.z, 1.0)
			if (--replanIn <= 0) plan()
			return
		}
		if (--replanIn <= 0) {
			plan()
			if (route.isEmpty()) return
		}

		val step = route[index]
		val pendingBreak = step.breaks.firstOrNull { !level.getBlockState(it.pos()).isAir }
		if (pendingBreak != null) {
			startDig(pendingBreak.pos())
			return
		}
		val ladder = step.ladder
		if (ladder != null) {
			startLadder(ladder, step)
			return
		}

		// Por una escalera que ya está puesta (la suya de antes, o una del mapa): sube empujando contra la pared, baja
		// dejándose caer despacio. La navegación del juego no planifica escaleras.
		if (vertical(index)) {
			climbExisting(step)
			return
		}

		// Tramo a pie: hasta el último paso sencillo seguido, como mucho 10 por delante.
		var end = index
		while (end + 1 < route.size && end - index < 10 && route[end + 1].breaks.isEmpty() && route[end + 1].ladder == null && !vertical(end + 1)) end++
		for (k in end downTo index) {
			if (near(route[k])) {
				index = k + 1
				walkTarget = null
				return
			}
		}
		val target = route[end]
		if (walkTarget != target || unit.navigation.isDone) {
			walkTarget = target
			val moving = unit.navigation.moveTo(target.x + 0.5, target.y.toDouble(), target.z + 0.5, 1.0)
			if (!moving) unit.moveControl.setWantedPosition(step.x + 0.5, step.y.toDouble(), step.z + 0.5, 1.0)
		}
		watchProgress()
	}

	/** El paso `k` sube o baja en la misma columna que el anterior: es por una escalera ya puesta. */
	private fun vertical(k: Int): Boolean {
		val step = route[k]
		val px = if (k == 0) floor(unit.x).toInt() else route[k - 1].x
		val pz = if (k == 0) floor(unit.z).toInt() else route[k - 1].z
		val py = if (k == 0) floor(unit.y).toInt() else route[k - 1].y
		return step.x == px && step.z == pz && step.y != py
	}

	private fun climbExisting(step: RouteStep) {
		unit.navigation.stop()
		if (near(step)) {
			index++
			return
		}
		val feet = unit.blockPosition()
		val ladder = listOf(feet, feet.above(), BlockPos(step.x, step.y, step.z)).map { level.getBlockState(it) }.firstOrNull { it.block is LadderBlock }
		if (step.y > floor(unit.y).toInt() && ladder != null) {
			// Al pie, primero se centra en la columna: trepar descentrado acaba saliéndose de lado.
			if (unit.onGround() && hypot(step.x + 0.5 - unit.x, step.z + 0.5 - unit.z) > CENTERED) {
				unit.moveControl.setWantedPosition(step.x + 0.5, unit.y, step.z + 0.5, 0.5)
			} else {
				val wall = ladder.getValue(LadderBlock.FACING).opposite
				pushInto(step.x, step.z, wall.stepX, wall.stepZ)
				strikeFromLadder()
			}
		}
		watchProgress()
	}

	/**
	 * Trepa empujando contra la pared de la columna (x, z), que está hacia (wx, wz). Apunta lejos, dentro del muro:
	 * así la corrección hacia el centro de la columna es suave. Si apuntara a un bloque de distancia, en el aire (casi
	 * sin rozamiento) iría de lado a lado cada vez más hasta salirse de la escalera.
	 */
	private fun pushInto(x: Int, z: Int, wx: Int, wz: Int) {
		val tx = x + 0.5 + wx * PUSH_AHEAD
		val tz = z + 0.5 + wz * PUSH_AHEAD
		unit.moveControl.setWantedPosition(tx, unit.y, tz, 1.0)
		unit.lookControl.setLookAt(tx, unit.eyeY, tz)
	}

	/**
	 * Agarrado a la escalera, golpea a su enemigo si lo tiene al alcance: no sube indefenso. Mismo ritmo que el ataque
	 * cuerpo a cuerpo del juego (un golpe por segundo).
	 */
	private fun strikeFromLadder() {
		val target = unit.target ?: return
		if (!target.isAlive || attackCooldown > 0 || !unit.onClimbable()) return
		if (!unit.isWithinMeleeAttackRange(target) || !unit.sensing.hasLineOfSight(target)) return
		unit.lookControl.setLookAt(target)
		unit.swingForAttack(InteractionHand.MAIN_HAND)
		unit.doHurtTarget(level, target)
		attackCooldown = ATTACK_COOLDOWN
	}

	private fun near(step: RouteStep): Boolean =
		hypot(step.x + 0.5 - unit.x, step.z + 0.5 - unit.z) <= 0.7 && abs(step.y - unit.y) < 1.0

	/** Si en 3 s no se ha movido, algo ha cambiado: se vuelve a planear. */
	private fun watchProgress() {
		if (unit.position().distanceToSqr(lastProgressPos) > 0.25) {
			lastProgressPos = unit.position()
			stillTicks = 0
		} else if (++stillTicks > 60) {
			log("Atasco en ${unit.blockPosition().toShortString()}: vuelve a planear")
			plan()
		}
	}

	// --- Picar ---

	private fun startDig(pos: BlockPos) {
		val state = level.getBlockState(pos)
		if (!Digging.canBreak(level, pos, state)) {
			plan()
			return
		}
		val total = Digging.ticksToBreak(level, pos, state, unit.weapon).toInt().coerceAtLeast(1)
		unit.navigation.stop()
		unit.holdWeapon()
		phase = Dig(pos, total)
		log("Picar: ${state.block.name.string} en ${pos.toShortString()}, ${total} ticks")
	}

	private fun dig(p: Dig) {
		val state = level.getBlockState(p.pos)
		if (state.isAir) {
			finishDig(p)
			return
		}
		val center = Vec3.atCenterOf(p.pos)
		unit.lookControl.setLookAt(center.x, center.y, center.z)
		if (unit.eyePosition.distanceToSqr(center) > REACH * REACH) {
			// Algo lo ha apartado: vuelve a planear desde donde está.
			level.destroyBlockProgress(unit.id, p.pos, -1)
			plan()
			return
		}
		if (p.ticks % SWING_TICKS == 0) {
			unit.swingForAttack(InteractionHand.MAIN_HAND)
			val sound = state.soundType
			level.playSound(null, p.pos, sound.hitSound, SoundSource.BLOCKS, (sound.volume + 1f) / 4f, sound.pitch * 0.5f)
			level.sendParticles(BlockParticleOption(ParticleTypes.BLOCK, state), center.x, center.y, center.z, 6, 0.3, 0.3, 0.3, 0.05)
		}
		p.ticks++
		val stage = (p.ticks * 10 / p.total).coerceAtMost(9)
		if (stage != p.stage) {
			level.destroyBlockProgress(unit.id, p.pos, stage)
			p.stage = stage
		}
		if (p.ticks >= p.total) {
			level.destroyBlock(p.pos, false, unit)
			unit.blocksBroken++
			finishDig(p)
		}
	}

	private fun finishDig(p: Dig) {
		level.destroyBlockProgress(unit.id, p.pos, -1)
		phase = Walk
		walkTarget = null
		stillTicks = 0
	}

	// --- Escaleras ---

	private fun startLadder(move: LadderMove, step: RouteStep) {
		val missing = move.cells.count { !isLadder(it) }
		if (missing > unit.ladders) {
			log("Escalera: le faltan ${missing - unit.ladders} tramos; vuelve a planear sin ellos")
			plan()
			return
		}
		unit.navigation.stop()
		unit.holdLadders()
		phase = Ladder(move, step, timeout = move.cells.size * 40 + 100)
		log("Escalera: ${if (move.up) "sube" else "baja"} ${move.cells.size} tramos en ${move.cells.first().pos().toShortString()}")
	}

	private fun isLadder(c: LadderCell) = level.getBlockState(c.pos()).block is LadderBlock

	private fun ladderState(c: LadderCell) =
		Direction.getNearest(c.dx, 0, c.dz, null)?.let { Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, it) }

	/**
	 * ¿Se le metería el tramo en el cuerpo? Pasa si está pegado a la pared: la escalera quedaría dentro de él y el
	 * tramo de encima le haría de techo (se quedaba atascado a media altura). Antes de colocar, se centra.
	 */
	private fun inTheWay(c: LadderCell): Boolean {
		val state = ladderState(c) ?: return false
		val shape = state.getCollisionShape(level, c.pos())
		return !shape.isEmpty && shape.bounds().move(c.pos()).intersects(unit.boundingBox)
	}

	/** Se acerca al centro de la casilla, sin tocar su altura. */
	private fun center(c: LadderCell) = unit.moveControl.setWantedPosition(c.x + 0.5, unit.y, c.z + 0.5, 0.5)

	/** Coloca un tramo como lo haría un jugador: gesto, bloque, sonido. Gasta una escalera. `false` si no puede. */
	private fun place(c: LadderCell): Boolean {
		if (unit.ladders <= 0) return false
		val pos = c.pos()
		val state = ladderState(c) ?: return false
		if (!level.getBlockState(pos).canBeReplaced() || !state.canSurvive(level, pos)) return false
		unit.lookControl.setLookAt(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
		unit.swingForAttack(InteractionHand.MAIN_HAND)
		level.setBlock(pos, state, 3)
		level.gameEvent(unit, GameEvent.BLOCK_PLACE, pos)
		val sound = SoundType.LADDER
		level.playSound(null, pos, sound.placeSound, SoundSource.BLOCKS, (sound.volume + 1f) / 2f, sound.pitch * 0.8f)
		unit.ladders--
		unit.laddersPlaced++
		unit.holdLadders()
		return true
	}

	/**
	 * Apunta los tramos puestos y dice si falta alguno que estuvo: se la han roto. Entonces no se repone a media caída
	 * (eso sería hacer trampa): se suelta, cae y vuelve a planear desde el suelo.
	 */
	private fun broken(p: Ladder): Boolean {
		var broken = false
		p.move.cells.forEachIndexed { i, c ->
			if (isLadder(c)) p.held[i] = true else if (p.held[i]) broken = true
		}
		return broken
	}

	/** Solo coloca con los pies en el suelo o agarrado a la escalera, nunca cayendo. */
	private fun steady() = unit.onGround() || unit.onClimbable()

	private fun ladderUp(p: Ladder) {
		val cells = p.move.cells
		val base = cells.first()
		val feet = floor(unit.y).toInt()
		if (++p.ticks > p.timeout) return failLadder("tarda demasiado")
		if (broken(p)) return failLadder("le han roto la escalera en ${unit.blockPosition().toShortString()}")

		// Se ha caído (le han roto la escalera, o le han empujado): vuelve a planear desde el suelo.
		if (p.climbing && unit.onGround() && unit.y < p.exit.y - 0.5 && !unit.onClimbable()) {
			return failLadder("se ha caído en ${unit.blockPosition().toShortString()}")
		}

		// Coloca el siguiente tramo que le quede a mano: hasta uno por encima de la cabeza.
		val next = cells.firstOrNull { !isLadder(it) }
		if (next != null && p.cooldown-- <= 0 && next.y <= feet + 2 && steady()) {
			if (unit.ladders <= 0) return failLadder("se ha quedado sin escaleras")
			if (inTheWay(next)) {
				center(next)
				return
			}
			if (!place(next)) return failLadder("no puede colocar el tramo de ${next.pos().toShortString()}")
			p.cooldown = PLACE_COOLDOWN
		}

		// Arriba del todo: ha salido por encima de la pared.
		if (unit.onGround() && feet >= p.exit.y && hypot(p.exit.x + 0.5 - unit.x, p.exit.z + 0.5 - unit.z) <= 0.6) {
			log("Escalera: arriba en ${unit.blockPosition().toShortString()}")
			unit.holdWeapon()
			index++
			phase = Walk
			walkTarget = null
			stillTicks = 0
			return
		}

		if (!p.climbing) {
			// Al pie: se coloca en el centro de la casilla de la escalera.
			if (hypot(base.x + 0.5 - unit.x, base.z + 0.5 - unit.z) > CENTERED) {
				center(base)
				return
			}
			if (!isLadder(base)) return
			p.climbing = true
		}
		// Trepa empujando contra la pared, como el juego hace subir a quien se mueve contra una escalera. Si el tramo
		// de encima de la cabeza aún no está, espera agarrado.
		// Con todos los tramos puestos, vuelve a empuñar el hacha para lo que le espere arriba.
		if (next == null && unit.mainHandItem !== unit.weapon) unit.holdWeapon()
		strikeFromLadder()
		val ready = next == null || next.y > feet + 1
		if (ready) {
			// En el último tramo: apunta al centro de la casilla de salida, sobre el muro, y no más allá (si siguiera
			// empujando hacia dentro, cruzaría el paso de ronda y caería al otro lado).
			if (feet >= p.exit.y - 1) unit.moveControl.setWantedPosition(p.exit.x + 0.5, unit.y, p.exit.z + 0.5, 1.0)
			else pushInto(base.x, base.z, -base.dx, -base.dz)
		}
	}

	private fun ladderDown(p: Ladder) {
		val cells = p.move.cells // de abajo arriba
		val top = cells.last()
		val feet = floor(unit.y).toInt()
		if (++p.ticks > p.timeout) return failLadder("tarda demasiado")
		if (broken(p)) return failLadder("le han roto la escalera en ${unit.blockPosition().toShortString()}")

		// Coloca de arriba abajo los tramos que le queden a mano: hasta tres por debajo de los pies.
		val next = cells.lastOrNull { !isLadder(it) }
		if (next != null && p.cooldown-- <= 0 && next.y >= feet - 3 && steady()) {
			if (unit.ladders <= 0) return failLadder("se ha quedado sin escaleras")
			if (inTheWay(next)) {
				center(next)
				return
			}
			if (!place(next)) return failLadder("no puede colocar el tramo de ${next.pos().toShortString()}")
			p.cooldown = PLACE_COOLDOWN
		}

		if (unit.onGround() && feet == p.exit.y && hypot(p.exit.x + 0.5 - unit.x, p.exit.z + 0.5 - unit.z) <= 0.6) {
			log("Escalera: abajo en ${unit.blockPosition().toShortString()}")
			unit.holdWeapon()
			index++
			phase = Walk
			walkTarget = null
			stillTicks = 0
			return
		}

		// Entra en la columna cuando ya tiene puestos los tramos de arriba; dentro, se deja bajar sin empujar.
		val placedTop = cells.takeLastWhile { isLadder(it) }.size
		if (!p.climbing) {
			if (!isLadder(top) || placedTop < minOf(3, cells.size)) return
			if (hypot(top.x + 0.5 - unit.x, top.z + 0.5 - unit.z) > CENTERED) {
				center(top)
				return
			}
			p.climbing = true
		}
		if (unit.onGround() && feet > p.exit.y) return failLadder("se ha quedado de pie a media bajada")
		val wallX = top.x - top.dx + 0.5
		val wallZ = top.z - top.dz + 0.5
		unit.lookControl.setLookAt(wallX, unit.eyeY, wallZ)
	}

	private fun failLadder(why: String) {
		log("Escalera: $why; vuelve a planear")
		unit.holdWeapon()
		plan()
	}

	private fun log(message: String) = Bastion.LOGGER.info("[bastion] Invasor ${unit.id}: $message")

	private fun Cell.pos() = BlockPos(x, y, z)
	private fun LadderCell.pos() = BlockPos(x, y, z)

	companion object {
		/** A esta distancia de su enemigo, en su mismo piso, deja paso al ataque cuerpo a cuerpo. */
		const val FIGHT_DISTANCE = 2.5

		/** Escalera más alta que planea, como en Bedrock. */
		const val MAX_LADDER = 24

		/** Cada cuánto vuelve a planear mientras camina (el objetivo se mueve, el mundo cambia). */
		const val REPLAN_TICKS = 60

		/** Alcance para picar, desde los ojos. */
		const val REACH = 4.5

		/** Un golpe cada tantos ticks mientras pica. */
		const val SWING_TICKS = 6

		/** Registro detallado cada segundo, para depurar. */
		var DEBUG = false

		/** Ticks entre golpe y golpe desde la escalera. */
		const val ATTACK_COOLDOWN = 20

		/** Al trepar, apunta a tantos bloques dentro del muro (ver `pushInto`). */
		const val PUSH_AHEAD = 8.0

		/** Distancia al centro de la casilla a la que se da por centrado para empezar a trepar. */
		const val CENTERED = 0.12

		/** Ticks entre un tramo de escalera colocado y el siguiente. */
		const val PLACE_COOLDOWN = 3
	}
}
