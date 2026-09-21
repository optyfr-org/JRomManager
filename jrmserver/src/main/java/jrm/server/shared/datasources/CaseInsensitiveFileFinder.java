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
 * <p>
 * Every existing component of the searched path is resolved against the filesystem and case-corrected, so the returned
 * path carries the on-disk casing of all its components and stays {@link Object#equals(Object)} comparable in a
 * case-sensitive manner with paths built from real filesystem entries.
 */
public final class CaseInsensitiveFileFinder {

	private CaseInsensitiveFileFinder() {
		throw new IllegalStateException("Utility class");
	}

	/**
	 * Finds a file or a directory by its complete path, ignoring case.
	 *
	 * @param parentDir the (possibly badly-cased) absolute parent directory, or an existing relative directory
	 * @param fileName the file name to find in the case-corrected parent directory
	 * @return an Optional containing the case-corrected path of the found entry, or empty if not found
	 */
	public static Optional<Path> findFileIgnoreCase(final Path parentDir, final String fileName) {
		try {
			final var dir = findDirIgnoreCase(parentDir);
			if (dir == null)
				return Optional.empty();
			return Optional.ofNullable(findEntryIgnoreCase(dir, dir.resolve(fileName)));
		} catch (IOException e) {
			Log.err(e.getMessage(), e);
			return Optional.empty();
		}
	}

	/**
	 * Finds a file or a directory by its complete path, ignoring case.
	 *
	 * @param file the string representation of the (possibly badly-cased) path to find
	 * @return an Optional containing the case-corrected File, or empty if not found or if the path has no parent
	 */
	public static Optional<File> findFileIgnoreCase(final String file) {
		final var path = Paths.get(file);
		if (path.getParent() == null || path.getFileName() == null)
			return Optional.empty();
		return findFileIgnoreCase(path.getParent(), path.getFileName().toString()).map(Path::toFile);
	}

	/**
	 * Finds a file or a directory by parent directory and file name, ignoring case.
	 *
	 * @param parentDir the string representation of the (possibly badly-cased) parent directory
	 * @param fileName the file name to find in the case-corrected parent directory
	 * @return an Optional containing the case-corrected File, or empty if not found
	 */
	public static Optional<File> findFileIgnoreCase(final String parentDir, final String fileName) {
		return findFileIgnoreCase(Paths.get(parentDir), fileName).map(Path::toFile);
	}

	/**
	 * Finds a file or a directory by its complete path, ignoring case.
	 *
	 * @param path the (possibly badly-cased) path to find
	 * @return an Optional containing the case-corrected path, or empty if not found or if the path has no parent
	 */
	public static Optional<Path> findFileIgnoreCase(final Path path) {
		if (path.getParent() == null || path.getFileName() == null)
			return Optional.empty();
		return findFileIgnoreCase(path.getParent(), path.getFileName().toString());
	}

	/**
	 * Resolves each existing component of the given directory from the filesystem root, ignoring case, so that the
	 * returned path keeps the on-disk casing of every component.
	 *
	 * @param dir the directory path to case-correct
	 * @return the case-corrected directory path, or null if a component cannot be resolved
	 * @throws IOException if an I/O error occurs while listing a component
	 */
	private static Path findDirIgnoreCase(final Path dir) throws IOException {
		if (dir == null)
			return null;
		if (!dir.isAbsolute())
			return Files.exists(dir) ? dir : null;
		Path current = dir.getRoot();
		for (int i = 0; i < dir.getNameCount(); i++) {
			current = findEntryIgnoreCase(current, current.resolve(dir.getName(i)));
			if (current == null)
				return null;
		}
		return current;
	}

	/**
	 * Finds the entry of the {@code search} path within its parent directory, ignoring case and entry type.
	 *
	 * @param parent the parent directory to list
	 * @param search the path whose last component is searched
	 * @return the case-corrected path of the found entry, or null if not found
	 * @throws IOException if an I/O error occurs while listing the parent directory
	 */
	private static Path findEntryIgnoreCase(final Path parent, final Path search) throws IOException {
		try (final var stream = Files.list(parent)) {
			return stream.filter(p -> p.getFileName().toString().equalsIgnoreCase(search.getFileName().toString())).findFirst().orElse(null);
		}
	}
}
