package dev.limbers.bastion.test

import dev.limbers.bastion.Bastion
import dev.limbers.bastion.entity.Invader
import dev.limbers.bastion.scene.AssaultScene
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.phys.Vec3

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
