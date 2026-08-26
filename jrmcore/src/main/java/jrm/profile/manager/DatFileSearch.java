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
		while (!pending.isEmpty()) {
			final var current = pending.removeFirst();
			final File currentFile = current.file();
			if (currentFile == null)
				continue;
			if (currentFile.isFile()) {
				if (FilenameUtils.isExtension(currentFile.getName(), "xml", "dat") || MameExecutable.isLaunchable(currentFile))
					files.add(currentFile);
				continue;
			}
			if (current.depth() >= MAX_DAT_SEARCH_DEPTH || !currentFile.isDirectory())
				continue;
			final String canonical;
			try {
				canonical = currentFile.getCanonicalPath();
			} catch (final IOException e) {
				Log.warn(e.getMessage());
				continue;
			}
			if (!visited.add(canonical))
				continue;
			try (final var stream = Files.newDirectoryStream(currentFile.toPath())) {
				for (final var path : stream)
					pending.add(new PendingDat(path.toFile(), current.depth() + 1));
			} catch (IOException e) {
				Log.warn(e.getMessage());
			}
		}
		return files;
	}

	private record PendingDat(File file, int depth) {
	}
}
