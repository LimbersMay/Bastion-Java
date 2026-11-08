package dev.limbers.bastion.core.map

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Un mapa que no se pudo leer o que no pasa las comprobaciones, con todos sus problemas. */
class MapException(val mapId: String, val problems: List<String>) :
	RuntimeException("Mapa «$mapId» no válido:\n" + problems.joinToString("\n") { "  - $it" })

/**
 * Lee los mapas JSON. Admite comentarios (`//` y `/* */`) para explicar de dónde sale cada punto, como en los
 * mapas de Bedrock, y comas al final. Un campo que no existe es un error: así una errata no se pierde en silencio.
 */
object MapFormat {
	@OptIn(ExperimentalSerializationApi::class)
	private val json = Json {
		allowComments = true
		allowTrailingComma = true
		ignoreUnknownKeys = false
	}

	/** Lee y comprueba un mapa. `source` es el nombre del archivo, para los mensajes. Falla con `MapException`. */
	fun parse(text: String, source: String): MapDef {
		val map = try {
			json.decodeFromString(MapDef.serializer(), text)
		} catch (e: SerializationException) {
			throw MapException(source, listOf(e.message ?: e.toString()))
		} catch (e: IllegalArgumentException) {
			throw MapException(source, listOf(e.message ?: e.toString()))
		}
		val problems = MapValidator.validate(map)
		if (problems.isNotEmpty()) throw MapException(map.id, problems)
		if ("${map.id}.json" != source.substringAfterLast('/')) throw MapException(map.id, listOf("el archivo debe llamarse ${map.id}.json, no $source"))
		return map
	}
}
