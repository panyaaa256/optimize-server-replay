package com.panyaaa256.optimizeserverreplay.block;

import com.panyaaa256.optimizeserverreplay.config.FilterConfig;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

public final class BlockActionFilter {
	private BlockActionFilter() {
	}

	public static boolean shouldRecord(ClientboundBlockEventPacket packet, FilterConfig config) {
		if (config.ignoreBlockAction()) {
			return false;
		}
		if (config.blockUpdateIntervalTicks() >= 1) {
			// The batched updates never show the blocks that a piston is pushing.
			return !(packet.getBlock() instanceof PistonBaseBlock);
		}
		return true;
	}
}
