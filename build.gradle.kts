plugins {
	id("dev.kikugie.loom-back-compat")
}

version = "${sc.properties.get<String>("mod.version")}+${sc.current.version}"
base.archivesName = sc.properties.get<String>("mod.id")

val requiredJava: JavaVersion = when {
	sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
	else -> JavaVersion.VERSION_21
}

repositories {
	exclusiveContent {
		forRepository {
			maven("https://maven.supersanta.me/snapshots") { name = "Arcade" }
		}
		filter { includeGroup("net.casualchampionships") }
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${sc.current.version}")
	loomx.applyMojangMappings()
	modImplementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")

	// The replay library that Server Replay bundles. We only mix into it, so it is needed
	// at compile time only: at runtime the copy inside the Server Replay jar is used.
	modCompileOnly("net.casualchampionships:arcade-replay:${sc.properties.get<String>("deps.arcade")}") {
		isTransitive = false
	}
}

java {
	sourceCompatibility = requiredJava
	targetCompatibility = requiredJava
}

tasks {
	withType<JavaCompile>().configureEach {
		options.release = requiredJava.majorVersion.toInt()
	}

	processResources {
		val props = mapOf(
			"version" to project.version.toString(),
			"minecraft" to sc.properties.get<String>("mod.mc_compat"),
			"java" to requiredJava.majorVersion,
			"server_replay" to sc.properties.get<String>("mod.server_replay"),
		)
		inputs.properties(props)
		filesMatching("fabric.mod.json") { expand(props) }
	}

	withType<Jar>().configureEach {
		val projectName = sc.properties.get<String>("mod.id")
		inputs.property("projectName", projectName)
		from(rootProject.file("LICENSE")) {
			rename { "${it}_$projectName" }
		}
	}
}
