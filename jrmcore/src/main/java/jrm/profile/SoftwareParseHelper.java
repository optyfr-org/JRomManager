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
import org.xml.sax.Attributes;

import jrm.misc.ExceptionUtils;
import jrm.profile.data.Software;
import jrm.profile.data.Software.Part;
import jrm.profile.data.Software.Part.DataArea;
import jrm.profile.data.Software.Part.DataArea.Endianness;
import jrm.profile.data.Software.Part.DiskArea;
import jrm.profile.data.Machine;
import jrm.profile.data.SoftwareList;

/**
 * Helper for software list and software item SAX parsing.
 * Extracted to further split ProfileParseContext.
 */
class SoftwareParseHelper {

	private final ProfileParseContext ctx;

	SoftwareParseHelper(ProfileParseContext ctx) {
		this.ctx = ctx;
	}

	void startSoftwareList(final Attributes attributes) {
		if (ctx.state.currMachine != null)
			startSoftwareListDesc(attributes);
		else {
			ctx.state.currSoftwareList = new SoftwareList(ctx.state.profile);
			for (var i = 0; i < attributes.getLength(); i++) {
				switch (attributes.getQName(i)) {
					case "name" -> {
						ctx.state.currSoftwareList.setName(attributes.getValue(i).trim());
						ctx.state.profile.machineListList.getSoftwareListList().putByName(ctx.state.currSoftwareList);
					}
					case Profile.DESCRIPTION -> ctx.state.currSoftwareList.getDescription().append(attributes.getValue(i).trim());
					default -> { /* skip unknown */ }
				}
			}
		}
	}

	void startSoftwareListDesc(final Attributes attributes) {
		final var swlist = ctx.state.currMachine.new SWList();
		for (var i = 0; i < attributes.getLength(); i++) {
			switch (attributes.getQName(i)) {
				case "name" -> swlist.setName(attributes.getValue(i));
				case "status" -> swlist.setStatus(Machine.SWStatus.valueOf(attributes.getValue(i)));
				case "filter" -> swlist.setFilter(attributes.getValue(i));
				default -> { /* skip unknown */ }
			}
		}
		ctx.state.currMachine.getSwlists().put(swlist.getName(), swlist);
		ctx.state.profile.machineListList.getSoftwareListDefs().computeIfAbsent(swlist.getName(), _ -> new java.util.ArrayList<>()).add(ctx.state.currMachine);
	}

	void startSoftware(final Attributes attributes) {
		ctx.state.currSoftware = new Software(ctx.profile);
		for (var i = 0; i < attributes.getLength(); i++) {
			switch (attributes.getQName(i)) {
				case "name" -> ctx.state.currSoftware.setName(attributes.getValue(i).trim());
				case "cloneof" -> ctx.state.currSoftware.setCloneof(attributes.getValue(i).trim());
				case "supported" -> ctx.state.currSoftware.setSupported(Software.Supported.valueOf(attributes.getValue(i)));
				default -> { /* skip unknown */ }
			}
		}
	}

	void startSoftwareFeature(Attributes attributes) {
		if (ctx.state.currSoftware == null)
			return;
		if (attributes.getValue("name").equalsIgnoreCase("compatibility"))
			ctx.state.currSoftware.setCompatibility(attributes.getValue("value"));
	}

	void startSoftwarePart(Attributes attributes) {
		if (ctx.state.currSoftware == null)
			return;
		ctx.state.currPart = new Part();
		ctx.state.currSoftware.getParts().add(ctx.state.currPart);
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("name".equals(attributes.getQName(i)))
				ctx.state.currPart.setName(attributes.getValue(i).trim());
			else if ("interface".equals(attributes.getQName(i)))
				ctx.state.currPart.setIntrface(attributes.getValue(i).trim());
		}
	}

	void startSoftwarePartDataarea(Attributes attributes) {
		if (ctx.state.currSoftware == null || ctx.state.currPart == null)
			return;
		ctx.state.currDataArea = new DataArea();
		ctx.state.currPart.getDataareas().add(ctx.state.currDataArea);
		for (var i = 0; i < attributes.getLength(); i++) {
			switch (attributes.getQName(i)) {
				case "name" -> ctx.state.currDataArea.setName(attributes.getValue(i).trim());
				case "size" -> {
					final var value = attributes.getValue(i).trim();
					ExceptionUtils.unthrowF(ctx.state.currDataArea::setSize, Integer::decode, value, t -> ExceptionUtils.test(t, "0x" + value, 0));
				}
				case "width", "databits" -> ctx.state.currDataArea.setDatabits(Integer.valueOf(attributes.getValue(i)));
				case "endianness", "endian" -> ctx.state.currDataArea.setEndianness(Endianness.valueOf(attributes.getValue(i)));
				default -> { /* skip unknown */ }
			}
		}
	}

	void startSoftwarePartDiskarea(Attributes attributes) {
		if (ctx.state.currSoftware == null || ctx.state.currPart == null)
			return;
		ctx.state.currDiskArea = new DiskArea();
		ctx.state.currPart.getDiskareas().add(ctx.state.currDiskArea);
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("name".equals(attributes.getQName(i)))
				ctx.state.currDiskArea.setName(attributes.getValue(i).trim());
		}
	}

	void endSoftwareList() {
		if (ctx.state.currSoftwareList == null)
			return;
		ctx.state.profile.machineListList.getSoftwareListList().add(ctx.state.currSoftwareList);
		ctx.state.profile.softwaresListCnt++;
		ctx.state.currSoftwareList = null;
	}

	void endSoftware() {
		if (ctx.state.currSoftwareList == null || ctx.state.currSoftware == null)
			return;
		ctx.romDiskHelper.endMachineOrSoftware();  // reuse for clear
		ctx.state.currSoftwareList.add(ctx.state.currSoftware);
		ctx.state.profile.softwaresCnt++;
		ctx.state.currSoftware = null;
	}
}
