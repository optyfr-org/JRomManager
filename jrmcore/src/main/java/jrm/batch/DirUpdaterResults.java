package jrm.batch;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import jrm.aui.progress.ProgressHandler;
import jrm.misc.Log;
import jrm.profile.report.Report;
import jrm.security.DeserializationFilter;
import jrm.security.PathAbstractor;
import jrm.security.Session;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents the results of a directory update operation, including the DAT file used and the statistics of the update process.
 * This class can be serialized to save and load results for later reference.
 */
public class DirUpdaterResults implements Serializable {
    /** the serial version UID for serialization compatibility */
    private static final long serialVersionUID = 2L;

    /**
     * Represents the result of a directory update operation, including the DAT file used and the statistics of the update process.
     */
    public class DirUpdaterResult implements Serializable {
        /** the serial version UID for serialization compatibility */
        private static final long serialVersionUID = 1L;

        /**
         * the DAT file used for the update operation
         * 
         * @return the DAT file used for the update operation
         */
        @Getter
        private File dat;
        /**
         * the statistics of the update process
         * 
         * @return the statistics of the update process
         */
        @Getter
        private Report.Stats stats;

        /**
         * Default constructor for serialization. This constructor is required for the serialization process to create instances of
         * DirUpdaterResult when loading from a file. It does not perform any initialization and is only used for deserialization
         * purposes.
         */
        public DirUpdaterResult() {
            // default constructor for serialization
        }
    }

    /**
     * the DAT file used for the update operation
     * 
     * @param dat the DAT file used for the update operation
     * 
     * @return the DAT file used for the update operation
     */
    @Getter
    @Setter
    private File dat;
    /**
     * the list of results for each directory update operation
     * 
     * @return the list of results for each directory update operation
     */
    @Getter
    private final List<DirUpdaterResult> results = new ArrayList<>();

    /**
     * Default constructor for serialization. This constructor is required for the serialization process to create instances of
     * DirUpdaterResults when loading from a file. It does not perform any initialization and is only used for deserialization
     * purposes.
     */
    public DirUpdaterResults() {
        // default constructor for serialization
    }

    /**
     * Adds a new result to the list of directory update results.
     *
     * @param dat the DAT file used for the update operation
     * @param stats the statistics of the update process
     */
    public void add(final File dat, final Report.Stats stats) {
        final var result = new DirUpdaterResult();
        result.dat = dat;
        result.stats = stats;
        results.add(result);
    }

    /**
     * Retrieves the file used to store the results of the directory update operation for a given session and DAT file. The file is
     * determined by calculating a CRC32 checksum of the absolute path of the DAT file and using it to create a unique filename in
     * the user's work directory.
     *
     * @param session the active user session
     * @param file the DAT file used for the update operation
     * 
     * @return the file used to store the results of the directory update operation
     */
    private static File getFile(final Session session, final File file) {
        final var crc = new CRC32();
        crc.update(PathAbstractor.getAbsolutePath(session, file.toString()).toString().getBytes());
        final var reports = session.getUser().getSettings().getWorkPath().resolve("work").toFile(); //$NON-NLS-1$
        reports.mkdirs();
        return new File(reports, String.format("%08x", crc.getValue()) + ".results"); //$NON-NLS-1$
    }

    /**
     * Saves the current instance of DirUpdaterResults to a file determined by the session and DAT file. The results are serialized
     * using an ObjectOutputStream and written to a file in the user's work directory. If an error occurs during the saving process,
     * it is logged using the Log class.
     *
     * @param session the active user session
     */
    public void save(final Session session) {
        try (final var oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(getFile(session, dat))))) {
            oos.writeObject(this);
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Loads the results of a directory update operation from a file determined by the session and DAT file. The results are
     * deserialized from the file in the user's work directory. If an error occurs or the file is not found, the error is logged and
     * null is returned.
     *
     * @param session the active user session
     * @param file the DAT file used for the update operation
     * @param progress the handler for reporting progress during the load process
     * 
     * @return the loaded DirUpdaterResults instance, or null if an error occurred
     */
    public static DirUpdaterResults load(final Session session, final File file, final ProgressHandler progress) {
        final var rfile = getFile(session, file);
        try (final var ois = new ObjectInputStream(new BufferedInputStream(progress.getInputStream(new FileInputStream(rfile), (int) rfile.length())))) {
            // Apply deserialization filter to prevent arbitrary code execution
            ois.setObjectInputFilter(DeserializationFilter.createFilter());
            return (DirUpdaterResults) ois.readObject();
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Loads the results of a directory update operation from a file determined by the session and DAT file. The results are
     * deserialized from the file in the user's work directory. If an error occurs or the file is not found, the error is logged and
     * null is returned.
     *
     * @param session the active user session
     * @param file the DAT file used for the update operation
     * 
     * @return the loaded DirUpdaterResults instance, or null if an error occurred
     */
    public static DirUpdaterResults load(final Session session, final File file) {
        try (final var ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(getFile(session, file))))) {
            // Apply deserialization filter to prevent arbitrary code execution
            ois.setObjectInputFilter(DeserializationFilter.createFilter());
            return (DirUpdaterResults) ois.readObject();
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
        }
        return null;
    }
}
