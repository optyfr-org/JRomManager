package jrm.profile.manager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import jrm.misc.Log;

public final class DatFileSearch {
	public static final int MAX_DAT_SEARCH_DEPTH = 100;

	public static List<File> searchDats(File file, List<File> files) {
		if (file == null || files == null)
			return files;
		final var pending = new ArrayDeque<PendingDat>();
		pending.add(new PendingDat(file, 0));
		final var visited = new HashSet<String>();
		while (!pending.isEmpty())
			visitDatEntry(pending.removeFirst(), pending, visited, files);
		return files;
	}

	private static void visitDatEntry(final PendingDat current, final ArrayDeque<PendingDat> pending, final HashSet<String> visited, final List<File> files) {
		final File currentFile = current.file();
		if (currentFile == null)
			return;
		if (currentFile.isFile()) {
			collectDatFile(currentFile, files);
			return;
		}
		if (current.depth() >= MAX_DAT_SEARCH_DEPTH || !currentFile.isDirectory())
			return;
		final String canonical = canonicalPath(currentFile);
		if (canonical == null || !visited.add(canonical))
			return;
		enqueueDatChildren(current, pending);
	}

	private static void collectDatFile(final File file, final List<File> files) {
		if (FilenameUtils.isExtension(file.getName(), "xml", "dat") || MameExecutable.isLaunchable(file))
			files.add(file);
	}

	private static String canonicalPath(final File file) {
		try {
			return file.getCanonicalPath();
		} catch (final IOException e) {
			Log.warn(e.getMessage());
			return null;
		}
	}

	private static void enqueueDatChildren(final PendingDat current, final ArrayDeque<PendingDat> pending) {
		try (final var stream = Files.newDirectoryStream(current.file().toPath())) {
			for (final var path : stream)
				pending.add(new PendingDat(path.toFile(), current.depth() + 1));
		} catch (IOException e) {
			Log.warn(e.getMessage());
		}
	}

	private record PendingDat(File file, int depth) {
	}
}
