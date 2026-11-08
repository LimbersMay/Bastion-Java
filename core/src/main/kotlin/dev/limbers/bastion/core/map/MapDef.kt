package dev.limbers.bastion.core.map

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.floor

/**
 * Un mapa de Bastion: los puntos de un castillo en su mundo (convertido de Bedrock con Chunker, que mantiene las
 * coordenadas). Un mapa = un archivo `data/bastion/maps/<id>.json`; `MapFormat` lo lee y `MapValidator` lo
 * comprueba. Las coordenadas son absolutas del mundo.
 *
 * Viene de `../Bastion/scripts/maps/types.ts`, **sin los parches de Bedrock**: rutas de subida y de regreso del
 * agua (en Java las unidades encuentran el camino), bloqueos invisibles, muros retirados y áreas cargadas por radio
 * (aquí, `area`). Si en Java vuelve a hacer falta alguno, se añade entonces.
 */
@Serializable
data class MapDef(
	val id: String,
	val name: String,
	val description: String = "",
	/** Dimensión del juego, por defecto el mundo normal. */
	val dimension: String = "minecraft:overworld",
	/** El campo de batalla entero: la subida, el castillo y sus alrededores. Todo punto del mapa debe caer dentro. */
	val area: BlockBox,
	/** Los accesos del castillo (al menos uno), cada uno con su muro, su meta y sus tropas de brecha. */
	val gates: List<Gate>,
	/** Dónde aparecen los jugadores al empezar, en rotación. */
	val playerSpawns: List<Pos>,
	/** Aliados que se colocan al empezar. */
	val allies: List<UnitSpawn> = emptyList(),
	/** Puntos exteriores por donde entran las oleadas. */
	val invaderSpawns: List<Pos>,
	/** Otros frentes: parte de los soldados sale por aquí y se abre paso picando. Requiere `digZones`. */
	val flankSpawns: List<Pos> = emptyList(),
	/** Donde los invasores pueden picar (regla de Limbers: solo en la zona del castillo, ni puentes ni orillas). */
	val digZones: List<BlockBox> = emptyList(),
	/** Puestos de defensa: cada aliado ocupa uno con la orden de retirada. */
	val defensePosts: List<DefensePost> = emptyList(),
	/** El patio: quien no cabe en un puesto espera aquí. */
	val rallyPoints: List<Pos> = emptyList(),
	/** La torre: el último reducto del repliegue. */
	val keep: Pos? = null,
	/** Con la orden de ataque, adónde marchan los soldados si no alcanzan a nadie: junto a la puerta, por dentro. */
	val sortiePoint: Pos? = null,
	val reinforcements: Reinforcements? = null,
	val waves: List<Wave>,
	/** Vidas del equipo: cada muerte de un jugador gasta una. */
	val lives: Int,
	val countdownSeconds: Double,
	val intermissionSeconds: Double,
) {
	/**
	 * Los puntos donde se pone de pie una unidad o un jugador, con su nombre en el mapa (`allies[3]`). Sirven para
	 * comprobar el mapa sobre el mundo: en cada uno debe caber alguien de pie.
	 */
	fun standingPoints(): List<Pair<String, Pos>> = buildList {
		fun add(label: String, list: List<Pos>) = list.forEachIndexed { i, p -> add("$label[$i]" to p) }
		gates.forEachIndexed { i, g ->
			add("gates[$i].outside" to g.outside)
			add("gates[$i].inside" to g.inside)
			add("gates[$i].breachSpawns", g.breachSpawns)
		}
		add("playerSpawns", playerSpawns)
		allies.forEachIndexed { i, a -> add("allies[$i] (${a.unit})" to a.at) }
		add("invaderSpawns", invaderSpawns)
		add("flankSpawns", flankSpawns)
		defensePosts.forEachIndexed { i, p -> add("defensePosts[$i] (${p.kind.name.lowercase()})" to p.at) }
		add("rallyPoints", rallyPoints)
		keep?.let { add("keep" to it) }
		sortiePoint?.let { add("sortiePoint" to it) }
		reinforcements?.let { add("reinforcements.spawns", it.spawns) }
	}
}

/** Un punto del mundo. En el JSON, `[x, y, z]`. */
@Serializable(with = PosSerializer::class)
data class Pos(val x: Double, val y: Double, val z: Double) {
	/** El bloque que contiene el punto. */
	val blockX: Int get() = floor(x).toInt()
	val blockY: Int get() = floor(y).toInt()
	val blockZ: Int get() = floor(z).toInt()
}

/** Una caja de bloques, con las dos esquinas incluidas y en cualquier orden. */
@Serializable
data class BlockBox(val from: Pos, val to: Pos) {
	val minX: Int get() = minOf(from.blockX, to.blockX)
	val minY: Int get() = minOf(from.blockY, to.blockY)
	val minZ: Int get() = minOf(from.blockZ, to.blockZ)
	val maxX: Int get() = maxOf(from.blockX, to.blockX)
	val maxY: Int get() = maxOf(from.blockY, to.blockY)
	val maxZ: Int get() = maxOf(from.blockZ, to.blockZ)
	val volume: Long get() = (maxX - minX + 1).toLong() * (maxY - minY + 1) * (maxZ - minZ + 1)

	fun contains(p: Pos): Boolean = p.blockX in minX..maxX && p.blockY in minY..maxY && p.blockZ in minZ..maxZ
}

/** Un acceso al castillo: un muro que romper y lo que pasa cuando se abre. */
@Serializable
data class Gate(
	val id: String,
	/** Delante del muro, fuera, a nivel del suelo. */
	val outside: Pos,
	/** La meta de los invasores: justo detrás del muro, dentro. */
	val inside: Pos,
	/** Los tramos de muro que cierran el acceso. */
	val walls: List<Wall>,
	/** Puntos interiores donde aparecen las tropas de brecha cuando se abre. */
	val breachSpawns: List<Pos> = emptyList(),
	val breachTroops: List<UnitCount> = emptyList(),
)

/**
 * Tramo de muro: una caja de bloques de un material (`block`, un id de bloque sin estados). Se levanta al empezar,
 * solo sobre aire. Su grosor es su lado horizontal más corto.
 */
@Serializable
data class Wall(val from: Pos, val to: Pos, val block: String) {
	val box: BlockBox get() = BlockBox(from, to)
}

@Serializable
data class UnitCount(val unit: String, val count: Int)

@Serializable
data class UnitSpawn(val unit: String, val at: Pos)

@Serializable
enum class PostKind {
	/** Corta el paso de los invasores. */
	@SerialName("front") FRONT,

	/** Cubre desde un costado. */
	@SerialName("side") SIDE,

	/** En alto, para los que disparan. */
	@SerialName("high") HIGH,
}

/** Un puesto de defensa. `capacity`: cuántos caben (por defecto 3 en el frente y 2 en los demás). */
@Serializable
data class DefensePost(val at: Pos, val kind: PostKind, val capacity: Int? = null) {
	val slots: Int get() = capacity ?: if (kind == PostKind.FRONT) 3 else 2
}

/**
 * Aliados de refuerzo durante la oleada: cuando quedan menos de `minField` en el campo, cada `everySeconds` salen
 * hasta `batch` unidades (de `units`, en rotación) de una reserva de `reservePerWave`.
 */
@Serializable
data class Reinforcements(
	val spawns: List<Pos>,
	val units: List<String>,
	val minField: Int,
	val reservePerWave: Int,
	val batch: Int,
	val everySeconds: Double,
)

/** Una oleada: sus grupos salen por tandas de `batchSize`, con `batchIntervalTicks` entre tandas (20 = 1 s). */
@Serializable
data class Wave(val name: String = "", val groups: List<UnitCount>, val batchSize: Int, val batchIntervalTicks: Int)

/** `[x, y, z]` en el JSON, más cómodo de escribir a mano que `{"x": ..., "y": ..., "z": ...}`. */
object PosSerializer : KSerializer<Pos> {
	private val list = ListSerializer(Double.serializer())
	override val descriptor: SerialDescriptor = list.descriptor

	override fun serialize(encoder: Encoder, value: Pos) = list.serialize(encoder, listOf(value.x, value.y, value.z))

	override fun deserialize(decoder: Decoder): Pos {
		val v = list.deserialize(decoder)
		require(v.size == 3) { "un punto son 3 números [x, y, z], no ${v.size}" }
		return Pos(v[0], v[1], v[2])
	}
}
