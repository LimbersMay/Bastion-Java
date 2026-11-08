package dev.limbers.bastion.unit

import dev.limbers.bastion.Bastion
import dev.limbers.bastion.core.unit.UnitDef
import dev.limbers.bastion.core.unit.Variation
import dev.limbers.bastion.director.Battle
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * Base de las unidades del asedio: modelo humanoide del juego con una skin elegida al azar al nacer, y las cifras de
 * su ficha (`unit`, de `core.unit.Units`).
 *
 * - **Vida:** la de la ficha ±10 % al nacer (`Variation`). Se guarda con la entidad.
 * - **Golpe:** el de la ficha (con el arma incluida) ±10 % en cada golpe.
 * - **Arma:** la de la ficha, en la mano.
 *
 * Es un `PathfinderMob` y no un `Monster`: no arde al sol ni desaparece en pacífico.
 */
abstract class SiegeUnit(type: EntityType<out SiegeUnit>, level: Level, val unit: UnitDef) : PathfinderMob(type, level) {
	/** El arma de la ficha. Los invasores la guardan mientras llevan una escalera en la mano. */
	val weapon: ItemStack = ItemStack(weaponItem(unit))

	init {
		if (!level.isClientSide) {
			entityData.set(SKIN, random.nextInt(unit.skins))
			getAttribute(Attributes.MAX_HEALTH)!!.baseValue = Variation.roll(unit.health, random.nextDouble())
			health = maxHealth
			// El juego suma el arma de la mano al golpe de cualquier criatura: de base, la ficha menos el arma. Se
			// calcula aquí y no al registrar porque al arrancar el mod los objetos aún no tienen sus datos.
			getAttribute(Attributes.ATTACK_DAMAGE)!!.baseValue = (unit.damage - weaponBonus(weapon.item)).coerceAtLeast(0.0)
		}
		setPersistenceRequired()
		holdWeapon()
	}

	/** La batalla en la que pelea, si la hay: entonces el director le asigna enemigo y sigue su campo de flujo. */
	var battle: Battle? = null

	/** Índice de la skin, para el cliente. */
	val skin: Int get() = entityData.get(SKIN)

	fun holdWeapon() = setItemSlot(EquipmentSlot.MAINHAND, weapon)

	/**
	 * Caída máxima que acepta al buscar camino: 3, como `RoutePlanner`. El juego deja a una criatura con objetivo y
	 * mucha vida tirarse desde muy alto (con 60 de vida, hasta 43 bloques): el defensor saltaba del muro.
	 */
	override fun getMaxFallDistance(): Int = 3

	/** Cada golpe, el de la ficha ±10 %: se suma la diferencia solo durante este golpe. */
	override fun doHurtTarget(level: ServerLevel, target: Entity): Boolean {
		val attack = getAttribute(Attributes.ATTACK_DAMAGE) ?: return super.doHurtTarget(level, target)
		val delta = Variation.roll(unit.damage, random.nextDouble()) - unit.damage
		if (delta == 0.0) return super.doHurtTarget(level, target)
		attack.addTransientModifier(AttributeModifier(HIT_VARIATION, delta, AttributeModifier.Operation.ADD_VALUE))
		try {
			return super.doHurtTarget(level, target)
		} finally {
			attack.removeModifier(HIT_VARIATION)
		}
	}

	override fun defineSynchedData(entityData: SynchedEntityData.Builder) {
		super.defineSynchedData(entityData)
		entityData.define(SKIN, 0)
	}

	override fun addAdditionalSaveData(output: ValueOutput) {
		super.addAdditionalSaveData(output)
		output.putInt("Skin", skin)
	}

	override fun readAdditionalSaveData(input: ValueInput) {
		super.readAdditionalSaveData(input)
		entityData.set(SKIN, input.getIntOr("Skin", 0).coerceIn(0, unit.skins - 1))
	}

	companion object {
		private val SKIN: EntityDataAccessor<Int> = SynchedEntityData.defineId(SiegeUnit::class.java, EntityDataSerializers.INT)
		private val HIT_VARIATION: Identifier = Bastion.id("hit_variation")

		/** El objeto del arma de la ficha. Un id que no existe es un error de la ficha: se dice cuál. */
		fun weaponItem(unit: UnitDef): Item =
			BuiltInRegistries.ITEM.getOptional(Identifier.parse(unit.weapon)).orElseThrow { IllegalStateException("${unit.id}: no existe el arma ${unit.weapon}") }

		/** Lo que el arma suma al golpe de quien la lleva en la mano (hacha de hierro, 8; espada de hierro, 5). */
		fun weaponBonus(item: Item): Double =
			item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()
				.filter { it.attribute() == Attributes.ATTACK_DAMAGE && it.slot().test(EquipmentSlot.MAINHAND) && it.modifier().operation() == AttributeModifier.Operation.ADD_VALUE }
				.sumOf { it.modifier().amount() }

		/** Atributos de la ficha. El golpe de base se corrige al nacer, restando el arma (ver `init`). */
		fun attributes(unit: UnitDef, followRange: Double): AttributeSupplier.Builder = createMobAttributes()
			.add(Attributes.MAX_HEALTH, unit.health)
			.add(Attributes.ATTACK_DAMAGE, unit.damage)
			.add(Attributes.MOVEMENT_SPEED, unit.speed)
			.add(Attributes.KNOCKBACK_RESISTANCE, unit.knockbackResistance)
			.add(Attributes.FOLLOW_RANGE, followRange)
	}
}
