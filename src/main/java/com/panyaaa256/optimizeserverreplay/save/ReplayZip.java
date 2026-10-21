package com.panyaaa256.optimizeserverreplay.save;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Packs the folder of a Flashback recording into the replay file, in place of Arcade's
 * {@code FileUtils.zip}.
 */
public final class ReplayZip {
	public static final String METADATA = "metadata.json";
	public static final String METADATA_OLD = "metadata.json.old";

	// ZipOutputStream hands its output on in pieces of 512 bytes, which would each be a write
	// to the file without a buffer.
	private static final int BUFFER_SIZE = 1 << 20;

	private ReplayZip() {
	}

	public static ZipOutputStream open(Path output, int level) throws IOException {
		ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(output), BUFFER_SIZE));
		out.setLevel(level);
		return out;
	}

	/**
	 * Arcade keeps the previous metadata in {@code metadata.json.old} while recording, which
	 * the replay does not need once it is saved.
	 */
	public static void zip(Path source, Path output, int level) throws IOException {
		List<Path> files;
		try (Stream<Path> stream = Files.walk(source)) {
			files = stream.filter(Files::isRegularFile).sorted().toList();
		}

		try (ZipOutputStream out = open(output, level)) {
			for (Path file : files) {
				String name = source.relativize(file).toString().replace('\\', '/');
				if (name.equals(METADATA_OLD)) {
					continue;
				}
				out.putNextEntry(new ZipEntry(name));
				Files.copy(file, out);
				out.closeEntry();
			}
		}
	}
}
