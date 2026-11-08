package dev.limbers.bastion.test

import dev.limbers.bastion.Bastion
import dev.limbers.bastion.director.Battle
import dev.limbers.bastion.director.BattleDirector
import dev.limbers.bastion.unit.BastionEntities
import dev.limbers.bastion.unit.Invader
import dev.limbers.bastion.unit.SiegeUnit
import dev.limbers.bastion.core.unit.Side
import dev.limbers.bastion.core.unit.Variation
import dev.limbers.bastion.core.flow.Box
import dev.limbers.bastion.core.flow.FlowField
import dev.limbers.bastion.core.route.Cell
import dev.limbers.bastion.world.LevelCells
import dev.limbers.bastion.debug.AssaultScene
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * Pruebas dentro del juego. Se ejecutan con `./gradlew runGameTest` (y dentro de `./gradlew build`).
 *
 * Las de la escena A se repiten 5 veces y deben pasar las 5 (`maxAttempts`/`requiredSuccesses`): en Bedrock una sola
 * pasada engañó más de una vez. Ver docs/PRUEBA-INICIAL.md.
 */
class BastionGameTest {
	@GameTest
	fun elMundoExiste(helper: GameTestHelper) {
		val pos = BlockPos(1, 1, 1)
		helper.setBlock(pos, Blocks.STONE)
		helper.assertBlockPresent(Blocks.STONE, pos)
		helper.succeed()
	}

	/** El invasor coloca su escalera, sube y ataca al defensor de arriba en 30 s o menos. */
	@GameTest(structure = ARENA, maxTicks = 600, maxAttempts = 5, requiredSuccesses = 5)
	fun asaltoConEscalera(helper: GameTestHelper) {
		val scene = scene(helper)
		val actors = scene.build(AssaultScene.Variant.LADDER)
		val invader = actors.invader
		val defender = actors.defender!!
		val maxHealth = defender.maxHealth
		val topY = scene.pos(0, scene.wallHeight + 1, 0).y - 0.1
		var reachedTop = false
		var topTick = -1L
		watchNoTeleport(helper, invader)
		// Se apunta si llega arriba en algún momento: después puede perder la pelea y caer, y eso también vale.
		helper.onEachTick {
			if (!reachedTop && invader.isAlive && invader.y >= topY && invader.onGround()) {
				reachedTop = true
				topTick = helper.tick
			}
		}
		helper.succeedWhen {
			helper.assertTrue(invader.laddersPlaced > 0, "no ha colocado ninguna escalera")
			helper.assertTrue(
				reachedTop,
				"no ha llegado arriba (invasor en ${helper.relativeVec(invader.position())}, vida ${invader.health}; " +
					"defensor en ${helper.relativeVec(defender.position())}, vida ${defender.health})",
			)
			helper.assertTrue(defender.health < maxHealth || !defender.isAlive, "el defensor no ha recibido daño")
			report(helper, "asaltoConEscalera", "arriba en el tick $topTick, ${invader.laddersPlaced} tramos colocados, defensor con ${defender.health} de vida")
		}
	}

	/** Sin escaleras, el invasor pica los tablones (grieta, golpes, tiempo según dureza) y pasa al otro lado en 40 s. */
	@GameTest(structure = ARENA, maxTicks = 800, maxAttempts = 5, requiredSuccesses = 5)
	fun asaltoPicando(helper: GameTestHelper) {
		val scene = scene(helper)
		val actors = scene.build(AssaultScene.Variant.DIG)
		val invader = actors.invader
		val destination = actors.destination!!
		watchNoTeleport(helper, invader)
		helper.succeedWhen {
			helper.assertTrue(invader.blocksBroken >= 2, "no ha picado los tablones")
			helper.assertTrue(invader.laddersPlaced == 0, "ha puesto escaleras sin llevarlas")
			val behind = Vec3.atBottomCenterOf(scene.pos(scene.wallF + 2, 1, 4))
			helper.assertTrue(invader.position().distanceToSqr(destination) <= 4.0 || invader.position().distanceToSqr(behind) <= 1.0, "no ha pasado al otro lado")
			report(helper, "asaltoPicando", "al otro lado, ${invader.blocksBroken} bloques picados")
		}
	}

	/** Si le rompen la escalera mientras sube, cae: no se queda flotando ni se teletransporta. */
	@GameTest(structure = ARENA, maxTicks = 600, maxAttempts = 5, requiredSuccesses = 5)
	fun escaleraDerribada(helper: GameTestHelper) {
		val scene = scene(helper)
		val actors = scene.build(AssaultScene.Variant.LADDER)
		val invader = actors.invader
		val floorY = scene.pos(0, 1, 0).y.toDouble()
		var brokenAt = -1L
		watchNoTeleport(helper, invader)
		helper.onEachTick {
			// Cuando va por la mitad, el «defensor» (la prueba) rompe todos los tramos de escalera.
			if (brokenAt < 0 && invader.y >= floorY + 4 && invader.onClimbable()) {
				for (f in scene.wallF - 1..scene.wallF - 1) for (s in 0..8) for (y in 1..scene.wallHeight + 1) {
					val pos = scene.pos(f, y, s)
					if (helper.level.getBlockState(pos).block is LadderBlock) helper.level.destroyBlock(pos, false)
				}
				brokenAt = helper.tick
			}
		}
		helper.succeedWhen {
			helper.assertTrue(brokenAt >= 0, "aún no se ha roto la escalera")
			helper.assertTrue(invader.onGround() && invader.y < floorY + 0.5, "no ha caído al suelo")
			report(helper, "escaleraDerribada", "rota en el tick $brokenAt, en el suelo ${helper.tick - brokenAt} ticks después")
		}
	}

	/**
	 * Escena C en pequeño: 10 invasores de una batalla siguen el campo de flujo y cruzan la puerta de un muro, sin
	 * calcular rutas propias, en 30 s o menos.
	 */
	@GameTest(structure = ARENA, maxTicks = 600)
	fun campoDeFlujo(helper: GameTestHelper) {
		val level = helper.level
		val origin = helper.absolutePos(BlockPos(0, 0, 0))
		fun p(x: Int, y: Int, z: Int) = origin.offset(x, y, z)
		for (x in 0..22) for (z in 0..10) {
			level.setBlock(p(x, 0, z), Blocks.STONE.defaultBlockState(), 2)
			for (y in 1..4) level.setBlock(p(x, y, z), if (x == 12 && z !in 4..5) Blocks.STONE_BRICKS.defaultBlockState() else Blocks.AIR.defaultBlockState(), 2)
		}
		val a = p(0, 1, 0)
		val b = p(22, 3, 10)
		val box = Box(a.x, a.y, a.z, b.x, b.y, b.z)
		val goals = (0..10).map { z -> p(20, 1, z).let { Cell(it.x, it.y, it.z) } }
		val flow = FlowField.compute(box, goals, LevelCells(level, ItemStack(Items.IRON_AXE)))
		val battle = Battle(level, flow)
		val invaders = (0 until 10).map { i ->
			BastionEntities.INVADER_SOLDIER.type.spawn(level, p(2 + i % 2, 1, i), EntitySpawnReason.COMMAND)!!.also { battle.add(it) }
		}
		BattleDirector.start(battle)
		invaders.forEach { watchNoTeleport(helper, it) }
		helper.succeedWhen {
			val behind = invaders.count { it.x >= origin.x + 14 }
			helper.assertTrue(behind == invaders.size, "solo $behind de ${invaders.size} han cruzado la puerta")
			report(helper, "campoDeFlujo", "${invaders.size} invasores al otro lado del muro")
		}
	}

	/**
	 * La ficha llega a la entidad: 10 de cada soldado nacen con su arma en la mano, el golpe de la ficha (el arma
	 * contada una sola vez) y la vida de la ficha ±10 %, distinta entre ellos.
	 */
	@GameTest(structure = ARENA, maxTicks = 40)
	fun fichaDeUnidades(helper: GameTestHelper) {
		val origin = flat(helper)
		val units = BastionEntities.ALL.flatMap { r -> (0 until 10).map { i -> r.type.spawn(helper.level, origin.offset(2 + i, 1, if (r.unit.side == Side.ALLY) 1 else 9), EntitySpawnReason.COMMAND)!! } }
		helper.succeedWhen {
			helper.assertTrue(helper.tick >= 2, "esperando a que se equipen")
			for (u in units) {
				val def = u.unit
				val spread = Variation.spread(def.health)
				helper.assertTrue(u.mainHandItem.item == SiegeUnit.weaponItem(def), "${def.id} sin su arma: ${u.mainHandItem}")
				helper.assertTrue(abs(u.getAttributeValue(Attributes.ATTACK_DAMAGE) - def.damage) < 1e-6, "${def.id} pega ${u.getAttributeValue(Attributes.ATTACK_DAMAGE)}, la ficha dice ${def.damage}")
				helper.assertTrue(u.maxHealth in (def.health - spread).toFloat()..(def.health + spread).toFloat(), "${def.id} con ${u.maxHealth} de vida, fuera de ${def.health} ± $spread")
				helper.assertTrue(u.health == u.maxHealth, "${def.id} no nace con la vida llena")
			}
			for (r in BastionEntities.ALL) {
				val healths = units.filter { it.unit == r.unit }.map { it.maxHealth }
				helper.assertTrue(healths.toSet().size > 1, "${r.unit.id}: los 10 con la misma vida, sin variación")
			}
			val summary = BastionEntities.ALL.joinToString("; ") { r ->
				val hs = units.filter { it.unit == r.unit }.map { it.maxHealth }
				"${r.unit.id} golpe ${r.unit.damage}, vida ${"%.1f".format(hs.min())}-${"%.1f".format(hs.max())}"
			}
			units.forEach { it.discard() }
			report(helper, "fichaDeUnidades", summary)
		}
	}

	/**
	 * Duelo de un soldado invasor contra un soldado aliado en campo abierto, sin puesto ni batalla. Regla de Limbers
	 * (Bedrock): un duelo entre soldados dura de 6 a 10,5 s. De momento solo se mide y se apunta.
	 */
	@GameTest(structure = ARENA, maxTicks = 1200)
	fun duelo(helper: GameTestHelper) {
		val origin = flat(helper)
		val invader = BastionEntities.INVADER_SOLDIER.type.spawn(helper.level, origin.offset(8, 1, 5), EntitySpawnReason.COMMAND)!!
		val ally = BastionEntities.ALLY_SOLDIER.type.spawn(helper.level, origin.offset(14, 1, 5), EntitySpawnReason.COMMAND)!!
		var firstHit = -1L
		helper.onEachTick {
			if (firstHit < 0 && (invader.health < invader.maxHealth || ally.health < ally.maxHealth)) firstHit = helper.tick
		}
		helper.succeedWhen {
			helper.assertTrue(!invader.isAlive || !ally.isAlive, "el duelo sigue (invasor ${invader.health}, aliado ${ally.health})")
			val winner = if (invader.isAlive) "gana el invasor con ${invader.health}" else "gana el aliado con ${ally.health}"
			val seconds = (helper.tick - firstHit) / 20.0
			listOf(invader, ally).forEach { it.discard() }
			report(helper, "duelo", "${"%.1f".format(seconds)} s desde el primer golpe; $winner")
		}
	}

	/** Llano de piedra en toda la estructura, sin muros: devuelve la esquina (y = suelo). */
	private fun flat(helper: GameTestHelper): BlockPos {
		val origin = helper.absolutePos(BlockPos(0, 0, 0))
		for (x in 0..22) for (z in 0..10) {
			helper.level.setBlock(origin.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 2)
			for (y in 1..6) helper.level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2)
		}
		return origin
	}

	/** Una línea por prueba superada, con los ticks: así se pueden sacar tiempos de los registros. */
	private fun report(helper: GameTestHelper, name: String, detail: String) =
		Bastion.LOGGER.info("[bastion] Prueba $name: bien en ${helper.tick} ticks (${"%.1f".format(helper.tick / 20.0)} s); $detail")

	private fun scene(helper: GameTestHelper) = AssaultScene(helper.level, helper.absolutePos(BlockPos(1, 0, 1)), Direction.EAST)

	/** Sin trampas: ningún salto de más de 1,5 bloques entre un tick y el siguiente. */
	private fun watchNoTeleport(helper: GameTestHelper, invader: Invader) {
		var last = invader.position()
		helper.onEachTick {
			val now = invader.position()
			if (now.distanceToSqr(last) > 1.5 * 1.5) helper.fail("El invasor ha saltado de $last a $now en un tick")
			last = now
		}
	}

	companion object {
		const val ARENA = "bastion-gametest:arena"
	}
}
