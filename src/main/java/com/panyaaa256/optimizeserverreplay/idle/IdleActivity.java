package com.panyaaa256.optimizeserverreplay.idle;

import com.panyaaa256.optimizeserverreplay.block.BlockActionFilter;
import com.panyaaa256.optimizeserverreplay.config.FilterConfig;
import com.panyaaa256.optimizeserverreplay.entity.EntityPackets;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Decides whether a packet shows that something is happening in the recorded area.
 *
 * <p>Only changes to the blocks and the entities count. Packets that come on their own while
 * nothing happens, such as the time of day, the tab list, chat, sounds and the chunks that load
 * as players move around, do not.
 */
public final class IdleActivity {
	private static final Set<Class<?>> BLOCK_CHANGES = Set.of(
		ClientboundBlockUpdatePacket.class,
		ClientboundSectionBlocksUpdatePacket.class,
		ClientboundBlockEntityDataPacket.class,
		ClientboundBlockDestructionPacket.class,
		ClientboundLevelEventPacket.class,
		ClientboundExplodePacket.class
	);

	private IdleActivity() {
	}

	public static boolean counts(Packet<?> packet, FilterConfig config, ServerLevel level, int dummyId) {
		if (packet instanceof ClientboundBlockEventPacket blockEvent) {
			return BlockActionFilter.shouldRecord(blockEvent, config);
		}
		if (BLOCK_CHANGES.contains(packet.getClass())) {
			return true;
		}

		switch (EntityPackets.kindOf(packet)) {
			case EntityPackets.NOT_ENTITY -> {
				return false;
			}
			case EntityPackets.ADD -> {
				ClientboundAddEntityPacket add = (ClientboundAddEntityPacket) packet;
				return countsEntity(add.getId(), add.getType(), level, config, dummyId);
			}
			case EntityPackets.REMOVE -> {
				IntList ids = ((ClientboundRemoveEntitiesPacket) packet).getEntityIds();
				for (int i = 0; i < ids.size(); i++) {
					if (countsEntity(ids.getInt(i), null, level, config, dummyId)) {
						return true;
					}
				}
				return false;
			}
			case EntityPackets.PASSENGERS -> {
				ClientboundSetPassengersPacket passengers = (ClientboundSetPassengersPacket) packet;
				if (countsEntity(passengers.getVehicle(), null, level, config, dummyId)) {
					return true;
				}
				for (int id : passengers.getPassengers()) {
					if (countsEntity(id, null, level, config, dummyId)) {
						return true;
					}
				}
				return false;
			}
			case EntityPackets.LINK -> {
				ClientboundSetEntityLinkPacket link = (ClientboundSetEntityLinkPacket) packet;
				return countsEntity(link.getSourceId(), null, level, config, dummyId)
					|| countsEntity(link.getDestId(), null, level, config, dummyId);
			}
			case EntityPackets.TAKE_ITEM -> {
				ClientboundTakeItemEntityPacket take = (ClientboundTakeItemEntityPacket) packet;
				return countsEntity(take.getItemId(), null, level, config, dummyId)
					|| countsEntity(take.getPlayerId(), null, level, config, dummyId);
			}
			default -> {
				return countsEntity(EntityPackets.singleId(packet), null, level, config, dummyId);
			}
		}
	}

	/**
	 * @param type the type of the entity when the packet names it, for an entity that the level
	 *     may not have yet
	 */
	private static boolean countsEntity(
		int id,
		@Nullable EntityType<?> type,
		ServerLevel level,
		FilterConfig config,
		int dummyId
	) {
		if (id == dummyId) {
			return false;
		}
		Entity entity = level.getEntity(id);
		// Spectators are recorded like any other player, but they cannot change anything.
		if (entity instanceof ServerPlayer player && player.isSpectator()) {
			return false;
		}
		if (entity != null) {
			type = entity.getType();
		}
		// An entity that has already left the level.
		if (type == null) {
			return false;
		}

		if (config.ignoreEntities() && !config.entityWhitelist().contains(type)) {
			return false;
		}
		return !config.idleIgnoreEntities() || config.idleEntityWhitelist().contains(type);
	}
}
