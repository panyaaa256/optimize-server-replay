package com.panyaaa256.optimizeserverreplay;

/**
 * Implemented by {@code ReplayRecorderMixin}, so that code outside of the recorder
 * can reach the {@link RecordingFilter} of a recording.
 */
public interface RecordingFilterHolder {
	/**
	 * Writes the batched block updates of this recording, if it has any.
	 * Does nothing for recordings that the filters do not apply to.
	 */
	void osr$flushBlockUpdates();
}
