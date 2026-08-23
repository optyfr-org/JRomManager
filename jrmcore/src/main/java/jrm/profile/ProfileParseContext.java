/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import jrm.aui.progress.ProgressHandler;
import jrm.locale.Messages;
import jrm.misc.BreakException;
import jrm.misc.ExceptionUtils;
import jrm.profile.data.Device;
import jrm.profile.data.Disk;
import jrm.profile.data.Entity;
import jrm.profile.data.Machine;
import jrm.profile.data.Machine.CabinetType;
import jrm.profile.data.Machine.SWStatus;
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

/**
 * Holds transient state and element handling logic during SAX parsing of a DAT profile.
 * This separates the large amount of current-context fields and per-element start/end methods
 * from the top-level SAX handler dispatcher.
 */
class ProfileParseContext {

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
	 * Owning profile instance for accessing parse accumulators and collections.
	 */
	private final Profile profile;

	/**
	 * Progress handler for reporting and cancellation.
	 */
	private final ProgressHandler handler;

	ProfileParseContext(Profile profile, ProgressHandler handler) {
		this.profile = profile;
		this.handler = handler;
	}

	/**
	 * Main entry for start of element (called from the SAX handler).
	 */
	void startElement(final String qName, final Attributes attributes) {
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
			case Profile.DESCRIPTION -> startDescription();
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
	}

	/**
	 * Main entry for end of element (called from the SAX handler).
	 */
	void endElement(final String qName) throws BreakException {
		switch (qName) {
			case "header" -> inHeader = false;
			case "softwarelist" -> endSoftwareList();
			case "software" -> endSoftware();
			case "machine", "game" -> endMachine();
			case "rom" -> endRom();
			case "disk" -> endDisk();
			case Profile.DESCRIPTION -> endDescription();
			case "year" -> endYear();
			case "manufacturer" -> endManufacturer();
			case "publisher" -> endPublisher();
			case "dipswitch" -> endDipSwitch();
			default -> {
				/* skip unknown */ }
		}
	}

	/**
	 * Handles character data for the current target (called from SAX handler).
	 */
	void characters(final char[] ch, final int start, final int length) {
		final var value = new String(ch, start, length);
		if (!value.isBlank())
			Optional.ofNullable(getCharacterTarget()).ifPresent(target -> target.append(value));
	}

	/**
	 * Generates detailed debug information string mapping where error was met.
	 */
	String getDebugMsg(Attributes attributes, String qName, Exception e) {
		final var msg = new StringBuilder("Error");

		if (currMachine != null) {
			msg.append(" for machine ").append(currMachine.getName());
		} else if (currSoftwareList != null) {
			msg.append(" for software list ").append(currSoftwareList.getName());
			if (currSoftware != null)
				msg.append(", software ").append(currSoftware.getName());
		}

		if (currRom != null)
			msg.append(", rom ").append(currRom.getName());
		if (currDisk != null)
			msg.append(", disk ").append(currDisk.getName());

		msg.append(", xmltag=").append(qName);
		final var attrs = IntStream.range(0, attributes.getLength())
				.mapToObj(i -> attributes.getQName(i) + "=" + attributes.getValue(i))
				.collect(Collectors.joining(", "));
		msg.append(", xmlattributes={").append(attrs).append("}");

		msg.append("\nOriginal exception=").append(e.getClass().getSimpleName())
				.append(" ").append(e.getMessage());
		return msg.toString();
	}

	private void startDisk(final Attributes attributes) throws NumberFormatException {
		if (currMachine == null && currSoftware == null)
			return;
		currDisk = new Disk(currMachine != null ? currMachine : currSoftware);
		if (currSoftware != null && currDiskArea != null)
			currDiskArea.getDisks().add(currDisk);
		for (var i = 0; i < attributes.getLength(); i++) {
			final var value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "name" -> {
					var name = value.trim();
					if (name.endsWith(".chd"))
						name = name.substring(0, name.length() - 4);
					currDisk.setName(name);
				}
				case "sha1" -> {
					currDisk.setSha1(safeHex(value, 40));
					profile.sha1Disks = true;
				}
				case "md5" -> {
					currDisk.setMd5(safeHex(value, 32));
					profile.md5Disks = true;
				}
				case "merge" -> currDisk.setMerge(value.trim());
				case "index" -> currDisk.setIndex(Integer.decode(value));
				case "optional" -> currDisk.setOptional(BooleanUtils.toBoolean(value));
				case "writeable" -> currDisk.setWriteable(BooleanUtils.toBoolean(value));
				case "region" -> currDisk.setRegion(value);
				case STATUS -> currDisk.setDumpStatus(Entity.Status.valueOf(value));
				default -> { /* skip unknown */ }
			}
		}
	}

	private void startRom(final Attributes attributes) throws NumberFormatException {
		if (currMachine == null && currSoftware == null)
			return;
		currRom = new Rom(currMachine != null ? currMachine : currSoftware);
		if (currSoftware != null && currDataArea != null)
			currDataArea.getRoms().add(currRom);
		for (var i = 0; i < attributes.getLength(); i++) {
			final var value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "name" -> currRom.setName(value.trim());
				case "size" -> currRom.setSize(Long.decode(value));
				case "offset" -> {
					if (value.toLowerCase().startsWith("0x"))
						currRom.setOffset(Long.decode(value));
					else
						currRom.setOffset(Long.decode("0x" + value));
				}
				case "value" -> currRom.setValue(value);
				case "crc" -> currRom.setCrc(safeHex(value, 8));
				case "sha1" -> {
					currRom.setSha1(safeHex(value, 40));
					profile.sha1Roms = true;
				}
				case "md5" -> {
					currRom.setMd5(safeHex(value, 32));
					profile.md5Roms = true;
				}
				case "merge" -> currRom.setMerge(value.trim());
				case "bios" -> currRom.setBios(value);
				case "region" -> currRom.setRegion(value);
				case "date" -> currRom.setDate(value);
				case "optional" -> currRom.setOptional(BooleanUtils.toBoolean(value));
				case STATUS -> currRom.setDumpStatus(Entity.Status.valueOf(value));
				case "loadflag" -> currRom.setLoadflag(LoadFlag.getEnum(value));
				default -> { /* skip unknown */ }
			}
		}
	}

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

	private void startSlotOption(final Attributes attributes) {
		if (currMachine == null || currSlot == null)
			return;
		final var slotoption = new SlotOption();
		for (var i = 0; i < attributes.getLength(); i++) {
			String value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "name" -> {
					slotoption.setName(value);
					currSlot.add(slotoption);
				}
				case "devname" -> slotoption.setDevName(value);
				case "default" -> slotoption.setDef(BooleanUtils.toBoolean(value));
				default -> { /* skip unknown */ }
			}
		}
	}

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

	private void startDeviceRef(final Attributes attributes) {
		if (currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("name".equals(attributes.getQName(i)))
				currMachine.getDeviceRef().add(attributes.getValue(i));
		}
	}

	private void startSample(final Attributes attributes) {
		if (currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if (attributes.getQName(i).equals("name")) {
				if (currSampleSet == null) {
					currMachine.setSampleof(currMachine.getBaseName());
					if (!profile.machineListList.get(0).samplesets.containsName(currMachine.getSampleof())) {
						currSampleSet = new Samples(currMachine.getSampleof());
						profile.machineListList.get(0).samplesets.putByName(currSampleSet);
					} else
						currSampleSet = profile.machineListList.get(0).samplesets.getByName(currMachine.getSampleof());
				}
				currMachine.getSamples().add(currSampleSet.add(new Sample(currSampleSet, attributes.getValue(i))));
				profile.samplesCnt++;
			}
		}
	}

	private void startDipValue(final Attributes attributes) {
		if (currMachine == null || !inCabinetDipSW)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if (attributes.getQName(i).equals("name")) {
				if ("cocktail".equalsIgnoreCase(attributes.getValue(i)))
					cabTypeSet.add(CabinetType.cocktail);
				else if ("upright".equalsIgnoreCase(attributes.getValue(i)))
					cabTypeSet.add(CabinetType.upright);
			}
		}
	}

	private void startDipSwitch(final Attributes attributes) {
		if (currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("name".equals(attributes.getQName(i)) && "cabinet".equalsIgnoreCase(attributes.getValue(i)))
				inCabinetDipSW = true;
		}
	}

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

	private void startDevice(final Attributes attributes) {
		if (currMachine == null)
			return;
		currDevice = new Device();
		currMachine.getDevices().add(currDevice);
		for (var i = 0; i < attributes.getLength(); i++) {
			final String value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "type" -> currDevice.setType(value.trim());
				case "tag" -> currDevice.setTag(value.trim());
				case "interface" -> currDevice.setIntrface(value.trim());
				case "fixed_image" -> currDevice.setFixedImage(value.trim());
				case "mandatory" -> currDevice.setMandatory(value.trim());
				default -> { /* skip unknown */ }
			}
		}
	}

	private void startInput(final Attributes attributes) {
		if (currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			final var value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "players" -> currMachine.input.setPlayers(value);
				case "coins" -> currMachine.input.setCoins(value);
				case "service" -> currMachine.input.setService(value);
				case "tilt" -> currMachine.input.setTilt(value);
				default -> { /* skip unknown */ }
			}
		}
	}

	private void startPublisher() {
		if (currSoftware == null)
			return;
		inPublisher = true;
	}

	private void startManufacturer() {
		if (currMachine == null)
			return;
		inManufacturer = true;
	}

	private void startYear() {
		if (currMachine == null && currSoftware == null)
			return;
		inYear = true;
	}

	private void startDatfile(final Attributes attributes) {
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("build".equals(attributes.getQName(i)))
				profile.build = attributes.getValue(i);
		}
	}

	private void startHeader() {
		inHeader = true;
	}

	private void startSoftwareList(final Attributes attributes) {
		if (currMachine != null)
			startSoftwareListDesc(attributes);
		else {
			currSoftwareList = new SoftwareList(profile);
			for (var i = 0; i < attributes.getLength(); i++) {
				switch (attributes.getQName(i)) {
					case "name" -> {
						currSoftwareList.setName(attributes.getValue(i).trim());
						profile.machineListList.getSoftwareListList().putByName(currSoftwareList);
					}
					case Profile.DESCRIPTION -> currSoftwareList.getDescription().append(attributes.getValue(i).trim());
					default -> { /* skip unknown */ }
				}
			}
		}
	}

	private void startSoftwareListDesc(final Attributes attributes) {
		final var swlist = currMachine.new SWList();
		for (var i = 0; i < attributes.getLength(); i++) {
			switch (attributes.getQName(i)) {
				case "name" -> swlist.setName(attributes.getValue(i));
				case STATUS -> swlist.setStatus(SWStatus.valueOf(attributes.getValue(i)));
				case "filter" -> swlist.setFilter(attributes.getValue(i));
				default -> { /* skip unknown */ }
			}
		}
		currMachine.getSwlists().put(swlist.getName(), swlist);
		profile.machineListList.getSoftwareListDefs().computeIfAbsent(swlist.getName(), _ -> new ArrayList<>()).add(currMachine);
	}

	private void startSoftware(final Attributes attributes) {
		currSoftware = new Software(profile);
		for (var i = 0; i < attributes.getLength(); i++) {
			switch (attributes.getQName(i)) {
				case "name" -> currSoftware.setName(attributes.getValue(i).trim());
				case "cloneof" -> currSoftware.setCloneof(attributes.getValue(i).trim());
				case "supported" -> currSoftware.setSupported(Software.Supported.valueOf(attributes.getValue(i)));
				default -> { /* skip unknown */ }
			}
		}
	}

	private void startSoftwareFeature(Attributes attributes) {
		if (currSoftware == null)
			return;
		if (attributes.getValue("name").equalsIgnoreCase("compatibility"))
			currSoftware.setCompatibility(attributes.getValue("value"));
	}

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

	private void startSoftwarePartDataarea(Attributes attributes) {
		if (currSoftware == null || currPart == null)
			return;
		currDataArea = new DataArea();
		currPart.getDataareas().add(currDataArea);
		for (var i = 0; i < attributes.getLength(); i++) {
			switch (attributes.getQName(i)) {
				case "name" -> currDataArea.setName(attributes.getValue(i).trim());
				case "size" -> {
					final var value = attributes.getValue(i).trim();
					ExceptionUtils.unthrowF(currDataArea::setSize, Integer::decode, value, t -> ExceptionUtils.test(t, "0x" + value, 0));
				}
				case "width", "databits" -> currDataArea.setDatabits(Integer.valueOf(attributes.getValue(i)));
				case "endianness", "endian" -> currDataArea.setEndianness(Endianness.valueOf(attributes.getValue(i)));
				default -> { /* skip unknown */ }
			}
		}
	}

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

	private void startMachine(Attributes attributes) {
		currMachine = new Machine(profile);
		for (var i = 0; i < attributes.getLength(); i++) {
			final String value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "name" -> {
					currMachine.setName(value.trim());
					profile.machineListList.get(0).putByName(currMachine);
				}
				case "romof" -> currMachine.setRomof(value.trim());
				case "cloneof" -> currMachine.setCloneof(value.trim());
				case "sampleof" -> {
					currMachine.setSampleof(value.trim());
					if (!profile.machineListList.get(0).samplesets.containsName(currMachine.getSampleof())) {
						currSampleSet = new Samples(currMachine.getSampleof());
						profile.machineListList.get(0).samplesets.putByName(currSampleSet);
					} else
						currSampleSet = profile.machineListList.get(0).samplesets.getByName(currMachine.getSampleof());
				}
				case "isbios" -> currMachine.setIsbios(BooleanUtils.toBoolean(value));
				case "ismechanical" -> currMachine.setIsmechanical(BooleanUtils.toBoolean(value));
				case "isdevice" -> currMachine.setIsdevice(BooleanUtils.toBoolean(value));
				case "sourcefile" -> currMachine.setSourcefile(value);
				default -> { /* skip unknown */ }
			}
		}
		if (currMachine.getRomof() != null && currMachine.getRomof().equals(currMachine.getBaseName()))
			currMachine.setRomof(null);
		if (currMachine.getCloneof() != null && currMachine.getCloneof().equals(currMachine.getBaseName()))
			currMachine.setCloneof(null);
	}

	private void startDescription() {
		if (currMachine == null && currSoftware == null && currSoftwareList == null)
			return;
		inDescription = true;
	}

	private void startDriver(Attributes attributes) {
		if (currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			final String value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case STATUS -> currMachine.driver.setStatus(value);
				case "emulation" -> currMachine.driver.setEmulation(value);
				case "cocktail" -> currMachine.driver.setCocktail(value);
				case "savestate" -> currMachine.driver.setSaveState(value);
				default -> { /* skip unknown */ }
			}
		}
	}

	private void startDisplay(final Attributes attributes) {
		if (currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("rotate".equals(attributes.getQName(i))) {
				ExceptionUtils.unthrow(orientation -> {
					switch (orientation) {
						case 0, 180 -> currMachine.setOrientation(Machine.DisplayOrientation.horizontal);
						case 90, 270 -> currMachine.setOrientation(Machine.DisplayOrientation.vertical);
						default -> { /* ignore unknown orientation values */ }
					}
				}, Integer::parseInt, attributes.getValue(i));
			}
		}
	}

	private void endPublisher() {
		if (currSoftware == null)
			return;
		inPublisher = false;
	}

	private void endManufacturer() {
		if (currMachine == null)
			return;
		inManufacturer = false;
	}

	private void endYear() {
		if (currMachine == null && currSoftware == null)
			return;
		inYear = false;
	}

	private void endDescription() {
		if (currMachine == null && currSoftware == null && currSoftwareList == null)
			return;
		inDescription = false;
	}

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

	private void endDisk() {
		if (currDisk.getBaseName() != null && !disks.contains(currDisk.getBaseName())) {
			disks.add(currDisk.getBaseName());
			if (currMachine != null) {
				currMachine.getDisks().add(currDisk);
				profile.disksCnt++;
			} else if (currSoftware != null) {
				currSoftware.getDisks().add(currDisk);
				profile.swdisksCnt++;
			}
		}
		currDisk = null;
	}

	private void endRom() {
		if (currRom.getBaseName() != null) {
			if (!roms.contains(currRom.getBaseName())) {
				roms.add(currRom.getBaseName());
				if (currMachine != null) {
					currMachine.getRoms().add(currRom);
					profile.romsCnt++;
				} else if (currSoftware != null) {
					currSoftware.getRoms().add(currRom);
					profile.swromsCnt++;
				}
			}
			endRomCheckSuspiciousCRC();
		}
		currRom = null;
	}

	private void endRomCheckSuspiciousCRC() {
		if (currRom.getCrc() != null) {
			final var oldRom = romsByCRC.put(currRom.getCrc(), currRom);
			if (oldRom != null) {
				if (oldRom.getSha1() != null && currRom.getSha1() != null && !oldRom.equals(currRom))
					profile.suspiciousCRC.add(currRom.getCrc());
				if (oldRom.getMd5() != null && currRom.getMd5() != null && !oldRom.equals(currRom))
					profile.suspiciousCRC.add(currRom.getCrc());
			}
		}
	}

	private void endSoftwareList() {
		if (currSoftwareList == null)
			return;
		profile.machineListList.getSoftwareListList().add(currSoftwareList);
		profile.softwaresListCnt++;
		currSoftwareList = null;
	}

	private void endMachine() throws BreakException {
		roms.clear();
		disks.clear();
		profile.machineListList.get(0).add(currMachine);
		profile.machinesCnt++;
		currMachine = null;
		currSampleSet = null;
		handler.setProgress(null, null, null, String.format(Messages.getString("Profile.Loaded"), profile.machinesCnt, profile.romsCnt, profile.disksCnt, profile.samplesCnt));
		if (handler.isCancel())
			throw new BreakException();
	}

	private void endSoftware() throws BreakException {
		if (currSoftwareList == null || currSoftware == null)
			return;
		roms.clear();
		disks.clear();
		currSoftwareList.add(currSoftware);
		profile.softwaresCnt++;
		currSoftware = null;
		handler.setProgress(null, null, null, String.format(Messages.getString("Profile.SWLoaded"), profile.softwaresCnt, profile.swromsCnt, profile.swdisksCnt));
		if (handler.isCancel())
			throw new BreakException();
	}

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
			return profile.header.computeIfAbsent(currTag, _ -> new StringBuilder());
		}
		return null;
	}

	private StringBuilder getDescriptionTarget() {
		if (currMachine != null)
			return currMachine.description;
		if (currSoftware != null)
			return currSoftware.description;
		if (currSoftwareList != null)
			return currSoftwareList.getDescription();
		return null;
	}

	private StringBuilder getYearTarget() {
		if (currMachine != null)
			return currMachine.year;
		if (currSoftware != null)
			return currSoftware.year;
		return null;
	}
}
