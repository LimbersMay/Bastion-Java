package dev.limbers.bastion.map

import dev.limbers.bastion.Bastion
import dev.limbers.bastion.core.map.MapDef
import dev.limbers.bastion.core.map.MapException
import dev.limbers.bastion.core.map.MapFormat
import dev.limbers.bastion.core.map.Pos
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener

/**
 * Los mapas de Bastion: los archivos `data/<espacio>/maps/<id>.json` de los paquetes de datos (los del mod y los
 * que se añadan a un mundo). Se leen al arrancar el servidor y con `/reload`. Un mapa roto no tumba el servidor: se
 * avisa en el registro con todos sus problemas y se salta.
 */
object BastionMaps : ResourceManagerReloadListener {
	/** Los mapas leídos, por id. */
	var maps: Map<String, MapDef> = emptyMap()
		private set

	fun register() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Bastion.id("maps"), this)
	}

	override fun onResourceManagerReload(resourceManager: ResourceManager) {
		val loaded = LinkedHashMap<String, MapDef>()
		for ((id, resource) in resourceManager.listResources("maps") { it.path.endsWith(".json") }.toSortedMap()) {
			try {
				val map = resource.openAsReader().use { MapFormat.parse(it.readText(), id.path) }
				if (loaded.put(map.id, map) != null) Bastion.LOGGER.error("[bastion] Mapa «${map.id}» repetido; se queda el de $id")
			} catch (e: MapException) {
				Bastion.LOGGER.error("[bastion] $id: ${e.message}")
			} catch (e: Exception) {
				Bastion.LOGGER.error("[bastion] $id: no se pudo leer", e)
			}
		}
		maps = loaded
		Bastion.LOGGER.info("[bastion] Mapas: ${loaded.size} (${loaded.keys.joinToString()})")
	}

	/** Un punto del mapa, como posición de bloque del juego. */
	fun Pos.toBlockPos(): BlockPos = BlockPos(blockX, blockY, blockZ)

	/** Un punto del mapa que no está bien sobre el mundo: su nombre y qué le pasa. */
	data class Problem(val label: String, val at: Pos, val what: String)

	/**
	 * Comprueba el mapa sobre el mundo cargado: en cada punto donde se pone alguien de pie, que quepa (pies y cabeza
	 * sin bloque que choque) y que haya suelo debajo (o el punto esté justo sobre una losa o escalón). Sirve para
	 * saber si el mundo es el del mapa y si Chunker mantuvo las coordenadas. Carga los chunks que hagan falta.
	 */
	fun check(level: ServerLevel, map: MapDef): List<Problem> = map.standingPoints().mapNotNull { (label, p) ->
		val feet = p.toBlockPos()
		fun name(pos: BlockPos) = level.getBlockState(pos).block.name.string
		fun top(pos: BlockPos): Double {
			val shape = level.getBlockState(pos).getCollisionShape(level, pos)
			return if (shape.isEmpty) 0.0 else shape.max(Direction.Axis.Y)
		}
		val feetTop = top(feet)
		when {
			// Una losa, un escalón o una alfombra a los pies valen: se pone encima.
			feetTop > 0.5 -> Problem(label, p, "dentro de un bloque (${name(feet)})")
			top(feet.above()) > 0.0 -> Problem(label, p, "no cabe de pie: bloque en la cabeza (${name(feet.above())})")
			feetTop == 0.0 && top(feet.below()) == 0.0 -> Problem(label, p, "sin suelo debajo (${name(feet.below())})")
			else -> null
		}
	}
}
