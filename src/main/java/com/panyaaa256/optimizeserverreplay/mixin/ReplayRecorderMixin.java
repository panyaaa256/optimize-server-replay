package com.panyaaa256.optimizeserverreplay.mixin;

import com.mojang.authlib.GameProfile;
import com.panyaaa256.optimizeserverreplay.HookStatus;
import com.panyaaa256.optimizeserverreplay.OptimizeServerReplay;
import com.panyaaa256.optimizeserverreplay.RecordingFilter;
import com.panyaaa256.optimizeserverreplay.RecordingFilterHolder;
import com.panyaaa256.optimizeserverreplay.config.FilterConfig;
import net.casual.arcade.replay.io.ReplayFormat;
import net.casual.arcade.replay.recorder.ReplayRecorder;
import net.casual.arcade.replay.recorder.chunk.ReplayChunkRecorder;
import net.casual.arcade.replay.recorder.settings.RecorderSettings;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Mixin(value = ReplayRecorder.class, remap = false)
public abstract class ReplayRecorderMixin implements RecordingFilterHolder {
	@Unique
	@Nullable
	private RecordingFilter osr$filter;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void osr$onInit(
		MinecraftServer server,
		GameProfile profile,
		RecorderSettings settings,
		ReplayFormat format,
		Path path,
		CallbackInfo ci
	) {
		if (!((Object) this instanceof ReplayChunkRecorder recorder) || format != ReplayFormat.Flashback) {
			return;
		}

		try {
			if (HookStatus.allHooksApplied()) {
				this.osr$filter = new RecordingFilter(recorder, FilterConfig.current());
			}
		} catch (Exception e) {
			OptimizeServerReplay.LOGGER.error("Failed to set up the packet filters, the recording continues without them", e);
		}
	}

	@Inject(method = "canRecordPacket", at = @At("HEAD"), cancellable = true)
	private void osr$onCanRecordPacket(Packet<?> packet, CallbackInfoReturnable<Boolean> cir) {
		if (this.osr$filter != null && !this.osr$filter.shouldRecord(packet)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void osr$onTick(CallbackInfo ci) {
		if (this.osr$filter != null) {
			this.osr$filter.tick();
		}
	}

	// pause and stop each have an overload without parameters that calls these.
	@Inject(method = "pause(Z)Z", at = @At("HEAD"))
	private void osr$onPause(boolean force, CallbackInfoReturnable<Boolean> cir) {
		this.osr$flushBlockUpdates();
	}

	@Inject(method = "stop(Z)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
	private void osr$onStop(boolean save, CallbackInfoReturnable<CompletableFuture<Long>> cir) {
		this.osr$flushBlockUpdates();
	}

	@Override
	public void osr$flushBlockUpdates() {
		if (this.osr$filter != null) {
			this.osr$filter.flushBlockUpdates();
		}
	}
}
