package com.panyaaa256.optimizeserverreplay.mixin;

import com.panyaaa256.optimizeserverreplay.recovery.FlashbackRecovery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

// Server Replay calls this off the main thread at startup, for every recording that the server
// did not stop, and waits for it to finish before the server stops.
@Mixin(targets = "me.senseiwells.replay.processor.RecorderRecoverer", remap = false)
public abstract class RecorderRecovererMixin {
	@Inject(method = "recover", at = @At("HEAD"), cancellable = true)
	private void osr$onRecover(Path recording, CallbackInfo ci) {
		if (FlashbackRecovery.tryRecover(recording)) {
			ci.cancel();
		}
	}
}
