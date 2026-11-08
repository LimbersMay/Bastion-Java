package dev.limbers.bastion.unit

import dev.limbers.bastion.Bastion
import dev.limbers.bastion.core.unit.UnitDef
import dev.limbers.bastion.core.unit.Units
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.level.Level

/**
 * Registro de las entidades del mod: una por ficha de `Units` **cuyo rol ya existe** (de momento, los soldados).
 * Las demás fichas se registran cuando llegue su rol: una unidad sin comportamiento no debe poder nacer.
 */
object BastionEntities {
	/** Una entidad registrada, con su ficha. */
	class Registered<T : SiegeUnit>(val unit: UnitDef, val type: EntityType<T>, val attributes: () -> AttributeSupplier.Builder)

	val INVADER_SOLDIER = register(Units.INVADER_SOLDIER, ::Invader, Invader::createAttributes)
	val ALLY_SOLDIER = register(Units.ALLY_SOLDIER, ::Defender, Defender::createAttributes)

	val ALL: List<Registered<*>> = listOf(INVADER_SOLDIER, ALLY_SOLDIER)

	private fun <T : SiegeUnit> register(
		unit: UnitDef,
		create: (EntityType<T>, Level, UnitDef) -> T,
		attributes: (UnitDef) -> AttributeSupplier.Builder,
	): Registered<T> {
		val key = ResourceKey.create(Registries.ENTITY_TYPE, Bastion.id(unit.id))
		val builder = EntityType.Builder.of<T>({ type, level -> create(type, level, unit) }, MobCategory.MISC).sized(0.6f, 1.8f).clientTrackingRange(10)
		val type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key))
		return Registered(unit, type) { attributes(unit) }
	}

	fun initialize() {
		for (r in ALL) FabricDefaultAttributeRegistry.register(r.type, r.attributes())
	}
}
