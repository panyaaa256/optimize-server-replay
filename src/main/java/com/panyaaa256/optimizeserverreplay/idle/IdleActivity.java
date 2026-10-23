package com.panyaaa256.optimizeserverreplay.idle;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/**
 * Decides whether a packet shows that something is happening in the recorded area.
 *
 * <p>Only two packets count: the block actions, which pistons, note blocks, chests and bells
 * send, and the arm swings of players. Neither comes from random ticks or from mobs, and a player
 * who only walks around sends neither. They count whether or not the recording leaves them out.
 */
public final class IdleActivity {
	/** The blocks that send block actions on their own, however long nothing else happens. */
	private static final Set<Block> PERIODIC_BLOCK_ACTIONS = Set.of(Blocks.SPAWNER, Blocks.END_GATEWAY);

	private IdleActivity() {
	}

	public static boolean counts(Packet<?> packet, ServerLevel level, int dummyId) {
		if (packet instanceof ClientboundBlockEventPacket blockEvent) {
			return !PERIODIC_BLOCK_ACTIONS.contains(blockEvent.getBlock());
		}
		if (packet instanceof ClientboundAnimatePacket animate) {
			int id = animate.getId();
			if (id == dummyId) {
				return false;
			}
			Entity entity = level.getEntity(id);
			return entity instanceof ServerPlayer player && !player.isSpectator();
		}
		return false;
	}
}
