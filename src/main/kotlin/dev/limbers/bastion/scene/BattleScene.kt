package dev.limbers.bastion.scene

import dev.limbers.bastion.Bastion
import dev.limbers.bastion.battle.Battle
import dev.limbers.bastion.battle.BattleDirector
import dev.limbers.bastion.entity.BastionEntities
import dev.limbers.bastion.logic.flow.Box
import dev.limbers.bastion.logic.flow.FlowField
import dev.limbers.bastion.logic.route.Cell
import dev.limbers.bastion.world.LevelCells
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.max
import kotlin.math.min

/**
 * Escena C de la prueba inicial: la batalla grande, para medir el rendimiento. Un llano con un muro de lado a lado y
 * una puerta de 6 de ancho; los invasores salen de un extremo y van a la puerta siguiendo **un** campo de flujo; los
 * defensores esperan en formación al otro lado del muro, cada uno en su puesto.
 *
 * Coordenadas locales como en `AssaultScene`: `f` hacia el muro, `y` arriba, `s` a lo ancho.
 * ```
 *  f=0..18    invasores en filas (20 de ancho)
 *  f=80..81   muro de 5 de alto, puerta en s=21..26
 *  f=86..104  defensores en filas
 *  f=106      meta del campo de flujo (el fondo del patio)
 * ```
 */
class BattleScene(private val level: ServerLevel, private val origin: BlockPos, private val facing: Direction) {
	private val right: Direction = facing.clockWise

	fun pos(f: Int, y: Int, s: Int): BlockPos = origin.relative(facing, f).relative(right, s).above(y)

	/**
	 * Monta la batalla. Con `onTerrain` no toca el terreno: las unidades salen sobre el suelo que haya (colinas,
	 * árboles, agua) y el campo de flujo se calcula sobre él. Sirve para medir en un mapa real.
	 */
	fun build(perSide: Int, onTerrain: Boolean = false): Battle {
		if (!onTerrain) buildField()
		val flow = if (onTerrain) computeFlowOnTerrain() else computeFlow()
		val battle = Battle(level, flow)
		val rows = (perSide + COLUMNS - 1) / COLUMNS
		var spawned = 0
		for (row in 0 until rows) for (col in 0 until COLUMNS) {
			if (spawned >= perSide) break
			val s = 6 + col * 2 + (row % 2)
			val invaderPos = if (onTerrain) surface(2 + row * 2, s) else pos(2 + row * 2, 1, s)
			val invader = BastionEntities.INVADER.spawn(level, invaderPos, EntitySpawnReason.COMMAND) ?: continue
			invader.snapTo(invader.position(), facing.toYRot(), 0f)
			battle.add(invader)
			val defenderPos = if (onTerrain) surface(DEFENDERS_F + row * 2, s) else pos(DEFENDERS_F + row * 2, 1, s)
			val defender = BastionEntities.DEFENDER.spawn(level, defenderPos, EntitySpawnReason.COMMAND) ?: continue
			defender.snapTo(defender.position(), facing.opposite.toYRot(), 0f)
			defender.post = defenderPos
			defender.postRadius = 14.0
			battle.add(defender)
			spawned++
		}
		BattleDirector.start(battle)
		Bastion.LOGGER.info("[bastion] Escena C (batalla${if (onTerrain) " sobre el terreno" else ""}) en ${origin.toShortString()} mirando al ${facing.serializedName}: " +
			"$spawned por bando, campo de flujo de ${flow.reachable} casillas")
		return battle
	}

	private fun buildField() {
		val stone = Blocks.STONE.defaultBlockState()
		val grass = Blocks.GRASS_BLOCK.defaultBlockState()
		val bricks = Blocks.STONE_BRICKS.defaultBlockState()
		val air = Blocks.AIR.defaultBlockState()
		for (f in F_MIN..F_MAX) for (s in S_MIN..S_MAX) {
			set(f, -1, s, stone)
			set(f, 0, s, grass)
			for (y in 1..HEIGHT) set(f, y, s, air)
		}
		for (y in 1..WALL_HEIGHT) for (s in S_MIN..S_MAX) for (f in WALL_F..WALL_F + 1) {
			if (s !in GATE) set(f, y, s, bricks)
		}
	}

	/** La casilla donde se está de pie sobre el terreno en (f, s): encima del bloque más alto que no sea hoja. */
	private fun surface(f: Int, s: Int): BlockPos {
		val p = pos(f, 0, s)
		return BlockPos(p.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.x, p.z), p.z)
	}

	private fun computeFlowOnTerrain(): FlowField {
		val heights = (F_MIN..F_MAX).flatMap { f -> (S_MIN..S_MAX).map { s -> surface(f, s).y } }
		val a = pos(F_MIN, 0, S_MIN)
		val b = pos(F_MAX, 0, S_MAX)
		val box = Box(min(a.x, b.x), heights.min() - 2, min(a.z, b.z), max(a.x, b.x), heights.max() + 3, max(a.z, b.z))
		// En un mapa real la meta no puede ser una fila: si cae en la orilla de un lago queda aislada (pasó con la semilla
		// «bastion»: 32 casillas alcanzables). Es toda la franja donde esperan los defensores.
		val goals = (DEFENDERS_F..GOAL_F).flatMap { f -> (S_MIN..S_MAX).map { s -> surface(f, s).let { Cell(it.x, it.y, it.z) } } }
		return timedFlow(box, goals)
	}

	private fun timedFlow(box: Box, goals: List<Cell>): FlowField {
		val start = System.nanoTime()
		val flow = FlowField.compute(box, goals, LevelCells(level, ItemStack(Items.IRON_AXE)))
		Bastion.LOGGER.info("[bastion] Campo de flujo: ${flow.reachable} casillas (caja ${box.sizeX}x${box.sizeY}x${box.sizeZ}) en ${(System.nanoTime() - start) / 1_000_000} ms")
		return flow
	}

	private fun computeFlow(): FlowField {
		val a = pos(F_MIN, 1, S_MIN)
		val b = pos(F_MAX, HEIGHT - 2, S_MAX)
		val box = Box(min(a.x, b.x), min(a.y, b.y), min(a.z, b.z), max(a.x, b.x), max(a.y, b.y), max(a.z, b.z))
		val goals = (S_MIN..S_MAX).map { s -> pos(GOAL_F, 1, s).let { Cell(it.x, it.y, it.z) } }
		return timedFlow(box, goals)
	}

	private fun set(f: Int, y: Int, s: Int, state: BlockState) {
		level.setBlock(pos(f, y, s), state, 2)
	}

	companion object {
		const val F_MIN = -2
		const val F_MAX = 108
		const val S_MIN = -2
		const val S_MAX = 49
		const val HEIGHT = 8
		const val WALL_F = 80
		const val WALL_HEIGHT = 5
		val GATE = 21..26
		const val DEFENDERS_F = 86
		const val GOAL_F = 106

		/** Unidades por fila. */
		const val COLUMNS = 20
	}
}
