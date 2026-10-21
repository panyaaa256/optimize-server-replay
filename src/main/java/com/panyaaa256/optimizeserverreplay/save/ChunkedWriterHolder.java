package com.panyaaa256.optimizeserverreplay.save;

import org.jspecify.annotations.Nullable;

/**
 * Implemented by {@code FlashbackChunkedWriterMixin}.
 */
public interface ChunkedWriterHolder {
	void osr$skipChunkCacheSync();

	void osr$zipWhileRecording(int level);

	@Nullable StreamingZip osr$streamingZip();
}
