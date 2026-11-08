package dev.limbers.bastion.client

import dev.limbers.bastion.Bastion
import dev.limbers.bastion.unit.SiegeUnit
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.HumanoidMobRenderer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.resources.Identifier

/**
 * Lo que el dibujo necesita de la unidad: además de lo del jugador, qué skin lleva. El modelo de jugador del juego
 * trabaja con `AvatarRenderState`, así que el dibujador lo declara así y crea siempre este.
 */
class SiegeUnitRenderState : AvatarRenderState() {
	var skinIndex = 0
}

/**
 * Dibuja una unidad con el modelo de jugador del juego (con la segunda capa de la skin) y una de las skins de 64x64 de
 * Bedrock. Lleva en la mano lo que tenga: el hacha, la espada o las escaleras. Más adelante, GeckoLib.
 */
class SiegeUnitRenderer<T : SiegeUnit>(context: EntityRendererProvider.Context, private val skinPrefix: String, skins: Int) :
	HumanoidMobRenderer<T, AvatarRenderState, PlayerModel>(context, PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f) {

	private val textures = List(skins) { Bastion.id("textures/entity/${skinPrefix}_$it.png") }

	override fun createRenderState(): AvatarRenderState = SiegeUnitRenderState()

	override fun extractRenderState(entity: T, state: AvatarRenderState, partialTicks: Float) {
		super.extractRenderState(entity, state, partialTicks)
		(state as SiegeUnitRenderState).skinIndex = entity.skin
	}

	override fun getTextureLocation(state: AvatarRenderState): Identifier =
		textures[(state as SiegeUnitRenderState).skinIndex.coerceIn(0, textures.size - 1)]
}
