package dev.limbers.bastion.entity

import dev.limbers.bastion.entity.ai.AssaultGoal
import dev.limbers.bastion.entity.ai.FlowFieldGoal
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * Soldado invasor (ficha de Bedrock: 40 de vida, golpe 9, velocidad 0,28, hacha de hierro).
 *
 * Lleva su hacha y, si se le dan, escaleras de mano. Con `AssaultGoal` va hacia su objetivo por la ruta que él
 * mismo planea: camina, pica o pone escaleras, con su hacha o sus escaleras en la mano. En una batalla grande sigue
 * el campo de flujo de la batalla (`FlowFieldGoal`) y pelea con quien le asigne el director.
 */
class Invader(type: EntityType<out Invader>, level: Level) : SiegeUnit(type, level, SKINS) {
	/** El arma, guardada mientras lleva una escalera en la mano. */
	val weapon: ItemStack = ItemStack(Items.IRON_AXE)

	/** Escaleras de mano que lleva. Cada una que coloca se gasta: no hay escaleras infinitas. */
	var ladders: Int = 0

	/** Adónde ir si no tiene a nadie a quien atacar (por ejemplo, el otro lado del muro). */
	var destination: Vec3? = null

	/** Contadores para las pruebas y el registro: lo que ha hecho él, no el juego. */
	var laddersPlaced: Int = 0
	var blocksBroken: Int = 0

	init {
		holdWeapon()
	}

	fun holdWeapon() = setItemSlot(EquipmentSlot.MAINHAND, weapon)

	fun holdLadders() = setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.LADDER, ladders.coerceIn(1, 64)))

	override fun registerGoals() {
		goalSelector.addGoal(0, FloatGoal(this))
		goalSelector.addGoal(1, AssaultGoal(this))
		goalSelector.addGoal(2, MeleeAttackGoal(this, 1.0, true))
		goalSelector.addGoal(3, FlowFieldGoal(this))
		goalSelector.addGoal(8, RandomLookAroundGoal(this))
		targetSelector.addGoal(1, HurtByTargetGoal(this))
		// En una batalla el enemigo lo asigna el director; fuera de ella, lo busca él.
		targetSelector.addGoal(2, object : NearestAttackableTargetGoal<Defender>(this, Defender::class.java, false) {
			override fun canUse() = battle == null && super.canUse()
		})
	}

	companion object {
		const val SKINS = 5

		fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
			.add(Attributes.MAX_HEALTH, 40.0)
			.add(Attributes.ATTACK_DAMAGE, 9.0)
			.add(Attributes.MOVEMENT_SPEED, 0.28)
			.add(Attributes.FOLLOW_RANGE, 48.0)
	}
}
