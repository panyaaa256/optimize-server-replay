package com.panyaaa256.optimizeserverreplay.mixin.accessor;

import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Gives access to the entity id of the movement packets, which only have a getter that needs a level.
 */
@Mixin(ClientboundMoveEntityPacket.class)
public interface ClientboundMoveEntityPacketAccessor {
	@Accessor("entityId")
	int osr$getEntityId();
}
