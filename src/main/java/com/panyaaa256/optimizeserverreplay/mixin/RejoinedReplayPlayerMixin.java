package com.panyaaa256.optimizeserverreplay.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.panyaaa256.optimizeserverreplay.leak.NetworkingSessions;
import net.casual.arcade.replay.recorder.rejoin.RejoinConfigurationPacketListener;
import net.casual.arcade.replay.recorder.rejoin.RejoinedReplayPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code rejoin} records a player joining the game, for the start of a recording and for each
 * snapshot, with a player and two packet listeners of its own that are thrown away afterwards.
 */
@Mixin(value = RejoinedReplayPlayer.Companion.class, remap = false)
public abstract class RejoinedReplayPlayerMixin {
	@Inject(method = "rejoin", at = @At("RETURN"))
	private void osr$onRejoined(
		CallbackInfo ci,
		@Local RejoinedReplayPlayer rejoined,
		@Local RejoinConfigurationPacketListener config
	) {
		NetworkingSessions.end(config);
		NetworkingSessions.end(rejoined.connection);
	}
}
