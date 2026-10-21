package com.panyaaa256.optimizeserverreplay.mixin;

import com.panyaaa256.optimizeserverreplay.snapshot.TrackedEntities;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
	@Inject(method = "addEntity", at = @At("TAIL"))
	private void osr$onAddEntity(Entity entity, CallbackInfo ci) {
		TrackedEntities.onChanged();
	}

	@Inject(method = "removeEntity", at = @At("TAIL"))
	private void osr$onRemoveEntity(Entity entity, CallbackInfo ci) {
		TrackedEntities.onChanged();
	}
}
