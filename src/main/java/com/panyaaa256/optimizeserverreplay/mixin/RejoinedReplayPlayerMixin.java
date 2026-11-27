package com.panyaaa256.optimizeserverreplay.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.panyaaa256.optimizeserverreplay.rejoin.RejoinCleanup;
import net.casual.arcade.replay.recorder.rejoin.RejoinConfigurationPacketListener;
import net.casual.arcade.replay.recorder.rejoin.RejoinedReplayPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// rejoin runs on the server thread, for every snapshot of a chunk recording and every start of a
// player recording.
@Mixin(value = RejoinedReplayPlayer.Companion.class, remap = false)
public abstract class RejoinedReplayPlayerMixin {
	// By the end, Arcade has sent everything through both listeners and keeps neither. The game
	// listener sets itself as the connection of the player when it is made.
	@Inject(method = "rejoin", at = @At("TAIL"))
	private void osr$onRejoin(
		CallbackInfo ci,
		@Local RejoinedReplayPlayer rejoined,
		@Local RejoinConfigurationPacketListener config
	) {
		RejoinCleanup.endSessions(config, rejoined.connection);
	}
}
