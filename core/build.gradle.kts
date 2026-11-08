import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Lógica pura de Bastion: Kotlin sin Minecraft. Lo que va aquí se prueba con JUnit en segundos y no puede usar
// código del juego (el compilador no lo ve). Va dentro del jar del mod (`include` en el build principal).
// Qué va aquí y qué no: docs/ARQUITECTURA.md, «Estructura del código».

plugins {
	id("org.jetbrains.kotlin.jvm")
	// Leer los mapas JSON (`core.map`).
	id("org.jetbrains.kotlin.plugin.serialization")
}

fun prop(name: String): String = providers.gradleProperty(name).get()

repositories {
	mavenCentral()
}

dependencies {
	// La librería de Kotlin la añade el plugin; en el juego la pone fabric-language-kotlin (misma versión).
	// kotlinx.serialization también viene dentro de fabric-language-kotlin: aquí solo para compilar y probar.
	compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:${prop("kotlinx_serialization_version")}")
	testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${prop("kotlinx_serialization_version")}")
	testImplementation(platform("org.junit:junit-bom:${prop("junit_version")}"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
	useJUnitPlatform()
	// Las pruebas leen los mapas reales del mod (src/main/resources/data/bastion/maps) para comprobarlos.
	systemProperty("bastion.maps", rootProject.file("src/main/resources/data/bastion/maps").absolutePath)
	inputs.dir(rootProject.file("src/main/resources/data/bastion/maps"))
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
	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}
