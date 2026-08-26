package jrm.batch;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;

import jrm.aui.basic.AbstractSrcDstResult;
import jrm.io.torrent.TorrentFile;
import jrm.misc.Log;
import jrm.security.PathAbstractor;

final class TorrentArchiveExtractor<T extends AbstractSrcDstResult> {
	private final TorrentChecker<T> checker;

	TorrentArchiveExtractor(TorrentChecker<T> checker) {
		this.checker = checker;
	}

	void detectArchives(final T sdr, final List<TorrentFile> tfiles, final boolean unarchive) {
		final var archives = new HashSet<Path>();
		final Path destRoot = PathAbstractor.getAbsolutePath(checker.session, sdr.getDst()).toAbsolutePath().normalize();
		collectCandidateArchives(tfiles, destRoot, archives);
		excludeActualTorrentFiles(tfiles, destRoot, archives);
		for (Path archive : archives) {
			if (unarchive) {
				unarchive(archive);
			} else {
				Log.debug(archive);
			}
		}
	}

	private void collectCandidateArchives(final List<TorrentFile> tfiles, final Path destRoot, final Set<Path> archives) {
		final var components = new HashSet<String>();
		for (final TorrentFile tfile : tfiles) {
			final List<String> filedirs = tfile.getFileDirs();
			if (filedirs.size() > 1) {
				final String path = filedirs.get(0);
				if (components.add(path)) {
					try {
						isArchive(archives, TorrentChecker.resolveTorrentEntry(destRoot, List.of(path)));
					} catch (IOException e) {
						Log.debug(e.getMessage());
					}
				}
			}
		}
	}

	private void excludeActualTorrentFiles(final List<TorrentFile> tfiles, final Path destRoot, final Set<Path> archives) {
		for (final TorrentFile tfile : tfiles) {
			try {
				archives.remove(TorrentChecker.resolveTorrentEntry(destRoot, tfile.getFileDirs()));
			} catch (IOException e) {
				Log.debug(e.getMessage());
			}
		}
	}

	private void isArchive(final Set<Path> archives, Path file) {
		final Path parent = file.getParent();
		if (parent != null) {
			final Path filename = file.getFileName();
			if (filename != null) {
				final Path archive = parent.resolve(filename.toString() + ".zip");
				if (Files.exists(archive)) {
					archives.add(archive);
				}
			}
		}
	}

	private void unarchive(Path archive) {
		try {
			Path parent = archive.getParent();
			if (parent != null) {
				Path filename = archive.getFileName();
				if (filename != null) {
					unzip(archive, parent.resolve(FilenameUtils.getBaseName(filename.toString())));
				}
			}
		} catch (IOException e) {
			Log.err(e.getMessage(), e);
		}
	}

	private void unzip(final Path zipFile, final Path destDir) throws IOException {
		if (Files.notExists(destDir)) {
			Files.createDirectories(destDir);
		}
		final Path normalizedDestDir = destDir.toAbsolutePath().normalize();

		try (final var zipFileSystem = FileSystems.newFileSystem(zipFile, (ClassLoader) null)) {
			Log.debug(() -> "unzipping : " + zipFile);
			final Path root = zipFileSystem.getRootDirectories().iterator().next();

			Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					final Path destFile = resolveZipEntry(normalizedDestDir, root, file);
					final Path parent = destFile.getParent();
					if (parent != null && Files.notExists(parent)) {
						Files.createDirectories(parent);
					}
					try {
						Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING);
					} catch (DirectoryNotEmptyException _) {
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					if (dir.equals(root)) {
						return FileVisitResult.CONTINUE;
					}
					final Path dirToCreate = resolveZipEntry(normalizedDestDir, root, dir);
					if (Files.notExists(dirToCreate)) {
						Files.createDirectories(dirToCreate);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}

	static Path resolveZipEntry(final Path destDir, final Path zipRoot, final Path zipEntry) throws IOException {
		final Path relative = zipRoot.relativize(zipEntry);
		var relativeName = relative.toString().replace('\\', '/');
		while (relativeName.startsWith("/")) {
			relativeName = relativeName.substring(1);
		}
		if (relativeName.isEmpty() || ".".equals(relativeName)) {
			return destDir;
		}
		final Path resolved = destDir.resolve(relativeName).normalize();
		if (!resolved.startsWith(destDir)) {
			throw new IOException("Zip entry escapes destination directory: " + relativeName);
		}
		return resolved;
	}
}
