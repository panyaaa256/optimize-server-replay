package com.panyaaa256.optimizeserverreplay.snapshot;

import com.panyaaa256.optimizeserverreplay.mixin.accessor.ChunkMapAccessor;
import com.panyaaa256.optimizeserverreplay.mixin.accessor.TrackedEntityAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * The entities that a level tracks, by the chunk they are in.
 *
 * <p>For every chunk of a snapshot, Arcade goes through all the entities that the level tracks
 * to find the ones in that chunk, which takes chunks times entities steps. Sorting them into
 * their chunks once makes it one pass. The entities of a chunk keep the order that Arcade goes
 * through them in, so the packets are recorded in the same order.
 */
public final class TrackedEntities {
	// Only changed and read on the server thread.
	private static int changes = 0;

	private final Long2ObjectOpenHashMap<List<Entity>> byChunk = new Long2ObjectOpenHashMap<>();
	private final int changesWhenBuilt;
	private final int tick;

	private TrackedEntities(ServerLevel level) {
		this.changesWhenBuilt = changes;
		this.tick = level.getServer().getTickCount();
		for (Object tracked : ((ChunkMapAccessor) level.getChunkSource().chunkMap).osr$entityMap().values()) {
			Entity entity = ((TrackedEntityAccessor) tracked).osr$entity();
			this.byChunk.computeIfAbsent(entity.chunkPosition().toLong(), chunk -> new ArrayList<>()).add(entity);
		}
	}

	public static TrackedEntities of(ServerLevel level) {
		return new TrackedEntities(level);
	}

	/**
	 * Called when the level starts or stops tracking an entity.
	 */
	public static void onChanged() {
		changes++;
	}

	/**
	 * Entities only move while the level ticks, so the sorting holds until the level tracks
	 * another entity or stops tracking one, or the tick is over.
	 */
	public boolean isCurrent(ServerLevel level) {
		return this.changesWhenBuilt == changes && this.tick == level.getServer().getTickCount();
	}

	public List<Entity> in(long chunk) {
		return this.byChunk.getOrDefault(chunk, List.of());
	}
}
