package com.panyaaa256.optimizeserverreplay.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.panyaaa256.optimizeserverreplay.tracking.RecordableCache;
import net.casual.arcade.replay.recorder.chunk.ReplayChunkRecordable;
import net.casual.arcade.replay.recorder.chunk.ReplayChunkRecorder;
import net.casual.arcade.replay.recorder.chunk.ReplayChunkRecorders;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps {@link RecordableCache} up to date with the chunk recordings there are.
 */
@Mixin(value = ReplayChunkRecorders.class, remap = false)
public abstract class ReplayChunkRecordersMixin {
	// The overloads of create all call this one.
	@Inject(
		method = "create(Lnet/casual/arcade/replay/recorder/chunk/ChunkArea;Ljava/nio/file/Path;Lnet/casual/arcade/replay/io/ReplayFormat;Lnet/casual/arcade/replay/recorder/settings/RecorderSettings;Ljava/lang/String;)Lnet/casual/arcade/replay/recorder/chunk/ReplayChunkRecorder;",
		at = @At("RETURN")
	)
	private static void osr$onRecorderCreated(CallbackInfoReturnable<ReplayChunkRecorder> cir) {
		RecordableCache.Generation.next();
	}

	@Inject(method = "remove$arcade_replay", at = @At("TAIL"))
	private void osr$onRecorderRemoved(ReplayChunkRecorder recorder, CallbackInfo ci) {
		RecordableCache.Generation.next();
	}

	// Only the overload for a chunk, which the entities use. The classes in @Desc are remapped
	// with the rest of the mod, unlike a descriptor written out.
	@Inject(
		target = @Desc(value = "updateRecordable", args = {ReplayChunkRecordable.class, ResourceKey.class, ChunkPos.class}),
		at = @At("HEAD"),
		cancellable = true
	)
	private static void osr$onUpdateRecordable(
		ReplayChunkRecordable recordable,
		ResourceKey<Level> level,
		ChunkPos chunk,
		CallbackInfo ci,
		@Share("cache") LocalBooleanRef caches,
		@Share("generation") LocalIntRef generation
	) {
		if (!(recordable instanceof RecordableCache cache) || !RecordableCache.Generation.enabled()) {
			return;
		}
		int current = RecordableCache.Generation.current();
		if (cache.osr$isUpToDate(level, chunk.toLong(), current)) {
			ci.cancel();
			return;
		}
		caches.set(true);
		generation.set(current);
	}

	// Not reached when Arcade throws, so the entity is checked again next time.
	@Inject(
		target = @Desc(value = "updateRecordable", args = {ReplayChunkRecordable.class, ResourceKey.class, ChunkPos.class}),
		at = @At("TAIL")
	)
	private static void osr$onUpdatedRecordable(
		ReplayChunkRecordable recordable,
		ResourceKey<Level> level,
		ChunkPos chunk,
		CallbackInfo ci,
		@Share("cache") LocalBooleanRef caches,
		@Share("generation") LocalIntRef generation
	) {
		if (caches.get()) {
			((RecordableCache) recordable).osr$markUpToDate(level, chunk.toLong(), generation.get());
		}
	}
}
