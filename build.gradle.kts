import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Basado en la plantilla oficial de Fabric (https://fabricmc.net/develop/template/) para 26.3 en Kotlin.
// Desde la 26.1 el juego no está ofuscado: plugin "fabric-loom" sin remapeo e "implementation" en lugar de
// "modImplementation".

plugins {
	id("net.fabricmc.fabric-loom")
	id("org.jetbrains.kotlin.jvm")
}

fun prop(name: String): String = providers.gradleProperty(name).get()

repositories {
	// GeckoLib (modelos y animaciones)
	// Mods de Modrinth (Spark, para medir)
	maven {
		name = "Modrinth"
		url = uri("https://api.modrinth.com/maven")
		content { includeGroup("maven.modrinth") }
	}
	maven {
		name = "GeckoLib"
		url = uri("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
		content { includeGroup("com.geckolib") }
	}
}

loom {
	// Código de cliente (dibujo, teclas) separado del común: así el servidor nunca carga clases de cliente.
	splitEnvironmentSourceSets()

	mods {
		register("bastion") {
			sourceSet(sourceSets.main.get())
			sourceSet(sourceSets.getByName("client"))
		}
	}
}

fabricApi {
	// GameTest: pruebas dentro del juego, en su propio source set (src/gametest) con su fabric.mod.json.
	// Tarea: runGameTest (servidor sin pantalla, acelerado). Para verlas en vivo, runClientPruebas (más abajo).
	configureTests {
		createSourceSet = true
		modId = "bastion-gametest"
		enableGameTests = true
		enableClientGameTests = false
		// Acepta el EULA de Minecraft para el servidor de pruebas: https://aka.ms/MinecraftEULA
		eula = true
	}
}

// Va después de `fabricApi`, que es quien crea el source set «gametest».
loom {
	runs {
		// El juego con las GameTest cargadas, para verlas en vivo: `./gradlew runClientPruebas`, entrar en un mundo
		// y `/test run bastion-gametest:<prueba>` (o `/test runall`).
		register("clientPruebas") {
			client()
			displayName = "Minecraft Client (con pruebas)"
			sourceSet = "gametest"
		}
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${prop("minecraft_version")}")
	implementation("net.fabricmc:fabric-loader:${prop("loader_version")}")
	implementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_api_version")}")
	implementation("net.fabricmc:fabric-language-kotlin:${prop("fabric_kotlin_version")}")
	implementation("com.geckolib:geckolib-fabric-${prop("minecraft_version")}:${prop("geckolib_version")}")

	// Solo para desarrollar y medir (no son dependencias del mod): Spark (perfilador, /spark profiler), Lithium
	// (servidor: movimiento de criaturas, chunks) y Sodium (dibujo, FPS). Lithium y Sodium, como jugará Limbers.
	localRuntime("maven.modrinth:spark:${prop("spark_version")}")
	localRuntime("maven.modrinth:lithium:${prop("lithium_version")}")
	localRuntime("maven.modrinth:sodium:${prop("sodium_version")}")

	// Pruebas de lógica pura (sin Minecraft): src/test/kotlin
	testImplementation(platform("org.junit:junit-bom:${prop("junit_version")}"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
	val version = project.version
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.test {
	useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_25
	}
}

java {
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}
