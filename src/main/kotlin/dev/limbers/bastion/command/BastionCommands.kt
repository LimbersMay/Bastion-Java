package dev.limbers.bastion.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import dev.limbers.bastion.scene.AssaultScene
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component

/**
 * Órdenes del mod.
 *
 * - `/bastion escena escalera`: monta delante del jugador la escena A con escaleras y defensor.
 * - `/bastion escena picar`: la misma escena, sin escaleras: el invasor pica los tablones y pasa al otro lado.
 */
object BastionCommands {
	fun register() {
		CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> registerAll(dispatcher) }
	}

	private fun registerAll(dispatcher: CommandDispatcher<CommandSourceStack>) {
		val escena = Commands.literal("escena")
		for (variant in AssaultScene.Variant.entries) {
			escena.then(Commands.literal(variant.label).executes { buildScene(it, variant) })
		}
		dispatcher.register(
			Commands.literal("bastion")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(escena),
		)
	}

	private fun buildScene(context: CommandContext<CommandSourceStack>, variant: AssaultScene.Variant): Int {
		val source = context.source
		val player = source.playerOrException
		val facing = player.direction
		// El patio empieza 3 bloques por delante del jugador, con el suelo a la altura del que pisa.
		val origin: BlockPos = player.blockPosition().relative(facing, 3 - AssaultScene.F_MIN).relative(facing.counterClockWise, 4).below()
		val actors = AssaultScene(source.level, origin, facing).build(variant)
		val what = when (variant) {
			AssaultScene.Variant.LADDER -> "con ${actors.invader.ladders} escaleras; el defensor espera arriba del muro"
			AssaultScene.Variant.DIG -> "sin escaleras; tiene que picar los tablones"
		}
		source.sendSuccess({ Component.literal("Escena A (${variant.label}): invasor $what.") }, true)
		return 1
	}
}
