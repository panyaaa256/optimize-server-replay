package com.panyaaa256.optimizeserverreplay.block;

import com.panyaaa256.optimizeserverreplay.config.FilterConfig;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;

/**
 * Leaves block actions, such as the animation of a piston, out of a recording.
 */
public final class BlockActionFilter {
	private BlockActionFilter() {
	}

	public static boolean shouldRecord(ClientboundBlockEventPacket packet, FilterConfig config) {
		// TODO: filter the block actions.
		return true;
	}
}
