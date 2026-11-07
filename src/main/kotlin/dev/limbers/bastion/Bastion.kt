package dev.limbers.bastion

import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

/** Punto de entrada común (servidor y cliente). */
object Bastion : ModInitializer {
	const val MOD_ID: String = "bastion"

	/** Todo lo que escribe el mod lleva el prefijo «[bastion]», para poder contar líneas en el log. */
	val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		LOGGER.info("[bastion] Cargado")
	}

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
