package dev.limbers.bastion.world

import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.ceil

/** Cuánto tarda una unidad en picar un bloque, y qué bloques no se pican nunca. */
object Digging {
	/**
	 * Ticks para romper el bloque con esa herramienta: lo mismo que tardaría un jugador (Limbers, 2026-11-07: «mejor
	 * por defecto el que trae el juego»). Con hacha, la madera cae rápido; con espada, despacio; el pico, rápido en
	 * piedra. Es la fórmula del juego sin encantamientos ni efectos: velocidad de la herramienta / dureza / 30 (100 si
	 * el bloque pide otra herramienta).
	 */
	fun ticksToBreak(level: Level, pos: BlockPos, state: BlockState, tool: ItemStack): Double {
		val hardness = state.getDestroySpeed(level, pos)
		if (hardness <= 0f) return 1.0
		val speed = tool.getDestroySpeed(state)
		val divisor = if (!state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state)) 30.0 else 100.0
		val perTick = speed / hardness / divisor
		return ceil(1.0 / perTick)
	}

	/**
	 * Lo que nadie rompe: lo irrompible del juego y los bloques con contenido (cofres, hornos, carteles...), porque se
	 * perdería lo que guardan. En Bedrock era una lista de nombres; aquí, que el bloque tenga una entidad de bloque.
	 */
	fun canBreak(level: Level, pos: BlockPos, state: BlockState): Boolean =
		state.getDestroySpeed(level, pos) >= 0f && !state.hasBlockEntity()
}
