package com.panyaaa256.optimizeserverreplay.entity;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.EntityType;

import java.util.Set;
import java.util.function.IntSupplier;

public final class EntityFilter {
	private final Set<EntityType<?>> whitelist;
	private final IntSupplier dummyPlayerId;
	private final IntOpenHashSet kept = new IntOpenHashSet();

	private boolean dummyIdKnown = false;
	private int dummyId;

	public EntityFilter(Set<EntityType<?>> whitelist, IntSupplier dummyPlayerId) {
		this.whitelist = whitelist;
		this.dummyPlayerId = dummyPlayerId;
	}

	public boolean shouldRecord(Packet<?> packet) {
		int kind = EntityPackets.kindOf(packet);
		if (kind == EntityPackets.NOT_ENTITY) {
			return true;
		}

		int dummy = this.dummyId();
		boolean keepsNothing = this.whitelist.isEmpty();

		switch (kind) {
			case EntityPackets.ADD -> {
				ClientboundAddEntityPacket add = (ClientboundAddEntityPacket) packet;
				int id = add.getId();
				if (id == dummy) {
					return true;
				}
				if (keepsNothing || !this.whitelist.contains(add.getType())) {
					return false;
				}
				this.kept.add(id);
				return true;
			}
			case EntityPackets.REMOVE -> {
				IntList ids = ((ClientboundRemoveEntitiesPacket) packet).entityIds();
				boolean record = false;
				for (int i = 0; i < ids.size(); i++) {
					int id = ids.getInt(i);
					// Take every id out of the set, even after one of them has matched.
					record |= id == dummy || this.kept.remove(id);
				}
				return record;
			}
			case EntityPackets.PASSENGERS -> {
				ClientboundSetPassengersPacket passengers = (ClientboundSetPassengersPacket) packet;
				if (this.shouldKeep(passengers.getVehicle(), dummy, keepsNothing)) {
					return true;
				}
				for (int id : passengers.getPassengers()) {
					if (this.shouldKeep(id, dummy, keepsNothing)) {
						return true;
					}
				}
				return false;
			}
			case EntityPackets.LINK -> {
				ClientboundSetEntityLinkPacket link = (ClientboundSetEntityLinkPacket) packet;
				return this.shouldKeep(link.getSourceId(), dummy, keepsNothing)
					|| this.shouldKeep(link.getDestId(), dummy, keepsNothing);
			}
			case EntityPackets.TAKE_ITEM -> {
				ClientboundTakeItemEntityPacket take = (ClientboundTakeItemEntityPacket) packet;
				return this.shouldKeep(take.getItemId(), dummy, keepsNothing)
					|| this.shouldKeep(take.getPlayerId(), dummy, keepsNothing);
			}
			default -> {
				return this.shouldKeep(EntityPackets.singleId(packet), dummy, keepsNothing);
			}
		}
	}

	private boolean shouldKeep(int id, int dummy, boolean keepsNothing) {
		return id == dummy || (!keepsNothing && this.kept.contains(id));
	}

	/**
	 * The dummy player does not exist yet when the filter is created, so its id is read on the first packet.
	 */
	private int dummyId() {
		if (!this.dummyIdKnown) {
			this.dummyId = this.dummyPlayerId.getAsInt();
			this.dummyIdKnown = true;
		}
		return this.dummyId;
	}
}
