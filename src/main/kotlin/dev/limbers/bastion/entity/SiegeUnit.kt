package dev.limbers.bastion.entity

import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * Base de las unidades del asedio: modelo humanoide del juego con una skin de Bedrock elegida al azar al nacer.
 * Es un `PathfinderMob` y no un `Monster`: no arde al sol ni desaparece en pacífico.
 */
abstract class SiegeUnit(type: EntityType<out SiegeUnit>, level: Level, private val skinCount: Int) : PathfinderMob(type, level) {
	init {
		if (!level.isClientSide) entityData.set(SKIN, random.nextInt(skinCount))
		setPersistenceRequired()
	}

	/** Índice de la skin, para el cliente. */
	val skin: Int get() = entityData.get(SKIN)

	/**
	 * Caída máxima que acepta al buscar camino: 3, como `RoutePlanner`. El juego deja a una criatura con objetivo y
	 * mucha vida tirarse desde muy alto (con 60 de vida, hasta 43 bloques): el defensor saltaba del muro.
	 */
	override fun getMaxFallDistance(): Int = 3

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
		entityData.set(SKIN, input.getIntOr("Skin", 0).coerceIn(0, skinCount - 1))
	}

	companion object {
		private val SKIN: EntityDataAccessor<Int> = SynchedEntityData.defineId(SiegeUnit::class.java, EntityDataSerializers.INT)
	}
}
