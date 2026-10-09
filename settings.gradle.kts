pluginManagement {
	repositories {
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
		maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	id("dev.kikugie.stonecutter") version "0.9.8"
	// Applies fabric-loom-remap to the obfuscated versions and fabric-loom to 26.1 and later.
	id("dev.kikugie.loom-back-compat") version "0.4.2"
}

stonecutter {
	create(rootProject) {
		// One node per minor version: each jar covers the patch releases of its minor version.
		versions("1.21.11", "26.1", "26.2", "26.3")
		vcsVersion = "26.3"
	}
}

rootProject.name = "optimize-server-replay"
