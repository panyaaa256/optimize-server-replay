package com.panyaaa256.optimizeserverreplay.mixin;

import com.panyaaa256.optimizeserverreplay.config.FilterConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "me.senseiwells.replay.ServerReplay", remap = false)
public abstract class ServerReplayMixin {
	@Inject(method = "reload", at = @At("TAIL"))
	private void osr$onReload(CallbackInfo ci) {
		FilterConfig.reload();
	}
}
