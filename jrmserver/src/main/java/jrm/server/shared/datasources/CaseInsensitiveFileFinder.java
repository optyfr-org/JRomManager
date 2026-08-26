package jrm.server.shared.datasources;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import jrm.misc.Log;

/**
 * Utility class for finding files or directories on the filesystem ignoring case sensitivity.
 */
public final class CaseInsensitiveFileFinder {

	private CaseInsensitiveFileFinder() {
		throw new IllegalStateException("Utility class");
	}

	private static Path findDir(Path dir) throws IOException {
		try (final var stream = Files.list(dir.getParent())) {
			return stream.filter(Files::isDirectory).filter(p -> p.getFileName().toString().equalsIgnoreCase(dir.getFileName().toString())).findFirst().orElse(null);
		}
	}

	private static Path findFile(Path file) throws IOException {
		try (final var stream = Files.list(file.getParent())) {
			return stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().equalsIgnoreCase(file.getFileName().toString())).findFirst().orElse(null);
		}
	}

	public static Optional<Path> findFileIgnoreCase(final Path parentDir, final String fileName) {
		try {
			final var dir = findDir(parentDir);
			if (dir == null)
				return Optional.empty();
			final var resolved = dir.resolve(fileName);
			Path p = findFile(resolved);
			if (p == null)
				p = findDir(resolved);
			return p != null ? Optional.of(p) : Optional.empty();
		} catch (IOException e) {
			Log.err(e.getMessage(), e);
			return Optional.empty();
		}
	}

	public static Optional<File> findFileIgnoreCase(final String file) {
		var path = Paths.get(file);
		return findFileIgnoreCase(path.getParent(), path.getFileName().toString()).map(Path::toFile);
	}

	public static Optional<File> findFileIgnoreCase(final String parentDir, final String fileName) {
		return findFileIgnoreCase(Paths.get(parentDir), fileName).map(Path::toFile);
	}

	public static Optional<Path> findFileIgnoreCase(final Path path) {
		return findFileIgnoreCase(path.getParent(), path.getFileName().toString());
	}
}
