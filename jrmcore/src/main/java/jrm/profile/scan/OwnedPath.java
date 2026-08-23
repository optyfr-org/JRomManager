/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import jrm.profile.data.Entry;

/**
 * Holds a path that may own a zip {@link FileSystem}. Use in try-with-resources.
 * When {@code entryPath} is null, a filesystem is opened for the entry's parent
 * archive and closed on {@link #close()}; otherwise the borrowed path is not closed.
 */
final class OwnedPath implements AutoCloseable {
	private final Path path;
	private final FileSystem fileSystem;

	private OwnedPath(final Path borrowed) {
		this.path = borrowed;
		this.fileSystem = null;
	}

	private OwnedPath(final Entry entry) throws IOException {
		final var fs = FileSystems.newFileSystem(entry.getParent().getFile().toPath(), (ClassLoader) null);
		try {
			this.fileSystem = fs;
			this.path = fs.getPath(entry.getFile());
		} catch (RuntimeException e) {
			fs.close();
			throw e;
		}
	}

	static OwnedPath of(final Entry entry, final Path entryPath) throws IOException {
		return entryPath == null ? new OwnedPath(entry) : new OwnedPath(entryPath);
	}

	Path path() {
		return path;
	}

	@Override
	public void close() throws IOException {
		if (fileSystem != null)
			fileSystem.close();
	}
}
