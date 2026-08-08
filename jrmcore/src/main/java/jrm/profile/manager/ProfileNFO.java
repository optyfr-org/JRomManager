/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.manager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FilenameUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import jrm.aui.status.StatusRendererFactory;
import jrm.misc.Log;
import jrm.security.DeserializationFilter;
import jrm.security.Session;

import lombok.Getter;
import lombok.Setter;

/**
 * Handles the profile .nfo file lifecycle, storing stats, mame references, profile name, and file connections. Supports custom
 * serialization.
 * 
 * @author optyfr
 */
public final class ProfileNFO implements Serializable, StatusRendererFactory {
    /**
     * Field name constant for MAME info serialization.
     */
    private static final String MAME_STR = "mame";

    /**
     * Field name constant for profile statistics serialization.
     */
    private static final String STATS_STR = "stats";

    /**
     * Field name constant for profile name serialization.
     */
    private static final String NAME_STR = "name";

    /**
     * Field name constant for associated file serialization.
     */
    private static final String FILE_STR = "file";

    /**
     * Undefined status placeholder.
     */
    private static final String U = "?";

    /**
     * Double undefined status placeholder.
     */
    private static final String U_OF_U = "?/?";

    /**
     * Fraction formatting template.
     */
    private static final String N_OF_T = "%s/%d";

    /**
     * JRomManager identifier string.
     */
    private static final String JROMMANAGER_STR = "JRomManager";

    /**
     * Standard date-time formatter for displaying timestamps in the user's local timezone.
     */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * Unknown date-time formatted placeholder.
     */
    private static final String UNKNOWN_DATE = "????-??-?? ??:??:??";

    /**
     * Serial version UID for maintaining serialization compatibility.
     */
    private static final long serialVersionUID = 3L;

    /**
     * The profile's physical database file path (e.g. .jrm, .dat, .xml).
     * 
     * @return the associated profile database {@link File}
     */
    private @Getter File file = null;

    /**
     * The descriptive profile name to be rendered in the user interface.
     * 
     * @return the profile's display name
     */
    private @Getter String name = null;

    /**
     * The profile completion stats and event logs container.
     * 
     * @return the profile statistics tracker
     */
    private @Getter ProfileNFOStats stats = new ProfileNFOStats();

    /**
     * The linked MAME configuration status and paths tracker.
     * 
     * @return the MAME configuration manager
     */
    private @Getter ProfileNFOMame mame = new ProfileNFOMame();

    /**
     * Temporary placeholder for profile renaming transactions.
     * 
     * @param newName the tentative new profile name
     * 
     * @return the temporary new name
     */
    private transient @Getter @Setter String newName = null;

    /**
     * Declares persistent serialization fields for compliant manual object serialization.
     * 
     * @serialField file File the associated profile file location
     * @serialField name String display name of this profile
     * @serialField stats ProfileNFOStats completion statistics
     * @serialField mame ProfileNFOMame MAME executable configurations
     */
    private static final ObjectStreamField[] serialPersistentFields = { // NOSONAR
            new ObjectStreamField(FILE_STR, File.class),
            new ObjectStreamField(NAME_STR, String.class),
            new ObjectStreamField(STATS_STR, ProfileNFOStats.class),
            new ObjectStreamField(MAME_STR, ProfileNFOMame.class)
    };

    /**
     * Manually serializes the state of this profile NFO instance to the destination stream.
     * 
     * @param stream the target {@link ObjectOutputStream}
     * 
     * @throws IOException if a physical write error occurs
     */
    private void writeObject(final java.io.ObjectOutputStream stream) throws IOException {
        final var fields = stream.putFields();
        fields.put(FILE_STR, file); // $NON-NLS-1$
        fields.put(NAME_STR, name); // $NON-NLS-1$
        fields.put(STATS_STR, stats); // $NON-NLS-1$
        fields.put(MAME_STR, mame); // $NON-NLS-1$
        stream.writeFields();
    }

    /**
     * Manually deserializes the state of this profile NFO instance from the source stream.
     * 
     * @param stream the source {@link ObjectInputStream}
     * 
     * @throws IOException if a physical read error occurs
     * @throws ClassNotFoundException if any serialized class representation cannot be resolved
     */
    private void readObject(final java.io.ObjectInputStream stream) throws IOException, ClassNotFoundException {
        final ObjectInputStream.GetField fields = stream.readFields();
        file = (File) fields.get(FILE_STR, null); // $NON-NLS-1$
        name = (String) fields.get(NAME_STR, null); // $NON-NLS-1$
        stats = (ProfileNFOStats) fields.get(STATS_STR, new ProfileNFOStats()); // $NON-NLS-1$
        mame = (ProfileNFOMame) fields.get(MAME_STR, new ProfileNFOMame()); // $NON-NLS-1$
    }

    /**
     * Private internal constructor associating a profile NFO with its main database file. Sets the creation date and loads metadata
     * properties from JRM configuration file if possible.
     * 
     * @param file the associated database file
     */
    private ProfileNFO(final File file) {
        this.file = file;
        name = file.getName();
        stats.setCreated(Instant.now());
        if (isJRM())
            loadJrm(file);
    }

    /**
     * Resolves the absolute path where the .nfo metadata file for a profile is stored.
     * 
     * @param session the current active security session
     * @param file the associated profile database file
     * 
     * @return the resolved physical .nfo {@link File} on disk
     */
    private static File getFileNfo(final Session session, final File file) {
        return session.getUser().getSettings().getWorkFile(file.getParentFile(), file.getName(), ".nfo");
    }

    /**
     * Safely migrates this profile and its metadata to point to a new database file location. Deletes the legacy .nfo metadata
     * file, updates internal files references, and re-saves.
     * 
     * @param session the current active security session
     * @param file the new target physical folder or file path on disk
     */
    public void relocate(final Session session, final File file) {
        try {
            Files.deleteIfExists(ProfileNFO.getFileNfo(session, this.file).toPath());
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
        this.file = file;
        name = file.getName();
        save(session);
    }

    /**
     * Loads metadata properties from an existing .nfo file if present and fresh, otherwise instantiates a new empty ProfileNFO for
     * the supplied profile file.
     * 
     * @param session the current active security session
     * @param file the profile database file
     * 
     * @return the loaded or newly created {@link ProfileNFO}
     */
    public static ProfileNFO load(final Session session, final File file) {
        final var filenfo = ProfileNFO.getFileNfo(session, file);
        if (filenfo.lastModified() >= file.lastModified()) // $NON-NLS-1$
        {
            try (final var ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(filenfo)))) {
                // Apply deserialization filter to prevent arbitrary code execution
                ois.setObjectInputFilter(DeserializationFilter.createFilter());
                ProfileNFO nfo = (ProfileNFO) ois.readObject();
                if (nfo.file != null)
                    return nfo;
            } catch (final Exception e) {
                Log.err(e.getMessage(), e);
            }
        }
        return new ProfileNFO(file);
    }

    /**
     * Persists this ProfileNFO statistics to disk. If this profile is linked to a JRM file, it also updates and saves the JRM XML
     * configuration properties file first.
     * 
     * @param session the current active security session
     */
    public void save(final Session session) {
        if (isJRM())
            try {
                final var modified = Files.getLastModifiedTime(file.toPath());
                saveJrm(file, mame.getFileroms(), mame.getFilesl());
                Files.setLastModifiedTime(file.toPath(), modified);
            } catch (ParserConfigurationException | TransformerException | IOException e) {
                Log.err(e.getMessage(), e);
            }
        try (final var oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(ProfileNFO.getFileNfo(session, file))))) {
            oos.writeObject(this);
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Evaluates whether the associated database file is a custom JRomManager profile package (.jrm).
     * 
     * @return {@code true} if the extension is "jrm"; {@code false} otherwise
     */
    public boolean isJRM() {
        return FilenameUtils.getExtension(file.getName()).equals("jrm"); //$NON-NLS-1$
    }

    /**
     * Parses a standard XML JRM file to extract database paths and updates the internal MAME reference paths accordingly.
     * 
     * @param jrmfile the physical JRM configuration file on disk
     */
    public void loadJrm(final File jrmfile) {
        final var factory = SAXParserFactory.newInstance();
        try {
            final var parser = factory.newSAXParser();
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, ""); // Compliant
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); // compliant
            parser.parse(jrmfile, new DefaultHandler() {
                private boolean inJrm = false;

                @Override
                public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) throws SAXException {
                    if (qName.equalsIgnoreCase(JROMMANAGER_STR)) // $NON-NLS-1$
                    {
                        inJrm = true;
                    } else if (qName.equalsIgnoreCase("Profile") && inJrm) //$NON-NLS-1$
                    {
                        for (var i = 0; i < attributes.getLength(); i++) {
                            switch (attributes.getQName(i).toLowerCase()) {
                                case "roms" -> mame.setFileroms(new File(jrmfile.getParentFile(), attributes.getValue(i))); //$NON-NLS-1$
                                case "sl" -> mame.setFilesl(new File(jrmfile.getParentFile(), attributes.getValue(i))); //$NON-NLS-1$
                                default -> { /* do nothing */ }
                            }
                        }
                    }
                }

                @Override
                public void endElement(final String uri, final String localName, final String qName) throws SAXException {
                    if (qName.equalsIgnoreCase(JROMMANAGER_STR)) // $NON-NLS-1$
                    {
                        inJrm = false;
                    }
                }
            });
        } catch (ParserConfigurationException | SAXException | IOException e) {
            // JOptionPane.showMessageDialog(null, e, "Exception",
            // JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Creates and populates a new JRM XML configuration file linking physical ROM and software list DATs.
     * 
     * @param jrmFile the destination JRM file path
     * @param romsFile the associated ROMs list DAT file
     * @param slFile the optional associated software lists DAT file
     * 
     * @return the saved JRM file
     * 
     * @throws ParserConfigurationException if the XML document builder cannot be configured
     * @throws TransformerException if the XML document cannot be transformed to a file
     */
    public static File saveJrm(final File jrmFile, final File romsFile, final File slFile) throws ParserConfigurationException, TransformerException {
        final var docFactory = DocumentBuilderFactory.newInstance();
        final var docBuilder = docFactory.newDocumentBuilder();
        final var doc = docBuilder.newDocument();
        final var rootElement = doc.createElement(JROMMANAGER_STR); // $NON-NLS-1$
        doc.appendChild(rootElement);
        final var profile = doc.createElement("Profile"); //$NON-NLS-1$
        profile.setAttribute("roms", romsFile.getName()); //$NON-NLS-1$
        if (slFile != null)
            profile.setAttribute("sl", slFile.getName()); //$NON-NLS-1$
        rootElement.appendChild(profile);
        final var transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); // Compliant
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, ""); // Compliant
        final var transformer = transformerFactory.newTransformer();
        final var source = new DOMSource(doc);
        final var result = new StreamResult(jrmFile);
        transformer.transform(source, result);
        return jrmFile;
    }

    /**
     * Cleans up and deletes all metadata, cache, and properties files associated with this profile on disk.
     * 
     * @return {@code true} if deletion of the main file succeeded; {@code false} otherwise
     */
    public boolean delete() {
        try {
            if (Files.deleteIfExists(file.toPath())) {
                mame.delete();
                Files.deleteIfExists(Paths.get(file.getAbsolutePath() + ".cache"));
                Files.deleteIfExists(Paths.get(file.getAbsolutePath() + ".nfo"));
                Files.deleteIfExists(Paths.get(file.getAbsolutePath() + ".properties"));
                return true;
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
        return false;
    }

    /**
     * Generates HTML formatted representation of the current catalog database version.
     * 
     * @return the HTML formatted version string
     */
    public String getHTMLVersion() {
        return toDocument(Optional.ofNullable(stats.getVersion()).map(this::toNoBR).orElse(toGray("???"))); //$NON-NLS-1$
    }

    /**
     * Generates HTML formatted representation of owned game sets versus total sets.
     * 
     * @return the HTML formatted sets count string
     */
    public String getHTMLHaveSets() {
        final String have;
        if (stats.getHaveSets() == null) {
            if ((stats.getTotalSets() == null))
                have = toGray(U_OF_U);
            else
                have = String.format(N_OF_T, toGray(U), stats.getTotalSets());
        } else {
            final String n;
            if (stats.getHaveSets() == 0 && stats.getTotalSets() > 0)
                n = toRed("0");
            else if (stats.getHaveSets().equals(stats.getTotalSets()))
                n = toGreen(toStr(stats.getHaveSets()));
            else
                n = toOrange(toStr(stats.getHaveSets()));
            have = String.format(N_OF_T, n, stats.getTotalSets());
        }
        return toDocument(have);
    }

    /**
     * Generates HTML formatted representation of owned ROM files versus total ROMs.
     * 
     * @return the HTML formatted ROMs count string
     */
    public String getHTMLHaveRoms() {
        final String have;
        if (stats.getHaveRoms() == null) {
            if (stats.getTotalRoms() == null)
                have = toGray(U_OF_U);
            else
                have = String.format(N_OF_T, toGray(U), stats.getTotalRoms());
        } else {
            final String n;
            if (stats.getHaveRoms() == 0 && stats.getTotalRoms() > 0)
                n = toRed("0");
            else if (stats.getHaveRoms().equals(stats.getTotalRoms()))
                n = toGreen(toStr(stats.getHaveRoms()));
            else
                n = toOrange(toStr(stats.getHaveRoms()));
            have = String.format(N_OF_T, n, stats.getTotalRoms());
        }
        return toDocument(have);
    }

    /**
     * Generates HTML formatted representation of owned CHD/disk files versus total disks.
     * 
     * @return the HTML formatted disks count string
     */
    public String getHTMLHaveDisks() {
        final String have;
        if (stats.getHaveDisks() == null) {
            if (stats.getTotalDisks() == null)
                have = toGray(U_OF_U);
            else
                have = String.format(N_OF_T, toGray(U), stats.getTotalDisks());
        } else {
            final String n;
            if (stats.getHaveDisks() == 0 && stats.getTotalDisks() > 0)
                n = toRed("0");
            else if (stats.getHaveDisks().equals(stats.getTotalDisks()))
                n = toGreen(toStr(stats.getHaveDisks()));
            else
                n = toOrange(toStr(stats.getHaveDisks()));
            have = String.format(N_OF_T, n, stats.getTotalDisks());
        }
        return toDocument(have);
    }

    /**
     * Generates HTML formatted representation of profile creation timestamp.
     * 
     * @return the HTML formatted creation timestamp string
     */
    public String getHTMLCreated() {
        return toDocument(stats.getCreated() == null ? toGray(UNKNOWN_DATE) : DATE_FORMAT.format(stats.getCreated())); // $NON-NLS-1$
    }

    /**
     * Generates HTML formatted representation of last scanned timestamp.
     * 
     * @return the HTML formatted scanned timestamp string
     */
    public String getHTMLScanned() {
        return toDocument(stats.getScanned() == null ? toGray(UNKNOWN_DATE) : DATE_FORMAT.format(stats.getScanned())); // $NON-NLS-1$
    }

    /**
     * Generates HTML formatted representation of last repair timestamp.
     * 
     * @return the HTML formatted repaired timestamp string
     */
    public String getHTMLFixed() {
        return toDocument(stats.getFixed() == null ? toGray(UNKNOWN_DATE) : DATE_FORMAT.format(stats.getFixed())); // $NON-NLS-1$
    }
    /**
     * Scans the provided directory on disk and loads the metadata profile information of any matching catalog files found inside.
     * 
     * @param session the current active security session
     * @param dir the physical directory to explore
     * 
     * @return the list of discovered and loaded {@link ProfileNFO} files
     */
    public static List<ProfileNFO> list(Session session, File dir) {
        List<ProfileNFO> rows = new ArrayList<>();
        if (dir != null && dir.exists()) {
            final var filedir = dir;
            final File[] files = filedir.listFiles((dir1, name) -> {
                final var f = new File(dir1, name);
                return (f.isFile() && !Arrays.asList("cache", "properties", "nfo", "jrm1", "jrm2").contains(FilenameUtils.getExtension(name))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            });
            if (files != null) {
                Arrays.asList(files).stream().map(f -> ProfileNFO.load(session, f)).forEach(rows::add);
            }
        }
        return rows;
    }

    /**
     * Returns the profile display name.
     * 
     * @return the name string
     */
    @Override
    public String toString() {
        return getName();
    }
}
