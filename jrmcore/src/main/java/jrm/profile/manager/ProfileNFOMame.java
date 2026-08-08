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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.Optional;

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
     * Serialization key constant for the software list DAT file path.
     */
    private static final String FILESL_STR = "filesl";

    /**
     * Serialization key constant for the primary ROMs DAT file path.
     */
    private static final String FILEROMS_STR = "fileroms";

    /**
     * Serialization key constant for the software list availability flag.
     */
    private static final String SL_STR = "sl";

    /**
     * Serialization key constant for the executable's modification date.
     */
    private static final String MODIFIED_STR = "modified";

    /**
     * Serialization key constant for the MAME executable file path.
     */
    private static final String FILE_STR = "file";

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
     * Declares persistent serialization fields for compliant and predictable manual object serialization.
     * 
     * @serialField file File the MAME executable file location
     * @serialField modified Long the last recorded MAME executable modification timestamp
     * @serialField sl boolean software list active flag
     * @serialField fileroms File primary ROMs XML DAT file path
     * @serialField filesl File software list XML DAT file path
     */
    private static final ObjectStreamField[] serialPersistentFields = { // NOSONAR
            new ObjectStreamField(FILE_STR, File.class), // $NON-NLS-1$
            new ObjectStreamField(MODIFIED_STR, Long.class), // $NON-NLS-1$
            new ObjectStreamField(SL_STR, Boolean.TYPE), // $NON-NLS-1$
            new ObjectStreamField(FILEROMS_STR, File.class), // $NON-NLS-1$
            new ObjectStreamField(FILESL_STR, File.class), // $NON-NLS-1$
    };

    /**
     * Default zero-argument constructor for initializing an empty MAME metadata profile.
     */
    public ProfileNFOMame() {
        // Default constructor
    }

    /**
     * Manually serializes the state of this MAME metadata instance to the destination stream.
     * 
     * @param stream the target {@link ObjectOutputStream}
     * 
     * @throws IOException if a physical write error occurs
     */
    private void writeObject(final java.io.ObjectOutputStream stream) throws IOException {
        final var fields = stream.putFields();
        fields.put(FILE_STR, file); // $NON-NLS-1$
        fields.put(MODIFIED_STR, modified); // $NON-NLS-1$
        fields.put(SL_STR, sl); // $NON-NLS-1$
        fields.put(FILEROMS_STR, fileroms); // $NON-NLS-1$
        fields.put(FILESL_STR, filesl); // $NON-NLS-1$
        stream.writeFields();
    }

    /**
     * Manually deserializes the state of this MAME metadata instance from the source stream.
     * 
     * @param stream the source {@link ObjectInputStream}
     * 
     * @throws IOException if a physical read error occurs
     * @throws ClassNotFoundException if any serialized class representation cannot be resolved
     */
    private void readObject(final java.io.ObjectInputStream stream) throws IOException, ClassNotFoundException {
        final ObjectInputStream.GetField fields = stream.readFields();
        final File deserializedFile = (File) fields.get(FILE_STR, null); // $NON-NLS-1$
        
        // Validate the deserialized MAME executable path to prevent arbitrary program execution
        if (deserializedFile != null && !isValidMameExecutable(deserializedFile)) {
            Log.warn(() -> String.format("Rejected potentially malicious MAME executable path from deserialized profile metadata: %s", deserializedFile.getAbsolutePath()));
            file = null;
            modified = 0L;
            sl = false;
            fileroms = null;
            filesl = null;
            return;
        }
        
        file = deserializedFile;
        modified = Optional.ofNullable((Long) fields.get(MODIFIED_STR, (Long) 0L)).orElse(0L); // $NON-NLS-1$
        sl = fields.get(SL_STR, false); // $NON-NLS-1$
        fileroms = (File) fields.get(FILEROMS_STR, null); // $NON-NLS-1$
        filesl = (File) fields.get(FILESL_STR, null); // $NON-NLS-1$
    }
    
    /**
     * Validates that a file path represents a legitimate MAME executable.
     * This method performs security checks to prevent arbitrary program execution
     * from untrusted deserialized profile metadata.
     * 
     * @param executableFile the file to validate
     * @return {@code true} if the file appears to be a valid MAME executable; {@code false} otherwise
     */
    private static boolean isValidMameExecutable(final File executableFile) {
        if (executableFile == null) {
            return false;
        }
        
        try {
            // Normalize the path to prevent directory traversal attacks
            final var canonicalPath = executableFile.getCanonicalPath();
            final var canonicalFile = new File(canonicalPath);
            
            // Check if the file exists and is a regular file (not a directory or special file)
            if (!canonicalFile.exists() || !canonicalFile.isFile()) {
                return false;
            }
            
            // Check if the file is executable
            if (!canonicalFile.canExecute()) {
                return false;
            }
            
            // Validate the filename contains "mame" (case-insensitive) to ensure it's a MAME-related executable
            // This prevents execution of arbitrary programs while allowing legitimate MAME variants
            // (mame, mame64, sdlmame, mameui, etc.)
            final var fileName = canonicalFile.getName().toLowerCase();
            if (!fileName.contains("mame")) {
                Log.warn(() -> String.format("Executable file name does not contain 'mame': %s", canonicalFile.getAbsolutePath()));
                return false;
            }
            
            // Additional check: ensure the path doesn't contain suspicious patterns
            // that might indicate an attempt to execute system utilities or other programs
            final var suspiciousPatterns = new String[] {
                "/bin/", "/usr/bin/", "/sbin/", "/usr/sbin/",
                "\\windows\\", "\\system32\\", "\\syswow64\\",
                "cmd.exe", "powershell", "bash", "sh", "python", "perl", "ruby"
            };
            
            final var lowerPath = canonicalPath.toLowerCase();
            for (final var pattern : suspiciousPatterns) {
                if (lowerPath.contains(pattern.toLowerCase())) {
                    Log.warn(() -> String.format("Executable path contains suspicious pattern '%s': %s", pattern, canonicalPath));
                    return false;
                }
            }
            
            return true;
        } catch (IOException e) {
            Log.err("Failed to validate MAME executable path", e);
            return false;
        }
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
     * Physically deletes all extracted database XML files (ROM list and Software Lists) associated with this MAME installation.
     */
    public void delete() {
        try {
            if (fileroms != null)
                Files.deleteIfExists(fileroms.toPath());
            if (filesl != null)
                Files.deleteIfExists(filesl.toPath());
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }
}
