package com.panyaaa256.optimizeserverreplay.mixin;

import com.panyaaa256.optimizeserverreplay.RecordingFilterHolder;
import com.panyaaa256.optimizeserverreplay.config.FilterConfig;
import com.panyaaa256.optimizeserverreplay.save.ChunkCacheSyncHolder;
import com.panyaaa256.optimizeserverreplay.save.FlashbackWriterHolder;
import com.panyaaa256.optimizeserverreplay.save.ReplayZip;
import net.casual.arcade.replay.io.writer.flashback.FlashbackChunkedWriter;
import net.casual.arcade.replay.io.writer.flashback.FlashbackWriter;
import net.casual.arcade.replay.recorder.ReplayRecorder;
import net.casual.arcade.replay.util.FileUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Path;

@Mixin(value = FlashbackWriter.class, remap = false)
public abstract class FlashbackWriterMixin implements FlashbackWriterHolder {
	@Shadow
	@Final
	private FlashbackChunkedWriter writer;

	@Shadow
	public abstract ReplayRecorder getRecorder();

	// A new replay chunk starts with a snapshot of the world, which the batched block
	// updates have to come before.
	@Inject(method = "startNewReplayChunk", at = @At("HEAD"))
	private void osr$onStartNewReplayChunk(CallbackInfo ci) {
		((RecordingFilterHolder) this.getRecorder()).osr$flushBlockUpdates();
	}

	// The local function that saves the recording in close(), which runs on the writer thread.
	@Redirect(
		method = "close_VtjQ1oo$lambda$0$write",
		at = @At(value = "INVOKE", target = "Lnet/casual/arcade/replay/util/FileUtils;zip(Ljava/nio/file/Path;Ljava/nio/file/Path;)V")
	)
	private static void osr$onZip(FileUtils utils, Path source, Path output, FlashbackWriter writer) throws IOException {
		FilterConfig config = ((RecordingFilterHolder) writer.getRecorder()).osr$config();
		if (config == null) {
			utils.zip(source, output);
			return;
		}
		ReplayZip.zip(source, output, config.zipCompressionLevel());
	}

	@Override
	public void osr$skipChunkCacheSync() {
		((ChunkCacheSyncHolder) (Object) this.writer).osr$skipChunkCacheSync();
	}
}
