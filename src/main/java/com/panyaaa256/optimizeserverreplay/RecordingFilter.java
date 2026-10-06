package com.panyaaa256.optimizeserverreplay;

import com.panyaaa256.optimizeserverreplay.block.BlockActionFilter;
import com.panyaaa256.optimizeserverreplay.block.BlockUpdateBuffer;
import com.panyaaa256.optimizeserverreplay.config.FilterConfig;
import com.panyaaa256.optimizeserverreplay.entity.EntityFilter;
import net.casual.arcade.replay.recorder.chunk.ReplayChunkRecorder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;

/**
 * The filters of one Flashback chunk recording.
 *
 * <p>Holds the config the recording started with and the state the filters keep for it.
 * {@code ReplayRecorderMixin} creates one for each recording that the filters apply to,
 * and calls into it from the recorder's hooks.
 */
public final class RecordingFilter {
	private final ReplayChunkRecorder recorder;
	private final FilterConfig config;
	private final EntityFilter entities;
	private final BlockUpdateBuffer blockUpdates = new BlockUpdateBuffer();

	private int ticks = 0;
	// Set while we record the batched block updates, so that we let our own packets through.
	private boolean flushing = false;
	private boolean failed = false;

	/**
	 * This runs at the end of the constructor of {@code ReplayRecorder}, before the constructor
	 * of {@link ReplayChunkRecorder} has set its own fields, so nothing here may use the
	 * recorder's level, name or dummy player yet.
	 */
	public RecordingFilter(ReplayChunkRecorder recorder, FilterConfig config) {
		this.recorder = recorder;
		this.config = config;
		this.entities = new EntityFilter(config.entityWhitelist(), () -> recorder.getDummyPlayer().getId());

		if (this.batchesBlockUpdates() && !config.ignoreBlockAction()) {
			OptimizeServerReplay.LOGGER.info(
				"block_update_interval_ticks is {}, so the block actions of pistons are ignored automatically",
				config.blockUpdateIntervalTicks()
			);
		}
	}

	/**
	 * Decides whether the recorder may record a packet.
	 */
	public boolean shouldRecord(Packet<?> packet) {
		// The filters keep their state without locks, and the packets they look at are all
		// sent from the server thread.
		if (this.flushing || !this.recorder.getServer().isSameThread()) {
			return true;
		}

		try {
			if (this.config.ignoreEntities() && !this.entities.shouldRecord(packet)) {
				return false;
			}
			if (packet instanceof ClientboundBlockEventPacket blockEvent) {
				return BlockActionFilter.shouldRecord(blockEvent, this.config);
			}
			return !this.batchesBlockUpdates() || !this.blockUpdates.capture(packet);
		} catch (Exception e) {
			this.reportFailure(e);
			return true;
		}
	}

	/**
	 * Runs at the start of every tick of the recorder.
	 */
	public void tick() {
		if (!this.batchesBlockUpdates() || this.recorder.getPaused()) {
			return;
		}

		this.ticks++;
		if (this.ticks % this.config.blockUpdateIntervalTicks() == 0) {
			this.flushBlockUpdates();
		}
	}

	/**
	 * Records the block updates that were batched since the last flush.
	 */
	public void flushBlockUpdates() {
		// While paused the writer discards every packet, so the updates would be lost.
		if (!this.batchesBlockUpdates() || this.flushing || this.recorder.getPaused()) {
			return;
		}
		if (!this.recorder.getServer().isSameThread()) {
			return;
		}

		this.flushing = true;
		try {
			this.blockUpdates.flush(this.recorder.getLevel(), this.recorder::record);
		} catch (Exception e) {
			this.reportFailure(e);
		} finally {
			this.flushing = false;
		}
	}

	private boolean batchesBlockUpdates() {
		return this.config.blockUpdateIntervalTicks() >= 1;
	}

	private void reportFailure(Exception e) {
		if (!this.failed) {
			this.failed = true;
			OptimizeServerReplay.LOGGER.error("A packet filter failed, the recording continues without it", e);
		}
	}
}
