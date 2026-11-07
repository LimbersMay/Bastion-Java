package dev.limbers.bastion.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.limbers.bastion.battle.Bench
import dev.limbers.bastion.scene.AssaultScene
import dev.limbers.bastion.scene.BattleScene
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component

/**
 * Órdenes del mod.
 *
 * - `/bastion escena escalera`: monta delante del jugador la escena A con escaleras y defensor.
 * - `/bastion escena picar`: la misma escena, sin escaleras: el invasor pica los tablones y pasa al otro lado.
 * - `/bastion escena batalla [por bando]`: la escena C, la batalla grande (200 contra 200 por defecto).
 * - `/bastion medir [segundos]`: mide ticks/s y ms por tick durante ese tiempo (120 por defecto).
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
		escena.then(
			Commands.literal("batalla")
				.executes { buildBattle(it, 200) }
				.then(Commands.argument("porBando", IntegerArgumentType.integer(1, 1000)).executes { buildBattle(it, IntegerArgumentType.getInteger(it, "porBando")) }),
		)
		val medir = Commands.literal("medir")
			.executes { measure(it, 120) }
			.then(Commands.argument("segundos", IntegerArgumentType.integer(5, 1800)).executes { measure(it, IntegerArgumentType.getInteger(it, "segundos")) })
		dispatcher.register(
			Commands.literal("bastion")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(escena)
				.then(medir),
		)
	}

	/**
	 * Dónde y hacia dónde: la posición y la dirección de quien da la orden. Vale para un jugador y para la consola del
	 * servidor con `execute positioned ... rotated ... run bastion ...` (así se mide sin abrir el juego).
	 */
	private fun placement(source: CommandSourceStack): Pair<BlockPos, Direction> =
		BlockPos.containing(source.position) to Direction.fromYRot(source.rotation.y.toDouble())

	private fun buildBattle(context: CommandContext<CommandSourceStack>, perSide: Int): Int {
		val source = context.source
		val (at, facing) = placement(source)
		// El llano empieza 3 bloques por delante, centrado en quien lo pide a lo ancho.
		val origin = at.relative(facing, 3 - BattleScene.F_MIN).relative(facing.counterClockWise, 24).below()
		val battle = BattleScene(source.level, origin, facing).build(perSide)
		source.sendSuccess({
			Component.literal("Escena C (batalla): ${battle.invaders.size} invasores contra ${battle.defenders.size} defensores. " +
				"Mide con /bastion medir y /spark profiler start --timeout 120.")
		}, true)
		return 1
	}

	private fun measure(context: CommandContext<CommandSourceStack>, seconds: Int): Int {
		if (Bench.running) {
			context.source.sendFailure(Component.literal("Ya hay una medida en marcha."))
			return 0
		}
		Bench.start(seconds, "batalla")
		context.source.sendSuccess({ Component.literal("Midiendo $seconds s... el resultado sale en el registro y en bastion-bench.txt.") }, true)
		return 1
	}

	private fun buildScene(context: CommandContext<CommandSourceStack>, variant: AssaultScene.Variant): Int {
		val source = context.source
		val (at, facing) = placement(source)
		// El patio empieza 3 bloques por delante, con el suelo a la altura del que pisa quien lo pide.
		val origin: BlockPos = at.relative(facing, 3 - AssaultScene.F_MIN).relative(facing.counterClockWise, 4).below()
		val actors = AssaultScene(source.level, origin, facing).build(variant)
		val what = when (variant) {
			AssaultScene.Variant.LADDER -> "con ${actors.invader.ladders} escaleras; el defensor espera arriba del muro"
			AssaultScene.Variant.DIG -> "sin escaleras; tiene que picar los tablones"
		}
		source.sendSuccess({ Component.literal("Escena A (${variant.label}): invasor $what.") }, true)
		return 1
	}
}
