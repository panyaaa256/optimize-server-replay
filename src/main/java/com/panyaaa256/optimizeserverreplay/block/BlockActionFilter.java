package com.panyaaa256.optimizeserverreplay.block;

import com.panyaaa256.optimizeserverreplay.config.FilterConfig;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

/**
 * Leaves block actions, such as the animation of a piston, out of a recording.
 */
public final class BlockActionFilter {
	private BlockActionFilter() {
	}

	/**
	 * Decides whether a block action may be recorded.
	 *
	 * <p>With {@code ignore_block_action} every block action is left out. While block updates are
	 * batched, only the block actions of pistons are, because the batched updates never show the
	 * blocks that a piston is pushing.
	 *
	 * @return whether the packet may be recorded
	 */
	public static boolean shouldRecord(ClientboundBlockEventPacket packet, FilterConfig config) {
		if (config.ignoreBlockAction()) {
			return false;
		}
		if (config.blockUpdateIntervalTicks() >= 1) {
			return !(packet.getBlock() instanceof PistonBaseBlock);
		}
		return true;
	}
}
