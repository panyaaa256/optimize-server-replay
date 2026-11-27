package com.panyaaa256.optimizeserverreplay.tracking;

import com.panyaaa256.optimizeserverreplay.mixin.accessor.ChunkMapAccessor;
import com.panyaaa256.optimizeserverreplay.mixin.accessor.ServerLevelInvoker;
import com.panyaaa256.optimizeserverreplay.mixin.accessor.TrackedEntityAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the entities that the level tracks in one chunk, by looking only at the entities near that
 * chunk, where Arcade goes through every entity of the level.
 */
public final class ChunkEntities {
	// Wider than the world, so that no entity of the chunk is left out for being too high or low.
	private static final double HEIGHT = 1.0E8;

	private ChunkEntities() {
	}

	/**
	 * @return the {@code ChunkMap.TrackedEntity} of every tracked entity whose position is in the
	 *     chunk, the same ones Arcade finds, though not always in the same order
	 */
	public static List<Object> trackedIn(ServerLevel level, ChunkPos pos) {
		Int2ObjectMap<?> tracked = ((ChunkMapAccessor) level.getChunkSource().chunkMap).osr$entityMap();
		if (tracked.isEmpty()) {
			return List.of();
		}

		// A little wider than the chunk, so that an entity without size on the edge of the chunk
		// is found too. Only those whose position is in the chunk are kept.
		AABB box = new AABB(
			pos.getMinBlockX() - 1.0, -HEIGHT, pos.getMinBlockZ() - 1.0,
			pos.getMaxBlockX() + 2.0, HEIGHT, pos.getMaxBlockZ() + 2.0
		);
		List<Object> result = new ArrayList<>();
		((ServerLevelInvoker) level).osr$getEntities().get(box, entity -> {
			if (entity.chunkPosition().equals(pos)) {
				Object trackedEntity = tracked.get(entity.getId());
				if (trackedEntity != null && ((TrackedEntityAccessor) trackedEntity).osr$entity() == entity) {
					result.add(trackedEntity);
				}
			}
		});
		return result;
	}
}
