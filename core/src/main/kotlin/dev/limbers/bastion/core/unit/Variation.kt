package dev.limbers.bastion.core.unit

import kotlin.math.roundToInt

/**
 * Variación entre unidades del mismo tipo (Limbers, 2026-11-06): cada una nace con un ±10 % de vida y cada golpe
 * varía otro ±10 %. Así dos unidades iguales no tienen el duelo decidido de antemano, y la media sigue siendo la de
 * la ficha. Como en Bedrock (`tools/units/generate.ts`), el margen se redondea a entero: las cifras pequeñas (un
 * golpe de 2 o 3) quedan fijas.
 */
object Variation {
	const val FRACTION = 0.1

	/** Margen de `value`: su 10 %, redondeado. */
	fun spread(value: Double): Int = (value * FRACTION).roundToInt()

	/** `value` ± su margen, repartido por igual. `unit` es un número al azar entre 0 y 1. */
	fun roll(value: Double, unit: Double): Double {
		require(unit in 0.0..1.0) { "unit debe ir de 0 a 1: $unit" }
		return value + (unit * 2 - 1) * spread(value)
	}
}
