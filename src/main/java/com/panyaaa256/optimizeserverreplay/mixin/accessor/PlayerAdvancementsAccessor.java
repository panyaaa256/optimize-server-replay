package com.panyaaa256.optimizeserverreplay.mixin.accessor;

import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerAdvancements.class)
public interface PlayerAdvancementsAccessor {
	@Accessor("player")
	ServerPlayer osr$player();
}
