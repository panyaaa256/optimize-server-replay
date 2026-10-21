package com.panyaaa256.optimizeserverreplay.save;

import org.jspecify.annotations.Nullable;

/**
 * Implemented by {@code FlashbackWriterMixin}, so that the recorder can change how its writer
 * saves the recording.
 */
public interface FlashbackWriterHolder {
	void osr$skipChunkCacheSync();

	void osr$zipWhileRecording(int level);

	@Nullable StreamingZip osr$streamingZip();
}
