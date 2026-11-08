package dev.limbers.bastion.core.unit

/** El bando decide quiénes son sus enemigos, de qué daño se libra y qué órdenes obedece. */
enum class Side { ALLY, INVADER }

/**
 * Cómo pelea.
 * - `MELEE`: cuerpo a cuerpo. Los invasores, además, pican y ponen escaleras.
 * - `CASTER`: lanza proyectiles. El aliado dispara desde su puesto; el invasor ataca el muro hasta la brecha.
 * - `ARCHER`: dispara flechas a quien ve. No toca el muro.
 */
enum class Role { MELEE, CASTER, ARCHER }

/** Ataque a distancia: segundos entre disparos (de `minInterval` a `maxInterval`), alcance y daño de cada flecha. */
data class Ranged(val minInterval: Double, val maxInterval: Double, val radius: Double, val damage: Double)

/**
 * Ficha de una unidad: qué es, no cómo se comporta (eso lo pone su rol en el mod).
 *
 * @property id el de la entidad (`bastion:<id>`), el de sus skins (`<id>_<n>.png`) y el de su nombre en los idiomas.
 * @property damage golpe cuerpo a cuerpo **con el arma incluida**. El juego suma a cualquier criatura lo que da el
 *   arma que lleva en la mano (el hacha de hierro, 8), así que el mod le pone de base `damage` menos eso.
 * @property weapon lo que lleva en la mano derecha, un objeto normal del juego (`minecraft:iron_axe`).
 * @property skins cuántas skins tiene; cada unidad sale con una al azar para que un ejército no sean clones.
 * @property knockbackResistance de 0 a 1: cuánto aguanta los empujones.
 */
data class UnitDef(
	val id: String,
	val side: Side,
	val role: Role,
	val health: Double,
	val damage: Double,
	val speed: Double,
	val weapon: String,
	val skins: Int,
	val knockbackResistance: Double = 0.0,
	val ranged: Ranged? = null,
) {
	init {
		require(id.matches(Regex("[a-z0-9_]+"))) { "$id: el id solo puede llevar minúsculas, números y _" }
		require(health > 0 && damage >= 0 && speed > 0) { "$id: vida, golpe y velocidad deben ser positivos" }
		require(skins >= 1) { "$id: necesita al menos una skin" }
		require(knockbackResistance in 0.0..1.0) { "$id: la resistencia al empuje va de 0 a 1" }
		require((role == Role.ARCHER) == (ranged != null)) { "$id: solo los arqueros llevan datos de disparo" }
	}
}

/**
 * Las unidades de Bastion, con las cifras de Bedrock (`../Bastion/scripts/units/catalog.ts`, v0.1.55), que eligió
 * Limbers. Añadir una unidad: una ficha aquí, sus skins en `assets/bastion/textures/entity/` y su nombre en los
 * idiomas; si su rol ya existe en el mod, se registra en `BastionEntities`.
 */
object Units {
	val ALLY_SOLDIER = UnitDef("ally_soldier", Side.ALLY, Role.MELEE, health = 60.0, damage = 10.0, speed = 0.29, weapon = "minecraft:iron_sword", skins = 4)

	/** El tanque de la brecha (Limbers, 2026-11-06): más vida y golpe, más lento, y no lo empujan. */
	val ALLY_WARRIOR = UnitDef("ally_warrior", Side.ALLY, Role.MELEE, health = 80.0, damage = 13.0, speed = 0.26, weapon = "minecraft:iron_axe", skins = 4, knockbackResistance = 1.0)

	/** Pirómano: bola de fuego como la del Ghast, desde su puesto. */
	val ALLY_PYROMANCER = UnitDef("ally_pyromancer", Side.ALLY, Role.CASTER, health = 30.0, damage = 2.0, speed = 0.24, weapon = "minecraft:blaze_rod", skins = 4)

	/** Cifras de Limbers (2026-11-06): flecha de 5, una cada 1,5-2 s, a 24 bloques. */
	val ALLY_ARCHER = UnitDef("ally_archer", Side.ALLY, Role.ARCHER, health = 30.0, damage = 3.0, speed = 0.25, weapon = "minecraft:bow", skins = 4, ranged = Ranged(1.5, 2.0, 24.0, 5.0))

	val INVADER_SOLDIER = UnitDef("invader_soldier", Side.INVADER, Role.MELEE, health = 40.0, damage = 9.0, speed = 0.28, weapon = "minecraft:iron_axe", skins = 5)

	/** Ataca el muro hasta la brecha con un proyectil arcano que explota. */
	val INVADER_WIZARD = UnitDef("invader_wizard", Side.INVADER, Role.CASTER, health = 24.0, damage = 2.0, speed = 0.24, weapon = "minecraft:breeze_rod", skins = 4)

	/** Cifras de Limbers (2026-11-06): flecha de 4, una cada 2-3 s, a 20 bloques. Nunca apunta al muro. */
	val INVADER_ARCHER = UnitDef("invader_archer", Side.INVADER, Role.ARCHER, health = 24.0, damage = 3.0, speed = 0.26, weapon = "minecraft:bow", skins = 1, ranged = Ranged(2.0, 3.0, 20.0, 4.0))

	val ALL: List<UnitDef> = listOf(ALLY_SOLDIER, ALLY_WARRIOR, ALLY_PYROMANCER, ALLY_ARCHER, INVADER_SOLDIER, INVADER_WIZARD, INVADER_ARCHER)

	/** La ficha con ese id, o nula. */
	fun byId(id: String): UnitDef? = ALL.firstOrNull { it.id == id }
}
