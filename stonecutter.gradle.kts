plugins {
	id("dev.kikugie.stonecutter")
}

stonecutter active "26.3"

stonecutter parameters {
	// The source is written for the newest version. When a condition is false,
	// Stonecutter applies the replacement the other way, back to the older name.
	replacements {
		// ClientboundSetEntityMotionPacket became a record.
		string(current.parsed >= "26.1") {
			replace("motion.getId()", "motion.id()")
		}
		// The entity types moved to their own class.
		string(current.parsed >= "26.2") {
			replace("EntityType.", "EntityTypes.")
		}
		// ClientboundRemoveEntitiesPacket and ClientboundLevelChunkWithLightPacket became records.
		string(current.parsed >= "26.3") {
			replace(".getEntityIds()", ".entityIds()")
			replace("chunk.getX(), chunk.getZ()", "chunk.x(), chunk.z()")
		}
	}
}
