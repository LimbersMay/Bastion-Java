package dev.limbers.bastion.debug

import dev.limbers.bastion.Bastion
import dev.limbers.bastion.director.BattleDirector
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import net.minecraft.server.MinecraftServer
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Banco de medida (el equivalente al `cost.mjs` de Bedrock): durante N segundos mide cada tick del servidor y al
 * acabar escribe una línea con ticks por segundo, milisegundos por tick (media, p95 y peor) y las unidades en pie al
 * principio y al final. La línea va al registro y a `bastion-bench.txt` (en la carpeta del juego, `run/` en
 * desarrollo), con fecha y versiones, para comparar después.
 *
 * El desglose de qué gasta cada milisegundo lo da Spark (`/spark profiler start --timeout 120`).
 */
object Bench {
	private class Run(val seconds: Int, val label: String, val startUnits: String) {
		val tickNanos = ArrayList<Long>()
		val startWall = System.nanoTime()
		var tickStart = 0L
	}

	private var run: Run? = null
	val running: Boolean get() = run != null

	fun register() {
		ServerTickEvents.START_SERVER_TICK.register { run?.tickStart = System.nanoTime() }
		ServerTickEvents.END_SERVER_TICK.register { server ->
			val r = run ?: return@register
			// El tick en el que se pidió la medida empezó antes de que existiera: no se cuenta.
			if (r.tickStart == 0L) return@register
			r.tickNanos.add(System.nanoTime() - r.tickStart)
			if (System.nanoTime() - r.startWall >= r.seconds * 1_000_000_000L) finish(server, r)
		}
	}

	fun start(seconds: Int, label: String) {
		run = Run(seconds, label, units())
		Bastion.LOGGER.info("[bastion] Medida: empieza ($seconds s, $label; ${units()})")
	}

	private fun units(): String {
		val (i, d) = BattleDirector.counts()
		return "$i invasores y $d defensores"
	}

	private fun finish(server: MinecraftServer, r: Run) {
		run = null
		val wallSeconds = (System.nanoTime() - r.startWall) / 1e9
		val ms = r.tickNanos.map { it / 1e6 }.sorted()
		val tps = r.tickNanos.size / wallSeconds
		val mean = ms.average()
		val p95 = ms[(ms.size * 0.95).toInt().coerceAtMost(ms.size - 1)]
		val worst = ms.last()
		val version = FabricLoader.getInstance().getModContainer(Bastion.MOD_ID).map { it.metadata.version.friendlyString }.orElse("?")
		val line = "%s | bastion %s | MC %s | %s | %.0f s | %.2f ticks/s | ms/tick media %.2f, p95 %.2f, peor %.2f | inicio: %s | fin: %s".format(
			LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), version,
			SharedConstants.getCurrentVersion().name(), r.label, wallSeconds, tps, mean, p95, worst, r.startUnits, units(),
		)
		Bastion.LOGGER.info("[bastion] Medida: $line")
		val file = server.serverDirectory.resolve("bastion-bench.txt")
		Files.writeString(file, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
	}
}
