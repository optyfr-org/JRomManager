/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import java.io.File;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Describes a filesystem directory that can eventually be linked to an {@link AnywareBase} set. This class inherits from
 * {@link Container} and represents uncompressed folders.
 *
 * @author optyfr
 */
public class Directory extends Container {
    /**
     * Constructs a directory where the related database set is known.
     *
     * @param file the directory {@link File} object
     * @param relfile the relativized/relative directory path reference to show to the user
     * @param m the corresponding {@link AnywareBase} set
     */
    public Directory(final File file, final File relfile, final AnywareBase m) {
        super(Type.DIR, file, relfile, m);
    }

    /**
     * Constructs a directory with no related database set.
     *
     * @param file the directory {@link File} object
     * @param relfile the relativized/relative directory path reference to show to the user
     * @param attr the directory basic file attributes
     */
    public Directory(final File file, final File relfile, final BasicFileAttributes attr) {
        super(Type.DIR, file, relfile, attr);
    }

}
