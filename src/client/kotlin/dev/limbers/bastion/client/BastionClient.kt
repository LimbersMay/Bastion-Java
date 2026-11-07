package dev.limbers.bastion.client

import dev.limbers.bastion.entity.BastionEntities
import dev.limbers.bastion.entity.Defender
import dev.limbers.bastion.entity.Invader
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.renderer.entity.EntityRenderers

/** Punto de entrada del cliente: dibujo, modelos y teclas. */
object BastionClient : ClientModInitializer {
	override fun onInitializeClient() {
		EntityRenderers.register(BastionEntities.INVADER) { SiegeUnitRenderer<Invader>(it, "invader_soldier", Invader.SKINS) }
		EntityRenderers.register(BastionEntities.DEFENDER) { SiegeUnitRenderer<Defender>(it, "ally_soldier", Defender.SKINS) }
	}
}
