package jrm.fullserver.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.apache.commons.io.FilenameUtils;

import jrm.misc.Log;
import jrm.misc.SystemSettings;
import lombok.NonNull;

/**
 * H2 Database implementation.
 * <p>
 * H2 is an embedded Java database that can be used for development and testing purposes. It supports in-memory and file-based
 * databases, and provides a simple API for connecting and managing databases. This class implements the DB interface to provide
 * H2-specific functionality for connecting to and managing H2 databases.
 * <p>
 * The connectToDB method establishes a connection to an H2 database, with options to drop the database before connecting, ensure
 * safe connections, and check if the database exists. The dropDB method allows for dropping an H2 database by deleting its files.
 * The shouldDropDB method checks if the H2 database needs to be rebuilt based on changes to access files.
 * 
 * @author jrm
 * 
 * @version 1.0
 * 
 * @since 2024-06
 */
class H2 extends DB {
    /**
     * Constructs a new H2 database instance with the specified SystemSettings.
     * 
     * @param settings The SystemSettings object containing configuration for the database.
     */
    public H2(SystemSettings settings) {
        super(settings);
    }

    /**
     * Connects to an H2 database with the specified name and options. It constructs the JDBC URL based on the provided parameters
     * and opens a connection to the database. If the drop option is true, it drops the existing database before connecting. The
     * safe option determines whether to use safe connection settings, and the ifexists option checks if the database exists before
     * connecting.
     * 
     * @param name The name of the database to connect to.
     * @param drop Whether to drop the existing database before connecting.
     * @param safe Whether to use safe connection settings.
     * @param ifexists Whether to check if the database exists before connecting.
     * 
     * @return A Connection object representing the connection to the H2 database.
     * 
     * @throws IOException If an I/O error occurs while dropping the database or checking for its existence.
     * @throws SQLException If a database access error occurs while connecting to the database.
     */
    @Override
    public Connection connectToDB(final String name, final boolean drop, final boolean safe, final boolean ifexists) throws IOException, SQLException {
        // Open a connection
        if (drop)
            dropDB(name);
        final var url = new StringBuilder("jdbc:h2:");
        url.append(getDBPath(name));
        if (ifexists)
            url.append(";IFEXISTS=TRUE");
        if (!safe)
            url.append(";LOG=0;LOCK_MODE=0;UNDO_LOG=0");
        url.append(";MODE=MYSQL");
        Log.debug("Opening " + url);
        return DriverManager.getConnection(url.toString(), "sa", System.getProperty("DB_PW", ""));
    }

    /**
     * Drops the H2 database by deleting its associated files.
     * 
     * @param name The name of the database to drop.
     * 
     * @throws IOException If an I/O error occurs while deleting the files.
     */
    @Override
    public void dropDB(String name) throws IOException {
        for (final var file : getDBPath(name).getParent().toFile().listFiles(f -> f.getName().toLowerCase().startsWith(name.toLowerCase() + '.') && Files.isRegularFile(f.toPath())))
            file.delete();
    }

    /**
     * Determines whether the H2 database should be dropped based on changes to access files. It checks the creation time of the
     * database files and compares it with the last modified time of the source access file and the capture access file. If the
     * source access file or the capture access file is more recent than the database files, it indicates that the database should
     * be dropped and rebuilt.
     * 
     * @param cpsPath The path to the source access file.
     * @param capturePath The path to the capture access file.
     * 
     * @return true if the database should be dropped, false otherwise.
     * 
     * @throws IOException If an I/O error occurs while checking the file attributes.
     */
    @Override
    public boolean shouldDropDB(final @NonNull Path cpsPath, final Path capturePath) throws IOException {
        final var name = cpsPath.getFileName().toString();
        final var dbpath = getDBPath(name, true);
        if (!Files.exists(dbpath)) // pas de bd h2 => drop
            return true;
        if (!Files.exists(cpsPath)) // pas de source access mais une bd h2, pas d'import possible => pas de drop
            return false;
        final var created = Files.exists(dbpath) ? Files.getFileAttributeView(dbpath, BasicFileAttributeView.class).readAttributes().creationTime().toMillis() : 0L;
        if (cpsPath.toFile().lastModified() > created) // drop si la source access est plus récente
            return true;
        return (capturePath != null && capturePath.toFile().lastModified() > created); // drop si le capture access est plus récente
    }

    /**
     * Retrieves the file path for the H2 database based on the provided name and options. It resolves the database path by
     * replacing any placeholders in the name with the appropriate values from the settings, and then constructs the full path to
     * the database file. If the name is not an absolute path, it resolves it relative to the work path or base path depending on
     * the file extension. The full option determines whether to append the ".mv.db" extension to the resolved name.
     * 
     * @param name The name of the database.
     * @param full Whether to append the ".mv.db" extension to the resolved name.
     * 
     * @return The Path object representing the file path to the H2 database.
     */
    Path getDBPath(String name, final boolean full) {
        if (name.contains("%w"))
            name = name.replace("%w", getSettings().getWorkPath().toString());
        if (!Paths.get(name).isAbsolute()) {
            var basepath = settings.getWorkPath();
            String ext = FilenameUtils.getExtension(name).toLowerCase();
            if (ext.equalsIgnoreCase("db")) {
                ext = FilenameUtils.getExtension(FilenameUtils.getBaseName(name)).toLowerCase();
                if (ext.equalsIgnoreCase("mv") || ext.equalsIgnoreCase("h2"))
                    ext = FilenameUtils.getExtension(FilenameUtils.getBaseName(FilenameUtils.getBaseName(name))).toLowerCase();
            }
            if ("sys".equals(ext))
                basepath = settings.getBasePath();
            return basepath.resolve(resolveName(name, full));
        } else {
            return resolveName(name, full);
        }
    }

    /**
     * Resolves the database name by appending or removing the ".mv.db" extension based on the provided options. If the full option
     * is true and the name does not already end with ".db", it appends ".mv.db" to the name. If the full option is false and the
     * name ends with ".mv.db", it removes the ".mv.db" extension from the name. Otherwise, it returns the name as is.
     * 
     * @param name The name of the database.
     * @param full Whether to append or remove the ".mv.db" extension based on the name.
     * 
     * @return The Path object representing the resolved database name.
     */
    Path resolveName(String name, final boolean full) {
        if (full && !name.endsWith(".db"))
            return Paths.get(name + ".mv.db");
        else if (!full && name.endsWith(".mv.db"))
            return Paths.get(name.substring(0, name.length() - 6));
        else
            return Paths.get(name);
    }

    /**
     * Retrieves the file path for the H2 database based on the provided name. This method is a convenience overload of the
     * getDBPath method that defaults the full parameter to false. It resolves the database path by replacing any placeholders in
     * the name with the appropriate values from the settings, and then constructs the full path to the database file without
     * appending the ".mv.db" extension.
     * 
     * @param name The name of the database.
     * 
     * @return The Path object representing the file path to the H2 database.
     */
    Path getDBPath(final String name) {
        return getDBPath(name, false);
    }

}
