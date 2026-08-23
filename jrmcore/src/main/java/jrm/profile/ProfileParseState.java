/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile;

import java.util.EnumSet;

import jrm.profile.data.Device;
import jrm.profile.data.Disk;
import jrm.profile.data.Machine;
import jrm.profile.data.Machine.CabinetType;
import jrm.profile.data.Rom;
import jrm.profile.data.Sample;
import jrm.profile.data.Samples;
import jrm.profile.data.Slot;
import jrm.profile.data.Software;
import jrm.profile.data.Software.Part;
import jrm.profile.data.Software.Part.DataArea;
import jrm.profile.data.Software.Part.DiskArea;
import jrm.profile.data.SoftwareList;

/**
 * Holds all transient parsing state (current elements, flags, sets) for DAT XML parsing.
 * Extracted from ProfileParseContext to keep the context class smaller and focused on dispatching.
 */
class ProfileParseState {

	/** XML tag name for ROM elements. */
	static final String STATUS = "status";

	boolean inDescription = false;
	boolean inYear = false;
	boolean inManufacturer = false;
	boolean inPublisher = false;
	boolean inHeader = false;
	boolean inCabinetDipSW = false;

	final EnumSet<CabinetType> cabTypeSet = EnumSet.noneOf(CabinetType.class);

	SoftwareList currSoftwareList = null;
	Software currSoftware = null;
	Part currPart = null;
	DataArea currDataArea = null;
	DiskArea currDiskArea = null;

	Machine currMachine = null;
	Device currDevice = null;
	Samples currSampleSet = null;
	Rom currRom = null;
	Disk currDisk = null;
	Slot currSlot = null;

	String currTag;

	final Profile profile;

	ProfileParseState(Profile profile) {
		this.profile = profile;
	}
}
