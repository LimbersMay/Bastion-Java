package dev.limbers.bastion.entity

import dev.limbers.bastion.Bastion
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

/** Registro de las entidades del mod. */
object BastionEntities {
	val INVADER: EntityType<Invader> = register("invader", EntityType.Builder.of(::Invader, MobCategory.MISC).sized(0.6f, 1.8f).clientTrackingRange(10))
	val DEFENDER: EntityType<Defender> = register("defender", EntityType.Builder.of(::Defender, MobCategory.MISC).sized(0.6f, 1.8f).clientTrackingRange(10))

	private fun <T : SiegeUnit> register(name: String, builder: EntityType.Builder<T>): EntityType<T> {
		val key = ResourceKey.create(Registries.ENTITY_TYPE, Bastion.id(name))
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key))
	}

	fun initialize() {
		FabricDefaultAttributeRegistry.register(INVADER, Invader.createAttributes())
		FabricDefaultAttributeRegistry.register(DEFENDER, Defender.createAttributes())
	}
}
