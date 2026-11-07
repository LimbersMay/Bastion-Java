package dev.limbers.bastion

import dev.limbers.bastion.battle.BattleDirector
import dev.limbers.bastion.battle.Bench
import dev.limbers.bastion.command.BastionCommands
import dev.limbers.bastion.entity.BastionEntities
import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

/** Punto de entrada común (servidor y cliente). */
object Bastion : ModInitializer {
	const val MOD_ID: String = "bastion"

	/** Todo lo que escribe el mod lleva el prefijo «[bastion]», para poder contar líneas en el log. */
	val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		BastionEntities.initialize()
		BastionCommands.register()
		BattleDirector.register()
		Bench.register()
		LOGGER.info("[bastion] Cargado")
	}

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
