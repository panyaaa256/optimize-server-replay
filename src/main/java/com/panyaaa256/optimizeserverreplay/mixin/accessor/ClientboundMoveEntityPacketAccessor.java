package com.panyaaa256.optimizeserverreplay.mixin.accessor;

import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The packets only have a getter that needs a level, so the entity id is read from the field.
 */
@Mixin(ClientboundMoveEntityPacket.class)
public interface ClientboundMoveEntityPacketAccessor {
	@Accessor("entityId")
	int osr$getEntityId();
}
