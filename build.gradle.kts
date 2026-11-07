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
	// Tareas: runGameTest (servidor, sin pantalla) y runClientGameTest (cliente).
	configureTests {
		createSourceSet = true
		modId = "bastion-gametest"
		enableGameTests = true
		enableClientGameTests = false
		// Acepta el EULA de Minecraft para el servidor de pruebas: https://aka.ms/MinecraftEULA
		eula = true
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${prop("minecraft_version")}")
	implementation("net.fabricmc:fabric-loader:${prop("loader_version")}")
	implementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_api_version")}")
	implementation("net.fabricmc:fabric-language-kotlin:${prop("fabric_kotlin_version")}")
	implementation("com.geckolib:geckolib-fabric-${prop("minecraft_version")}:${prop("geckolib_version")}")

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
