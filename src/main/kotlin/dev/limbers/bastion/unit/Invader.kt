package dev.limbers.bastion.unit

import dev.limbers.bastion.core.unit.UnitDef
import dev.limbers.bastion.unit.ai.AssaultGoal
import dev.limbers.bastion.unit.ai.FlowFieldGoal
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
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
 * Invasor cuerpo a cuerpo (de momento, el soldado invasor de `Units`).
 *
 * Lleva su arma y, si se le dan, escaleras de mano. Con `AssaultGoal` va hacia su objetivo por la ruta que él
 * mismo planea: camina, pica o pone escaleras, con su hacha o sus escaleras en la mano. En una batalla grande sigue
 * el campo de flujo de la batalla (`FlowFieldGoal`) y pelea con quien le asigne el director.
 */
class Invader(type: EntityType<out Invader>, level: Level, unit: UnitDef) : SiegeUnit(type, level, unit) {
	/** Escaleras de mano que lleva. Cada una que coloca se gasta: no hay escaleras infinitas. */
	var ladders: Int = 0

	/** Adónde ir si no tiene a nadie a quien atacar (por ejemplo, el otro lado del muro). */
	var destination: Vec3? = null

	/** Contadores para las pruebas y el registro: lo que ha hecho él, no el juego. */
	var laddersPlaced: Int = 0
	var blocksBroken: Int = 0

	fun holdLadders() = setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.LADDER, ladders.coerceIn(1, 64)))

	/**
	 * Su `AssaultGoal`, para ver su ruta en el juego. `lateinit` sin valor inicial a propósito: `registerGoals` se
	 * llama desde el constructor del juego, antes que los de esta clase, y un valor inicial lo borraría.
	 */
	lateinit var assault: AssaultGoal
		private set

	override fun registerGoals() {
		assault = AssaultGoal(this)
		goalSelector.addGoal(0, FloatGoal(this))
		goalSelector.addGoal(1, assault)
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
		fun createAttributes(unit: UnitDef): AttributeSupplier.Builder = attributes(unit, followRange = 48.0)
	}
}
