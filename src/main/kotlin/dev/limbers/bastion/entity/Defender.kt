package dev.limbers.bastion.entity

import dev.limbers.bastion.entity.ai.GuardPostMeleeGoal
import dev.limbers.bastion.entity.ai.ReturnToPostGoal
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level

/**
 * Defensor aliado (ficha de «soldado aliado» de Bedrock: 60 de vida, golpe 10, velocidad 0,29, espada de hierro).
 * De momento aguanta en su puesto (`post`) y pelea con quien llegue a él.
 */
class Defender(type: EntityType<out Defender>, level: Level) : SiegeUnit(type, level, SKINS) {
	/** Su puesto: pelea con quien llegue a él y vuelve a él. Nulo: pelea donde sea. */
	var post: BlockPos? = null

	init {
		setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.IRON_SWORD))
	}

	override fun registerGoals() {
		goalSelector.addGoal(0, FloatGoal(this))
		goalSelector.addGoal(2, GuardPostMeleeGoal(this, { post }, POST_RADIUS))
		goalSelector.addGoal(3, ReturnToPostGoal(this) { post })
		goalSelector.addGoal(7, LookAtPlayerGoal(this, Player::class.java, 8f))
		goalSelector.addGoal(8, RandomLookAroundGoal(this))
		targetSelector.addGoal(1, HurtByTargetGoal(this))
		targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Invader::class.java, false))
	}

	companion object {
		const val SKINS = 4

		/** Pelea con quien esté a 3 bloques o menos de su puesto. */
		const val POST_RADIUS = 3.0

		fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
			.add(Attributes.MAX_HEALTH, 60.0)
			.add(Attributes.ATTACK_DAMAGE, 10.0)
			.add(Attributes.MOVEMENT_SPEED, 0.29)
			.add(Attributes.FOLLOW_RANGE, 32.0)
	}
}
