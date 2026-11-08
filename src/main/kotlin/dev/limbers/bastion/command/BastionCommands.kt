package dev.limbers.bastion.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import dev.limbers.bastion.Bastion
import dev.limbers.bastion.core.map.MapDef
import dev.limbers.bastion.map.BastionMaps
import com.mojang.brigadier.context.CommandContext
import dev.limbers.bastion.debug.Bench
import dev.limbers.bastion.debug.DebugView
import dev.limbers.bastion.debug.AssaultScene
import dev.limbers.bastion.debug.BattleScene
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component

/**
 * Órdenes del mod.
 *
 * - `/bastion escena escalera`: monta delante del jugador la escena A con escaleras y defensor.
 * - `/bastion escena picar`: la misma escena, sin escaleras: el invasor pica los tablones y pasa al otro lado.
 * - `/bastion escena batalla [por bando]`: la escena C, la batalla grande (200 contra 200 por defecto), en un llano.
 * - `/bastion escena batalla-terreno [por bando]`: la misma batalla sobre el terreno tal cual (mapa real).
 * - `/bastion medir [segundos]`: mide ticks/s y ms por tick durante ese tiempo (120 por defecto).
 * - `/bastion mapas`: los mapas leídos (`data/<espacio>/maps/<id>.json`).
 * - `/bastion mapa <id> comprobar`: comprueba el mapa sobre el mundo cargado (que en cada punto quepa alguien de pie).
 * - `/bastion mapa <id> ir`: lleva a quien lo pide al primer punto de los jugadores del mapa.
 * - `/bastion ver rutas|campo|nada`: enseña con partículas las rutas de los invasores o el campo de flujo (`DebugView`).
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
		for ((name, onTerrain) in listOf("batalla" to false, "batalla-terreno" to true)) {
			escena.then(
				Commands.literal(name)
					.executes { buildBattle(it, 200, onTerrain) }
					.then(Commands.argument("porBando", IntegerArgumentType.integer(1, 1000)).executes { buildBattle(it, IntegerArgumentType.getInteger(it, "porBando"), onTerrain) }),
			)
		}
		val medir = Commands.literal("medir")
			.executes { measure(it, 120) }
			.then(Commands.argument("segundos", IntegerArgumentType.integer(5, 1800)).executes { measure(it, IntegerArgumentType.getInteger(it, "segundos")) })
		val mapId = Commands.argument("id", StringArgumentType.word()).suggests { _, builder -> SharedSuggestionProvider.suggest(BastionMaps.maps.keys, builder) }
		val mapa = Commands.literal("mapa").then(
			mapId
				.then(Commands.literal("comprobar").executes { checkMap(it) })
				.then(Commands.literal("ir").executes { goToMap(it) }),
		)
		dispatcher.register(
			Commands.literal("bastion")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(escena)
				.then(medir)
				.then(Commands.literal("mapas").executes { listMaps(it) })
				.then(
					Commands.literal("ver")
						.then(Commands.literal("rutas").executes { view(it, routes = !DebugView.routes, flow = DebugView.flow) })
						.then(Commands.literal("campo").executes { view(it, routes = DebugView.routes, flow = !DebugView.flow) })
						.then(Commands.literal("nada").executes { view(it, routes = false, flow = false) }),
				)
				.then(mapa),
		)
	}

	/**
	 * Dónde y hacia dónde: la posición y la dirección de quien da la orden. Vale para un jugador y para la consola del
	 * servidor con `execute positioned ... rotated ... run bastion ...` (así se mide sin abrir el juego).
	 */
	private fun placement(source: CommandSourceStack): Pair<BlockPos, Direction> =
		BlockPos.containing(source.position) to Direction.fromYRot(source.rotation.y.toDouble())

	private fun buildBattle(context: CommandContext<CommandSourceStack>, perSide: Int, onTerrain: Boolean): Int {
		val source = context.source
		val (at, facing) = placement(source)
		// El llano empieza 3 bloques por delante, centrado en quien lo pide a lo ancho.
		val origin = at.relative(facing, 3 - BattleScene.F_MIN).relative(facing.counterClockWise, 24).below()
		val battle = BattleScene(source.level, origin, facing).build(perSide, onTerrain)
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

	/** Enciende o apaga lo que se ve; `ver rutas` y `ver campo` alternan. */
	private fun view(context: CommandContext<CommandSourceStack>, routes: Boolean, flow: Boolean): Int {
		DebugView.routes = routes
		DebugView.flow = flow
		val on = listOfNotNull("rutas de los invasores".takeIf { routes }, "campo de flujo".takeIf { flow })
		context.source.sendSuccess({ Component.literal(if (on.isEmpty()) "Sin partículas de depuración." else "Se ve: ${on.joinToString(" y ")}.") }, true)
		return 1
	}

	private fun listMaps(context: CommandContext<CommandSourceStack>): Int {
		val maps = BastionMaps.maps.values
		val text = if (maps.isEmpty()) "No hay mapas (mira el registro: un mapa con errores se salta)." else maps.joinToString("\n") { "${it.id}: ${it.name} (${it.gates.size} puerta(s), ${it.waves.size} oleadas)" }
		context.source.sendSuccess({ Component.literal(text) }, false)
		return maps.size
	}

	/** El mapa del argumento `id`, o nulo tras avisar. */
	private fun mapArg(context: CommandContext<CommandSourceStack>): MapDef? {
		val id = StringArgumentType.getString(context, "id")
		return BastionMaps.maps[id] ?: run {
			context.source.sendFailure(Component.literal("No hay ningún mapa «$id». Mapas: ${BastionMaps.maps.keys.joinToString()}"))
			null
		}
	}

	private fun checkMap(context: CommandContext<CommandSourceStack>): Int {
		val map = mapArg(context) ?: return 0
		val total = map.standingPoints().size
		val problems = BastionMaps.check(context.source.level, map)
		for (p in problems) Bastion.LOGGER.warn("[bastion] Mapa ${map.id}: ${p.label} [${p.at.x}, ${p.at.y}, ${p.at.z}]: ${p.what}")
		val summary = "Mapa ${map.id}: ${total - problems.size} de $total puntos bien."
		if (problems.isEmpty()) {
			context.source.sendSuccess({ Component.literal("$summary El mundo es el del mapa.") }, true)
		} else {
			val list = problems.take(8).joinToString("\n") { "${it.label}: ${it.what}" } + if (problems.size > 8) "\n... y ${problems.size - 8} más (en el registro)" else ""
			context.source.sendFailure(Component.literal("$summary Con problemas:\n$list"))
		}
		return total - problems.size
	}

	private fun goToMap(context: CommandContext<CommandSourceStack>): Int {
		val map = mapArg(context) ?: return 0
		val player = context.source.playerOrException
		val at = map.playerSpawns.first()
		// Mover al jugador que lo pide (no a las tropas): es una orden de depuración.
		player.teleportTo(context.source.level, at.x, at.y, at.z, setOf(), player.yRot, player.xRot, true)
		context.source.sendSuccess({ Component.literal("En ${map.name}.") }, false)
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
