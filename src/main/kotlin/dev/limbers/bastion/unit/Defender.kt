package dev.limbers.bastion.unit

import dev.limbers.bastion.core.unit.UnitDef
import dev.limbers.bastion.unit.ai.GuardPostMeleeGoal
import dev.limbers.bastion.unit.ai.ReturnToPostGoal
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

/**
 * Defensor aliado cuerpo a cuerpo (de momento, el soldado aliado de `Units`). De momento aguanta en su puesto (`post`) y pelea con quien llegue a él.
 */
class Defender(type: EntityType<out Defender>, level: Level, unit: UnitDef) : SiegeUnit(type, level, unit) {
	/** Su puesto: pelea con quien llegue a él y vuelve a él. Nulo: pelea donde sea. */
	var post: BlockPos? = null

	/** Hasta dónde del puesto sale a pelear. */
	var postRadius: Double = POST_RADIUS

	override fun registerGoals() {
		goalSelector.addGoal(0, FloatGoal(this))
		goalSelector.addGoal(2, GuardPostMeleeGoal(this, { post }) { postRadius })
		goalSelector.addGoal(3, ReturnToPostGoal(this) { post })
		goalSelector.addGoal(7, LookAtPlayerGoal(this, Player::class.java, 8f))
		goalSelector.addGoal(8, RandomLookAroundGoal(this))
		targetSelector.addGoal(1, HurtByTargetGoal(this))
		// En una batalla el enemigo lo asigna el director; fuera de ella, lo busca él.
		targetSelector.addGoal(2, object : NearestAttackableTargetGoal<Invader>(this, Invader::class.java, false) {
			override fun canUse() = battle == null && super.canUse()
		})
	}

	companion object {
		/** Pelea con quien esté a 3 bloques o menos de su puesto. */
		const val POST_RADIUS = 3.0

		fun createAttributes(unit: UnitDef): AttributeSupplier.Builder = attributes(unit, followRange = 32.0)
	}
}
