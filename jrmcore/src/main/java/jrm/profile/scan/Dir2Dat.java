/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import jrm.aui.progress.ProgressHandler;
import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.profile.data.Container;
import jrm.profile.data.Entry;
import jrm.profile.data.Entry.Type;
import jrm.profile.data.ExportMode;
import jrm.profile.data.Machine;
import jrm.profile.data.Software;
import jrm.profile.data.SoftwareList;
import jrm.profile.manager.Export;
import jrm.profile.manager.Export.ExportType;
import jrm.profile.scan.DirScan.Options;
import jrm.security.Session;
import jrm.xml.EnhancedXMLStreamWriter;
import jrm.xml.SimpleAttribute;

/**
 * Exporter class that walks a physical directory, scans its ROM and CHD contents, and serializes the metadata back into a standard
 * MAME-compatible XML DAT file. Supports MAME, software list, and generic database formats.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public class Dir2Dat {
    /**
     * Localization resource bundle key for progress messaging during Dir2Dat saving operations.
     */
    private static final String DIR2_DAT_SAVING = "Dir2Dat.Saving";
    /**
     * Literal tag string representing a cdrom or disk-based entity interface.
     */
    private static final String CDROM = "cdrom";
    /**
     * XML description element name tag.
     */
    private static final String DESCRIPTION = "description";
    /**
     * Encoding name tag utilized during serialization output formatting.
     */
    private static final String UTF_8 = "UTF-8";
    /**
     * The current execution session.
     */
    private Session session;

    /**
     * Constructs a new Dir2Dat converter, triggers a parallel directory scan, and serializes the scanned container metadata to the
     * specified destination DAT file.
     * 
     * @param session the active workspace session context
     * @param srcdir the physical source folder to scan
     * @param dstdat the target metadata XML DAT file to generate
     * @param progress the progress handler UI updating monitor
     * @param options the scanning option ruleset filter configuration
     * @param type the target DAT file serialization style format
     * @param headers custom key-value pairs to write in the XML DAT header block
     * @throws IllegalArgumentException if the destination file path escapes the workspace directory
     */
    public Dir2Dat(final Session session, File srcdir, File dstdat, final ProgressHandler progress, Set<Options> options, ExportType type, Map<String, String> headers) {
        this.session = session;
        
        // Validate destination path to prevent directory traversal
        if (!isPathWithinWorkspace(dstdat)) {
            throw new IllegalArgumentException("Destination file path escapes workspace: " + dstdat.getAbsolutePath());
        }
        
        DirScan srcDirScan = new DirScan(session, srcdir, progress, options);
        write(dstdat, srcDirScan, progress, options, type, headers);
    }

    /**
     * Validates that a file path remains within the user's workspace directory.
     * <p>
     * This method canonicalizes the provided path and ensures it is a descendant of the user's work path,
     * preventing directory traversal attacks.
     * </p>
     * 
     * @param file the file to validate
     * @return true if the path is within the workspace, false if it escapes the workspace
     */
    private boolean isPathWithinWorkspace(File file) {
        if (file == null) {
            return false;
        }
        
        try {
            java.nio.file.Path workPath = session.getUser().getSettings().getWorkPath().toRealPath();
            java.nio.file.Path canonicalPath = file.getCanonicalFile().toPath();
            
            // Check if the canonical path starts with the work path
            return canonicalPath.startsWith(workPath);
        } catch (java.io.IOException e) {
            // If we can't resolve the path, reject it for safety
            Log.err("Failed to validate path: " + file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Orchestrates the physical writing of the scanned metadata to disk, wrapping exceptions.
     * 
     * @param dstdat the target physical metadata file
     * @param scan the completed physical directory scanner results context
     * @param progress the progress reporting channel
     * @param options the scanning configuration constraints
     * @param type the destination format type (MAME, DATAFILE, SOFTWARELIST)
     * @param headers custom headers map block
     */
    private void write(final File dstdat, final DirScan scan, final ProgressHandler progress, Set<Options> options, final ExportType type, Map<String, String> headers) {
        progress.clearInfos();
        progress.setInfos(1, false);
        AtomicInteger i = new AtomicInteger();
        scan.getContainersIterable().forEach(_ -> i.incrementAndGet());
        progress.setProgress(Messages.getString(DIR2_DAT_SAVING), 0, i.get()); // $NON-NLS-1$
        i.set(0);
        try (BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(dstdat))) {
            final EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(fos, UTF_8)); // $NON-NLS-1$
            writer.writeStartDocument(UTF_8, "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
            switch (type) {
                case MAME -> writeMame(scan, progress, options, i, writer);
                case DATAFILE -> writeDataFile(scan, progress, options, headers, i, writer);
                case SOFTWARELIST -> writeSoftwareList(scan, progress, options, i, writer);
            }
            writer.writeEndDocument();
            writer.close();
        } catch (FactoryConfigurationError | XMLStreamException | IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Prepares, groups, and writes software list structures based on container layouts.
     * 
     * @param scan the directory scan results
     * @param progress the progress tracker
     * @param options the filter options
     * @param i progress counter
     * @param writer the XML formatting output stream writer
     * 
     * @throws XMLStreamException if writing fails
     * @throws IOException if resource files are missing
     */
    private void writeSoftwareList(final DirScan scan, final ProgressHandler progress, Set<Options> options, AtomicInteger i, final EnhancedXMLStreamWriter writer)
            throws XMLStreamException, IOException {
        Map<String, Map<String, AtomicInteger>> slcounter = new HashMap<>();
        Map<String, SL> slmap = new HashMap<>();
        buildSLMap(scan, progress, options, i, slcounter, slmap);
        if (slmap.size() > 1) {
            writer.writeDTD("<!DOCTYPE softwarelists [\n" + IOUtils.toString(Export.class.getResourceAsStream("/jrm/resources/dtd/softwarelists.dtd"), StandardCharsets.UTF_8) //$NON-NLS-1$ //$NON-NLS-2$
                    + "\n]>" + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
            writer.writeStartElement("softwarelists"); //$NON-NLS-1$
        } else
            writer.writeDTD("<!DOCTYPE softwarelist [\n" + IOUtils.toString(Export.class.getResourceAsStream("/jrm/resources/dtd/softwarelist.dtd"), StandardCharsets.UTF_8) //$NON-NLS-1$ //$NON-NLS-2$
                    + "\n]>" + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
        for (final var e : slmap.entrySet())
            writeSoftwareList(writer, e, options);
        if (slmap.size() > 1)
            writer.writeEndElement();
    }

    /**
     * Serializes a single SoftwareList group mapping.
     * 
     * @param writer the destination stream writer
     * @param sl the software list mapping entry
     * @param options the options filter ruleset
     * 
     * @throws XMLStreamException if writing fails
     */
    private void writeSoftwareList(final EnhancedXMLStreamWriter writer, final java.util.Map.Entry<String, SL> sl, Set<Options> options) throws XMLStreamException {
        writer.writeStartElement("softwarelist", new SimpleAttribute("name", sl.getValue().name)); // $NON-NLS-1$ //$NON-NLS-2$
        if (sl.getValue().softwarelist != null)
            writer.writeElement(DESCRIPTION, sl.getValue().softwarelist.getDescription()); // $NON-NLS-1$
        for (final var ee : sl.getValue().sw.entrySet()) {
            if (ee.getValue().software != null)
                ee.getValue().software.export(writer, ee.getValue().container.getEntries(), EnumSet.of(ExportMode.ALL));
            else {
                writer.writeStartElement("software", new SimpleAttribute("name", ee.getValue().name)); //$NON-NLS-1$ //$NON-NLS-2$
                final var ii = new AtomicInteger();
                for (Entry entry : ee.getValue().container.getEntries()) {
                    if (entry.getType() == Type.CHD)
                        writeSWCHD(writer, entry, ii, options);
                    else
                        writeSWRom(writer, entry, ii, options);
                }
                writer.writeEndElement();
            }
        }
        writer.writeEndElement();
    }

    /**
     * Serializes a standard ROM entry nested inside a Software List partition element.
     * 
     * @param writer the stream writer
     * @param entry the parsed file details
     * @param ii incremental sequence element counter
     * @param options options filters ruleset
     * 
     * @throws XMLStreamException if writing fails
     */
    private void writeSWRom(final EnhancedXMLStreamWriter writer, Entry entry, final AtomicInteger ii, Set<Options> options) throws XMLStreamException {
        String ename = normalize(entry.getName());
        if (options.contains(Options.JUNK_SUBFOLDERS)) {
            Path path = Paths.get(ename);
            Path fileName = path.getFileName();
            if (fileName != null)
                ename = fileName.toString();
        }
        writer.writeStartElement("part", //$NON-NLS-1$
                new SimpleAttribute("name", "flop" + ii.incrementAndGet()), //$NON-NLS-1$ //$NON-NLS-2$
                new SimpleAttribute("interface", "floppy_3_5") //$NON-NLS-1$ //$NON-NLS-2$
        );
        writer.writeStartElement("dataarea", //$NON-NLS-1$
                new SimpleAttribute("name", "flop"), //$NON-NLS-1$ //$NON-NLS-2$
                new SimpleAttribute("size", entry.getSize()) //$NON-NLS-1$
        );
        writer.writeElement("rom", //$NON-NLS-1$
                new SimpleAttribute("name", ename), //$NON-NLS-1$
                new SimpleAttribute("size", entry.getSize()), //$NON-NLS-1$
                new SimpleAttribute("crc", entry.getCrc()), //$NON-NLS-1$
                new SimpleAttribute("sha1", options.contains(Options.NEED_SHA1) ? entry.getSha1() : null) //$NON-NLS-1$
        );
        writer.writeEndElement();
        writer.writeEndElement();
    }

    /**
     * Serializes a CHD disk image entry nested inside a Software List partition element.
     * 
     * @param writer the stream writer
     * @param entry the disk file details
     * @param ii incremental sequence element counter
     * @param options options filters ruleset
     * 
     * @throws XMLStreamException if writing fails
     */
    private void writeSWCHD(final EnhancedXMLStreamWriter writer, Entry entry, final AtomicInteger ii, Set<Options> options) throws XMLStreamException {
        String ename = normalize(FilenameUtils.removeExtension(entry.getName()));
        if (options.contains(Options.JUNK_SUBFOLDERS)) {
            Path path = Paths.get(ename);
            Path fileName = path.getFileName();
            if (fileName != null)
                ename = fileName.toString();
        }
        writer.writeStartElement("part", //$NON-NLS-1$
                new SimpleAttribute("name", CDROM + ii.incrementAndGet()), //$NON-NLS-1$ //$NON-NLS-2$
                new SimpleAttribute("interface", CDROM) //$NON-NLS-1$ //$NON-NLS-2$
        );
        writer.writeStartElement("diskarea", //$NON-NLS-1$
                new SimpleAttribute("name", CDROM) //$NON-NLS-1$ //$NON-NLS-2$
        );
        writer.writeElement("disk", //$NON-NLS-1$
                new SimpleAttribute("name", ename), //$NON-NLS-1$
                new SimpleAttribute("sha1", entry.getSha1()) //$NON-NLS-1$
        );
        writer.writeEndElement();
        writer.writeEndElement();
    }

    /**
     * Walks containers to resolve local mappings of physical files to MAME Software and SoftwareList references.
     * 
     * @param scan the directory scanner
     * @param progress the progress handler monitor
     * @param options options filter configuration ruleset
     * @param i progress sequence step counter
     * @param slcounter duplicate naming protection count registry
     * @param slmap the resolved software list structure target map
     */
    private void buildSLMap(final DirScan scan, final ProgressHandler progress, Set<Options> options, AtomicInteger i, Map<String, Map<String, AtomicInteger>> slcounter,
            Map<String, SL> slmap) {
        for (Container c : scan.getContainersIterable()) {
            progress.setProgress(Messages.getString(DIR2_DAT_SAVING), i.incrementAndGet()); // $NON-NLS-1$
            final Path relativized = scan.getDir().toPath().relativize(c.getFile().toPath());
            final Path filename = relativized.getFileName();
            final Path parent = relativized.getParent();
            if (filename == null || parent == null)
                continue;
            final var swname = new StringBuilder(FilenameUtils.removeExtension(filename.toString()));
            final var slname = new StringBuilder(parent.toString());
            final var software = buildSLMapNames(swname, slname, options);
            final var swcounter = slcounter.computeIfAbsent(slname.toString(), _ -> new HashMap<>());
            final var sl = slmap.computeIfAbsent(slname.toString(), k -> new SL(k, software != null ? software.getSl() : null));
            final var val = swcounter.computeIfAbsent(swname.toString(), _ -> new AtomicInteger());
            if (val.incrementAndGet() > 1)
                swname.append("_").append(val.get());
            sl.sw.put(swname.toString(), new SL.SW(swname.toString(), software, c));
        }
    }

    /**
     * Checks if the scanned elements correspond to known profile software structures, returning matches.
     * 
     * @param swname current scanned software set name builder
     * @param slname current scanned parent software list name builder
     * @param options options ruleset configuration
     * 
     * @return the resolved profile {@link Software} structure, or {@code null} if unmatched
     */
    private Software buildSLMapNames(final StringBuilder swname, final StringBuilder slname, Set<Options> options) {
        final Software software;
        if (session.getCurrProfile() != null) {
            SoftwareList sl = session.getCurrProfile().getMachineListList().getSoftwareListList().getByName(slname.toString());
            if (sl != null && sl.containsName(swname.toString()))
                software = sl.getByName(swname.toString());
            else
                software = null;
            if (software != null && options.contains(Options.MATCH_PROFILE)) {
                swname.setLength(0);
                swname.append(software.getBaseName());
                slname.setLength(0);
                slname.append(software.getSl().getBaseName());
            }
        } else
            software = null;
        return software;
    }

    /**
     * Serializes the scanned folder data into a generic dat XML schema format.
     * 
     * @param scan the parsed directory contents
     * @param progress the progress channel
     * @param options options constraints ruleset
     * @param headers the header tags map values
     * @param i incremental sequence counter
     * @param writer the stream writer target
     * 
     * @throws XMLStreamException if serialization fails
     * @throws IOException if static template files cannot be read
     */
    private void writeDataFile(final DirScan scan, final ProgressHandler progress, Set<Options> options, Map<String, String> headers, AtomicInteger i,
            final EnhancedXMLStreamWriter writer) throws XMLStreamException, IOException {
        writer.writeDTD("<!DOCTYPE datafile [\n" + IOUtils.toString(Export.class.getResourceAsStream("/jrm/resources/dtd/datafile.dtd"), StandardCharsets.UTF_8) + "\n]>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                                                                                                                                                              // //$NON-NLS-4$
        writer.writeStartElement("datafile"); //$NON-NLS-1$
        writer.writeStartElement("header"); //$NON-NLS-1$
        for (Map.Entry<String, String> entry : headers.entrySet())
            writer.writeElement(entry.getKey(), entry.getValue());
        writer.writeEndElement();
        Map<String, AtomicInteger> counter = new HashMap<>();
        for (Container container : scan.getContainersIterable()) {
            progress.setProgress(Messages.getString(DIR2_DAT_SAVING), i.incrementAndGet()); // $NON-NLS-1$
            writeDataFile(writer, container, counter, options);
        }
        writer.writeEndElement();
    }

    /**
     * Writes a single physical container out as a "game" XML element.
     * 
     * @param writer the XML formatting writer
     * @param container the scanned file package metadata
     * @param counter duplicate game protection counting registry
     * @param options the active scan options ruleset
     * 
     * @throws XMLStreamException if serialization fails
     */
    private void writeDataFile(final EnhancedXMLStreamWriter writer, Container container, Map<String, AtomicInteger> counter, Set<Options> options) throws XMLStreamException {
        String name = FilenameUtils.removeExtension(container.getFile().getName());
        final var machine = (session.getCurrProfile() != null && options.contains(Options.MATCH_PROFILE)) ? session.getCurrProfile().getMachineListList().get(0).getByName(name)
                : null;
        if (machine != null)
            name = machine.getBaseName();
        final var val = counter.computeIfAbsent(name, _ -> new AtomicInteger());
        if (val.incrementAndGet() > 1)
            name = name + "_" + val.get(); //$NON-NLS-1$
        writer.writeStartElement("game", //$NON-NLS-1$
                new SimpleAttribute("name", name), //$NON-NLS-1$
                new SimpleAttribute("isbios", Optional.ofNullable(machine).filter(Machine::isBios).map(_ -> "yes").orElse(null)), //$NON-NLS-1$ //$NON-NLS-2$
                new SimpleAttribute("cloneof", Optional.ofNullable(machine).map(Machine::getCloneof).orElse(null)), //$NON-NLS-1$
                new SimpleAttribute("romof", Optional.ofNullable(machine).map(Machine::getRomof).orElse(null)), //$NON-NLS-1$
                new SimpleAttribute("sampleof", Optional.ofNullable(machine).map(Machine::getSampleof).orElse(null)) //$NON-NLS-1$
        );
        writer.writeElement(DESCRIPTION, machine != null ? machine.description : name); // $NON-NLS-1$
        writer.writeElement("year", machine != null ? machine.year : "????"); //$NON-NLS-1$ //$NON-NLS-2$
        writer.writeElement("manufacturer", machine != null ? machine.manufacturer : ""); //$NON-NLS-1$ //$NON-NLS-2$
        for (Entry entry : container.getEntries()) {
            if (entry.getType() == Type.CHD)
                writeDataFileCHD(writer, entry, options);
            else
                writeDataFileRom(writer, entry, options);
        }
        writer.writeEndElement();
    }

    /**
     * Writes a "rom" element metadata tag.
     * 
     * @param writer the stream writer
     * @param entry the physical file metrics
     * @param options the scanning verification ruleset
     * 
     * @throws XMLStreamException if writing fails
     */
    private void writeDataFileRom(final EnhancedXMLStreamWriter writer, Entry entry, Set<Options> options) throws XMLStreamException {
        String ename = normalize(entry.getName());
        if (options.contains(Options.JUNK_SUBFOLDERS)) {
            Path path = Paths.get(ename);
            Path fileName = path.getFileName();
            if (fileName != null)
                ename = fileName.toString();
        }
        writer.writeElement("rom", //$NON-NLS-1$
                new SimpleAttribute("name", ename), //$NON-NLS-1$
                new SimpleAttribute("size", entry.getSize()), //$NON-NLS-1$
                new SimpleAttribute("crc", entry.getCrc()), //$NON-NLS-1$
                new SimpleAttribute("md5", options.contains(Options.NEED_MD5) ? entry.getMd5() : null), //$NON-NLS-1$
                new SimpleAttribute("sha1", options.contains(Options.NEED_SHA1) ? entry.getSha1() : null), //$NON-NLS-1$
                new SimpleAttribute("offset", 0), //$NON-NLS-1$
                new SimpleAttribute("date", entry.getModified()) //$NON-NLS-1$
        );
    }

    /**
     * Writes a "disk" element metadata tag representing a CHD hard disk package.
     * 
     * @param writer the stream writer
     * @param entry the hard disk file properties
     * @param options the active ruleset constraints
     * 
     * @throws XMLStreamException if writing fails
     */
    private void writeDataFileCHD(final EnhancedXMLStreamWriter writer, Entry entry, Set<Options> options) throws XMLStreamException {
        String ename = normalize(FilenameUtils.removeExtension(entry.getName()));
        if (options.contains(Options.JUNK_SUBFOLDERS)) {
            Path path = Paths.get(ename);
            Path fileName = path.getFileName();
            if (fileName != null)
                ename = fileName.toString();
        }
        writer.writeElement("disk", //$NON-NLS-1$
                new SimpleAttribute("name", ename), //$NON-NLS-1$
                new SimpleAttribute("md5", entry.getMd5()), //$NON-NLS-1$
                new SimpleAttribute("sha1", entry.getSha1()) //$NON-NLS-1$
        );
    }

    /**
     * Writes a full list of physical files as a "mame" DTD-compliant XML representation.
     * 
     * @param scan the parsed directory contents
     * @param progress the progress handler channel
     * @param options the options filters ruleset
     * @param i incremental progress counter
     * @param writer the target stream writer
     * 
     * @throws XMLStreamException if serialization fails
     * @throws IOException if resource files are missing
     */
    private void writeMame(final DirScan scan, final ProgressHandler progress, Set<Options> options, AtomicInteger i, final EnhancedXMLStreamWriter writer)
            throws XMLStreamException, IOException {
        writer.writeDTD("<!DOCTYPE mame [\n" + IOUtils.toString(Export.class.getResourceAsStream("/jrm/resources/dtd/mame.dtd"), StandardCharsets.UTF_8) + "\n]>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                                                                                                                                                      // //$NON-NLS-4$
        writer.writeStartElement("mame"); //$NON-NLS-1$
        Map<String, AtomicInteger> counter = new HashMap<>();
        for (Container c : scan.getContainersIterable()) {
            progress.setProgress(Messages.getString(DIR2_DAT_SAVING), i.incrementAndGet()); // $NON-NLS-1$
            writeMame(writer, c, counter, options);
        }
        writer.writeEndElement();
    }

    /**
     * Writes a single container as a standard "machine" element matching MAME specifications.
     * 
     * @param writer the XML stream writer
     * @param container the scanned file package metadata
     * @param counter duplicate safety protections counter registry
     * @param options the active scanning ruleset constraints
     * 
     * @throws XMLStreamException if writing fails
     */
    private void writeMame(final EnhancedXMLStreamWriter writer, Container container, Map<String, AtomicInteger> counter, Set<Options> options) throws XMLStreamException {
        String name = FilenameUtils.removeExtension(container.getFile().getName());
        Machine machine = (session.getCurrProfile() != null && options.contains(Options.MATCH_PROFILE)) ? session.getCurrProfile().getMachineListList().get(0).getByName(name)
                : null;
        if (machine != null)
            name = machine.getBaseName();
        final var val = counter.computeIfAbsent(name, _ -> new AtomicInteger());
        if (val.incrementAndGet() > 1)
            name = name + "_" + val.get(); //$NON-NLS-1$
        writer.writeStartElement("machine", //$NON-NLS-1$
                new SimpleAttribute("name", name), //$NON-NLS-1$
                new SimpleAttribute("isbios", Optional.ofNullable(machine).filter(Machine::isBios).map(_ -> "yes").orElse(null)), //$NON-NLS-1$ //$NON-NLS-2$
                new SimpleAttribute("isdevice", Optional.ofNullable(machine).filter(Machine::isIsdevice).map(_ -> "yes").orElse(null)), //$NON-NLS-1$ //$NON-NLS-2$
                new SimpleAttribute("ismechanical", Optional.ofNullable(machine).filter(Machine::isIsmechanical).map(_ -> "yes").orElse(null)), //$NON-NLS-1$ //$NON-NLS-2$
                new SimpleAttribute("cloneof", Optional.ofNullable(machine).map(Machine::getCloneof).orElse(null)), //$NON-NLS-1$
                new SimpleAttribute("romof", Optional.ofNullable(machine).map(Machine::getRomof).orElse(null)), //$NON-NLS-1$
                new SimpleAttribute("sampleof", Optional.ofNullable(machine).map(Machine::getSampleof).orElse(null)) //$NON-NLS-1$
        );
        writer.writeElement(DESCRIPTION, machine != null ? machine.description : name); // $NON-NLS-1$
        writer.writeElement("year", machine != null ? machine.year : "????"); //$NON-NLS-1$ //$NON-NLS-2$
        writer.writeElement("manufacturer", machine != null ? machine.manufacturer : ""); //$NON-NLS-1$ //$NON-NLS-2$
        for (Entry e : container.getEntries()) {
            if (e.getType() == Type.CHD)
                writeDataFileCHD(writer, e, options);
            else
                writeMameRom(writer, e, options);
        }
        writer.writeEndElement();
    }

    /**
     * Writes a MAME-compatible "rom" element metadata tag.
     * 
     * @param writer the stream writer
     * @param entry the physical file properties
     * @param options the active filters configuration ruleset
     * 
     * @throws XMLStreamException if writing fails
     */
    private void writeMameRom(final EnhancedXMLStreamWriter writer, Entry entry, Set<Options> options) throws XMLStreamException {
        String ename = normalize(entry.getName());
        if (options.contains(Options.JUNK_SUBFOLDERS)) {
            Path path = Paths.get(ename);
            Path fileName = path.getFileName();
            if (fileName != null)
                ename = fileName.toString();
        }
        writer.writeElement("rom", //$NON-NLS-1$
                new SimpleAttribute("name", ename), //$NON-NLS-1$
                new SimpleAttribute("size", entry.getSize()), //$NON-NLS-1$
                new SimpleAttribute("crc", entry.getCrc()), //$NON-NLS-1$
                new SimpleAttribute("md5", options.contains(Options.NEED_MD5) ? entry.getMd5() : null), //$NON-NLS-1$
                new SimpleAttribute("sha1", options.contains(Options.NEED_SHA1) ? entry.getSha1() : null) //$NON-NLS-1$
        );
    }

    /**
     * Local private structure containing software list category maps and sub software listings.
     */
    private static class SL {
        /**
         * The name of the software list package.
         */
        private String name;
        /**
         * The parsed profile metadata list software list reference, or {@code null}.
         */
        private SoftwareList softwarelist = null;
        /**
         * Map containing associated soft ware configurations indexed by name.
         */
        private Map<String, SW> sw = new HashMap<>();

        /**
         * Local private structure describing an individual software instance within the software list container.
         */
        private static class SW {
            /**
             * The physical name of the software package.
             */
            private String name;
            /**
             * The parsed profile metadata software reference, or {@code null}.
             */
            private Software software = null;
            /**
             * The scanned container instance tracking physical files.
             */
            private Container container = null;

            /**
             * Instantiates a new Software mapping wrapper.
             * 
             * @param name the game reference name
             * @param software the resolved profile details (can be null)
             * @param container the scanned physical zip contents
             */
            private SW(String name, Software software, Container container) {
                this.name = name;
                this.software = software;
                this.container = container;
            }
        }

        /**
         * Instantiates a new SoftwareList package tracking mapping.
         * 
         * @param name the partition sub-folder package name
         * @param softwarelist the active profile SoftwareList details
         */
        private SL(String name, SoftwareList softwarelist) {
            this.name = name;
            this.softwarelist = softwarelist;
        }
    }

    /**
     * Normalize path character separator markers based on standard OS platforms.
     * 
     * @param entry the path sequence string to normalize
     * 
     * @return the platform corrected path representation sequence
     */
    private String normalize(final String entry) {
        if (File.separatorChar == '/')
            return entry.replace('\\', '/');
        return entry.replace('/', '\\');
    }
}
