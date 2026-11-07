package dev.limbers.bastion.test

import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks

/**
 * Prueba vacía de la prueba inicial (paso 3): comprueba que el bucle de GameTest funciona antes de escribir IA.
 * Se ejecuta con `./gradlew runGameTest`.
 */
class BastionGameTest {
	@GameTest
	fun elMundoExiste(helper: GameTestHelper) {
		val pos = BlockPos(1, 1, 1)
		helper.setBlock(pos, Blocks.STONE)
		helper.assertBlockPresent(Blocks.STONE, pos)
		helper.succeed()
	}
}
