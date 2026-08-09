/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import jrm.aui.progress.ProgressHandler;
import jrm.aui.status.StatusRendererFactory;
import jrm.locale.Messages;
import jrm.misc.BreakException;
import jrm.misc.ExceptionUtils;
import jrm.misc.Log;
import jrm.misc.ProfileSettings;
import jrm.misc.ProfileSettingsEnum;
import jrm.misc.SettingsEnum;
import jrm.profile.data.AnywareStatus;
import jrm.profile.data.Device;
import jrm.profile.data.Disk;
import jrm.profile.data.Entity;
import jrm.profile.data.EntityStatus;
import jrm.profile.data.Machine;
import jrm.profile.data.Machine.CabinetType;
import jrm.profile.data.Machine.SWStatus;
import jrm.profile.data.MachineListList;
import jrm.profile.data.Rom;
import jrm.profile.data.Rom.LoadFlag;
import jrm.profile.data.Sample;
import jrm.profile.data.Samples;
import jrm.profile.data.Slot;
import jrm.profile.data.SlotOption;
import jrm.profile.data.Software;
import jrm.profile.data.Software.Part;
import jrm.profile.data.Software.Part.DataArea;
import jrm.profile.data.Software.Part.DataArea.Endianness;
import jrm.profile.data.Software.Part.DiskArea;
import jrm.profile.data.SoftwareList;
import jrm.profile.data.Source;
import jrm.profile.data.Sources;
import jrm.profile.data.SystmDevice;
import jrm.profile.data.SystmMechanical;
import jrm.profile.data.SystmStandard;
import jrm.profile.data.Systms;
import jrm.profile.filter.CatVer;
import jrm.profile.filter.CatVer.Category;
import jrm.profile.filter.CatVer.Category.SubCategory;
import jrm.profile.filter.NPlayer;
import jrm.profile.filter.NPlayers;
import jrm.profile.manager.ProfileNFO;
import jrm.security.DeserializationFilter;
import jrm.security.PathAbstractor;
import jrm.security.Session;
import jrm.xml.XMLTools;
import lombok.Getter;
import lombok.Setter;

/**
 * Parses and models retro system profile databases from DAT catalogs. Loads dat files, matches system constraints, serializes
 * resulting profiles for caching, and configures global filter structures (systems, release years, catver.ini, nplayers.ini).
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public class Profile implements Serializable, StatusRendererFactory {
    private static final long serialVersionUID = 3L;

    private static final String DESCRIPTION = "description";
    private static final String VERSION = "version";

    /**
     * Scanned machines count.
     * 
     * @return count of processed machines
     */
    private @Getter long machinesCnt = 0;
    /**
     * Scanned software lists count.
     * 
     * @return count of software lists
     */
    private @Getter long softwaresListCnt = 0;
    /**
     * Scanned software entries count.
     * 
     * @return count of software entries
     */
    private @Getter long softwaresCnt = 0;
    /**
     * Scanned ROM files count.
     * 
     * @return count of ROM files
     */
    private @Getter long romsCnt = 0;
    /**
     * Scanned software ROM files count.
     * 
     * @return count of software ROM files
     */
    private @Getter long swromsCnt = 0;
    /**
     * Scanned disk files count.
     * 
     * @return count of disk files
     */
    private @Getter long disksCnt = 0;
    /**
     * Scanned software disk files count.
     * 
     * @return count of software disk files
     */
    private @Getter long swdisksCnt = 0;
    /**
     * Scanned sound sample files count.
     * 
     * @return count of processed sample files
     */
    private @Getter long samplesCnt = 0;

    /**
     * Whether MD5 checksum values are declared on ROM elements in the profile.
     * 
     * @return true if MD5 values are present on ROMs
     */
    private @Getter boolean md5Roms = false;
    /**
     * Whether MD5 checksum values are declared on CHD elements in the profile.
     * 
     * @return true if MD5 values are present on CHDs
     */
    private @Getter boolean md5Disks = false;
    /**
     * Whether SHA-1 checksum values are declared on ROM elements in the profile.
     * 
     * @return true if SHA-1 values are present on ROMs
     */
    private @Getter boolean sha1Roms = false;
    /**
     * Whether SHA-1 checksum values are declared on CHD elements in the profile.
     * 
     * @return true if SHA-1 values are present on CHDs
     */
    private @Getter boolean sha1Disks = false;

    /**
     * Build timestamp or identifier string.
     * 
     * @return the build version string
     */
    private @Getter String build = null;
    /**
     * Custom DAT properties read from the XML header elements block.
     * 
     * @return map containing XML header elements key-value associations
     */
    private final @Getter Map<String, StringBuilder> header = new HashMap<>();

    /**
     * Global collection grouping parsed machines, computer clones, and associated software catalogs.
     * 
     * @return the unified target machines listing representation
     */
    private final @Getter MachineListList machineListList = new MachineListList(this);

    /**
     * Set storing ROM CRCs which resolve to distinct SHA1/MD5 signatures.
     * 
     * @return suspicious CRC checksum values set
     */
    private final @Getter Set<String> suspiciousCRC = new HashSet<>();

    /**
     * Dynamic anyware lists visibility status filter settings.
     * 
     * @param filterListLists anyware list visibility filters
     * 
     * @return visibility filters set
     */
    private transient @Getter @Setter Set<AnywareStatus> filterListLists = null;

    /**
     * Dynamic single machine anyware visibility status filter settings.
     * 
     * @param filterList single anyware item filters
     * 
     * @return visibility filters set
     */
    private transient @Getter @Setter Set<AnywareStatus> filterList = null;

    /**
     * Dynamic physical entities visibility status filter settings.
     * 
     * @param filterEntities physical item visibility filters
     * 
     * @return visibility filters set
     */
    private transient @Getter @Setter Set<EntityStatus> filterEntities = null;

    /**
     * Local profiles Settings parameters.
     * 
     * @return profiles settings container
     */
    private transient @Getter ProfileSettings settings = null;
    /**
     * Categorized and grouped system boundaries filter.
     * 
     * @return standard and custom systems filters
     */
    private transient @Getter Systms systems = null;
    /**
     * Dynamic years list collected from scanned elements.
     * 
     * @return sorted collection of years
     */
    private transient @Getter Collection<String> years = null;
    /**
     * JRomManager database profile information stats summary.
     * 
     * @return profile NFO summary reference
     */
    private transient @Getter ProfileNFO nfo = null;
    /**
     * Parsed categories configuration mapping.
     * 
     * @param catver parsed category ruleset mapping
     * 
     * @return categories config mapping
     */
    private transient @Getter @Setter CatVer catver = null;
    /**
     * Parsed multiplayer specifications configuration mapping.
     * 
     * @param nplayers parsed multiplayer capabilities mapping
     * 
     * @return multiplayer config mapping
     */
    private transient @Getter @Setter NPlayers nplayers = null;
    /**
     * Active execution context workspace session.
     * 
     * @return active workspace session
     */
    private transient @Getter Session session = null;
    /**
     * Parsed metadata DAT catalogs specifications tracking metrics.
     * 
     * @return standard dat definitions tracking metrics
     */
    private transient @Getter Sources sources = null;

    /**
     * Protected zero-argument constructor initializing an empty profile.
     */
    private Profile() {

    }

    /**
     * SAX Handler mapping parsed XML tags back into profile domain components.
     */
    private class ProfileHandler extends DefaultHandler {
        /** XML tag name for ROM elements. */
        private static final String STATUS = "status";

        /**
         * Map for tracking ROMs by their CRC values to identify suspicious cases where identical CRCs map to different SHA1/MD5
         * signatures.
         */
        private final HashMap<String, Rom> romsByCRC = new HashMap<>();

        /**
         * Flags and temporary variables used during XML parsing to track the current context and state of the parsing process.
         * These include flags for whether the parser is currently within certain XML elements (e.g., description, year,
         * manufacturer) and references to the current machine, software, ROM, disk, etc., being parsed. The romsByCRC map is used
         * to track ROMs by their CRC values for identifying suspicious cases.
         */
        private boolean inDescription = false;

        /**
         * Flag indicating whether the parser is currently within a "year" XML element. This flag is used to track the context of
         * parsing and to determine when to capture year information for machines or software entries.
         */
        private boolean inYear = false;

        /**
         * Flag indicating whether the parser is currently within a "manufacturer" XML element. This flag is used to track the
         * context of parsing and to determine when to capture manufacturer information for machines or software entries.
         */
        private boolean inManufacturer = false;

        /**
         * Flag indicating whether the parser is currently within a "publisher" XML element. This flag is used to track the context
         * of parsing and to determine when to capture publisher information for software entries.
         */
        private boolean inPublisher = false;

        /**
         * Flag indicating whether the parser is currently within a "header" XML element. This flag is used to track the context of
         * parsing header information.
         */
        private boolean inHeader = false;

        /**
         * Flag indicating whether the parser is currently within a "cabinet" dipswitch element. This flag is used to track the
         * context of parsing cabinet-related dipswitches.
         */
        private boolean inCabinetDipSW = false;

        /**
         * Set of cabinet types currently being parsed within a "cabinet" dipswitch element. This set is used to track the specific
         * cabinet types associated with the dipswitches being parsed, allowing for proper association of dipswitch settings with
         * the relevant machine configurations.
         */
        private final EnumSet<CabinetType> cabTypeSet = EnumSet.noneOf(CabinetType.class);

        /**
         * Reference to the currently parsed software list. This variable is used to keep track of the active software list being
         * parsed in the XML, allowing for proper association of software entries and their related data with the correct software
         * list context.
         */
        private SoftwareList currSoftwareList = null;

        /**
         * Reference to the currently parsed software entry. This variable is used to keep track of the active software entry being
         * parsed in the XML, allowing for proper association of ROMs, disks, and other related data with the correct software entry
         * context.
         */
        private Software currSoftware = null;

        /**
         * Reference to the currently parsed software part. This variable is used to keep track of the active software part being
         * parsed in the XML, allowing for proper association of data areas, disk areas, and other related information with the
         * correct software part context.
         */
        private Software.Part currPart = null;

        /**
         * Reference to the currently parsed software part data area. This variable is used to keep track of the active data area
         * being parsed within a software part, allowing for proper association of ROMs and other related data with the correct data
         * area context.
         */
        private Software.Part.DataArea currDataArea = null;

        /**
         * Reference to the currently parsed software part disk area. This variable is used to keep track of the active disk area
         * being parsed within a software part, allowing for proper association of disks and other related data with the correct
         * disk area context.
         */
        private Software.Part.DiskArea currDiskArea = null;

        /**
         * Reference to the currently parsed machine. This variable is used to keep track of the active machine being parsed in the
         * XML, allowing for proper association of software lists, ROMs, disks, and other related data with the correct machine
         * context.
         */
        private Machine currMachine = null;

        /**
         * Reference to the currently parsed device. This variable is used to keep track of the active device being parsed within a
         * machine, allowing for proper association of device-related elements (such as ROMs or disks) with the correct device
         * context.
         */
        private Device currDevice = null;

        /**
         * Reference to the currently parsed sample set. This variable is used to keep track of the active sample set being parsed
         * in the XML, allowing for proper association of sample files and related data with the correct sample set context.
         */
        private Samples currSampleSet = null;

        /**
         * Reference to the currently parsed ROM. This variable is used to keep track of the active ROM being parsed in the XML,
         * allowing for proper association of ROM attributes and related data with the correct ROM context.
         */
        private Rom currRom = null;

        /**
         * Reference to the currently parsed disk. This variable is used to keep track of the active disk being parsed in the XML,
         * allowing for proper association of disk attributes and related data with the correct disk context.
         */
        private Disk currDisk = null;

        /**
         * Reference to the currently parsed slot. This variable is used to keep track of the active slot being parsed within a
         * machine, allowing for proper association of slot options and related data with the correct slot context.
         */
        private Slot currSlot = null;

        /**
         * Set of parsed ROM names for the current machine, software, or device, used to keep track of processed ROMs and detect or
         * prevent duplicates.
         */
        private final HashSet<String> roms = new HashSet<>();

        /**
         * Set of disk names currently being parsed. This set is used to track the specific disk names associated with the disks
         * being parsed, allowing for proper association of disk attributes and related data with the correct disk context.
         */
        private final HashSet<String> disks = new HashSet<>();

        /**
         * Current XML tag name being processed. This variable is used to keep track of the active XML tag being parsed, allowing
         * for context-aware processing of attributes and content based on the specific tag being handled.
         */
        private String currTag;

        /**
         * Progress handler reference for monitoring and reporting progress during the XML parsing process. This variable is used to
         * interact with the progress handler to provide updates on the parsing progress, report any errors encountered, and manage
         * cancellation requests if necessary.
         */
        private final ProgressHandler handler;

        /**
         * Instantiates a new parsing XML SAX handler.
         * 
         * @param handler the progress handler monitor
         */
        public ProfileHandler(ProgressHandler handler) {
            this.handler = handler;
        }

        /**
         * Intercepts the start of XML elements to parse and delegate metadata attributes to their respective profile component
         * builder methods.
         *
         * @param uri the Namespace URI, or the empty string if the element has no Namespace URI or if Namespace processing is not
         *        being performed
         * @param localName the local name (without prefix), or the empty string if Namespace processing is not being performed
         * @param qName the qualified name (with prefix), or the empty string if qualified names are not available
         * @param attributes the attributes attached to the element
         * 
         * @throws SAXException if any parsing or configuration error occurs during processing
         */
        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) throws SAXException {
            try {
                currTag = qName;
                switch (qName) {
                    case "mame", "datafile" -> startDatfile(attributes);
                    case "header" -> startHeader();
                    case "softwarelist" -> startSoftwareList(attributes);
                    case "software" -> startSoftware(attributes);
                    case "feature" -> startSoftwareFeature(attributes);
                    case "part" -> startSoftwarePart(attributes);
                    case "dataarea" -> startSoftwarePartDataarea(attributes);
                    case "diskarea" -> startSoftwarePartDiskarea(attributes);
                    case "machine", "game" -> startMachine(attributes);
                    case DESCRIPTION -> startDescription();
                    case "year" -> startYear();
                    case "manufacturer" -> startManufacturer();
                    case "publisher" -> startPublisher();
                    case "driver" -> startDriver(attributes);
                    case "display" -> startDisplay(attributes);
                    case "input" -> startInput(attributes);
                    case "device" -> startDevice(attributes);
                    case "instance" -> startInstance(attributes);
                    case "extension" -> startExtension(attributes);
                    case "dipswitch" -> startDipSwitch(attributes);
                    case "dipvalue" -> startDipValue(attributes);
                    case "sample" -> startSample(attributes);
                    case "device_ref" -> startDeviceRef(attributes);
                    case "slot" -> startSlot(attributes);
                    case "slotoption" -> startSlotOption(attributes);
                    case "rom" -> startRom(attributes);
                    case "disk" -> startDisk(attributes);
                    default -> {
                        /* skip unknown */ }
                }
            } catch (Exception e) {
                throw new ProfileHandlerException(getDebugMsg(attributes, qName, e), e);
            }
        }

        /**
         * Exception thrown during parsing errors inside the SAX parser pipeline.
         */
        private class ProfileHandlerException extends RuntimeException {
            private static final long serialVersionUID = 1L;

            /**
             * Constructs a new parser exception.
             * 
             * @param message debug explanation message
             * @param e nested source exception
             */
            public ProfileHandlerException(String message, Exception e) {
                super(message, e);
            }

        }

        /**
         * Generates detailed debug information string mapping where error was met.
         * 
         * @param attributes parsed XML attributes list
         * @param qName active parsed element tag name
         * @param e source exception
         * 
         * @return debug string details
         */
        private String getDebugMsg(Attributes attributes, String qName, Exception e) {
            final var msg = new StringBuilder("Error");

            // Add machine or software list context
            if (currMachine != null) {
                msg.append(" for machine ").append(currMachine.getName());
            } else if (currSoftwareList != null) {
                msg.append(" for software list ").append(currSoftwareList.getName());
                if (currSoftware != null)
                    msg.append(", software ").append(currSoftware.getName());
            }

            // Add ROM and disk context
            if (currRom != null)
                msg.append(", rom ").append(currRom.getName());
            if (currDisk != null)
                msg.append(", disk ").append(currDisk.getName());

            // Add XML tag and attributes using Stream API
            msg.append(", xmltag=").append(qName);
            final var attrs = IntStream.range(0, attributes.getLength())
                    .mapToObj(i -> attributes.getQName(i) + "=" + attributes.getValue(i))
                    .collect(Collectors.joining(", "));
            msg.append(", xmlattributes={").append(attrs).append("}");

            // Add exception details
            msg.append("\nOriginal exception=").append(e.getClass().getSimpleName())
                    .append(" ").append(e.getMessage());
            return msg.toString();
        }

        /**
         * Parsed "disk" element parser callback.
         * 
         * @param attributes element attributes list
         * 
         * @throws NumberFormatException if integer values are invalid
         */
        private void startDisk(final Attributes attributes) throws NumberFormatException {
            if (currMachine == null && currSoftware == null)
                return;
            currDisk = new Disk(currMachine != null ? currMachine : currSoftware);
            if (currSoftware != null && currDiskArea != null)
                currDiskArea.getDisks().add(currDisk);
            for (var i = 0; i < attributes.getLength(); i++) {
                final var value = attributes.getValue(i);
                switch (attributes.getQName(i)) {
                    case "name" -> { //$NON-NLS-1$
                        var name = value.trim();
                        if (name.endsWith(".chd")) //$NON-NLS-1$
                            name = name.substring(0, name.length() - 4);
                        currDisk.setName(name);
                    }
                    case "sha1" -> { //$NON-NLS-1$
                        currDisk.setSha1(safeHex(value, 40));
                        sha1Disks = true;
                    }
                    case "md5" -> { //$NON-NLS-1$
                        currDisk.setMd5(safeHex(value, 32));
                        md5Disks = true;
                    }
                    case "merge" -> currDisk.setMerge(value.trim()); //$NON-NLS-1$
                    case "index" -> currDisk.setIndex(Integer.decode(value)); //$NON-NLS-1$
                    case "optional" -> currDisk.setOptional(BooleanUtils.toBoolean(value)); //$NON-NLS-1$
                    case "writeable" -> currDisk.setWriteable(BooleanUtils.toBoolean(value)); //$NON-NLS-1$
                    case "region" -> currDisk.setRegion(value); //$NON-NLS-1$
                    case STATUS -> currDisk.setDumpStatus(Entity.Status.valueOf(value)); // $NON-NLS-1$
                    default -> {
                        /* skip unknown */ }
                }
            }
        }

        /**
         * Parsed "rom" element parser callback.
         * 
         * @param attributes element attributes list
         * 
         * @throws NumberFormatException if values are invalid
         */
        private void startRom(final Attributes attributes) throws NumberFormatException {
            if (currMachine == null && currSoftware == null)
                return;
            currRom = new Rom(currMachine != null ? currMachine : currSoftware);
            if (currSoftware != null && currDataArea != null)
                currDataArea.getRoms().add(currRom);
            for (var i = 0; i < attributes.getLength(); i++) {
                final var value = attributes.getValue(i);
                switch (attributes.getQName(i)) {
                    case "name" -> currRom.setName(value.trim()); //$NON-NLS-1$
                    case "size" -> currRom.setSize(Long.decode(value)); //$NON-NLS-1$
                    case "offset" -> { //$NON-NLS-1$
                        if (value.toLowerCase().startsWith("0x")) //$NON-NLS-1$
                            currRom.setOffset(Long.decode(value));
                        else
                            currRom.setOffset(Long.decode("0x" + value)); //$NON-NLS-1$
                    }
                    case "value" -> currRom.setValue(value); //$NON-NLS-1$
                    case "crc" -> currRom.setCrc(safeHex(value, 8)); //$NON-NLS-1$
                    case "sha1" -> { //$NON-NLS-1$
                        currRom.setSha1(safeHex(value, 40));
                        sha1Roms = true;
                    }
                    case "md5" -> { //$NON-NLS-1$
                        currRom.setMd5(safeHex(value, 32));
                        md5Roms = true;
                    }
                    case "merge" -> currRom.setMerge(value.trim()); //$NON-NLS-1$
                    case "bios" -> currRom.setBios(value); //$NON-NLS-1$
                    case "region" -> currRom.setRegion(value); //$NON-NLS-1$
                    case "date" -> currRom.setDate(value); //$NON-NLS-1$
                    case "optional" -> currRom.setOptional(BooleanUtils.toBoolean(value)); //$NON-NLS-1$
                    case STATUS -> currRom.setDumpStatus(Entity.Status.valueOf(value)); // $NON-NLS-1$
                    case "loadflag" -> currRom.setLoadflag(LoadFlag.getEnum(value)); //$NON-NLS-1$
                    default -> {
                        /* skip unknown */ }
                }
            }
        }

        /**
         * Formats strings securely into lowercase hexadecimal representations with leading zeros.
         * 
         * @param value raw hexadecimal string
         * @param len expected output length
         * 
         * @return formatted hexadecimal string representation
         */
        private String safeHex(String value, int len) {
            value = value.trim();
            if (value.startsWith("0x")) {
                if (len > 8) {
                    final var bi = new BigInteger(value.substring(2), 16).toString(16);
                    return StringUtils.leftPad(bi.toLowerCase(), len - bi.length(), '0');
                } else {
                    final var fmt = "%0" + len + "x";
                    return String.format(fmt, Long.decode(value));
                }
            } else if (value.length() == len)
                return value.toLowerCase();
            else
                return StringUtils.leftPad(value.toLowerCase(), len - value.length(), '0');
        }

        /**
         * Parsed "slotoption" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startSlotOption(final Attributes attributes) {
            if (currMachine == null || currSlot == null)
                return;
            final var slotoption = new SlotOption();
            for (var i = 0; i < attributes.getLength(); i++) {
                String value = attributes.getValue(i);
                switch (attributes.getQName(i)) {
                    case "name" -> { //$NON-NLS-1$
                        slotoption.setName(value);
                        currSlot.add(slotoption);
                    }
                    case "devname" -> slotoption.setDevName(value); //$NON-NLS-1$
                    case "default" -> slotoption.setDef(BooleanUtils.toBoolean(value)); //$NON-NLS-1$
                    default -> {
                        /* skip unknown */ }
                }
            }
        }

        /**
         * Parsed "slot" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startSlot(final Attributes attributes) {
            if (currMachine == null)
                return;
            for (var i = 0; i < attributes.getLength(); i++) {
                if ("name".equals(attributes.getQName(i))) {
                    currSlot = new Slot();
                    currSlot.setName(attributes.getValue(i));
                    currMachine.getSlots().put(currSlot.getName(), currSlot);
                }
            }
        }

        /**
         * Parsed "device_ref" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startDeviceRef(final Attributes attributes) {
            if (currMachine == null)
                return;
            for (var i = 0; i < attributes.getLength(); i++) {
                if ("name".equals(attributes.getQName(i)))
                    currMachine.getDeviceRef().add(attributes.getValue(i));
            }
        }

        /**
         * Parsed "sample" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startSample(final Attributes attributes) {
            if (currMachine == null)
                return;
            for (var i = 0; i < attributes.getLength(); i++) {
                if (attributes.getQName(i).equals("name")) {
                    if (currSampleSet == null) {
                        currMachine.setSampleof(currMachine.getBaseName());
                        if (!machineListList.get(0).samplesets.containsName(currMachine.getSampleof())) {
                            currSampleSet = new Samples(currMachine.getSampleof());
                            machineListList.get(0).samplesets.putByName(currSampleSet);
                        } else
                            currSampleSet = machineListList.get(0).samplesets.getByName(currMachine.getSampleof());
                    }
                    currMachine.getSamples().add(currSampleSet.add(new Sample(currSampleSet, attributes.getValue(i))));
                    samplesCnt++;
                }
            }
        }

        /**
         * Parsed "dipvalue" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startDipValue(final Attributes attributes) {
            if (currMachine == null || !inCabinetDipSW)
                return;
            for (var i = 0; i < attributes.getLength(); i++) {
                if (attributes.getQName(i).equals("name")) {
                    if ("cocktail".equalsIgnoreCase(attributes.getValue(i))) //$NON-NLS-1$
                        cabTypeSet.add(CabinetType.cocktail);
                    else if ("upright".equalsIgnoreCase(attributes.getValue(i))) //$NON-NLS-1$
                        cabTypeSet.add(CabinetType.upright);
                }
            }
        }

        /**
         * Parsed "dipswitch" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startDipSwitch(final Attributes attributes) {
            if (currMachine == null)
                return;
            for (var i = 0; i < attributes.getLength(); i++) {
                if ("name".equals(attributes.getQName(i)) && "cabinet".equalsIgnoreCase(attributes.getValue(i))) //$NON-NLS-1$
                    inCabinetDipSW = true;
            }
        }

        /**
         * Parsed "extension" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startExtension(final Attributes attributes) {
            if (currMachine == null || currDevice == null)
                return;
            final var ext = currDevice.new Extension();
            currDevice.getExtensions().add(ext);
            for (var i = 0; i < attributes.getLength(); i++) {
                if (attributes.getQName(i).equals("name"))
                    ext.setName(attributes.getValue(i).trim());
            }
        }

        /**
         * Parsed "instance" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startInstance(final Attributes attributes) {
            if (currMachine == null || currDevice == null)
                return;
            currDevice.setInstance(currDevice.new Instance());
            for (var i = 0; i < attributes.getLength(); i++) {
                if ("name".equals(attributes.getQName(i)))
                    currDevice.getInstance().setName(attributes.getValue(i).trim());
                else if ("briefname".equals(attributes.getQName(i)))
                    currDevice.getInstance().setBriefname(attributes.getValue(i).trim());
            }
        }

        /**
         * Parsed "device" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startDevice(final Attributes attributes) {
            if (currMachine == null)
                return;
            currDevice = new Device();
            currMachine.getDevices().add(currDevice);
            for (var i = 0; i < attributes.getLength(); i++) {
                final String value = attributes.getValue(i);
                switch (attributes.getQName(i)) {
                    case "type" -> currDevice.setType(value.trim()); //$NON-NLS-1$
                    case "tag" -> currDevice.setTag(value.trim()); //$NON-NLS-1$
                    case "interface" -> currDevice.setIntrface(value.trim()); //$NON-NLS-1$
                    case "fixed_image" -> currDevice.setFixedImage(value.trim()); //$NON-NLS-1$
                    case "mandatory" -> currDevice.setMandatory(value.trim()); //$NON-NLS-1$
                    default -> {
                        /* skip unknown */ }
                }
            }
        }

        /**
         * Parsed "input" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startInput(final Attributes attributes) {
            if (currMachine == null)
                return;
            for (var i = 0; i < attributes.getLength(); i++) {
                final var value = attributes.getValue(i);
                switch (attributes.getQName(i)) {
                    case "players" -> currMachine.input.setPlayers(value); //$NON-NLS-1$
                    case "coins" -> currMachine.input.setCoins(value); //$NON-NLS-1$
                    case "service" -> currMachine.input.setService(value); //$NON-NLS-1$
                    case "tilt" -> currMachine.input.setTilt(value); //$NON-NLS-1$
                    default -> {
                        /* skip unknown */ }
                }
            }
        }

        /**
         * Parsed "publisher" element callback.
         */
        private void startPublisher() {
            if (currSoftware == null)
                return;
            inPublisher = true;
        }

        /**
         * Parsed "manufacturer" element callback.
         */
        private void startManufacturer() {
            if (currMachine == null)
                return;
            inManufacturer = true;
        }

        /**
         * Parsed "year" element callback.
         */
        private void startYear() {
            if (currMachine == null && currSoftware == null)
                return;
            inYear = true;
        }

        /**
         * Parsed global datafile element callback.
         * 
         * @param attributes element attributes list
         */
        private void startDatfile(final Attributes attributes) {
            for (var i = 0; i < attributes.getLength(); i++) {
                if ("build".equals(attributes.getQName(i)))
                    build = attributes.getValue(i);
            }
        }

        /**
         * Parsed "header" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startHeader() {
            inHeader = true;
        }

        /**
         * Parsed "softwarelist" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startSoftwareList(final Attributes attributes) {
            if (currMachine != null)
                startSoftwareListDesc(attributes);
            else {
                currSoftwareList = new SoftwareList(Profile.this);
                for (var i = 0; i < attributes.getLength(); i++) {
                    switch (attributes.getQName(i)) {
                        case "name" -> { //$NON-NLS-1$
                            currSoftwareList.setName(attributes.getValue(i).trim());
                            machineListList.getSoftwareListList().putByName(currSoftwareList);
                        }
                        case DESCRIPTION -> currSoftwareList.getDescription().append(attributes.getValue(i).trim()); // $NON-NLS-1$
                        default -> {
                            /* skip unknown */ }
                    }
                }
            }
        }

        /**
         * Parsed machine-associated "softwarelist" descriptor callback.
         * 
         * @param attributes element attributes list
         */
        private void startSoftwareListDesc(final Attributes attributes) {
            final var swlist = currMachine.new SWList();
            for (var i = 0; i < attributes.getLength(); i++) {
                switch (attributes.getQName(i)) {
                    case "name" -> swlist.setName(attributes.getValue(i)); //$NON-NLS-1$
                    case STATUS -> swlist.setStatus(SWStatus.valueOf(attributes.getValue(i))); // $NON-NLS-1$
                    case "filter" -> swlist.setFilter(attributes.getValue(i)); //$NON-NLS-1$
                    default -> {
                        /* skip unknown */ }
                }
            }
            currMachine.getSwlists().put(swlist.getName(), swlist);
            machineListList.getSoftwareListDefs().computeIfAbsent(swlist.getName(), _ -> new ArrayList<>()).add(currMachine);
        }

        /**
         * Parsed "software" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startSoftware(final Attributes attributes) {
            currSoftware = new Software(Profile.this);
            for (var i = 0; i < attributes.getLength(); i++) {
                switch (attributes.getQName(i)) {
                    case "name" -> currSoftware.setName(attributes.getValue(i).trim()); //$NON-NLS-1$
                    case "cloneof" -> currSoftware.setCloneof(attributes.getValue(i).trim()); //$NON-NLS-1$
                    case "supported" -> currSoftware.setSupported(Software.Supported.valueOf(attributes.getValue(i))); //$NON-NLS-1$
                    default -> {
                        /* skip unknown */ }
                }
            }
        }

        /**
         * Parsed software "feature" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startSoftwareFeature(Attributes attributes) {
            if (currSoftware == null)
                return;
            if (attributes.getValue("name").equalsIgnoreCase("compatibility")) //$NON-NLS-1$ //$NON-NLS-2$
                currSoftware.setCompatibility(attributes.getValue("value")); //$NON-NLS-1$
        }

        /**
         * Parsed software "part" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startSoftwarePart(Attributes attributes) {
            if (currSoftware == null)
                return;
            currPart = new Part();
            currSoftware.getParts().add(currPart);
            for (var i = 0; i < attributes.getLength(); i++) {
                if ("name".equals(attributes.getQName(i)))
                    currPart.setName(attributes.getValue(i).trim());
                else if ("interface".equals(attributes.getQName(i)))
                    currPart.setIntrface(attributes.getValue(i).trim());
            }
        }

        /**
         * Parsed software "dataarea" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startSoftwarePartDataarea(Attributes attributes) {
            if (currSoftware == null || currPart == null)
                return;
            currDataArea = new DataArea();
            currPart.getDataareas().add(currDataArea);
            for (var i = 0; i < attributes.getLength(); i++) {
                switch (attributes.getQName(i)) {
                    case "name" -> currDataArea.setName(attributes.getValue(i).trim()); //$NON-NLS-1$
                    case "size" -> { //$NON-NLS-1$
                        final var value = attributes.getValue(i).trim();
                        ExceptionUtils.unthrowF(currDataArea::setSize, Integer::decode, value, t -> ExceptionUtils.test(t, "0x" + value, 0));
                    }
                    case "width", "databits" -> currDataArea.setDatabits(Integer.valueOf(attributes.getValue(i))); //$NON-NLS-1$
                    case "endianness", "endian" -> currDataArea.setEndianness(Endianness.valueOf(attributes.getValue(i))); //$NON-NLS-1$
                    default -> {
                        /* skip unknown */ }
                }
            }
        }

        /**
         * Parsed software "diskarea" element callback.
         * 
         * @param attributes element attributes list
         */
        private void startSoftwarePartDiskarea(Attributes attributes) {
            if (currSoftware == null || currPart == null)
                return;
            currDiskArea = new DiskArea();
            currPart.getDiskareas().add(currDiskArea);
            for (var i = 0; i < attributes.getLength(); i++) {
                if ("name".equals(attributes.getQName(i)))
                    currDiskArea.setName(attributes.getValue(i).trim());
            }
        }

        /**
         * Parsed machine or game container element callback.
         * 
         * @param attributes element attributes list
         */
        private void startMachine(Attributes attributes) {
            currMachine = new Machine(Profile.this);
            for (var i = 0; i < attributes.getLength(); i++) {
                final String value = attributes.getValue(i);
                switch (attributes.getQName(i)) {
                    case "name" -> { //$NON-NLS-1$
                        currMachine.setName(value.trim());
                        machineListList.get(0).putByName(currMachine);
                    }
                    case "romof" -> currMachine.setRomof(value.trim()); //$NON-NLS-1$
                    case "cloneof" -> currMachine.setCloneof(value.trim()); //$NON-NLS-1$
                    case "sampleof" -> { //$NON-NLS-1$
                        currMachine.setSampleof(value.trim());
                        if (!machineListList.get(0).samplesets.containsName(currMachine.getSampleof())) {
                            currSampleSet = new Samples(currMachine.getSampleof());
                            machineListList.get(0).samplesets.putByName(currSampleSet);
                        } else
                            currSampleSet = machineListList.get(0).samplesets.getByName(currMachine.getSampleof());
                    }
                    case "isbios" -> currMachine.setIsbios(BooleanUtils.toBoolean(value)); //$NON-NLS-1$
                    case "ismechanical" -> currMachine.setIsmechanical(BooleanUtils.toBoolean(value)); //$NON-NLS-1$
                    case "isdevice" -> currMachine.setIsdevice(BooleanUtils.toBoolean(value)); //$NON-NLS-1$
                    case "sourcefile" -> currMachine.setSourcefile(value); //$NON-NLS-1$
                    default -> {
                        /* skip unknown */ }
                }
            }
            if (currMachine.getRomof() != null && currMachine.getRomof().equals(currMachine.getBaseName()))
                currMachine.setRomof(null);
            if (currMachine.getCloneof() != null && currMachine.getCloneof().equals(currMachine.getBaseName()))
                currMachine.setCloneof(null);
        }

        /**
         * Parsed description container element callback.
         * 
         * @param attributes element attributes list
         */
        private void startDescription() {
            if (currMachine == null && currSoftware == null && currSoftwareList == null)
                return;
            inDescription = true;
        }

        /**
         * Parsed driver specifications element callback.
         * 
         * @param attributes element attributes list
         */
        private void startDriver(Attributes attributes) {
            if (currMachine == null)
                return;
            for (var i = 0; i < attributes.getLength(); i++) {
                final String value = attributes.getValue(i);
                switch (attributes.getQName(i)) {
                    case STATUS -> currMachine.driver.setStatus(value); // $NON-NLS-1$
                    case "emulation" -> currMachine.driver.setEmulation(value); //$NON-NLS-1$
                    case "cocktail" -> currMachine.driver.setCocktail(value); //$NON-NLS-1$
                    case "savestate" -> currMachine.driver.setSaveState(value); //$NON-NLS-1$
                    default -> {
                        /* skip unknown */ }
                }
            }
        }

        /**
         * Parsed display specifications element callback.
         * 
         * @param attributes element attributes list
         */
        private void startDisplay(final Attributes attributes) {
            if (currMachine == null)
                return;
            for (var i = 0; i < attributes.getLength(); i++) {
                if ("rotate".equals(attributes.getQName(i))) {
                    ExceptionUtils.unthrow(orientation -> {
                        switch (orientation) {
                            case 0, 180 -> currMachine.setOrientation(Machine.DisplayOrientation.horizontal);
                            case 90, 270 -> currMachine.setOrientation(Machine.DisplayOrientation.vertical);
                            default -> {
                                /* ignore unknown orientation values */ }
                        }
                    }, Integer::parseInt, attributes.getValue(i));
                }
            }
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) throws SAXException {
            switch (qName) {
                case "header" -> inHeader = false; //$NON-NLS-1$
                case "softwarelist" -> endSoftwareList(); //$NON-NLS-1$
                case "software" -> endSoftware(); //$NON-NLS-1$
                case "machine", "game" -> endMachine(); //$NON-NLS-1$ //$NON-NLS-2$
                case "rom" -> endRom(); //$NON-NLS-1$
                case "disk" -> endDisk(); //$NON-NLS-1$
                case DESCRIPTION -> endDescription(); // $NON-NLS-1$
                case "year" -> endYear(); //$NON-NLS-1$
                case "manufacturer" -> endManufacturer(); //$NON-NLS-1$
                case "publisher" -> endPublisher(); //$NON-NLS-1$
                case "dipswitch" -> endDipSwitch(); //$NON-NLS-1$
                default -> {
                    /* skip unknown */ }
            }
        }

        /**
         * Closes software lists publisher context callback.
         */
        private void endPublisher() {
            if (currSoftware == null)
                return;
            inPublisher = false;
        }

        /**
         * Closes machines manufacturer context callback.
         */
        private void endManufacturer() {
            if (currMachine == null)
                return;
            inManufacturer = false;
        }

        /**
         * Closes year parser context callback.
         */
        private void endYear() {
            if (currMachine == null && currSoftware == null)
                return;
            inYear = false;
        }

        /**
         * Closes description parser context callback.
         */
        private void endDescription() {
            if (currMachine == null && currSoftware == null && currSoftwareList == null)
                return;
            inDescription = false;
        }

        /**
         * Formulates CabinetType orientation constraints from parsed dipswitch elements.
         */
        private void endDipSwitch() {
            if (!inCabinetDipSW || currMachine == null)
                return;
            if (cabTypeSet.contains(CabinetType.cocktail)) {
                if (cabTypeSet.contains(CabinetType.upright))
                    currMachine.setCabinetType(CabinetType.any);
                else
                    currMachine.setCabinetType(CabinetType.cocktail);
            } else
                currMachine.setCabinetType(CabinetType.upright);
            cabTypeSet.clear();
            inCabinetDipSW = false;
        }

        /**
         * Validates and stores parsed CHD disk properties.
         */
        private void endDisk() {
            if (currDisk.getBaseName() != null && !disks.contains(currDisk.getBaseName())) {
                disks.add(currDisk.getBaseName());
                if (currMachine != null) {
                    currMachine.getDisks().add(currDisk);
                    disksCnt++;
                } else if (currSoftware != null) {
                    currSoftware.getDisks().add(currDisk);
                    swdisksCnt++;
                }
            }
            currDisk = null;
        }

        /**
         * Validates, registers, and tracks parsed ROM properties.
         */
        private void endRom() {
            if (currRom.getBaseName() != null) {
                if (!roms.contains(currRom.getBaseName())) {
                    roms.add(currRom.getBaseName());
                    if (currMachine != null) {
                        currMachine.getRoms().add(currRom);
                        romsCnt++;
                    } else if (currSoftware != null) {
                        currSoftware.getRoms().add(currRom);
                        swromsCnt++;
                    }
                }
                endRomCheckSuspiciousCRC();
            }
            currRom = null;
        }

        /**
         * Detects whether ROM element contains suspicious CRC associations.
         */
        private void endRomCheckSuspiciousCRC() {
            if (currRom.getCrc() != null) {
                final var oldRom = romsByCRC.put(currRom.getCrc(), currRom);
                if (oldRom != null) {
                    if (oldRom.getSha1() != null && currRom.getSha1() != null && !oldRom.equals(currRom))
                        suspiciousCRC.add(currRom.getCrc());
                    if (oldRom.getMd5() != null && currRom.getMd5() != null && !oldRom.equals(currRom))
                        suspiciousCRC.add(currRom.getCrc());
                }
            }
        }

        /**
         * Validates and finalizes software list contexts registrations.
         */
        private void endSoftwareList() {
            if (currSoftwareList == null)
                return;
            machineListList.getSoftwareListList().add(currSoftwareList);
            softwaresListCnt++;
            currSoftwareList = null;
        }

        /**
         * Closes machine element building parsing and checks execution cancellation limits.
         * 
         * @throws BreakException if execution is stopped by the user
         */
        private void endMachine() throws BreakException {
            roms.clear();
            disks.clear();
            machineListList.get(0).add(currMachine);
            machinesCnt++;
            currMachine = null;
            currSampleSet = null;
            handler.setProgress(null, null, null, String.format(Messages.getString("Profile.Loaded"), machinesCnt, romsCnt, disksCnt, samplesCnt)); //$NON-NLS-1$
            if (handler.isCancel())
                throw new BreakException();
        }

        /**
         * Closes software item context parsing and validates cancellation controls.
         * 
         * @throws BreakException if execution is stopped by the user
         */
        private void endSoftware() throws BreakException {
            if (currSoftwareList == null || currSoftware == null)
                return;
            roms.clear();
            disks.clear();
            currSoftwareList.add(currSoftware);
            softwaresCnt++;
            currSoftware = null;
            handler.setProgress(null, null, null, String.format(Messages.getString("Profile.SWLoaded"), softwaresCnt, swromsCnt, swdisksCnt)); //$NON-NLS-1$
            if (handler.isCancel())
                throw new BreakException();
        }

        /**
         * Determines the target StringBuilder for appending character data based on current parsing context.
         * 
         * @return the target StringBuilder, or null if no target is available
         */
        private StringBuilder getCharacterTarget() {
            if (inDescription) {
                return getDescriptionTarget();
            } else if (inYear) {
                return getYearTarget();
            } else if (inManufacturer && currMachine != null) {
                return currMachine.manufacturer;
            } else if (inPublisher && currSoftware != null) {
                return currSoftware.getPublisher();
            } else if (inHeader) {
                return header.computeIfAbsent(currTag, _ -> new StringBuilder());
            }
            return null;
        }

        /**
         * Determines the target StringBuilder for the description element.
         * 
         * @return the target StringBuilder, or null if no target is available
         */
        private StringBuilder getDescriptionTarget() {
            if (currMachine != null)
                return currMachine.description;
            if (currSoftware != null)
                return currSoftware.description;
            if (currSoftwareList != null)
                return currSoftwareList.getDescription();
            return null;
        }

        /**
         * Determines the target StringBuilder for the year element.
         * 
         * @return the target StringBuilder, or null if no target is available
         */
        private StringBuilder getYearTarget() {
            if (currMachine != null)
                return currMachine.year;
            if (currSoftware != null)
                return currSoftware.year;
            return null;
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) throws SAXException {
            final var value = new String(ch, start, length);
            if (!value.isBlank())
                Optional.ofNullable(getCharacterTarget()).ifPresent(target -> target.append(value));
        }
    }

    /**
     * Private internal load parser orchestration.
     * 
     * @param file the source xml catalog dat
     * @param handler progressive feedback reporter
     * 
     * @return true on success, false on errors or cancellations
     */
    private boolean internalLoad(final File file, final ProgressHandler handler) {
        handler.setProgress(String.format(Messages.getString("Profile.Parsing"), new PathAbstractor(session).getRelativePath(file.toPath())), -1); //$NON-NLS-1$
        try (var in = handler.getInputStream(new FileInputStream(file), (int) file.length())) {
            XMLTools.getSaxParser().parse(in, new ProfileHandler(handler));
            return true;
        } catch (final ParserConfigurationException | SAXException e) {
            handler.addError(e.getMessage());
            Log.err("Parser Exception", e); //$NON-NLS-1$
        } catch (final IOException e) {
            handler.addError(e.getMessage());
            Log.err("IO Exception", e); //$NON-NLS-1$
        } catch (final BreakException _) {
            return false;
        } catch (final Exception e) {
            handler.addError(e.getMessage());
            Log.err("Other Exception", e); //$NON-NLS-1$
        }
        return false;
    }

    /**
     * Serializes current profile state properties to cached binary files.
     */
    public void save() {
        try (final var oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(session.getUser().getSettings().getCacheFile(nfo.getFile()))))) {
            oos.writeObject(this);
        } catch (final Exception _) {
            // do nothing
        }
    }

    /**
     * Loads profile database configurations from physical file descriptors.
     * 
     * @param session execution workspace context
     * @param file target source config catalog file (.jrm, .dat, .xml)
     * @param handler progressive feedback reporter
     * 
     * @return parsed profile metadata container
     */
    public static Profile load(final Session session, final File file, final ProgressHandler handler) {
        return Profile.load(session, ProfileNFO.load(session, file), handler);
    }

    /**
     * Loads profile properties matching cached descriptors or walk parsers.
     * 
     * @param session execution workspace context
     * @param nfo JRomManager database profile information stats summary
     * @param handler progressive feedback reporter
     * 
     * @return parsed profile metadata container
     */
    public static Profile load(final Session session, final ProfileNFO nfo, final ProgressHandler handler) {
        Profile profile = loadProfile(session, nfo, handler);
        if (profile == null)
            return null;
        
        initializeProfile(profile, handler);
        return profile;
    }

    /**
     * Loads profile from cache or creates new profile from source files.
     * 
     * @param session execution workspace context
     * @param nfo JRomManager database profile information stats summary
     * @param handler progressive feedback reporter
     * 
     * @return loaded profile or null if loading failed
     */
    private static Profile loadProfile(final Session session, final ProfileNFO nfo, final ProgressHandler handler) {
        final var cachefile = session.getUser().getSettings().getCacheFile(nfo.getFile());
        if (shouldLoadFromCache(cachefile, nfo, session)) {
            return loadCache(session, nfo, handler, null, cachefile);
        }
        return loadThenSaveToCache(session, nfo, handler);
    }

    /**
     * Determines whether profile should be loaded from cache based on file timestamps and cache settings.
     * 
     * @param cachefile cached profile file
     * @param nfo JRomManager database profile information stats summary
     * @param session execution workspace context
     * 
     * @return true if cache should be used, false otherwise
     */
    private static boolean shouldLoadFromCache(final File cachefile, final ProfileNFO nfo, final Session session) {
        return cachefile.lastModified() >= nfo.getFile().lastModified()
            && (!nfo.isJRM() || cachefile.lastModified() >= nfo.getMame().getFileroms().lastModified())
            && Boolean.TRUE.equals(!session.getUser().getSettings().getProperty(SettingsEnum.debug_nocache, Boolean.class)); // $NON-NLS-1$
    }

    /**
     * Initializes loaded profile by building relationships, updating statistics, and loading components.
     * 
     * @param profile profile to initialize
     * @param handler progressive feedback reporter
     */
    private static void initializeProfile(final Profile profile, final ProgressHandler handler) {
        handler.setProgress(Messages.getString("Profile.BuildingParentClonesRelations"), -1); //$NON-NLS-1$
        profile.buildParentClonesRelations();
        updateNfoStats(profile);
        profile.nfo.save(profile.session);
        
        loadProfileComponents(profile, handler);
        
        profile.filterEntities = EnumSet.allOf(EntityStatus.class);
        profile.filterList = EnumSet.allOf(AnywareStatus.class);
        profile.filterListLists = EnumSet.allOf(AnywareStatus.class);
    }

    /**
     * Updates profile NFO statistics with current counts and version information.
     * 
     * @param profile profile containing statistics to update
     */
    private static void updateNfoStats(final Profile profile) {
        final var stats = profile.nfo.getStats();
        final String version;
        if (profile.build != null) {
            version = profile.build;
        } else if (profile.header.containsKey(VERSION)) {
            version = profile.header.get(VERSION).toString();
        } else {
            version = null;
        }
        stats.setVersion(version); // $NON-NLS-1$
        stats.setTotalSets(profile.softwaresCnt + profile.machinesCnt);
        stats.setTotalRoms(profile.romsCnt + profile.swromsCnt);
        stats.setTotalDisks(profile.disksCnt + profile.swdisksCnt);
    }

    /**
     * Loads all profile components including settings, filters, and configuration files.
     * 
     * @param profile profile to load components for
     * @param handler progressive feedback reporter
     */
    private static void loadProfileComponents(final Profile profile, final ProgressHandler handler) {
        handler.setProgress("Loading settings...", -1); //$NON-NLS-1$
        profile.loadSettings();
        
        handler.setProgress("Creating Systems filters...", -1); //$NON-NLS-1$
        profile.loadSystems();
        
        handler.setProgress("Creating Years filters...", -1); //$NON-NLS-1$
        profile.loadYears();
        
        profile.loadCatVer(handler);
        profile.loadNPlayers(handler);
    }

    /**
     * Parses profile catalog DAT file content and serializes binary database states.
     * 
     * @param session active session context
     * @param nfo catalogs info summary
     * @param handler progressive feedback reporter
     * 
     * @return parsed profile metadata database
     */
    private static Profile loadThenSaveToCache(final Session session, final ProfileNFO nfo, final ProgressHandler handler) {
        Profile profile;
        handler.setInfos(1, true);
        profile = new Profile();
        profile.session = session;
        session.setCurrProfile(null);
        profile.nfo = nfo;
        if (!load(nfo, profile, handler))
            return null;
        session.setCurrProfile(profile);
        // save cache
        handler.setInfos(1, null);
        handler.setProgress(Messages.getString("Profile.SavingCache"), -1); //$NON-NLS-1$
        profile.save();
        return profile;
    }

    /**
     * Triggers XML parsing on single dat files or paired ROMs + SoftwareLists.
     * 
     * @param nfo catalogs details stats
     * @param profile parent target empty database
     * @param handler progressive feedback reporter
     * 
     * @return true on success, false on failure or cancellation
     */
    private static boolean load(final ProfileNFO nfo, Profile profile, final ProgressHandler handler) {
        if (!nfo.isJRM()) // load DAT file not attached to a JRM
            return (nfo.getFile().exists() && profile.internalLoad(nfo.getFile(), handler));

        // we use JRM file keep ROMs/SL DATs in relation
        if (nfo.getMame().getFileroms() != null) { // load ROMs dat
            if (!nfo.getMame().getFileroms().exists() || !profile.internalLoad(nfo.getMame().getFileroms(), handler))
                return false;
            if (nfo.getMame().getFilesl() != null && (!nfo.getMame().getFilesl().exists() || !profile.internalLoad(nfo.getMame().getFilesl(), handler))) {
                // load SL dat (note that loading software list without ROMs dat is NOT
                // recommended)
                return false;
            }
        }
        return true;
    }

    /**
     * Retrieves profile database state properties from standard cache files.
     * 
     * @param session active session context
     * @param nfo catalogs info stats
     * @param handler progressive reporter
     * @param profile target empty profile object
     * @param cachefile target cache binary file
     * 
     * @return loaded profile, or null on cache mismatches
     */
    private static Profile loadCache(final Session session, final ProfileNFO nfo, final ProgressHandler handler, Profile profile, final File cachefile) {
        handler.setInfos(1, null);
        handler.setProgress(Messages.getString("Profile.LoadingCache"), -1); //$NON-NLS-1$
        try (final var ois = new ObjectInputStream(new BufferedInputStream(handler.getInputStream(new FileInputStream(cachefile), (int) cachefile.length())))) {
            // Apply deserialization filter to prevent arbitrary code execution
            ois.setObjectInputFilter(DeserializationFilter.createFilter());
            profile = (Profile) ois.readObject();
            profile.session = session;
            session.setCurrProfile(profile);
            profile.nfo = nfo;
        } catch (final Exception e) {
            // may fail to load because serialized classes did change since last cache save
            // or if deserialization filter rejected untrusted classes
            Log.debug(() -> "Failed to load cache file: " + e.getMessage());
        }
        return profile;
    }

    /**
     * Maps parent-clones database relationships sequentially after loading metadata catalog elements.
     */
    private void buildParentClonesRelations() {
        machineListList.forEach(machineList -> machineList.forEach(machine -> {
            if (machine.getRomof() != null) {
                machine.setParent(machineList.getByName(machine.getRomof()));
                if (machine.getParent() != null && !machine.getParent().isIsbios())
                    machine.getParent().getClones().put(machine.getName(), machine);
            }
            machine.getDeviceRef().forEach(deviceRef -> machine.getDeviceMachines().putIfAbsent(deviceRef, machineList.getByName(deviceRef)));
            machine.getSlots().values()
                    .forEach(slot -> slot.forEach(slotoption -> machine.getDeviceMachines().putIfAbsent(slotoption.getDevName(), machineList.getByName(slotoption.getDevName()))));
        }));
        machineListList.getSoftwareListList().forEach(softwareList -> softwareList.forEach(software -> {
            if (software.getCloneof() != null) {
                software.setParent(softwareList.getByName(software.getCloneof()));
                if (software.getParent() != null)
                    software.getParent().getClones().put(software.getName(), software);
            }
        }));
    }

    /**
     * Saves profile XML settings files.
     */
    public void saveSettings() {
        saveSettings(nfo.getFile());
    }

    /**
     * Saves profile XML settings files to custom locations.
     * 
     * @param file destination configurations XML path
     */
    public void saveSettings(File file) {
        settings = session.getUser().getSettings().saveProfileSettings(file, settings);
        nfo.save(session);
    }

    /**
     * Loads profiles XML settings properties.
     */
    public void loadSettings() {
        loadSettings(nfo.getFile());
    }

    /**
     * Loads profiles XML settings properties from specific files.
     * 
     * @param file source configuration xml file
     */
    public void loadSettings(File file) {
        settings = session.getUser().getSettings().loadProfileSettings(file, settings);
    }

    /**
     * Updates Boolean property associations in local profiles configuration maps.
     * 
     * @param property target configuration option key
     * @param value target option state value
     */
    public void setProperty(final ProfileSettingsEnum property, final boolean value) {
        Log.info(() -> "%s : %b".formatted(property, value));
        settings.setProperty(property, Boolean.toString(value));
    }

    /**
     * Updates string-mapped Boolean property associations.
     * 
     * @param property target option key
     * @param value target option state value
     */
    public void setProperty(final String property, final boolean value) {
        Log.info(() -> "%s : %b".formatted(property, value));
        settings.setProperty(property, Boolean.toString(value));
    }

    /**
     * Updates string property associations.
     * 
     * @param property target configuration option key
     * @param value target option text value
     */
    public void setProperty(final ProfileSettingsEnum property, final String value) {
        settings.setProperty(property, value);
    }

    /**
     * Updates string property associations using text keys.
     * 
     * @param property target option text key
     * @param value target option text value
     */
    public void setProperty(final String property, final String value) {
        settings.setProperty(property, value);
    }

    /**
     * Resolves Boolean settings values or returns defaults if keys are missing.
     * 
     * @param property target option text key
     * @param def the default option state value
     * 
     * @return option state value
     */
    public boolean getProperty(final String property, final boolean def) {
        return Boolean.parseBoolean(settings.getProperty(property, Boolean.toString(def)));
    }

    /**
     * Resolves integer settings values.
     * 
     * @param property target option text key
     * @param def the default integer option value
     * 
     * @return option value
     */
    public int getProperty(final String property, final int def) {
        return Integer.parseInt(settings.getProperty(property, Integer.toString(def)));
    }

    /**
     * Resolves text settings values.
     * 
     * @param property target option key
     * @param def the default text value
     * 
     * @return option value
     */
    public String getProperty(final String property, final String def) {
        return settings.getProperty(property, def);
    }

    /**
     * Resolves settings values matching expected output classes.
     * 
     * @param property target option key
     * @param cls expected output class type
     * @param <T> class template argument
     * 
     * @return option value
     */
    public <T> T getProperty(final ProfileSettingsEnum property, Class<T> cls) {
        return settings.getProperty(property, cls);
    }

    /**
     * Resolves settings string values.
     * 
     * @param property target option key
     * 
     * @return option text value
     */
    public String getProperty(final ProfileSettingsEnum property) {
        return settings.getProperty(property, String.class);
    }

    /**
     * Cached hash code of the profile settings used to detect modifications.
     */
    private int propsHashCode = 0;

    /**
     * Saves properties checkpoints hash codes to track modifications state.
     */
    public void setPropsCheckPoint() {
        propsHashCode = settings.hashCode();
    }

    /**
     * Compares active properties configurations against saved checkpoints.
     * 
     * @return true if properties changed, false otherwise
     */
    public boolean hasPropsChanged() {
        return propsHashCode != settings.hashCode();
    }

    /**
     * Generates detailed HTML formatted text representation of profile catalogs counts.
     * 
     * @return HTML format text summary
     */
    public String getName() {
        final var xmlpath = session.getUser().getSettings().getWorkPath().resolve("xmlfiles").toAbsolutePath().normalize();
        final var fname = nfo.getFile().toPath().startsWith(xmlpath)
            ? xmlpath.relativize(nfo.getFile().toPath()).toString()
            : nfo.getFile().getName();
        final var nameBuilder = new StringBuilder("[")
            .append(toBlue(fname))
            .append("] "); //$NON-NLS-1$ //$NON-NLS-2$
        if (build != null) {
            nameBuilder.append(toBoldBlack(build)); // $NON-NLS-1$
        } else if (!header.isEmpty()) {
            if (header.containsKey(DESCRIPTION)) { // $NON-NLS-1$
                nameBuilder.append(toBoldBlack(header.get(DESCRIPTION))); // $NON-NLS-1$
            } else if (header.containsKey("name")) { //$NON-NLS-1$
                nameBuilder.append(toBoldBlack(header.get("name"))); //$NON-NLS-1$
                if (header.containsKey(VERSION)) // $NON-NLS-1$
                    nameBuilder.append(" (").append(header.get(VERSION)).append(")"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
        final var strcntBuilder = new StringBuilder();
        if (!machineListList.get(0).isEmpty())
            strcntBuilder.append(machinesCnt).append(" Machines"); //$NON-NLS-1$
        if (!machineListList.getSoftwareListList().isEmpty()) {
            if (!strcntBuilder.isEmpty())
                strcntBuilder.append(", "); //$NON-NLS-1$
            strcntBuilder.append(softwaresListCnt).append(" Software Lists, ") //$NON-NLS-1$
                .append(softwaresCnt).append(" Softwares"); //$NON-NLS-1$
        }
        nameBuilder.append("(").append(strcntBuilder).append(")"); //$NON-NLS-1$ //$NON-NLS-2$
        return toDocument(nameBuilder.toString());
    }

    /**
     * Populates standard, mechanical, bios, or software lists categories inside System filter sets.
     */
    public void loadSystems() {
        systems = new Systms();
        systems.add(SystmStandard.STANDARD);
        systems.add(SystmMechanical.MECHANICAL);
        systems.add(SystmDevice.DEVICE);
        final ArrayList<Machine> machines = new ArrayList<>();
        this.sources = new Sources();
        final var srces = new TreeMap<String, Source>();
        machineListList.get(0).forEach(m -> {
            if (m.isIsbios())
                machines.add(m);
            Optional.ofNullable(m.getSourcefile()).ifPresent(s -> srces.compute(s, (k, v) -> v == null ? new Source(k) : v.inc()));
        });
        machines.sort((a, b) -> a.getName().compareTo(b.getName()));
        machines.forEach(systems::add);
        srces.forEach((_, src) -> sources.add(src));
        machineListList.get(0).stream().filter(m -> m.getSourcefile() != null).forEach(m -> m.setSource(srces.get(m.getSourcefile())));

        final ArrayList<SoftwareList> softwarelists = new ArrayList<>();
        machineListList.getSoftwareListList().forEach(softwarelists::add);
        softwarelists.sort((a, b) -> a.getName().compareTo(b.getName()));
        softwarelists.forEach(systems::add);
    }

    /**
     * Compiles distinct release years across parsed machines or software catalogs.
     */
    public void loadYears() {
        final var y = new HashSet<String>();
        y.add(""); //$NON-NLS-1$
        machineListList.get(0).forEach(m -> y.add(m.year.toString()));
        machineListList.getSoftwareListList().forEach(sl -> sl.forEach(s -> y.add(s.year.toString())));
        y.add("????"); //$NON-NLS-1$
        this.years = y;
    }

    /**
     * Loads catver.ini mappings if files are found, linking machines back to subcategories.
     * 
     * @param handler progress reporting monitor
     */
    public void loadCatVer(ProgressHandler handler) {
        try {
            final var file = PathAbstractor.getAbsolutePath(session, getProperty(ProfileSettingsEnum.filter_catver_ini, String.class)).toFile();
            if (!file.exists()) {
                catver = null;
                return;
            }
            if (handler != null)
                handler.setProgress("Loading catver.ini ...", -1); //$NON-NLS-1$
            catver = CatVer.read(this, file); // $NON-NLS-1$
            for (final Category cat : catver) {
                for (final SubCategory subcat : cat) {
                    for (final String game : subcat) {
                        final Machine m = machineListList.get(0).getByName(game);
                        if (m != null)
                            m.setSubcat(subcat);
                    }
                }
            }
        } catch (final Exception _) {
            catver = null;
        }
    }

    /**
     * Loads nplayers.ini capability configurations if files are present.
     * 
     * @param handler progress reporting monitor
     */
    public void loadNPlayers(ProgressHandler handler) {
        try {
            final var file = PathAbstractor.getAbsolutePath(session, getProperty(ProfileSettingsEnum.filter_nplayers_ini, String.class)).toFile();
            if (file.exists()) {
                if (handler != null)
                    handler.setProgress("Loading nplayers.ini ...", -1); //$NON-NLS-1$
                nplayers = NPlayers.read(file); // $NON-NLS-1$
                for (final NPlayer nplayer : nplayers) {
                    for (final String game : nplayer) {
                        final Machine m = machineListList.get(0).getByName(game);
                        if (m != null)
                            m.setNplayer(nplayer);
                    }
                }
            } else
                nplayers = null;
        } catch (final Exception _) {
            nplayers = null;
        }
    }

    /**
     * Computes the cumulative size across all parsed software lists and target machines.
     * 
     * @return cumulative size count
     */
    public int size() {
        return machineListList.size() + machineListList.getSoftwareListList().size();
    }

    /**
     * Computes cumulative size count across filtered visible machine items and software catalogs.
     * 
     * @return filtered visibility size count
     */
    public int filteredSubsize() {
        return (int) machineListList.get(0).getFilteredStream().count() + machineListList.get(0).samplesets.size()
                + (int) machineListList.getSoftwareListList().getFilteredStream().mapToLong(sl -> sl.getFilteredStream().count()).sum();
    }

    /**
     * Computes cumulative size count across all raw machine elements and software lists.
     * 
     * @return raw cumulative entries count
     */
    public int subsize() {
        return machineListList.get(0).size() + machineListList.get(0).samplesets.size() + machineListList.getSoftwareListList().stream().mapToInt(SoftwareList::size).sum();
    }
}
