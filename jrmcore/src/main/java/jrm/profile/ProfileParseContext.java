/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile;

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
	 * Owning profile instance for accessing parse accumulators and collections.
	 */
	final Profile profile;

	/**
	 * Progress handler for reporting and cancellation.
	 */
	private final ProgressHandler handler;

	final ProfileParseState state;

	final RomDiskParseHelper romDiskHelper;
	private final MachineParseHelper machineHelper;
	private final SoftwareParseHelper softwareHelper;
	private final ParseCharacterHandler characterHandler;

	ProfileParseContext(Profile profile, ProgressHandler handler) {
		this.profile = profile;
		this.handler = handler;
		this.state = new ProfileParseState(profile);
		this.romDiskHelper = new RomDiskParseHelper(this);
		this.machineHelper = new MachineParseHelper(this);
		this.softwareHelper = new SoftwareParseHelper(this);
		this.characterHandler = new ParseCharacterHandler(this);
	}

	/**
	 * Main entry for start of element (called from the SAX handler).
	 */
	void startElement(final String qName, final Attributes attributes) {
		state.currTag = qName;
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
			case "header" -> state.inHeader = false;
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
		characterHandler.characters(ch, start, length);
	}

	/**
	 * Generates detailed debug information string mapping where error was met.
	 */
	String getDebugMsg(Attributes attributes, String qName, Exception e) {
		return characterHandler.getDebugMsg(attributes, qName, e);
	}

	private void startDisk(final Attributes attributes) {
		romDiskHelper.startDisk(attributes);
	}

	private void startRom(final Attributes attributes) {
		romDiskHelper.startRom(attributes);
	}



	private void startSlotOption(final Attributes attributes) {
		machineHelper.startSlotOption(attributes);
	}

	private void startSlot(final Attributes attributes) {
		machineHelper.startSlot(attributes);
	}

	private void startDeviceRef(final Attributes attributes) { machineHelper.startDeviceRef(attributes); }
	private void startSample(final Attributes attributes) { machineHelper.startSample(attributes); }
	private void startDipValue(final Attributes attributes) { machineHelper.startDipValue(attributes); }
	private void startDipSwitch(final Attributes attributes) { machineHelper.startDipSwitch(attributes); }
	private void startExtension(final Attributes attributes) { machineHelper.startExtension(attributes); }
	private void startInstance(final Attributes attributes) { machineHelper.startInstance(attributes); }

	private void startDevice(final Attributes attributes) {
		machineHelper.startDevice(attributes);
	}

	private void startInput(final Attributes attributes) { machineHelper.startInput(attributes); }
	private void startPublisher() { if (state.currSoftware == null) return; state.inPublisher = true; }
	private void startManufacturer() { if (state.currMachine == null) return; state.inManufacturer = true; }
	private void startYear() { if (state.currMachine == null && state.currSoftware == null) return; state.inYear = true; }
	private void startDatfile(final Attributes attributes) { for (var i = 0; i < attributes.getLength(); i++) { if ("build".equals(attributes.getQName(i))) state.profile.build = attributes.getValue(i); } }
	private void startHeader() { state.inHeader = true; }

	private void startSoftwareList(final Attributes attributes) {
		softwareHelper.startSoftwareList(attributes);
	}

	private void startSoftwareListDesc(final Attributes attributes) {
		softwareHelper.startSoftwareListDesc(attributes);
	}

	private void startSoftware(final Attributes attributes) {
		softwareHelper.startSoftware(attributes);
	}

	private void startSoftwareFeature(Attributes attributes) { softwareHelper.startSoftwareFeature(attributes); }
	private void startSoftwarePart(Attributes attributes) { softwareHelper.startSoftwarePart(attributes); }
	private void startSoftwarePartDataarea(Attributes attributes) { softwareHelper.startSoftwarePartDataarea(attributes); }
	private void startSoftwarePartDiskarea(Attributes attributes) { softwareHelper.startSoftwarePartDiskarea(attributes); }

	private void startMachine(Attributes attributes) {
		machineHelper.startMachine(attributes);
	}

	private void startDescription() { if (state.currMachine == null && state.currSoftware == null && state.currSoftwareList == null) return; state.inDescription = true; }
	private void startDriver(Attributes attributes) { machineHelper.startDriver(attributes); }
	private void startDisplay(final Attributes attributes) { machineHelper.startDisplay(attributes); }

	private void endPublisher() { if (state.currSoftware == null) return; state.inPublisher = false; }
	private void endManufacturer() { if (state.currMachine == null) return; state.inManufacturer = false; }
	private void endYear() { if (state.currMachine == null && state.currSoftware == null) return; state.inYear = false; }
	private void endDescription() { if (state.currMachine == null && state.currSoftware == null && state.currSoftwareList == null) return; state.inDescription = false; }
	private void endDipSwitch() { machineHelper.endDipSwitch(); }

	private void endDisk() {
		romDiskHelper.endDisk();
	}

	private void endRom() {
		romDiskHelper.endRom();
	}

	private void endSoftwareList() {
		softwareHelper.endSoftwareList();
	}

	private void endMachine() throws BreakException {
		machineHelper.endMachine();
		romDiskHelper.endMachineOrSoftware();
		handler.setProgress(null, null, null, String.format(Messages.getString("Profile.Loaded"), profile.machinesCnt, profile.romsCnt, profile.disksCnt, profile.samplesCnt));
		if (handler.isCancel())
			throw new BreakException();
	}

	private void endSoftware() throws BreakException {
		if (state.currSoftwareList == null || state.currSoftware == null)
			return;
		softwareHelper.endSoftware();
		romDiskHelper.endMachineOrSoftware();
		handler.setProgress(null, null, null, String.format(Messages.getString("Profile.SWLoaded"), profile.softwaresCnt, profile.swromsCnt, profile.swdisksCnt));
		if (handler.isCancel())
			throw new BreakException();
	}
}
