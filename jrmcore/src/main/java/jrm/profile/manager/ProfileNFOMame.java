/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.manager;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;

import jrm.locale.Messages;
import jrm.misc.Log;
import lombok.Getter;
import lombok.Setter;

/**
 * Manages physical properties, metadata paths, and current integrity status of a MAME (or variants) executable associated with a
 * profile. Handles detection of updates and custom serialization of the metadata paths.
 * 
 * @author optyfr
 */
public final class ProfileNFOMame implements Serializable {
    /**
     * Serial version UID for maintaining serialization compatibility across releases.
     */
    private static final long serialVersionUID = 2L;

    /**
     * The physical location of the MAME executable file on disk.
     */
    private File file = null;

    /**
     * The last recorded modification timestamp of the MAME executable file.
     */
    private Long modified = 0L;

    /**
     * Indicates if software lists database parsing is configured and active.
     */
    private boolean sl = false;

    /**
     * The XML DAT file holding compiled ROM descriptions extracted from the executable.
     * 
     * @param fileroms the primary ROMs XML DAT file reference
     * 
     * @return the primary ROMs XML DAT file reference
     */
    private @Getter @Setter File fileroms = null;

    /**
     * The XML DAT file holding Software List definitions extracted from the executable.
     * 
     * @param filesl the software list XML DAT file reference
     * 
     * @return the software list XML DAT file reference
     */
    private @Getter @Setter File filesl = null;

    /**
     * Default zero-argument constructor for initializing an empty MAME metadata profile.
     */
    public ProfileNFOMame() {
        // Default constructor
    }

    /**
     * Categorizes the physical existence, version synchronization, and integrity status of the configured MAME executable and its
     * corresponding extracted XML databases.
     * 
     * @author optyfr
     */
    public enum MameStatus {
        /**
         * The MAME configuration is absent or completely uninitialized.
         */
        UNKNOWN(Messages.getString("ProfileNFOMame.Unknown")), //$NON-NLS-1$
        /**
         * The configured MAME executable is present and all extracted XML database caches are up to date.
         */
        UPTODATE(Messages.getString("ProfileNFOMame.UpToDate")), //$NON-NLS-1$
        /**
         * The MAME executable has been modified on disk since the last extraction, or extracted XML database files are missing.
         */
        NEEDUPDATE(Messages.getString("ProfileNFOMame.NeedUpdate")), //$NON-NLS-1$
        /**
         * The configured MAME executable file could not be found at its recorded file path.
         */
        NOTFOUND(Messages.getString("ProfileNFOMame.NotFound")); //$NON-NLS-1$

        /**
         * The translated, human-readable descriptive message for this status.
         */
        private final String msg;

        /**
         * Internal enum constructor linking the status with its translated description.
         * 
         * @param msg the localized status description
         */
        private MameStatus(final String msg) {
            this.msg = msg;
        }

        /**
         * Returns the localized text message describing the status.
         * 
         * @return the localized status message
         */
        public String getMsg() {
            return msg;
        }
    }

    /**
     * Configures this profile with a physical MAME executable file path, recording its modification date and registering whether
     * software lists should be extracted.
     * 
     * @param mame the physical MAME executable file
     * @param sl {@code true} to configure and enable software lists; {@code false} to disable
     */
    public void set(final File mame, final boolean sl) {
        if (mame.exists()) {
            file = mame;
            modified = mame.lastModified();
            this.sl = sl;
        }
    }

    /**
     * Determines the current status of the MAME configuration, checking executable modification timestamps and verify the presence
     * of the extracted ROM and software list database files on disk.
     * 
     * @return the evaluated {@link MameStatus}
     */
    public MameStatus getStatus() {
        if (file != null) {
            if (file.exists()) {
                if (file.lastModified() > modified)
                    return MameStatus.NEEDUPDATE;
                if (fileroms == null || !fileroms.exists())
                    return MameStatus.NEEDUPDATE;
                if (isSL() && (filesl == null || !filesl.exists()))
                    return MameStatus.NEEDUPDATE;
                return MameStatus.UPTODATE;
            }
            return MameStatus.NOTFOUND;
        }
        return MameStatus.UNKNOWN;
    }

    /**
     * Retrieves the physical MAME executable file.
     * 
     * @return the MAME executable file reference, or {@code null} if not configured
     */
    public File getFile() {
        return file;
    }

    /**
     * Resynchronizes the internal modification timestamp of the executable to the physical file's current modification date on
     * disk, indicating that extraction is complete.
     */
    public void setUpdated() {
        if (file != null) {
            modified = file.lastModified();
        }
    }

    /**
     * Re-associates this metadata with a new physical MAME executable file path, evaluating its modification state and returning
     * the updated status.
     * 
     * @param newFile the new physical MAME executable path on disk
     * 
     * @return the recalculated {@link MameStatus} after relocation
     */
    public MameStatus relocate(final File newFile) {
        if (newFile != null) {
            if (newFile.exists()) {
                file = newFile;
                if (file.lastModified() > modified)
                    return MameStatus.NEEDUPDATE;
                return MameStatus.UPTODATE;
            }
            return MameStatus.NOTFOUND;
        }
        return MameStatus.UNKNOWN;
    }

    /**
     * Indicates whether software lists are configured and tracked by this profile.
     * 
     * @return {@code true} if software list metadata is enabled; {@code false} otherwise
     */
    public boolean isSL() {
        return sl;
    }

    /**
     * Physically deletes extracted database XML files that sit beside the given profile file.
     * Paths outside the profile directory are ignored so crafted serialized metadata cannot delete arbitrary files.
     *
     * @param profileFile the trusted profile path whose directory bounds companion deletion
     */
    public void deleteAlongside(final File profileFile) {
        try {
            if (isSiblingOf(fileroms, profileFile))
                Files.deleteIfExists(fileroms.toPath());
            if (isSiblingOf(filesl, profileFile))
                Files.deleteIfExists(filesl.toPath());
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Keeps only companion DAT paths that resolve as siblings of {@code profileFile}; clears the rest.
     *
     * @param profileFile the trusted profile path
     */
    void retainOnlySiblingPaths(final File profileFile) {
        if (!isSiblingOf(fileroms, profileFile))
            fileroms = null;
        if (!isSiblingOf(filesl, profileFile))
            filesl = null;
    }

    private static boolean isSiblingOf(final File candidate, final File profileFile) {
        if (candidate == null || profileFile == null)
            return false;
        try {
            final var profileParent = profileFile.getCanonicalFile().getParentFile();
            final var candidateParent = candidate.getCanonicalFile().getParentFile();
            return profileParent != null && profileParent.equals(candidateParent);
        } catch (IOException e) {
            Log.err("Failed to validate companion path against profile directory", e);
            return false;
        }
    }
}
