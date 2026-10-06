package com.panyaaa256.optimizeserverreplay.entity;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.EntityType;

import java.util.Set;
import java.util.function.IntSupplier;

/**
 * Leaves the packets of entities out of a recording, except for the whitelisted entity types.
 */
public final class EntityFilter {
	private final Set<EntityType<?>> whitelist;
	private final IntSupplier dummyPlayerId;

	/**
	 * @param whitelist the entity types to keep recording
	 * @param dummyPlayerId gives the entity id of the chunk recorder's dummy player, which must
	 *     not be asked for before the first packet arrives
	 */
	public EntityFilter(Set<EntityType<?>> whitelist, IntSupplier dummyPlayerId) {
		this.whitelist = whitelist;
		this.dummyPlayerId = dummyPlayerId;
	}

	/**
	 * Decides whether a packet may be recorded. Only called on the server thread and while
	 * {@code ignore_entities} is on.
	 */
	public boolean shouldRecord(Packet<?> packet) {
		// TODO: filter the packets of entities.
		return true;
	}
}
