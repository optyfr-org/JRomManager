package jrm.server.shared.datasources;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import jrm.misc.Log;
import jrm.server.shared.datasources.XMLRequest.Operation;
import lombok.val;

/**
 * Handles XML responses for a remote file chooser interface. This class processes operations to browse directories, filter files by
 * extension, create new directories, rename or delete files/directories, and perform custom actions such as extracting archives or
 * expanding paths.
 */
public class RemoteFileChooserXMLResponse extends XMLResponse {

    /** XML attribute name for the parent directory path. */
    private static final String PARENT = "parent";

    /** XML attribute name for the list of paths. */
    private static final String PATHS = "paths";

    /** XML attribute name for the relative path. */
    private static final String REL_PATH = "RelPath";

    /** XML attribute name for the initial path to highlight. */
    private static final String INITIAL_PATH = "initialPath";

    /** XML attribute name indicating if the entry is a directory. */
    private static final String IS_DIR = "isDir";

    /** XML attribute name for the last modified timestamp. */
    private static final String MODIFIED = "Modified";

    /** XML element name for a single record in the data payload. */
    private static final String RECORD = "record";

    /** XML element name for the operation status. */
    private static final String STATUS = "status";

    /** XML element name for the root response wrapper. */
    private static final String RESPONSE = "response";

    /** XML attribute name for the operation context. */
    private static final String CONTEXT = "context";

    /** The root path for the current file browsing session. */
    Path root;

    /** Configuration options derived from the operation context. */
    Options options = null;

    /**
     * Configuration options for the file chooser based on the operation context.
     */
    static class Options {
        /** Indicates whether the chooser should only display directories. */
        final boolean isDir;

        /** The glob pattern to match file names, or null if no filtering is applied. */
        final String pathmatcher;

        /**
         * Constructs Options based on the provided context.
         *
         * @param context the operation context determining the filtering rules
         */
        public Options(String context) {
            switch (context) {
                case "tfRomsDest", "tfDisksDest", "tfSWDest", "tfSWDisksDest", "tfSamplesDest", "tfBackupDest", "listSrcDir", "addDatSrc", "updDat", "updTrnt", "tfSrcDir" -> {
                    isDir = true;
                    pathmatcher = null;
                }
                case "addTrnt" -> {
                    pathmatcher = "glob:*.torrent";
                    isDir = false;
                }
                case "importDat", "addDat", "tfDstDat" -> {
                    pathmatcher = "glob:*.{xml,dat}";
                    isDir = false;
                }
                case "addArc" -> {
                    pathmatcher = "glob:*.{zip,7z,rar,arj,tar,lzh,lha,tgz,tbz,tbz2,rpm,iso,deb,cab}";
                    isDir = false;
                }
                case "importSettings", "exportSettings" -> {
                    pathmatcher = "glob:*.properties";
                    isDir = false;
                }
                default -> {
                    pathmatcher = null;
                    isDir = false;
                }
            }
        }
    }

    /**
     * Utility class for finding files or directories on the filesystem ignoring case sensitivity.
     */
    public static class CaseInsensitiveFileFinder {

        private CaseInsensitiveFileFinder() {
            throw new IllegalStateException("Utility class");
        }

        /**
         * Finds a directory by name, ignoring case.
         *
         * @param dir the target directory path to find
         * 
         * @return the matched directory path, or null if not found
         * 
         * @throws IOException if an I/O error occurs
         */
        private static Path findDir(Path dir) throws IOException {
            try (final var stream = Files.list(dir.getParent())) {
                return stream.filter(Files::isDirectory).filter(p -> p.getFileName().toString().equalsIgnoreCase(dir.getFileName().toString())).findFirst().orElse(null);
            }
        }

        /**
         * Finds the last component of a path, ignoring case.
         *
         * @param path the target path to find
         * 
         * @return the matched path, or null if not found
         * 
         * @throws IOException if an I/O error occurs
         */
        private static Path findLast(Path path) throws IOException {
            if (Files.exists(path))
                return path;
            try (final var stream = Files.list(path.getParent())) {
                return stream.filter(p -> p.getFileName().toString().equalsIgnoreCase(path.getFileName().toString())).findFirst().orElse(null);
            }
        }

        /**
         * Finds a file within a parent directory, ignoring case.
         *
         * @param parent the parent directory path
         * @param fileName the name of the file to find
         * 
         * @return an Optional containing the matched file path, or empty if not found
         */
        public static Optional<Path> findFileIgnoreCase(final Path parent, final String fileName) {
            Path testpath = null;
            try {
                if (!Files.exists(parent)) {
                    // test all
                    testpath = parent.getRoot();
                    for (var i = 0; i < parent.getNameCount(); i++)
                        if (null == (testpath = findDir(testpath.resolve(parent.getName(i)))))
                            break;
                } else
                    testpath = parent;
                if (testpath != null)
                    testpath = findLast(testpath.resolve(fileName));
            } catch (IOException e) {
                Log.err(e.getMessage(), e);
            }
            return Optional.ofNullable(testpath);
        }

        /**
         * Finds a file by its full path, ignoring case.
         *
         * @param path the target path to find
         * 
         * @return an Optional containing the matched file path, or empty if not found
         */
        public static Optional<Path> findFileIgnoreCase(final Path path) {
            return findFileIgnoreCase(path.getParent(), path.getFileName().toString());
        }

        /**
         * Finds a file by its string path, ignoring case.
         *
         * @param file the string representation of the file path
         * 
         * @return an Optional containing the matched File, or empty if not found
         */
        public static Optional<File> findFileIgnoreCase(final String file) {
            var path = Paths.get(file);
            return findFileIgnoreCase(path.getParent(), path.getFileName().toString()).map(Path::toFile);
        }

        /**
         * Finds a file within a parent directory string, ignoring case.
         *
         * @param parentDir the string representation of the parent directory
         * @param fileName the name of the file to find
         * 
         * @return an Optional containing the matched File, or empty if not found
         */
        public static Optional<File> findFileIgnoreCase(final String parentDir, final String fileName) {
            return findFileIgnoreCase(Paths.get(parentDir), fileName).map(Path::toFile);
        }
    }

    /**
     * Constructs a new RemoteFileChooserXMLResponse.
     *
     * @param request the incoming XML request containing operation details
     * 
     * @throws IOException if an I/O error occurs during initialization
     * @throws XMLStreamException if an XML writing error occurs during initialization
     */
    public RemoteFileChooserXMLResponse(XMLRequest request) throws IOException, XMLStreamException {
        super(request);
    }

    /**
     * Fetches the contents of a directory and writes them to the XML response. Applies filtering based on the operation context
     * (e.g., directories only, specific extensions).
     *
     * @param operation the operation details from the request
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     * @throws IOException if an I/O error occurs while accessing the file system
     */
    @Override
    protected void fetch(Operation operation) throws XMLStreamException, IOException {
        if (operation.hasData(CONTEXT))
            options = new Options(operation.getData(CONTEXT));
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        writer.writeElement("startRow", "0");
        Path parent = writeParent(getParent(operation));
        PathMatcher matcher = options.pathmatcher != null ? parent.getFileSystem().getPathMatcher(options.pathmatcher) : null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent, entry -> {
            if (options.isDir)
                return Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS);
            else if (matcher != null && Files.isRegularFile(entry))
                return matcher.matches(entry.getFileName());
            return true;
        })) {
            long cnt = 0;
            writer.writeStartElement("data");
            if (!parent.equals(root) && parent.getParent() != null) {
                writer.writeStartElement(RECORD);
                writer.write("Name", "..");
                writer.write("Path", pathAbstractor.getRelativePath(parent).getParent());
                writer.write("Size", -1);
                writer.write(MODIFIED, Files.getLastModifiedTime(parent.getParent()));
                writer.write(IS_DIR, true);
                writer.writeEndElement();
                cnt++;
            }
            val initialPath = operation.hasData(INITIAL_PATH) ? CaseInsensitiveFileFinder.findFileIgnoreCase(pathAbstractor.getAbsolutePath(operation.getData(INITIAL_PATH)))
                    : Optional.empty();
            for (Path entry : stream) {
                BasicFileAttributeView view = Files.getFileAttributeView(entry, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                BasicFileAttributes attr = view.readAttributes();
                writer.writeStartElement(RECORD);
                writer.write("Name", entry.getFileName());
                writer.write("Path", pathAbstractor.getRelativePath(entry));
                writer.write(REL_PATH, pathAbstractor.getRelativePath(entry));
                writer.write("Size", !attr.isRegularFile() ? -1 : attr.size());
                writer.write(MODIFIED, attr.lastModifiedTime());
                writer.write(IS_DIR, attr.isDirectory());
                if (initialPath.isPresent() && entry.equals(initialPath.get()))
                    writer.write("isSelected", true);
                writer.writeEndElement();
                cnt++;
            }
            writer.writeEndElement();
            writer.writeElement("endRow", Long.toString(cnt - 1));
            writer.writeElement("totalRows", Long.toString(cnt));
        }
        writer.writeEndElement();
    }

    /**
     * Adds a new directory to the current path.
     *
     * @param operation the operation details containing the new directory name
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     */
    @Override
    protected void add(Operation operation) throws XMLStreamException {
        if (operation.hasData(CONTEXT))
            options = new Options(operation.getData(CONTEXT));
        Path parent = getParent(operation);
        String name = operation.getData("Name");
        Path entry = validateResolvedPath(parent, name);
        if (entry != null && name != null && Files.isDirectory(parent) && !Files.exists(entry)) {
            try {
                Files.createDirectory(entry);
                writeResponseSingle(parent, entry);
            } catch (Exception ex) {
                failure(ex.getMessage());
            }
        } else
            failure("Can't create " + name);
    }

    /**
     * Writes a single record response for a newly created or modified entry.
     *
     * @param parent the parent directory path
     * @param entry the newly created or modified entry path
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     * @throws IOException if an I/O error occurs while accessing file attributes
     */
    protected void writeResponseSingle(Path parent, Path entry) throws XMLStreamException, IOException {
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        writeParent(parent);
        writer.writeStartElement("data");
        writer.writeStartElement(RECORD);
        writer.writeAttribute("Name", entry.getFileName().toString());
        writer.writeAttribute("Path", pathAbstractor.getRelativePath(entry).toString());
        writer.writeAttribute(REL_PATH, pathAbstractor.getRelativePath(entry).toString());
        writer.writeAttribute("Size", "-1");
        writer.writeAttribute(MODIFIED, Files.getLastModifiedTime(entry).toString());
        writer.writeAttribute(IS_DIR, Boolean.TRUE.toString());
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndElement();
    }

    /**
     * Updates (renames) an existing file or directory.
     *
     * @param operation the operation details containing the old and new names
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     */
    @Override
    protected void update(Operation operation) throws XMLStreamException {
        if (operation.hasData(CONTEXT))
            options = new Options(operation.getData(CONTEXT));
        Path parent = getParent(operation);
        String name = operation.getData("Name");
        String oldname = operation.oldValues.get("Name");
        Path entry = validateResolvedPath(parent, name);
        Path oldentry = validateResolvedPath(parent, oldname);
        if (entry != null && oldentry != null && name != null && oldname != null && Files.isDirectory(parent) && Files.exists(oldentry) && !Files.exists(entry)) {
            try {
                Files.move(oldentry, entry);
                writeResponseSingle(parent, entry);
            } catch (Exception ex) {
                failure(ex.getMessage());
            }
        } else
            failure("Can't update " + oldname + " to " + name);
    }

    /**
     * Removes a file or directory recursively.
     *
     * @param operation the operation details containing the name of the entry to remove
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     */
    @Override
    protected void remove(Operation operation) throws XMLStreamException {
        if (operation.hasData(CONTEXT))
            options = new Options(operation.getData(CONTEXT));
        Path parent = getParent(operation);
        String name = operation.getData("Name");
        Path entry = validateResolvedPath(parent, name);
        if (entry != null && name != null && Files.exists(entry)) {
            try {
                try (final var stream = Files.walk(entry)) {
                    stream.map(Path::toFile).sorted(Comparator.reverseOrder()).forEach(File::delete); // recursive dir delete
                }
                writer.writeStartElement(RESPONSE);
                writer.writeElement(STATUS, "0");
                writeParent(parent);
                writer.writeStartElement("data");
                writer.writeStartElement(RECORD);
                writer.writeAttribute("Name", entry.getFileName().toString());
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeEndElement();

            } catch (Exception ex) {
                failure(ex.getMessage());
            }
        } else
            failure("Can't remove " + name);
    }

    /**
     * Performs custom operations such as expanding paths or extracting archives.
     *
     * @param operation the operation details containing the custom operation ID
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     * @throws IOException if an I/O error occurs while accessing the file system
     */
    @Override
    protected void custom(Operation operation) throws XMLStreamException, IOException {

        if (operation.getOperationId().toString().equals("expand")) {
            customExpand(operation);
        } else if (operation.operationId.toString().equals("extract_subfolder")) {
            if (operation.hasData("Path")) {
                var zipfile = pathAbstractor.getAbsolutePath(operation.getData("Path"));
                Path dest = zipfile.getParent().resolve(StringUtils.substring(zipfile.getFileName().toString(), 0, -4));
                unzip(zipfile, dest);
                success();
            } else
                failure("path missing");
        } else if (operation.operationId.toString().equals("extract_here")) {
            if (operation.hasData("Path")) {
                var zipfile = pathAbstractor.getAbsolutePath(operation.getData("Path"));
                Path dest = zipfile.getParent();
                unzip(zipfile, dest);
                success();
            } else
                failure("path missing");
        } else
            super.custom(operation);
    }

    /**
     * Expands the provided paths into individual records in the XML response. If the context is "addArc", it recursively searches
     * for archive files.
     *
     * @param operation the operation details containing the paths to expand
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     * @throws SecurityException if a security manager denies access
     * @throws IOException if an I/O error occurs while accessing the file system
     */
    private void customExpand(Operation operation) throws XMLStreamException, SecurityException, IOException {
        if (operation.hasData(PATHS)) {
            var dir = request.getSession().getUser().getSettings().getWorkPath();
            if (operation.hasData(PARENT))
                dir = new File(operation.getData(PARENT)).toPath();
            writer.writeStartElement(RESPONSE);
            writer.writeElement(STATUS, "0");
            writer.writeElement(PARENT, dir.toString());
            writer.writeStartElement("data");
            var cnt = new AtomicInteger();
            if ("addArc".equals(operation.getData(CONTEXT))) {
                customExpandAddArc(operation, cnt);
            } else {
                for (String path : operation.getDatas(PATHS)) {
                    var entry = Paths.get(path);
                    writer.writeEmptyElement(RECORD);
                    writer.writeAttribute("Name", entry.getFileName().toString());
                    writer.writeAttribute("Path", pathAbstractor.getRelativePath(entry).toString());
                    cnt.incrementAndGet();
                }
            }
            writer.writeEndElement();
            writer.writeElement("endRow", Long.toString(cnt.get() - 1L));
            writer.writeElement("totalRows", Long.toString(cnt.get()));
            writer.writeEndElement();
        } else
            failure("paths missing");
    }

    /**
     * Recursively searches for archive files within the provided paths and writes them to the response.
     *
     * @param operation the operation details containing the paths to search
     * @param cnt an atomic integer to keep track of the number of records written
     * 
     * @throws SecurityException if a security manager denies access
     * @throws IOException if an I/O error occurs while accessing the file system
     */
    private void customExpandAddArc(Operation operation, AtomicInteger cnt) throws SecurityException, IOException {
        for (String path : operation.getDatas(PATHS)) {
            var entry = pathAbstractor.getAbsolutePath(path);
            Files.walkFileTree(entry, new SimpleFileVisitor<Path>() {
                String[] exts = new String[] { "zip", "7z", "rar", "arj", "tar", "lzh", "lha", "tgz", "tbz", "tbz2", "rpm", "iso", "deb", "cab" };

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (FilenameUtils.isExtension(file.getFileName().toString(), exts)) {
                        try {
                            writer.writeEmptyElement(RECORD);
                            writer.writeAttribute("Name", file.getFileName().toString());
                            writer.writeAttribute("Path", pathAbstractor.getRelativePath(file).toString());
                            cnt.incrementAndGet();
                        } catch (XMLStreamException _) {
                            // ignore silently
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /**
     * Retrieves the root name relative to the abstractor.
     *
     * @return the relative path string of the root directory
     */
    private String getRootName() {
        return pathAbstractor.getRelativePath(root).toString();
    }

    /**
     * Determines the parent directory for the current operation.
     *
     * @param operation the operation details containing path information
     * 
     * @return the resolved parent Path
     */
    private Path getParent(Operation operation) {
        var parent = request.getSession().getUser().getSettings().getWorkPath();
        root = parent;
        if (operation.hasData("root"))
            root = parent = pathAbstractor.getAbsolutePath(operation.getData("root"));
        if (operation.hasData(PARENT))
            parent = pathAbstractor.getAbsolutePath(operation.getData(PARENT));
        else if (operation.hasData(INITIAL_PATH)) {
            parent = pathAbstractor.getAbsolutePath(operation.getData(INITIAL_PATH));
            if (!Files.isDirectory(parent))
                parent = parent.getParent();
        }
        return parent;
    }

    /**
     * Validates that a resolved path remains within the parent directory boundary.
     * This method prevents path traversal attacks by ensuring that the resolved path,
     * after normalization, starts with the parent directory path.
     *
     * @param parent the parent directory that should contain the resolved path
     * @param name the name or relative path to resolve against the parent
     * 
     * @return the validated resolved path, or null if the path attempts to escape the parent directory
     */
    private Path validateResolvedPath(Path parent, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            // Normalize the parent path to establish the security boundary
            Path normalizedParent = parent.toAbsolutePath().normalize();
            
            // Resolve the name against the parent and normalize the result
            Path resolvedPath = normalizedParent.resolve(name).normalize();
            
            // Verify the resolved path stays within the parent directory
            if (!resolvedPath.startsWith(normalizedParent)) {
                Log.err("Path traversal attempt detected: " + name + " escapes parent directory");
                return null;
            }
            
            return resolvedPath;
        } catch (Exception e) {
            Log.err("Error validating path: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Writes the parent and root path information to the XML response.
     *
     * @param parent the parent directory path to write
     * 
     * @return the parent path
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     */
    private Path writeParent(Path parent) throws XMLStreamException {
        writer.writeElement(PARENT, pathAbstractor.getRelativePath(parent).toString());
        writer.writeElement("relparent", pathAbstractor.getRelativePath(parent).toString());
        writer.writeElement("root", pathAbstractor.getRelativePath(root).toString());
        writer.writeElement("parentRelative", Paths.get(getRootName()).resolve(root.relativize(parent)).toString());
        return parent;
    }

    /**
     * Adapter class to convert an Enumeration to an Iterator.
     *
     * @param <T> the type of elements returned by this iterator
     */
    private static class EnumerationToIterator<T> implements Iterator<T> {
        /** The underlying enumeration being wrapped. */
        Enumeration<T> enumeration;

        /**
         * Constructs an iterator backed by the given enumeration.
         *
         * @param enmueration the enumeration to wrap
         */
        public EnumerationToIterator(Enumeration<T> enmueration) {
            this.enumeration = enmueration;
        }

        @Override
        public boolean hasNext() {
            return enumeration.hasMoreElements();
        }

        @Override
        public T next() {
            if (!enumeration.hasMoreElements())
                throw new NoSuchElementException();
            return enumeration.nextElement();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Extracts the contents of a ZIP file to a specified output directory.
     *
     * @param zipfile the path to the ZIP file to extract
     * @param outputPath the destination directory for the extracted files
     */
    private void unzip(Path zipfile, Path outputPath) {
        try (var zf = new ZipFile(zipfile.toFile())) {
            Path normalizedOutputPath = outputPath.toAbsolutePath().normalize();

            Enumeration<? extends ZipEntry> zipEntries = zf.entries();
            new EnumerationToIterator<>(zipEntries).forEachRemaining(entry -> {
                try {
                    Path resolvedPath = normalizedOutputPath.resolve(entry.getName()).normalize();
                    if (!resolvedPath.startsWith(normalizedOutputPath)) {
                        Log.err("Skipping unsafe zip entry outside target dir: " + entry.getName());
                        return;
                    }

                    if (entry.isDirectory()) {
                        if (!Files.exists(resolvedPath))
                            Files.createDirectories(resolvedPath);
                    } else {
                        if (!Files.exists(resolvedPath.getParent()))
                            Files.createDirectories(resolvedPath.getParent());
                        Files.copy(zf.getInputStream(entry), resolvedPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ei) {
                    Log.err(ei.getMessage(), ei);
                }
            });
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }

    }

}
