pluginManagement {
	repositories {
		maven {
			name = "Fabric"
			url = uri("https://maven.fabricmc.net/")
		}
		mavenCentral()
		gradlePluginPortal()
	}

	plugins {
		id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version")
		id("org.jetbrains.kotlin.jvm") version providers.gradleProperty("kotlin_version")
		id("org.jetbrains.kotlin.plugin.serialization") version providers.gradleProperty("kotlin_version")
	}
}

// Debe coincidir con el id del mod
rootProject.name = "bastion"

// Lógica pura, sin Minecraft (rutas, campo de flujo, grupos, oleadas...). Ver docs/ARQUITECTURA.md.
include("core")
