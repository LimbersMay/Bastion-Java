package dev.limbers.bastion.client

import dev.limbers.bastion.unit.BastionEntities
import dev.limbers.bastion.unit.SiegeUnit
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.renderer.entity.EntityRenderers

/** Punto de entrada del cliente: dibujo, modelos y teclas. */
object BastionClient : ClientModInitializer {
	override fun onInitializeClient() {
		// Cada unidad, con el modelo de jugador y las skins de su ficha (`<id>_<n>.png`).
		for (r in BastionEntities.ALL) register(r)
	}

	private fun <T : SiegeUnit> register(r: BastionEntities.Registered<T>) {
		EntityRenderers.register(r.type) { SiegeUnitRenderer<T>(it, r.unit.id, r.unit.skins) }
	}
}
