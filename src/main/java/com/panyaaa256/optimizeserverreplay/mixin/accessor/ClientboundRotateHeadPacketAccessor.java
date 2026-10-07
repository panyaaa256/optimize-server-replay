package com.panyaaa256.optimizeserverreplay.mixin.accessor;

import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The packet only has a getter that needs a level, so the entity id is read from the field.
 */
@Mixin(ClientboundRotateHeadPacket.class)
public interface ClientboundRotateHeadPacketAccessor {
	@Accessor("entityId")
	int osr$getEntityId();
}
