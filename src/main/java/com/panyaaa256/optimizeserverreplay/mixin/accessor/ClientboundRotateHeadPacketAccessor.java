package com.panyaaa256.optimizeserverreplay.mixin.accessor;

import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Gives access to the entity id of the head rotation packet, which only has a getter that needs a level.
 */
@Mixin(ClientboundRotateHeadPacket.class)
public interface ClientboundRotateHeadPacketAccessor {
	@Accessor("entityId")
	int osr$getEntityId();
}
