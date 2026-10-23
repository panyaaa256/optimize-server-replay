package com.panyaaa256.optimizeserverreplay.idle;

import com.panyaaa256.optimizeserverreplay.OptimizeServerReplay;
import net.casual.arcade.replay.recorder.chunk.ReplayChunkRecorder;

/**
 * Pauses a recording when nothing has happened in its area for a while, and resumes it when
 * something does. {@link IdleActivity} decides what counts.
 *
 * <p>Arcade pauses and resumes the recording for reasons of its own too (no players in the area,
 * no chunks loaded, an empty server). While we hold the recording paused, its resumes are
 * cancelled, and we keep track of whether it would have the recording run, so that once something
 * happens we resume only when it would.
 *
 * <p>Everything here runs on the server thread.
 */
public final class IdlePause {
	private final ReplayChunkRecorder recorder;
	private final int idleTicksToPause;

	private int idleTicks = 0;
	// Whether we have paused the recording, and it has not been resumed since.
	private boolean paused = false;
	// Whether something has happened since we paused the recording.
	private boolean active = false;
	// Whether Arcade, going by its last call, would have the recording run.
	private boolean arcadeRuns = false;
	// Set while we pause or resume the recording ourselves.
	private boolean switching = false;

	public IdlePause(ReplayChunkRecorder recorder, int idleTicksToPause) {
		this.recorder = recorder;
		this.idleTicksToPause = idleTicksToPause;
	}

	public void onActivity() {
		this.idleTicks = 0;
		if (this.paused) {
			this.active = true;
		}
	}

	/**
	 * Resuming takes a snapshot, which records packets, so it is done here rather than while
	 * a packet is being recorded.
	 */
	public void tick() {
		if (this.paused) {
			if (this.active) {
				this.paused = false;
				this.active = false;
				// Otherwise the recording stays paused until Arcade resumes it.
				if (this.arcadeRuns && this.switchTo(false)) {
					OptimizeServerReplay.LOGGER.info("Resumed recording {}: a block action or a player swing in its area", this.recorder.getName());
					PauseNotifier.resumed(this.recorder);
				}
			}
			return;
		}

		if (this.recorder.getPaused()) {
			this.idleTicks = 0;
			return;
		}
		this.idleTicks++;
		if (this.idleTicks >= this.idleTicksToPause) {
			this.idleTicks = 0;
			if (this.switchTo(true)) {
				this.paused = true;
				this.arcadeRuns = true;
				OptimizeServerReplay.LOGGER.info(
					"Paused recording {}: no block action or player swing in its area for {} seconds",
					this.recorder.getName(),
					this.idleTicksToPause / 20
				);
				PauseNotifier.paused(this.recorder);
			}
		}
	}

	public void onPause() {
		if (this.paused && !this.switching) {
			this.arcadeRuns = false;
		}
	}

	/**
	 * @return whether to cancel the resume
	 */
	public boolean onResume() {
		if (!this.paused || this.switching) {
			return false;
		}
		this.arcadeRuns = true;
		return true;
	}

	private boolean switchTo(boolean pause) {
		this.switching = true;
		try {
			return pause ? this.recorder.pause(true) : this.recorder.resume();
		} finally {
			this.switching = false;
		}
	}
}
