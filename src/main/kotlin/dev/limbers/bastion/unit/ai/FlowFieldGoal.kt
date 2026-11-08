package dev.limbers.bastion.unit.ai

import dev.limbers.bastion.unit.SiegeUnit
import dev.limbers.bastion.core.route.Cell
import net.minecraft.world.entity.ai.goal.Goal
import java.util.EnumSet
import kotlin.math.floor

/**
 * Avanza siguiendo el campo de flujo de su batalla: mira la flecha de su casilla y anda hacia la siguiente. No calcula
 * ninguna ruta: por eso 200 unidades cuestan casi lo mismo que una. Solo cuando no tiene enemigo asignado.
 */
class FlowFieldGoal(private val unit: SiegeUnit) : Goal() {
	init {
		setFlags(EnumSet.of(Flag.MOVE))
	}

	private fun nextCell(): Cell? {
		val flow = unit.battle?.flow ?: return null
		val x = floor(unit.x).toInt()
		val z = floor(unit.z).toInt()
		val y = floor(unit.y + 0.01).toInt()
		// Si está en el aire (saltando o cayendo), mira también la casilla de debajo.
		val here = listOf(y, y - 1, y + 1).firstOrNull { flow.distance(x, it, z) >= 0 } ?: return null
		val next = flow.next(x, here, z) ?: return null
		// Mira una casilla más allá si sigue al mismo nivel: avanza más recto, sin zigzag de casilla en casilla.
		val after = flow.next(next.x, next.y, next.z)
		return if (after != null && after.y == here && next.y == here) after else next
	}

	override fun canUse(): Boolean = unit.battle != null && unit.target?.isAlive != true && nextCell() != null

	override fun canContinueToUse(): Boolean = canUse()

	override fun requiresUpdateEveryTick(): Boolean = true

	override fun start() {
		unit.navigation.stop()
	}

	override fun tick() {
		val next = nextCell() ?: return
		unit.moveControl.setWantedPosition(next.x + 0.5, next.y.toDouble(), next.z + 0.5, 1.0)
	}
}
