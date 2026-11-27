package com.panyaaa256.optimizeserverreplay.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.panyaaa256.optimizeserverreplay.OptimizeServerReplay;
import com.panyaaa256.optimizeserverreplay.save.ChunkedWriterHolder;
import com.panyaaa256.optimizeserverreplay.save.StreamingZip;
import net.casual.arcade.replay.io.writer.flashback.FlashbackChunkedWriter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

// Arcade calls everything here on its writer thread, except getWrittenBytes.
@Mixin(value = FlashbackChunkedWriter.class, remap = false)
public abstract class FlashbackChunkedWriterMixin implements ChunkedWriterHolder {
	private static final String CHUNK_CACHES = "level_chunk_caches/";
	// How much of a replay chunk is kept in memory before it is written to its file.
	private static final int STREAM_THRESHOLD = 4 << 20;
	// A buffer that grew past this while a chunk was recorded is made smaller once it ends.
	private static final int SHRINK_ABOVE = 16 << 20;
	private static final int SHRINK_TO = 1 << 20;

	@Shadow
	@Final
	private Path directory;

	@Shadow
	@Final
	private RegistryFriendlyByteBuf buffer;

	@Shadow
	private int chunk;

	// The settings are made on the server thread before the recording starts, and read on the
	// writer thread, which only runs tasks that were handed to it after that.
	@Unique
	private boolean osr$skipSync = false;

	@Unique
	private boolean osr$keepCacheOpen = false;

	@Unique
	private boolean osr$stream = false;

	@Unique
	private @Nullable StreamingZip osr$zip;

	// The chunk cache file that Arcade is adding to.
	@Unique
	private int osr$chunkCacheFile = -1;

	// The chunk cache file that is kept open, with osr$keepCacheOpen.
	@Unique
	private @Nullable FileChannel osr$cacheOut;
	@Unique
	private @Nullable Path osr$cachePath;

	// The file of the replay chunk that is being recorded, once part of it is written, with osr$stream.
	@Unique
	private @Nullable FileChannel osr$chunkOut;
	@Unique
	private @Nullable Path osr$chunkPath;
	// Whether the snapshot at the start of the current replay chunk is complete. Until then, its size
	// at the start of the buffer is not written yet, so nothing can leave the buffer.
	@Unique
	private boolean osr$snapshotDone = false;
	@Unique
	private boolean osr$streamFailed = false;
	// The bytes that went to the replay chunk files early, which Arcade does not count itself.
	@Unique
	private volatile long osr$streamed = 0;

	/**
	 * Arcade waits for every chunk it adds to the chunk caches to reach the disk, which holds up
	 * the writer thread for each of the many chunks of a snapshot. Nothing else in the recording
	 * is written that way, and a server that stops without saving leaves the files to the system
	 * all the same, so the wait buys nothing. Arcade also opens and closes the file for every
	 * chunk, so the file is kept open instead while chunks are added to it.
	 */
	@Redirect(
		method = "writeLevelChunk",
		at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;write(Ljava/nio/file/Path;[B[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;")
	)
	private Path osr$onWriteLevelChunk(Path path, byte[] bytes, OpenOption[] options) throws IOException {
		if (this.osr$keepCacheOpen) {
			if (this.osr$cacheOut == null || !path.equals(this.osr$cachePath)) {
				this.osr$closeCacheFile();
				this.osr$cacheOut = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
				this.osr$cachePath = path;
			}
			try {
				osr$writeFully(this.osr$cacheOut, bytes);
			} catch (IOException | RuntimeException e) {
				this.osr$closeCacheFile();
				throw e;
			}
			return path;
		}
		if (this.osr$skipSync) {
			options = Arrays.stream(options).filter(option -> option != StandardOpenOption.SYNC).toArray(OpenOption[]::new);
		}
		return Files.write(path, bytes, options);
	}

	// The cache files are numbered in the order they are filled, so once Arcade adds to the next
	// one, the previous one never changes again. The other parameter is a Kotlin function, which
	// is not on the compile classpath.
	@Inject(method = "writeLevelChunk", at = @At("HEAD"))
	private void osr$onStartLevelChunk(CallbackInfo ci, @Local(argsOnly = true) int fileIndex) {
		if (fileIndex <= this.osr$chunkCacheFile) {
			return;
		}
		// Closed before it goes into the zip, which deletes it.
		this.osr$closeCacheFile();
		if (this.osr$zip != null && this.osr$chunkCacheFile >= 0) {
			this.osr$zip.add(CHUNK_CACHES + this.osr$chunkCacheFile);
		}
		this.osr$chunkCacheFile = fileIndex;
	}

	@Inject(method = "startSnapshot", at = @At("HEAD"))
	private void osr$onStartSnapshot(CallbackInfo ci) {
		this.osr$snapshotDone = false;
	}

	@Inject(method = "endSnapshot", at = @At("TAIL"))
	private void osr$onEndSnapshot(CallbackInfo ci) {
		this.osr$snapshotDone = true;
	}

	/**
	 * Arcade keeps a whole replay chunk (5 minutes) in memory, in a buffer that never gets
	 * smaller, and copies it once more to write it. Once the snapshot of the chunk is complete,
	 * the actions after it are only ever appended, so what is in the buffer can go to the file
	 * of the chunk after each action. The chunk is not in the metadata until it ends, so a chunk
	 * file that a crash cuts short is not used, as before.
	 */
	@Inject(
		method = "writeAction(Lnet/casual/arcade/replay/util/flashback/FlashbackAction;Lkotlin/jvm/functions/Function1;)Ljava/lang/Object;",
		at = @At("RETURN")
	)
	private void osr$onWriteAction(CallbackInfoReturnable<Object> cir) {
		if (!this.osr$stream || !this.osr$snapshotDone || this.osr$streamFailed) {
			return;
		}
		int size = this.buffer.writerIndex();
		if (size < STREAM_THRESHOLD) {
			return;
		}

		int written = 0;
		try {
			if (this.osr$chunkOut == null) {
				Path path = this.directory.resolve("c" + this.chunk + ".flashback");
				this.osr$chunkOut = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
				this.osr$chunkPath = path;
			}
			while (written < size) {
				written += this.buffer.getBytes(written, this.osr$chunkOut, size - written);
			}
		} catch (IOException | RuntimeException e) {
			// The rest stays in memory, and goes after what was written when the chunk ends.
			this.osr$streamFailed = true;
			OptimizeServerReplay.LOGGER.error(
				"Failed to write part of a replay chunk of {} early, the rest of the chunk is kept in memory", this.directory, e
			);
		} finally {
			if (written == size) {
				this.buffer.clear();
			} else if (written > 0) {
				this.buffer.readerIndex(written);
				this.buffer.discardReadBytes();
			}
			this.osr$streamed += written;
		}
	}

	// Appends the rest of the chunk to what was written early, instead of writing all of it.
	@Redirect(
		method = "endChunk",
		at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;write(Ljava/nio/file/Path;[B[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;")
	)
	private Path osr$onWriteChunk(Path path, byte[] bytes, OpenOption[] options) throws IOException {
		FileChannel out = this.osr$chunkOut;
		if (out == null) {
			return Files.write(path, bytes, options);
		}
		this.osr$chunkOut = null;
		try (out) {
			if (!path.equals(this.osr$chunkPath)) {
				throw new IOException("The replay chunk " + path + " was partly written to " + this.osr$chunkPath);
			}
			osr$writeFully(out, bytes);
		} finally {
			this.osr$chunkPath = null;
		}
		return path;
	}

	// Closed every few minutes, so that the file is complete on disk when the recording is saved.
	@Inject(method = "endChunk", at = @At("HEAD"))
	private void osr$onStartEndChunk(int tick, boolean forceSnapshot, CallbackInfo ci) {
		this.osr$closeCacheFile();
	}

	// The chunk that just ended is written in full, and never changes again.
	@Inject(method = "endChunk", at = @At("TAIL"))
	private void osr$onEndChunk(int tick, boolean forceSnapshot, CallbackInfo ci) {
		this.osr$snapshotDone = false;
		if (this.osr$stream && this.buffer.capacity() > SHRINK_ABOVE) {
			this.buffer.capacity(Math.max(this.buffer.writerIndex(), SHRINK_TO));
		}
		if (this.osr$zip != null) {
			this.osr$zip.add("c" + (this.chunk - 1) + ".flashback");
		}
	}

	@Inject(method = "getWrittenBytes", at = @At("RETURN"), cancellable = true)
	private void osr$onGetWrittenBytes(CallbackInfoReturnable<Long> cir) {
		long streamed = this.osr$streamed;
		if (streamed != 0) {
			cir.setReturnValue(cir.getReturnValue() + streamed);
		}
	}

	// Arcade deletes the recording folder after this, whether the recording was saved or not.
	@Inject(method = "close", at = @At("HEAD"))
	private void osr$onClose(CallbackInfo ci) {
		this.osr$closeFiles();
		if (this.osr$zip != null) {
			this.osr$zip.close();
		}
	}

	@Override
	public void osr$skipChunkCacheSync() {
		this.osr$skipSync = true;
	}

	@Override
	public void osr$keepChunkCacheOpen() {
		this.osr$keepCacheOpen = true;
	}

	@Override
	public void osr$streamReplayChunks() {
		this.osr$stream = true;
	}

	@Override
	public void osr$zipWhileRecording(int level) {
		this.osr$zip = new StreamingZip(this.directory, level);
	}

	@Override
	public @Nullable StreamingZip osr$streamingZip() {
		return this.osr$zip;
	}

	@Override
	public void osr$releaseAfterFailedSave() {
		this.osr$closeFiles();
		if (this.osr$zip != null) {
			this.osr$zip.close();
		}
		if (this.buffer.refCnt() > 0) {
			this.buffer.release();
		}
	}

	@Unique
	private void osr$closeFiles() {
		this.osr$closeCacheFile();
		FileChannel out = this.osr$chunkOut;
		this.osr$chunkOut = null;
		this.osr$chunkPath = null;
		if (out != null) {
			try {
				out.close();
			} catch (IOException e) {
				OptimizeServerReplay.LOGGER.warn("Failed to close a replay chunk file of {}", this.directory, e);
			}
		}
	}

	@Unique
	private void osr$closeCacheFile() {
		FileChannel out = this.osr$cacheOut;
		this.osr$cacheOut = null;
		this.osr$cachePath = null;
		if (out != null) {
			try {
				out.close();
			} catch (IOException e) {
				OptimizeServerReplay.LOGGER.warn("Failed to close a chunk cache file of {}", this.directory, e);
			}
		}
	}

	@Unique
	private static void osr$writeFully(FileChannel out, byte[] bytes) throws IOException {
		ByteBuffer source = ByteBuffer.wrap(bytes);
		while (source.hasRemaining()) {
			out.write(source);
		}
	}
}
