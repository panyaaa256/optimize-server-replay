package com.panyaaa256.optimizeserverreplay;

/**
 * Implemented by {@code ReplayRecorderMixin}, so that code outside of the recorder
 * can reach the {@link RecordingFilter} of a recording.
 */
public interface RecordingFilterHolder {
	void osr$flushBlockUpdates();
}
