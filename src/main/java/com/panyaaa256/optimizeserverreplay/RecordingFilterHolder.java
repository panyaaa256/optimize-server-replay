package com.panyaaa256.optimizeserverreplay;

import com.panyaaa256.optimizeserverreplay.config.FilterConfig;
import org.jspecify.annotations.Nullable;

/**
 * Implemented by {@code ReplayRecorderMixin}, so that code outside of the recorder
 * can reach the {@link RecordingFilter} of a recording.
 */
public interface RecordingFilterHolder {
	void osr$flushBlockUpdates();

	/**
	 * @return the settings of the recording, or null when the mod does not apply to it
	 */
	@Nullable FilterConfig osr$config();

	/**
	 * Runs the action without recording the packets it sends, like {@code ReplayRecorder.ignore}
	 * does, which takes a Kotlin function.
	 */
	void osr$withoutRecording(Runnable action);
}
