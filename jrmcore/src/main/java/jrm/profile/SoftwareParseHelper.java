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
		if (ctx.currMachine != null)
			startSoftwareListDesc(attributes);
		else {
			ctx.currSoftwareList = new SoftwareList(ctx.profile);
			for (var i = 0; i < attributes.getLength(); i++) {
				switch (attributes.getQName(i)) {
					case "name" -> {
						ctx.currSoftwareList.setName(attributes.getValue(i).trim());
						ctx.profile.machineListList.getSoftwareListList().putByName(ctx.currSoftwareList);
					}
					case Profile.DESCRIPTION -> ctx.currSoftwareList.getDescription().append(attributes.getValue(i).trim());
					default -> { /* skip unknown */ }
				}
			}
		}
	}

	void startSoftwareListDesc(final Attributes attributes) {
		final var swlist = ctx.currMachine.new SWList();
		for (var i = 0; i < attributes.getLength(); i++) {
			switch (attributes.getQName(i)) {
				case "name" -> swlist.setName(attributes.getValue(i));
				case "status" -> swlist.setStatus(Machine.SWStatus.valueOf(attributes.getValue(i)));
				case "filter" -> swlist.setFilter(attributes.getValue(i));
				default -> { /* skip unknown */ }
			}
		}
		ctx.currMachine.getSwlists().put(swlist.getName(), swlist);
		ctx.profile.machineListList.getSoftwareListDefs().computeIfAbsent(swlist.getName(), _ -> new java.util.ArrayList<>()).add(ctx.currMachine);
	}

	void startSoftware(final Attributes attributes) {
		ctx.currSoftware = new Software(ctx.profile);
		for (var i = 0; i < attributes.getLength(); i++) {
			switch (attributes.getQName(i)) {
				case "name" -> ctx.currSoftware.setName(attributes.getValue(i).trim());
				case "cloneof" -> ctx.currSoftware.setCloneof(attributes.getValue(i).trim());
				case "supported" -> ctx.currSoftware.setSupported(Software.Supported.valueOf(attributes.getValue(i)));
				default -> { /* skip unknown */ }
			}
		}
	}

	void startSoftwareFeature(Attributes attributes) {
		if (ctx.currSoftware == null)
			return;
		if (attributes.getValue("name").equalsIgnoreCase("compatibility"))
			ctx.currSoftware.setCompatibility(attributes.getValue("value"));
	}

	void startSoftwarePart(Attributes attributes) {
		if (ctx.currSoftware == null)
			return;
		ctx.currPart = new Part();
		ctx.currSoftware.getParts().add(ctx.currPart);
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("name".equals(attributes.getQName(i)))
				ctx.currPart.setName(attributes.getValue(i).trim());
			else if ("interface".equals(attributes.getQName(i)))
				ctx.currPart.setIntrface(attributes.getValue(i).trim());
		}
	}

	void startSoftwarePartDataarea(Attributes attributes) {
		if (ctx.currSoftware == null || ctx.currPart == null)
			return;
		ctx.currDataArea = new DataArea();
		ctx.currPart.getDataareas().add(ctx.currDataArea);
		for (var i = 0; i < attributes.getLength(); i++) {
			switch (attributes.getQName(i)) {
				case "name" -> ctx.currDataArea.setName(attributes.getValue(i).trim());
				case "size" -> {
					final var value = attributes.getValue(i).trim();
					ExceptionUtils.unthrowF(ctx.currDataArea::setSize, Integer::decode, value, t -> ExceptionUtils.test(t, "0x" + value, 0));
				}
				case "width", "databits" -> ctx.currDataArea.setDatabits(Integer.valueOf(attributes.getValue(i)));
				case "endianness", "endian" -> ctx.currDataArea.setEndianness(Endianness.valueOf(attributes.getValue(i)));
				default -> { /* skip unknown */ }
			}
		}
	}

	void startSoftwarePartDiskarea(Attributes attributes) {
		if (ctx.currSoftware == null || ctx.currPart == null)
			return;
		ctx.currDiskArea = new DiskArea();
		ctx.currPart.getDiskareas().add(ctx.currDiskArea);
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("name".equals(attributes.getQName(i)))
				ctx.currDiskArea.setName(attributes.getValue(i).trim());
		}
	}

	void endSoftwareList() {
		if (ctx.currSoftwareList == null)
			return;
		ctx.profile.machineListList.getSoftwareListList().add(ctx.currSoftwareList);
		ctx.profile.softwaresListCnt++;
		ctx.currSoftwareList = null;
	}

	void endSoftware() {
		if (ctx.currSoftwareList == null || ctx.currSoftware == null)
			return;
		ctx.romDiskHelper.endMachineOrSoftware();  // reuse for clear
		ctx.currSoftwareList.add(ctx.currSoftware);
		ctx.profile.softwaresCnt++;
		ctx.currSoftware = null;
	}
}
