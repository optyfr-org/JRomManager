/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.manager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.compress.utils.Sets;
import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.DOMException;

import jrm.aui.progress.ProgressHandler;
import jrm.aui.progress.ProgressHandler.Option;
import jrm.misc.IOUtils;
import jrm.misc.Log;
import jrm.misc.UnitRenderer;
import jrm.security.Session;
import lombok.Getter;

/**
 * Manages the import process for retro-gaming XML and DAT metadata. Supports direct database files as well as automated query
 * extraction from MAME/MESS executables to produce JRomManager profiles (.jrm).
 * 
 * @author optyfr
 */
public class Import implements UnitRenderer {
    /**
     * The original user-supplied file before any processing or extraction.
     * 
     * @return the original configuration file on disk
     */
    private final @Getter File orgFile;

    /**
     * The imported profile configuration file (typically a JRM file if imported from an executable, or the original file if already
     * in a standard DAT/XML format).
     * 
     * @return the ready-to-use profile database file reference
     */
    private @Getter File file;

    /**
     * The temporary ROM definitions XML file extracted from the MAME/MESS executable.
     * 
     * @return the temporary file holding the XML ROMs database
     */
    private @Getter File romsFile;

    /**
     * The temporary Software List definitions XML file extracted from the MAME/MESS executable.
     * 
     * @return the temporary file holding the XML Software List database, or {@code null} if not queried
     */
    private @Getter File slFile;

    /**
     * Flag indicating whether this import was initiated from an executable MAME/MESS instance.
     * 
     * @return {@code true} if this import queries an executable; {@code false} if a database file was supplied directly
     */
    private @Getter boolean isMame = false;

    /**
     * Initiates the import workflow from a physical file or executable. If the file is an executable, it automatically invokes
     * standard command-line flags to generate the appropriate XML DAT databases, wrapping them inside a JRomManager profile.
     * 
     * @param session the active security user session
     * @param file the user-selected file or MAME executable
     * @param sl {@code true} to enable Software Lists extraction from the executable
     * @param progress the UI progress listener to report ongoing status
     */
    public Import(final Session session, final File file, final boolean sl, ProgressHandler progress) {
        progress.setOptions(Option.LAZY);
        orgFile = file;
        
        // Validate file is not null to prevent NullPointerException
        if (file == null) {
            Log.warn("Import attempted with null file");
            this.file = null;
            return;
        }
        
        final var workdir = session.getUser().getSettings().getWorkPath().toFile(); // $NON-NLS-1$
        final var xmldir = new File(workdir, "xmlfiles"); //$NON-NLS-1$
        xmldir.mkdir();

        final String ext = FilenameUtils.getExtension(file.getName());
        if (Sets.newHashSet("xml", "dat").contains(ext.toLowerCase())) { //$NON-NLS-1$ //$NON-NLS-2$
            this.file = file;
        } else if (MameExecutable.isLaunchable(file)) {
            try {
                if ((romsFile = importMame(file, false, progress)) != null) {
                    slFile = sl ? importMame(file, true, progress) : null;
                    this.file = ProfileNFO.saveJrm(IOUtils.createTempFile("JRM", ".jrm").toFile(), romsFile, slFile); //$NON-NLS-1$ //$NON-NLS-2$
                    isMame = true;
                }
            } catch (DOMException | ParserConfigurationException | TransformerException | IOException e) {
                Log.err(e.getMessage(), e);
            }
        } else if (file.canExecute()) {
            Log.warn(() -> "Rejected non-native or script file as MAME executable: " + file.getAbsolutePath());
        } else {
            this.file = file;
        }

    }

    /**
     * Executes the MAME process to query and write the internal XML definitions directly to a temporary file. Updates the graphical
     * progress bar continuously with the parsed line and byte counts.
     * 
     * @param file the MAME executable file
     * @param sl {@code true} to query software list data via {@code -listsoftware}, {@code false} to query primary ROM set data via
     *        {@code -listxml}
     * @param progress the active progress monitor
     * 
     * @return the temporary file on disk containing the full XML printout, or {@code null} if an error occurred
     */
    public File importMame(final File file, final boolean sl, ProgressHandler progress) {
        if (!MameExecutable.isLaunchable(file)) {
            Log.warn(() -> "Rejected non-launchable file as MAME executable: " + describeFile(file));
            return null;
        }
        File tmpfile = null;
        Process process = null;
        try {
            final var exe = file.getCanonicalFile();
            tmpfile = createMameTempFile(sl);
            process = startMameListProcess(exe, sl);
            final var header = captureMameListOutput(process, tmpfile, sl, progress);
            process.waitFor();
            return acceptMameListOutput(exe, tmpfile, header, sl);
        } catch (final IOException e) {
            Log.err("Caught IO Exception", e); //$NON-NLS-1$
        } catch (final InterruptedException e) {
            Log.err("Caught Interrupted Exception", e); //$NON-NLS-1$
            Thread.currentThread().interrupt();
        } finally {
            destroyIfAlive(process);
        }
        deleteQuietly(tmpfile);
        return null;
    }

    /**
     * @param file the rejected candidate, possibly {@code null}
     * @return a path description suitable for log messages
     */
    private static String describeFile(final File file) {
        return file == null ? "null" : file.getAbsolutePath();
    }

    /**
     * @param sl {@code true} for software-list extract suffix {@code .jrm2}, otherwise {@code .jrm1}
     * @return a new temporary file marked for deletion on JVM exit
     * @throws IOException if the temporary file cannot be created
     */
    private static File createMameTempFile(final boolean sl) throws IOException {
        final var tmpfile = IOUtils.createTempFile("JRM", sl ? ".jrm2" : ".jrm1").toFile(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        tmpfile.deleteOnExit();
        return tmpfile;
    }

    /**
     * @param exe canonical MAME/MESS executable
     * @param sl {@code true} to run {@code -listsoftware}, otherwise {@code -listxml}
     * @return the started process with stderr merged into stdout
     * @throws IOException if the process cannot be started
     */
    private static Process startMameListProcess(final File exe, final boolean sl) throws IOException {
        return new ProcessBuilder(exe.getAbsolutePath(), sl ? "-listsoftware" : "-listxml") //$NON-NLS-1$ //$NON-NLS-2$
                .directory(exe.getParentFile())
                .redirectErrorStream(true)
                .start();
    }

    /**
     * Copies XML lines from the MAME process to {@code tmpfile} and captures a prefix for validation.
     *
     * @param process the running MAME list process
     * @param tmpfile destination for captured XML
     * @param sl {@code true} when reading a software list
     * @param progress progress monitor updated with line and byte counts
     * @return the captured XML header prefix
     * @throws IOException if reading or writing fails
     */
    private StringBuilder captureMameListOutput(final Process process, final File tmpfile, final boolean sl, final ProgressHandler progress)
            throws IOException {
        var linecnt = 0;
        var size = 0;
        var xml = false;
        final var header = new StringBuilder();
        try (final var out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpfile), StandardCharsets.UTF_8));
                BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (null != (line = in.readLine())) {
                xml |= line.startsWith("<?xml"); //$NON-NLS-1$
                if (!xml) {
                    continue;
                }
                out.write(line);
                out.write('\n');
                appendHeader(header, line);
                size += line.getBytes(StandardCharsets.UTF_8).length;
                progress.setProgress(null, null, null,
                        listProgressLabel(sl) + " / " + (++linecnt) + " lines / " + humanReadableByteCount(size, false));
            }
        }
        return header;
    }

    /**
     * @param header captured XML prefix
     * @param line the next XML line
     */
    private static void appendHeader(final StringBuilder header, final String line) {
        if (header.length() < 8192) {
            header.append(line).append('\n');
        }
    }

    /**
     * @param sl {@code true} when reading a software list
     * @return the progress label for the current extract mode
     */
    private static String listProgressLabel(final boolean sl) {
        return sl ? "Reading Softwares list" : "Reading roms list";
    }

    /**
     * @param exe canonical MAME/MESS executable used for log messages
     * @param tmpfile captured output file
     * @param header captured XML prefix
     * @param sl {@code true} when {@code -listsoftware} was requested
     * @return {@code tmpfile} if the header is valid MAME/MESS list XML; {@code null} after deleting {@code tmpfile}
     * @throws IOException if the invalid output file cannot be deleted
     */
    private static File acceptMameListOutput(final File exe, final File tmpfile, final CharSequence header, final boolean sl) throws IOException {
        if (MameExecutable.isMameListOutput(header, sl)) {
            return tmpfile;
        }
        Log.warn(() -> "Rejected process output that is not MAME/MESS list XML: " + exe.getAbsolutePath());
        Files.deleteIfExists(tmpfile.toPath());
        return null;
    }

    /**
     * @param process process to stop if still running; ignored when {@code null}
     */
    private static void destroyIfAlive(final Process process) {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    /**
     * @param tmpfile temporary file to delete; ignored when {@code null}
     */
    private static void deleteQuietly(final File tmpfile) {
        if (tmpfile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tmpfile.toPath());
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * @param sl {@code true} when software-list extracts are required
     * @return {@code true} if this import produced the MAME extracts needed to update a profile
     */
    public boolean canApplyMameUpdate(final boolean sl) {
        return isMame && romsFile != null && (!sl || slFile != null);
    }
}
