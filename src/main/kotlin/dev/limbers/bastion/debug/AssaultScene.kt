package dev.limbers.bastion.debug

import dev.limbers.bastion.Bastion
import dev.limbers.bastion.unit.BastionEntities
import dev.limbers.bastion.unit.Defender
import dev.limbers.bastion.unit.Invader
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

/**
 * Escena A de la prueba inicial: el asalto a un muro. La misma escena la monta el comando `/bastion escena` (para
 * verla en el juego) y las GameTest (para medirla).
 *
 * Coordenadas locales: `f` hacia el muro, `y` hacia arriba, `s` a lo ancho. Un patio cerrado por muros de piedra de
 * 8 de alto, para que no se pueda rodear:
 *
 * ```
 *  s=9  ███████████████████████████████
 *       █ I . . . . . . . . . . . . . ██▒ . . D
 *  s=4  █ I . . . . . . . . . . . . . TT  . . D   I = invasor (f=1), T = tablones (2 de ancho y 2 de alto)
 *       █ . . . . . . . . . . . . . . ██  . . .   D = destino al otro lado (f=20)
 *  s=-1 ███████████████████████████████
 *        f=0                          f=16-17 (muro de la puerta, 2 de grueso; defensor arriba en f=17)
 * ```
 */
class AssaultScene(private val level: ServerLevel, private val origin: BlockPos, private val facing: Direction) {
	enum class Variant(val label: String) {
		/** Con 16 escaleras y el defensor arriba: debe subir y atacarle. */
		LADDER("escalera"),

		/** Sin escaleras y sin defensor: debe picar los tablones y pasar al otro lado. */
		DIG("picar"),
	}

	class Actors(val invader: Invader, val defender: Defender?, val destination: Vec3?)

	private val right: Direction = facing.clockWise

	fun pos(f: Int, y: Int, s: Int): BlockPos = origin.relative(facing, f).relative(right, s).above(y)

	/** El centro del suelo de la casilla local (f, y, s), en coordenadas del mundo. */
	fun center(f: Int, y: Int, s: Int): Vec3 = Vec3.atBottomCenterOf(pos(f, y, s))

	/** Altura en bloques de cada muro. */
	val wallHeight = 8

	/** Primera fila (f) del muro de la puerta. */
	val wallF = 16

	fun build(variant: Variant): Actors {
		val stone = Blocks.STONE.defaultBlockState()
		val bricks = Blocks.STONE_BRICKS.defaultBlockState()
		val planks = Blocks.OAK_PLANKS.defaultBlockState()
		val air = Blocks.AIR.defaultBlockState()

		for (f in F_MIN..F_MAX) for (s in S_MIN..S_MAX) {
			set(f, 0, s, stone)
			for (y in 1..HEIGHT) set(f, y, s, air)
		}
		// Muros del patio: detrás y a los lados, hasta el muro de la puerta.
		for (y in 1..wallHeight) {
			for (s in S_MIN..S_MAX) set(-1, y, s, bricks)
			for (f in -1..wallF + 1) {
				set(f, y, S_MIN, bricks)
				set(f, y, S_MAX, bricks)
			}
			// Muro de la puerta, 2 de grueso.
			for (s in S_MIN..S_MAX) for (f in wallF..wallF + 1) set(f, y, s, bricks)
		}
		// La franja de tablones en la base del muro: 2 de ancho y 2 de alto, de lado a lado del grosor.
		for (s in 4..5) for (y in 1..2) for (f in wallF..wallF + 1) set(f, y, s, planks)

		val invader = BastionEntities.INVADER_SOLDIER.type.spawn(level, pos(1, 1, 4), EntitySpawnReason.COMMAND)
			?: error("No se pudo crear el invasor")
		invader.snapTo(center(1, 1, 4), facing.toYRot(), 0f)
		var defender: Defender? = null
		var destination: Vec3? = null
		when (variant) {
			Variant.LADDER -> {
				invader.ladders = 16
				defender = BastionEntities.ALLY_SOLDIER.type.spawn(level, pos(wallF + 1, wallHeight + 1, 4), EntitySpawnReason.COMMAND)
				defender?.snapTo(center(wallF + 1, wallHeight + 1, 4), facing.opposite.toYRot(), 0f)
				defender?.post = pos(wallF + 1, wallHeight + 1, 4)
			}
			Variant.DIG -> {
				destination = center(20, 1, 4)
				invader.destination = destination
			}
		}
		Bastion.LOGGER.info("[bastion] Escena A (${variant.label}) en ${origin.toShortString()} mirando al ${facing.serializedName}")
		return Actors(invader, defender, destination)
	}

	private fun set(f: Int, y: Int, s: Int, state: BlockState) {
		level.setBlock(pos(f, y, s), state, 2)
	}

	companion object {
		const val F_MIN = -1
		const val F_MAX = 21
		const val S_MIN = -1
		const val S_MAX = 9
		const val HEIGHT = 12

		/** Tamaño de la estructura de GameTest que la contiene (en f, y, s). */
		const val SIZE_F = F_MAX - F_MIN + 1
		const val SIZE_S = S_MAX - S_MIN + 1
	}
}
