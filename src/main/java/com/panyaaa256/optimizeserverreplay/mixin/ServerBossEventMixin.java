package com.panyaaa256.optimizeserverreplay.mixin;

import net.casual.arcade.replay.recorder.chunk.ReplayChunkRecordable;
import net.casual.arcade.replay.recorder.chunk.ReplayChunkRecorder;
import net.minecraft.server.level.ServerBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Arcade adds {@code addRecorder} and {@code removeRecorder} to boss bars, and links the boss bar
 * to the recorder only when the bar is visible. A chunk recording that took in a hidden boss bar,
 * such as the one of the dragon fight once the dragon is dead, was never unlinked from it when it
 * stopped, and the boss bar, which lives as long as the level, kept the whole recording.
 *
 * <p>Applied after Arcade's mixin, which adds the methods.
 */
@Mixin(value = ServerBossEvent.class, priority = 1500)
public abstract class ServerBossEventMixin {
	@Inject(method = "addRecorder", at = @At("TAIL"), remap = false)
	private void osr$onAddRecorder(ReplayChunkRecorder recorder, CallbackInfo ci) {
		ReplayChunkRecordable self = (ReplayChunkRecordable) this;
		if (self.getRecorders().contains(recorder)) {
			recorder.addRecordable(self);
		}
	}

	@Inject(method = "removeRecorder", at = @At("TAIL"), remap = false)
	private void osr$onRemoveRecorder(ReplayChunkRecorder recorder, CallbackInfo ci) {
		ReplayChunkRecordable self = (ReplayChunkRecordable) this;
		if (!self.getRecorders().contains(recorder)) {
			recorder.removeRecordable(self);
		}
	}
}
