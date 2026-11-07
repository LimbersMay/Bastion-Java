package dev.limbers.bastion.world

import dev.limbers.bastion.logic.route.CellAt
import dev.limbers.bastion.logic.route.RouteCell
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.FenceBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.level.block.ScaffoldingBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.WallBlock
import kotlin.math.ceil

/**
 * Lee el mundo para el buscador de rutas (`CellAt`). En Bedrock se clasificaba por el nombre del bloque con
 * expresiones regulares (y «grass» confundía la hierba con el bloque de césped); aquí se usan las propiedades
 * reales del bloque: su forma de colisión, su dureza y si tiene inventario.
 *
 * El coste de romper sale del tiempo real de picar con la herramienta que lleva la unidad (`Digging`).
 */
class LevelCells(private val level: Level, private val tool: ItemStack) : CellAt {
	private val pos = BlockPos.MutableBlockPos()

	override fun at(x: Int, y: Int, z: Int): RouteCell {
		pos.set(x, y, z)
		val state = level.getBlockState(pos)
		if (state.isAir) return RouteCell.FREE
		if (!state.fluidState.isEmpty) return RouteCell.HAZARD
		val block = state.block
		if (block is LadderBlock || block is ScaffoldingBlock) return RouteCell.CLIMB
		val shape = state.getCollisionShape(level, pos)
		if (shape.isEmpty) return RouteCell.FREE

		val partial = !state.isCollisionShapeFullBlock(level, pos)
		val flammable = state.ignitedByLava()
		val noStep = block is FenceBlock || block is WallBlock || block is FenceGateBlock || block is TrapDoorBlock || shape.max(Direction.Axis.Y) > 1.0
		if (!Digging.canBreak(level, pos, state)) {
			return RouteCell.SOLID.copy(partial = partial, flammable = flammable, noStep = noStep)
		}
		val cost = ceil(Digging.ticksToBreak(level, pos, state, tool) / TICKS_PER_STEP).coerceAtLeast(1.0)
		return RouteCell(cost = cost, ground = true, noStep = noStep, partial = partial, flammable = flammable)
	}

	fun at(p: BlockPos): RouteCell = at(p.x, p.y, p.z)

	companion object {
		/** Ticks que tarda una unidad en andar un bloque (velocidad ~0,28): el coste de picar se cuenta en pasos. */
		const val TICKS_PER_STEP = 5.0
	}
}
