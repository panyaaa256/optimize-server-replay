package com.panyaaa256.optimizeserverreplay.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.panyaaa256.optimizeserverreplay.save.ChunkedWriterHolder;
import com.panyaaa256.optimizeserverreplay.save.StreamingZip;
import net.casual.arcade.replay.io.writer.flashback.FlashbackChunkedWriter;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

// Arcade calls everything here on its writer thread.
@Mixin(value = FlashbackChunkedWriter.class, remap = false)
public abstract class FlashbackChunkedWriterMixin implements ChunkedWriterHolder {
	private static final String CHUNK_CACHES = "level_chunk_caches/";

	@Shadow
	@Final
	private Path directory;

	@Shadow
	private int chunk;

	// The settings are made on the server thread before the recording starts, and read on the
	// writer thread, which only runs tasks that were handed to it after that.
	@Unique
	private boolean osr$skipSync = false;

	@Unique
	private @Nullable StreamingZip osr$zip;

	// The chunk cache file that Arcade is adding to.
	@Unique
	private int osr$chunkCacheFile = -1;

	/**
	 * Arcade waits for every chunk it adds to the chunk caches to reach the disk, which holds up
	 * the writer thread for each of the many chunks of a snapshot. Nothing else in the recording
	 * is written that way, and a server that stops without saving leaves the files to the system
	 * all the same, so the wait buys nothing.
	 */
	@Redirect(
		method = "writeLevelChunk",
		at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;write(Ljava/nio/file/Path;[B[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;")
	)
	private Path osr$onWriteLevelChunk(Path path, byte[] bytes, OpenOption[] options) throws IOException {
		if (this.osr$skipSync) {
			options = Arrays.stream(options).filter(option -> option != StandardOpenOption.SYNC).toArray(OpenOption[]::new);
		}
		return Files.write(path, bytes, options);
	}

	// The chunk that just ended is written in full, and never changes again.
	@Inject(method = "endChunk", at = @At("TAIL"))
	private void osr$onEndChunk(int tick, boolean forceSnapshot, CallbackInfo ci) {
		if (this.osr$zip != null) {
			this.osr$zip.add("c" + (this.chunk - 1) + ".flashback");
		}
	}

	// The cache files are numbered in the order they are filled, so once Arcade adds to the next
	// one, the previous one never changes again. The other parameter is a Kotlin function, which
	// is not on the compile classpath.
	@Inject(method = "writeLevelChunk", at = @At("HEAD"))
	private void osr$onStartLevelChunk(CallbackInfo ci, @Local(argsOnly = true) int fileIndex) {
		if (this.osr$zip == null || fileIndex <= this.osr$chunkCacheFile) {
			return;
		}
		if (this.osr$chunkCacheFile >= 0) {
			this.osr$zip.add(CHUNK_CACHES + this.osr$chunkCacheFile);
		}
		this.osr$chunkCacheFile = fileIndex;
	}

	// Arcade deletes the recording folder after this, whether the recording was saved or not.
	@Inject(method = "close", at = @At("HEAD"))
	private void osr$onClose(CallbackInfo ci) {
		if (this.osr$zip != null) {
			this.osr$zip.close();
		}
	}

	@Override
	public void osr$skipChunkCacheSync() {
		this.osr$skipSync = true;
	}

	@Override
	public void osr$zipWhileRecording(int level) {
		this.osr$zip = new StreamingZip(this.directory, level);
	}

	@Override
	public @Nullable StreamingZip osr$streamingZip() {
		return this.osr$zip;
	}
}
