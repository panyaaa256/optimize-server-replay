package com.panyaaa256.optimizeserverreplay.recovery;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.panyaaa256.optimizeserverreplay.OptimizeServerReplay;
import com.panyaaa256.optimizeserverreplay.config.FilterConfig;
import com.panyaaa256.optimizeserverreplay.save.ReplayZip;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Turns the folder of a Flashback recording that the server never finished saving back into a
 * replay, which Server Replay only does for the ReplayMod format.
 *
 * <p>Arcade writes a replay chunk to the folder first and lists it in {@code metadata.json} after,
 * so every chunk that the metadata lists is complete. Only the chunk that was being recorded,
 * which is in memory until it ends, is lost.
 */
public final class FlashbackRecovery {
	private static final String METADATA = ReplayZip.METADATA;
	private static final String METADATA_OLD = ReplayZip.METADATA_OLD;
	private static final String CHUNK_CACHES = "level_chunk_caches";
	private static final String CHUNK_EXTENSION = ".flashback";
	private static final String ZIP_EXTENSION = ".zip";

	private FlashbackRecovery() {
	}

	/**
	 * @return whether the path was a Flashback recording, which then needs no other recovery
	 */
	public static boolean tryRecover(Path recording) {
		if (!isFlashbackRecording(recording)) {
			return false;
		}

		try {
			Metadata metadata = readMetadata(recording);
			if (metadata == null) {
				OptimizeServerReplay.LOGGER.warn(
					"Could not recover the Flashback recording {}: it has no readable metadata, so the folder is left as it is",
					recording
				);
				return true;
			}
			if (metadata.chunks().isEmpty()) {
				OptimizeServerReplay.LOGGER.warn(
					"Could not recover the Flashback recording {}: it ended before its first replay chunk was complete, "
						+ "so the folder is left as it is",
					recording
				);
				return true;
			}

			Path output = findOutputPath(recording);
			Path temp = output.resolveSibling(output.getFileName() + ".part");
			try {
				zip(recording, metadata, temp);
				Files.move(temp, output, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException | RuntimeException e) {
				Files.deleteIfExists(temp);
				throw e;
			}
			deleteRecursively(recording);
			OptimizeServerReplay.LOGGER.info(
				"Recovered the Flashback recording {} with {} replay chunks to {}",
				recording, metadata.chunks().size(), output
			);
		} catch (IOException | RuntimeException e) {
			OptimizeServerReplay.LOGGER.error(
				"Failed to recover the Flashback recording {}, the folder is left as it is", recording, e
			);
		}
		return true;
	}

	// Server Replay keeps a ReplayMod recording next to a folder with the same name and ".tmp".
	private static boolean isFlashbackRecording(Path recording) {
		if (!Files.isDirectory(recording)) {
			return false;
		}
		if (Files.exists(recording.resolveSibling(recording.getFileName() + ".tmp"))) {
			return false;
		}
		return Files.isRegularFile(recording.resolve(METADATA)) || Files.isRegularFile(recording.resolve(METADATA_OLD));
	}

	// Arcade moves metadata.json to metadata.json.old before writing the new one, so a crash in
	// between leaves only the old one, which lists one chunk fewer.
	private static @Nullable Metadata readMetadata(Path recording) {
		for (String name : List.of(METADATA, METADATA_OLD)) {
			Metadata metadata = readMetadata(recording, recording.resolve(name));
			if (metadata != null) {
				return metadata;
			}
		}
		return null;
	}

	private static @Nullable Metadata readMetadata(Path recording, Path file) {
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try {
			byte[] bytes = Files.readAllBytes(file);
			JsonElement json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
			if (!(json instanceof JsonObject object) || !(object.get("chunks") instanceof JsonObject chunks)) {
				OptimizeServerReplay.LOGGER.warn("{} has no list of replay chunks", file);
				return null;
			}
			for (String chunk : chunks.keySet()) {
				if (!Files.isRegularFile(recording.resolve(chunk))) {
					OptimizeServerReplay.LOGGER.warn("{} lists the replay chunk {}, which is missing", file, chunk);
					return null;
				}
			}
			return new Metadata(bytes, chunks.keySet());
		} catch (IOException | RuntimeException e) {
			OptimizeServerReplay.LOGGER.warn("Could not read {}", file, e);
			return null;
		}
	}

	// Never overwrite a replay that is already there.
	private static Path findOutputPath(Path recording) {
		String name = recording.getFileName().toString();
		Path output = recording.resolveSibling(name + ZIP_EXTENSION);
		for (int i = 1; Files.exists(output); i++) {
			output = recording.resolveSibling(name + " (recovered " + i + ")" + ZIP_EXTENSION);
		}
		return output;
	}

	private static void zip(Path recording, Metadata metadata, Path output) throws IOException {
		List<Path> files;
		try (Stream<Path> stream = Files.walk(recording)) {
			files = stream.filter(Files::isRegularFile).sorted().toList();
		}

		try (ZipOutputStream out = ReplayZip.open(output, FilterConfig.current().zipCompressionLevel())) {
			out.putNextEntry(new ZipEntry(METADATA));
			out.write(metadata.bytes());
			out.closeEntry();

			for (Path file : files) {
				Path relative = recording.relativize(file);
				String name = relative.toString().replace('\\', '/');
				if (!shouldInclude(relative, name, metadata)) {
					continue;
				}

				out.putNextEntry(new ZipEntry(name));
				if (relative.getNameCount() == 2 && relative.getName(0).toString().equals(CHUNK_CACHES)) {
					copy(file, completeChunkCacheLength(file), out);
				} else {
					Files.copy(file, out);
				}
				out.closeEntry();
			}
		}
	}

	// A replay chunk that the metadata does not list was still being written.
	private static boolean shouldInclude(Path relative, String name, Metadata metadata) {
		if (relative.getNameCount() != 1) {
			return true;
		}
		if (name.equals(METADATA) || name.equals(METADATA_OLD)) {
			return false;
		}
		return !name.endsWith(CHUNK_EXTENSION) || metadata.chunks().contains(name);
	}

	/**
	 * A chunk cache is a list of entries, each an int with the size of its data followed by the
	 * data. The last entry can be cut short, and a reader fails on the whole file when it reaches
	 * it, so only the complete entries are kept. The replay chunks never refer to the cut entry,
	 * because Arcade writes it before the chunk that refers to it is complete.
	 */
	private static long completeChunkCacheLength(Path file) throws IOException {
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
			long length = channel.size();
			ByteBuffer header = ByteBuffer.allocate(Integer.BYTES);
			long position = 0;
			while (position + Integer.BYTES <= length) {
				header.clear();
				while (header.hasRemaining()) {
					if (channel.read(header, position + header.position()) < 0) {
						return position;
					}
				}
				int size = header.getInt(0);
				long next = position + Integer.BYTES + size;
				if (size < 0 || next > length) {
					break;
				}
				position = next;
			}
			return position;
		}
	}

	private static void copy(Path file, long length, OutputStream out) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buffer = new byte[8192];
			long remaining = length;
			while (remaining > 0) {
				int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
				if (read < 0) {
					throw new IOException("The file " + file + " got shorter while it was being copied");
				}
				out.write(buffer, 0, read);
				remaining -= read;
			}
		}
	}

	private static void deleteRecursively(Path directory) throws IOException {
		List<Path> paths;
		try (Stream<Path> stream = Files.walk(directory)) {
			paths = stream.sorted(Comparator.reverseOrder()).toList();
		}
		for (Path path : paths) {
			Files.delete(path);
		}
	}

	private record Metadata(byte[] bytes, Set<String> chunks) {
	}
}
