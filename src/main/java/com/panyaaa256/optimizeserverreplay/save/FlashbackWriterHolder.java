package com.panyaaa256.optimizeserverreplay.save;

/**
 * Implemented by {@code FlashbackWriterMixin}, so that the recorder can change how its writer
 * saves the recording.
 */
public interface FlashbackWriterHolder {
	void osr$skipChunkCacheSync();
}
