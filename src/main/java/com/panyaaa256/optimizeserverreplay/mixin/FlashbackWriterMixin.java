package com.panyaaa256.optimizeserverreplay.mixin;

import com.panyaaa256.optimizeserverreplay.RecordingFilterHolder;
import net.casual.arcade.replay.io.writer.flashback.FlashbackWriter;
import net.casual.arcade.replay.recorder.ReplayRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FlashbackWriter.class, remap = false)
public abstract class FlashbackWriterMixin {
	@Shadow
	public abstract ReplayRecorder getRecorder();

	// A new replay chunk starts with a snapshot of the world, which the batched block
	// updates have to come before.
	@Inject(method = "startNewReplayChunk", at = @At("HEAD"))
	private void osr$onStartNewReplayChunk(CallbackInfo ci) {
		((RecordingFilterHolder) this.getRecorder()).osr$flushBlockUpdates();
	}
}
