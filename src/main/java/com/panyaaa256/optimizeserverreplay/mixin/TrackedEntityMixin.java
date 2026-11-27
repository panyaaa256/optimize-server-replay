package com.panyaaa256.optimizeserverreplay.mixin;

import com.panyaaa256.optimizeserverreplay.tracking.RecordableCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin implements RecordableCache {
	@Unique
	private Object osr$cachedLevel;
	@Unique
	private long osr$cachedChunk;
	@Unique
	private int osr$cachedGeneration;

	@Override
	public boolean osr$isUpToDate(Object level, long chunk, int generation) {
		return this.osr$cachedLevel == level && this.osr$cachedChunk == chunk && this.osr$cachedGeneration == generation;
	}

	@Override
	public void osr$markUpToDate(Object level, long chunk, int generation) {
		this.osr$cachedLevel = level;
		this.osr$cachedChunk = chunk;
		this.osr$cachedGeneration = generation;
	}
}
