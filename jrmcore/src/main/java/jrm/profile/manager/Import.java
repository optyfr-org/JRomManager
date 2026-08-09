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
        if (!Sets.newHashSet("xml", "dat").contains(ext.toLowerCase()) && file.canExecute()) //$NON-NLS-1$ //$NON-NLS-2$
        {
            try {
                if ((romsFile = importMame(file, false, progress)) != null) {
                    slFile = sl ? importMame(file, true, progress) : null;
                    this.file = ProfileNFO.saveJrm(IOUtils.createTempFile("JRM", ".jrm").toFile(), romsFile, slFile); //$NON-NLS-1$ //$NON-NLS-2$
                    isMame = true;
                }
            } catch (DOMException | ParserConfigurationException | TransformerException | IOException e) {
                // JOptionPane.showMessageDialog(null, e, "Exception", JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
                Log.err(e.getMessage(), e);
            }
        } else
            this.file = file;

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
        try {
            final var tmpfile = IOUtils.createTempFile("JRM", sl ? ".jrm2" : ".jrm1").toFile(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            tmpfile.deleteOnExit();
            final var process = new ProcessBuilder(file.getAbsolutePath(), sl ? "-listsoftware" : "-listxml").directory(file.getAbsoluteFile().getParentFile()).start(); //$NON-NLS-1$ //$NON-NLS-2$

            var linecnt = 0;
            var size = 0;
            try (final var out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpfile), StandardCharsets.UTF_8));
                    BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));) // $NON-NLS-1$
                                                                                                                                      // //$NON-NLS-2$
            {
                String line;
                var xml = false;
                while (null != (line = in.readLine())) {
                    // we need to skip occasional garbage before the xml start tag
                    if (line.startsWith("<?xml")) //$NON-NLS-1$
                        xml = true;
                    if (xml) {
                        out.write(line + "\n"); //$NON-NLS-1$
                        size += line.getBytes(StandardCharsets.UTF_8).length;
                        progress.setProgress(null, null, null,
                                (sl ? "Reading Softwares list" : "Reading roms list") + " / " + (++linecnt) + " lines / " + humanReadableByteCount(size, false));
                    }
                }
            }
            process.waitFor();
            return tmpfile;
        } catch (final IOException e) {
            Log.err("Caught IO Exception", e); //$NON-NLS-1$
        } catch (final InterruptedException e) {
            Log.err("Caught Interrupted Exception", e); //$NON-NLS-1$
            Thread.currentThread().interrupt();
        }
        return null;
    }
}
