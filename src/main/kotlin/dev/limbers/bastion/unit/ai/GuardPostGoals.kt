package dev.limbers.bastion.unit.ai

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import java.util.EnumSet

/*
 * Defender un puesto, como los puestos de Bedrock (`postLogic`): el defensor pelea con quien llegue a su puesto,
 * pero no lo persigue por los muros. Sin puesto (`post()` nulo), pelea como cualquier criatura.
 */

/** Ataque cuerpo a cuerpo solo contra quien esté a `radius` bloques del puesto. */
class GuardPostMeleeGoal(
	private val guard: PathfinderMob,
	private val post: () -> BlockPos?,
	private val radius: () -> Double,
) : MeleeAttackGoal(guard, 1.0, true) {
	private fun targetNearPost(): Boolean {
		val p = post() ?: return true
		val target = guard.target ?: return false
		val r = radius()
		return target.distanceToSqr(p.x + 0.5, p.y.toDouble(), p.z + 0.5) <= r * r
	}

	override fun canUse(): Boolean = targetNearPost() && super.canUse()

	override fun canContinueToUse(): Boolean = targetNearPost() && super.canContinueToUse()
}

/** Vuelve a su puesto si se ha apartado más de un bloque y medio. */
class ReturnToPostGoal(private val mob: PathfinderMob, private val post: () -> BlockPos?) : Goal() {
	init {
		setFlags(EnumSet.of(Flag.MOVE))
	}

	private fun away(): Boolean {
		val p = post() ?: return false
		return mob.distanceToSqr(p.x + 0.5, p.y.toDouble(), p.z + 0.5) > 1.5 * 1.5
	}

	override fun canUse(): Boolean = away()

	override fun canContinueToUse(): Boolean = away() && !mob.navigation.isDone

	override fun start() {
		val p = post() ?: return
		mob.navigation.moveTo(p.x + 0.5, p.y.toDouble(), p.z + 0.5, 1.0)
	}
}
