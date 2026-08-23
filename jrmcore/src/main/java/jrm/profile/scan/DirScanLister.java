/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FilenameUtils;

import jrm.aui.progress.ProgressHandler;
import jrm.locale.Messages;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.profile.scan.options.FormatOptions.Ext;
import jrm.profile.data.Archive;
import jrm.profile.data.Container;
import jrm.profile.data.Container.Type;
import jrm.profile.data.Directory;
import jrm.profile.data.FakeDirectory;

/**
 * Handles initial filesystem traversal and registration of {@link Container} objects (archives, directories, fake entries)
 * before content scanning and hashing is performed.
 */
final class DirScanLister {
	private final DirScan ds;
	private final List<Container> containers;
	private final Map<String, Container> containersByName;
	private final List<Map.Entry<String, PathMatcher>> exclusions;
	private final ProgressHandler handler;

	DirScanLister(DirScan ds, List<Container> containers, Map<String, Container> containersByName,
			List<Map.Entry<String, PathMatcher>> exclusions, ProgressHandler handler) {
		this.ds = ds;
		this.containers = containers;
		this.containersByName = containersByName;
		this.exclusions = exclusions;
		this.handler = handler;
	}

	/**
	 * Lists and filters all physical files on the filesystem prior to performing full verification.
	 */
	void listFiles(final File dir, final ProgressHandler h, final Path path, final ScanOptions options) {
		handler.setProgress(String.format(Messages.getString("DirScan.ListingFiles"), ds.getRelativePath(dir.toPath())));

		try {
			final var i = new AtomicInteger();

			Files.walkFileTree(path, Collections.singleton(FileVisitOption.FOLLOW_LINKS), options.isDest ? 1 : 100, listFilesVisitor(dir, h, path, options, i));
			containersByName.entrySet().removeIf(entry -> !entry.getValue().isUp2date());
		} catch (IOException e) {
			Log.err("IOException when listing", e); //$NON-NLS-1$
		} catch (final Exception e) {
			Log.err("Other Exception when listing", e); //$NON-NLS-1$
		}

	}

	private SimpleFileVisitor<Path> listFilesVisitor(final File dir, final ProgressHandler h, final Path rootPath,
			final ScanOptions options, final AtomicInteger i) {
		return new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult visitFile(Path entryPath, BasicFileAttributes entryAttrs) throws IOException {
				return doVisitFile(entryPath, entryAttrs, dir, h, rootPath, options, i);
			}
		};
	}

	private FileVisitResult doVisitFile(Path entryPath, BasicFileAttributes entryAttrs, final File dir,
			final ProgressHandler h, final Path rootPath, final ScanOptions options, final AtomicInteger i) {
		if (h.isCancel())
			return FileVisitResult.TERMINATE;
		if (rootPath.equals(entryPath))
			return FileVisitResult.CONTINUE;
		final var entryFile = entryPath.toFile();
		try {
			if (options.isDest) {
				if (isExcluded(entryPath))
					return FileVisitResult.CONTINUE;
				listFilesDest(entryFile, entryAttrs);
			} else
				listFilesSrc(rootPath, entryPath, entryFile, entryAttrs, options);
			updateVisitProgress(entryPath, rootPath, dir, i);
		} catch (final IOException e) {
			Log.err(e.getMessage(), e);
		} catch (final BreakException _) {
			h.doCancel();
		}

		updateVisitProgress(entryPath, rootPath, dir, i);
		return FileVisitResult.CONTINUE;
	}

	private boolean isExcluded(Path entryPath) {
		return exclusions.stream().anyMatch(pm -> {
			if (pm.getValue().matches(entryPath)) {
				Log.info(() -> "match for exclusion %s on %s, will skip...".formatted(pm.getKey(), entryPath.toString()));
				return true;
			}
			return false;
		});
	}

	private void updateVisitProgress(Path entryPath, final Path rootPath, final File dir, final AtomicInteger i) {
		handler.setProgress(rootPath.relativize(entryPath).toString(), -1); // $NON-NLS-1$
		handler.setProgress2(String.format(Messages.getString("DirScan.ListingFiles2"), //$NON-NLS-1$
				ds.getRelativePath(dir.toPath()), i.incrementAndGet()), 0);
	}

	private void listFilesSrc(final Path rootPath, Path entryPath, final File entryFile, final BasicFileAttributes entryAttr, ScanOptions options) throws IOException {
		if (entryAttr.isRegularFile()) {
			final var entryType = Container.getType(entryFile);
			if (entryType == Type.UNK || options.archivesAndChdAsRoms) {
				if (rootPath.equals(entryFile.getParentFile().toPath())) {
					listFilesSrcUnknown(entryFile, entryAttr, entryType);
				} else {
					listFilesSrcParentDir(rootPath, entryPath, entryFile, entryAttr);
				}
			} else {
				listFilesSrcArchive(rootPath, entryPath, entryFile, entryAttr);
			}
		} else if (options.includeEmptyDirs) {
			listFilesSrcEmptyDir(rootPath, entryPath, entryFile, entryAttr);
		}
	}

	private void listFilesSrcEmptyDir(final Path rootPath, Path entryPath, final File entryFile, final BasicFileAttributes entryAttrs) throws IOException {
		try (DirectoryStream<Path> dirstream = Files.newDirectoryStream(entryPath)) {
			if (!dirstream.iterator().hasNext()) {
				final Container existingContainer;
				final var relativePath = rootPath.relativize(entryPath);
				if (null == (existingContainer = containersByName.get(relativePath.toString()))
						|| (existingContainer.getModified() != entryAttrs.lastModifiedTime().toMillis() && !existingContainer.isUp2date())) {
					final var newContainer = new Directory(entryFile, ds.getRelativePath(entryFile), entryAttrs);
					newContainer.setUp2date(true);
					containers.add(newContainer);
					containersByName.put(relativePath.toString(), newContainer);
					if (relativePath.getNameCount() > 1)
						containersByName.put(relativePath.getFileName().toString(), newContainer);
				} else if (!existingContainer.isUp2date()) {
					existingContainer.setUp2date(true);
					containers.add(existingContainer);
					if (relativePath.getNameCount() > 1)
						containersByName.putIfAbsent(relativePath.getFileName().toString(), existingContainer);
				}
			}
		}
	}

	private void listFilesSrcParentDir(final Path rootPath, Path entryPath, final File entryFile, final BasicFileAttributes entryAttrs) throws IOException {
		final Container existingContainer;
		final var parentDir = entryFile.getParentFile();
		final var parentAttr = Files.readAttributes(entryPath.getParent(), BasicFileAttributes.class);
		final var relativePath = rootPath.relativize(entryPath.getParent());
		if (null == (existingContainer = containersByName.get(relativePath.toString()))
				|| (existingContainer.getModified() != parentAttr.lastModifiedTime().toMillis() && !existingContainer.isUp2date())) {
			final var newContainer = new Directory(parentDir, ds.getRelativePath(parentDir), entryAttrs);
			newContainer.setUp2date(true);
			containers.add(newContainer);
			containersByName.put(relativePath.toString(), newContainer);
			if (relativePath.getNameCount() > 1)
				containersByName.put(relativePath.getFileName().toString(), newContainer);
		} else if (!existingContainer.isUp2date()) {
			existingContainer.setUp2date(true);
			containers.add(existingContainer);
			if (relativePath.getNameCount() > 1)
				containersByName.putIfAbsent(relativePath.getFileName().toString(), existingContainer);
		}
	}

	private void listFilesSrcUnknown(final File file, final BasicFileAttributes attr, final Container.Type type) {
		final Container existingContainer;
		final var fname = type == Type.UNK ? (FilenameUtils.getBaseName(file.getName()) + Ext.FAKE) : file.getName();
		if (null == (existingContainer = containersByName.get(fname))
				|| (existingContainer.getModified() != attr.lastModifiedTime().toMillis() && !existingContainer.isUp2date())) {
			final var newContainer = new FakeDirectory(file, ds.getRelativePath(file), attr);
			newContainer.setUp2date(true);
			containers.add(newContainer);
			containersByName.put(fname, newContainer);
		} else if (!existingContainer.isUp2date()) {
			existingContainer.setUp2date(true);
			containers.add(existingContainer);
		}
	}

	private void listFilesSrcArchive(final Path rootPath, Path entryPath, final File file, final BasicFileAttributes attr) {
		final Container existingContainer;
		final var relativePath = rootPath.relativize(entryPath);
		if (null == (existingContainer = containersByName.get(relativePath.toString()))
				|| ((existingContainer.getModified() != attr.lastModifiedTime().toMillis() || existingContainer.getSize() != attr.size()) && !existingContainer.isUp2date())) {
			final var newContainer = new Archive(file, ds.getRelativePath(file), attr);
			newContainer.setUp2date(true);
			containers.add(newContainer);
			containersByName.put(relativePath.toString(), newContainer);
			if (relativePath.getNameCount() > 1)
				containersByName.put(relativePath.getFileName().toString(), newContainer);
		} else if (!existingContainer.isUp2date()) {
			existingContainer.setUp2date(true);
			containers.add(existingContainer);
			if (relativePath.getNameCount() > 1)
				containersByName.putIfAbsent(relativePath.getFileName().toString(), existingContainer);
		}
	}

	private void listFilesDest(final File file, final BasicFileAttributes attr) {
		final var type = attr.isRegularFile() ? Container.getType(file) : Type.DIR;
		final var fname = type == Type.UNK ? (FilenameUtils.getBaseName(file.getName()) + Ext.FAKE) : file.getName();
		var c = containersByName.get(fname);
		if (null == c || ((c.getModified() != attr.lastModifiedTime().toMillis() || (c instanceof Archive && c.getSize() != attr.size())) && !c.isUp2date())) {
			if (attr.isRegularFile()) {
				if (type != Container.Type.UNK)
					c = new Archive(file, ds.getRelativePath(file), attr);
				else
					c = new FakeDirectory(file, ds.getRelativePath(file), attr);
			} else
				c = new Directory(file, ds.getRelativePath(file), attr);
			c.setUp2date(true);
			containers.add(c);
			containersByName.put(fname, c);
		} else if (!c.isUp2date()) {
			c.setUp2date(true);
			containers.add(c);
		}
	}
}
