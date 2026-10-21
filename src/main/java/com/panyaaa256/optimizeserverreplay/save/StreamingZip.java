package com.panyaaa256.optimizeserverreplay.save;

import com.panyaaa256.optimizeserverreplay.OptimizeServerReplay;
import com.panyaaa256.optimizeserverreplay.recovery.FlashbackRecovery;
import org.jspecify.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Packs the files of a Flashback recording into the replay while it is still recording, as soon
 * as Arcade is done writing each of them, and deletes them from the folder. Saving then only has
 * to add the last few.
 *
 * <p>The zip is written to {@link #PARTIAL} in the recording folder, and moved next to the
 * folder once it is complete. Everything runs on a thread of its own, so the writer thread of
 * Arcade only waits for it when the recording is saved or closed.
 */
public final class StreamingZip {
	public static final String PARTIAL = "osr-partial.zip";

	private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
	private static final int BUFFER_SIZE = 1 << 20;

	private final Path directory;
	private final int level;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "osr-replay-zip-" + THREAD_NUMBER.incrementAndGet());
		// Arcade's writer thread waits for this one whenever it matters.
		thread.setDaemon(true);
		return thread;
	});

	// Everything below is only used on the thread of the executor.
	private final Set<String> added = new HashSet<>();
	private @Nullable FileChannel channel;
	private @Nullable ZipOutputStream out;
	// After a failure, the files stay in the folder and the replay is put together when it is saved.
	private boolean failed = false;
	private boolean closed = false;

	public StreamingZip(Path directory, int level) {
		this.directory = directory;
		this.level = level;
	}

	/**
	 * Called on Arcade's writer thread for a file that Arcade will not write to again.
	 */
	public void add(String name) {
		if (this.executor.isShutdown()) {
			return;
		}
		this.executor.execute(() -> {
			if (this.failed || this.closed || this.added.contains(name)) {
				return;
			}
			try {
				Path file = this.directory.resolve(name);
				ZipOutputStream out = this.open();
				out.putNextEntry(new ZipEntry(name));
				Files.copy(file, out);
				out.closeEntry();
				out.flush();
				// The file is only deleted once its copy is on the disk.
				this.channel.force(true);
				this.added.add(name);
				Files.delete(file);
			} catch (IOException | RuntimeException e) {
				this.fail(e);
			}
		});
	}

	/**
	 * Called on Arcade's writer thread in place of zipping the whole folder, once every file of
	 * the recording is written.
	 */
	public void finish(Path output) throws IOException {
		this.await(this.executor.submit(() -> {
			this.finishOnThread(output);
			return null;
		}));
	}

	/**
	 * Called on Arcade's writer thread before it deletes the recording folder.
	 */
	public void close() {
		try {
			this.await(this.executor.submit(() -> {
				this.closeQuietly();
				return null;
			}));
		} catch (IOException e) {
			OptimizeServerReplay.LOGGER.warn("Failed to close the zip of the recording {}", this.directory, e);
		} finally {
			this.executor.shutdown();
		}
	}

	private void finishOnThread(Path output) throws IOException {
		if (!this.failed) {
			try {
				ZipOutputStream out = this.open();
				for (String name : this.remainingFiles()) {
					out.putNextEntry(new ZipEntry(name));
					Files.copy(this.directory.resolve(name), out);
					out.closeEntry();
				}
				out.close();
				this.out = null;
				this.channel = null;
				Files.move(this.directory.resolve(PARTIAL), output, StandardCopyOption.REPLACE_EXISTING);
				this.closed = true;
				return;
			} catch (IOException | RuntimeException e) {
				this.fail(e);
			}
		}

		// Some of the files may be in the partial zip and the rest in the folder, which is what a
		// recording looks like after a crash.
		this.closeQuietly();
		OptimizeServerReplay.LOGGER.info("Putting the replay of {} together from the partial zip and the folder", this.directory);
		FlashbackRecovery.assemble(this.directory, this.level, output);
	}

	private List<String> remainingFiles() throws IOException {
		try (Stream<Path> stream = Files.walk(this.directory)) {
			return stream.filter(Files::isRegularFile)
				.map(file -> this.directory.relativize(file).toString().replace('\\', '/'))
				.filter(name -> !name.equals(PARTIAL) && !name.equals(ReplayZip.METADATA_OLD) && !this.added.contains(name))
				.sorted()
				.toList();
		}
	}

	private ZipOutputStream open() throws IOException {
		if (this.closed) {
			throw new IOException("The zip of the recording " + this.directory + " is already closed");
		}
		if (this.out == null) {
			this.channel = FileChannel.open(
				this.directory.resolve(PARTIAL),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
			);
			this.out = new ZipOutputStream(new BufferedOutputStream(Channels.newOutputStream(this.channel), BUFFER_SIZE));
			this.out.setLevel(this.level);
		}
		return this.out;
	}

	private void fail(Exception e) {
		if (!this.failed) {
			this.failed = true;
			OptimizeServerReplay.LOGGER.error(
				"Failed to add to the zip of the recording {}, the rest of its files stay in the folder until it is saved",
				this.directory, e
			);
		}
	}

	// Leaves the partial zip without its list of entries, which the recovery does not need.
	private void closeQuietly() {
		this.closed = true;
		if (this.channel != null) {
			try {
				this.channel.close();
			} catch (IOException ignored) {
			}
		}
		this.out = null;
		this.channel = null;
	}

	private void await(Future<?> future) throws IOException {
		try {
			future.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while waiting for the zip of the recording " + this.directory, e);
		} catch (ExecutionException e) {
			if (e.getCause() instanceof IOException io) {
				throw io;
			}
			throw new IOException("Failed to save the zip of the recording " + this.directory, e.getCause());
		}
	}
}
