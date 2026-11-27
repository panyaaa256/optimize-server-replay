package com.panyaaa256.optimizeserverreplay.save;

import org.jspecify.annotations.Nullable;

/**
 * Implemented by {@code FlashbackChunkedWriterMixin}.
 */
public interface ChunkedWriterHolder {
	void osr$skipChunkCacheSync();

	void osr$zipWhileRecording(int level);

	@Nullable StreamingZip osr$streamingZip();

	/**
	 * Keeps the chunk cache file open between the chunks that are added to it.
	 */
	void osr$keepChunkCacheOpen();

	/**
	 * Writes the replay chunk that is being recorded to its file as it grows, rather than keeping
	 * all of it in memory until it ends.
	 */
	void osr$streamReplayChunks();

	/**
	 * Called once saving the recording failed, when Arcade does not close the writer: frees its
	 * memory and closes its files, and leaves the recording folder as it is.
	 */
	void osr$releaseAfterFailedSave();
}
