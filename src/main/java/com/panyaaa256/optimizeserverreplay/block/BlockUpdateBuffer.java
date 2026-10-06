package com.panyaaa256.optimizeserverreplay.block;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;

import java.util.function.Consumer;

/**
 * Collects the block updates of a recording, so that they can be recorded together every few ticks.
 *
 * <p>Only used on the server thread.
 */
public final class BlockUpdateBuffer {
	/**
	 * Takes note of a packet that changes blocks.
	 *
	 * @return whether the packet was taken, in which case it must not be recorded
	 */
	public boolean capture(Packet<?> packet) {
		// TODO: collect the block updates.
		return false;
	}

	/**
	 * Hands the current state of every collected position to {@code recorder}, and forgets the positions.
	 *
	 * @param level the level that the recording is in
	 * @param recorder records a packet
	 */
	public void flush(ServerLevel level, Consumer<Packet<?>> recorder) {
		// TODO: record the collected block updates.
	}
}
