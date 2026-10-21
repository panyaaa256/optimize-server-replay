package com.panyaaa256.optimizeserverreplay.mixin;

import com.panyaaa256.optimizeserverreplay.save.ChunkCacheSyncHolder;
import net.casual.arcade.replay.io.writer.flashback.FlashbackChunkedWriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

@Mixin(value = FlashbackChunkedWriter.class, remap = false)
public abstract class FlashbackChunkedWriterMixin implements ChunkCacheSyncHolder {
	// Set on the server thread before the recording starts, and read on the writer thread, which
	// only runs tasks that were handed to it after that.
	@Unique
	private boolean osr$skipSync = false;

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

	@Override
	public void osr$skipChunkCacheSync() {
		this.osr$skipSync = true;
	}
}
