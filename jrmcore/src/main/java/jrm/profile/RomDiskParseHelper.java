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
import java.util.HashMap;
import java.util.HashSet;

import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Attributes;

import jrm.profile.data.Disk;
import jrm.profile.data.Entity;
import jrm.profile.data.Rom;
import jrm.profile.data.Rom.LoadFlag;
import jrm.profile.data.Software;

/**
 * Helper for ROM and Disk element parsing, including duplicate detection and suspicious CRC tracking.
 * Extracted to shrink ProfileParseContext.
 */
class RomDiskParseHelper {

	private final ProfileParseContext ctx;

	private final HashMap<String, Rom> romsByCRC = new HashMap<>();
	private final HashSet<String> roms = new HashSet<>();
	private final HashSet<String> disks = new HashSet<>();

	RomDiskParseHelper(ProfileParseContext ctx) {
		this.ctx = ctx;
	}

	void clear() {
		roms.clear();
		disks.clear();
	}

	void startRom(final Attributes attributes) {
		if (ctx.state.currMachine == null && ctx.state.currSoftware == null)
			return;
		ctx.state.currRom = new Rom(ctx.state.currMachine != null ? ctx.state.currMachine : ctx.state.currSoftware);
		if (ctx.state.currSoftware != null && ctx.state.currDataArea != null)
			ctx.state.currDataArea.getRoms().add(ctx.state.currRom);
		for (var i = 0; i < attributes.getLength(); i++) {
			final var value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "name" -> ctx.state.currRom.setName(value.trim());
				case "size" -> ctx.state.currRom.setSize(Long.decode(value));
				case "offset" -> {
					if (value.toLowerCase().startsWith("0x"))
						ctx.state.currRom.setOffset(Long.decode(value));
					else
						ctx.state.currRom.setOffset(Long.decode("0x" + value));
				}
				case "value" -> ctx.state.currRom.setValue(value);
				case "crc" -> ctx.state.currRom.setCrc(safeHex(value, 8));
				case "sha1" -> {
					ctx.state.currRom.setSha1(safeHex(value, 40));
					ctx.state.profile.sha1Roms = true;
				}
				case "md5" -> {
					ctx.state.currRom.setMd5(safeHex(value, 32));
					ctx.state.profile.md5Roms = true;
				}
				case "merge" -> ctx.state.currRom.setMerge(value.trim());
				case "bios" -> ctx.state.currRom.setBios(value);
				case "region" -> ctx.state.currRom.setRegion(value);
				case "date" -> ctx.state.currRom.setDate(value);
				case "optional" -> ctx.state.currRom.setOptional(org.apache.commons.lang3.BooleanUtils.toBoolean(value));
				case "status" -> ctx.state.currRom.setDumpStatus(Entity.Status.valueOf(value));
				case "loadflag" -> ctx.state.currRom.setLoadflag(LoadFlag.getEnum(value));
				default -> { /* skip unknown */ }
			}
		}
	}

	void startDisk(final Attributes attributes) {
		if (ctx.state.currMachine == null && ctx.state.currSoftware == null)
			return;
		ctx.state.currDisk = new Disk(ctx.state.currMachine != null ? ctx.state.currMachine : ctx.state.currSoftware);
		if (ctx.state.currSoftware != null && ctx.state.currDiskArea != null)
			ctx.state.currDiskArea.getDisks().add(ctx.state.currDisk);
		for (var i = 0; i < attributes.getLength(); i++) {
			final var value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "name" -> {
					var name = value.trim();
					if (name.endsWith(".chd"))
						name = name.substring(0, name.length() - 4);
					ctx.state.currDisk.setName(name);
				}
				case "sha1" -> {
					ctx.state.currDisk.setSha1(safeHex(value, 40));
					ctx.state.profile.sha1Disks = true;
				}
				case "md5" -> {
					ctx.state.currDisk.setMd5(safeHex(value, 32));
					ctx.state.profile.md5Disks = true;
				}
				case "merge" -> ctx.state.currDisk.setMerge(value.trim());
				case "index" -> ctx.state.currDisk.setIndex(Integer.decode(value));
				case "optional" -> ctx.state.currDisk.setOptional(org.apache.commons.lang3.BooleanUtils.toBoolean(value));
				case "writeable" -> ctx.state.currDisk.setWriteable(org.apache.commons.lang3.BooleanUtils.toBoolean(value));
				case "region" -> ctx.state.currDisk.setRegion(value);
				case "status" -> ctx.state.currDisk.setDumpStatus(Entity.Status.valueOf(value));
				default -> { /* skip unknown */ }
			}
		}
	}

	void endRom() {
		if (ctx.state.currRom.getBaseName() != null) {
			if (!roms.contains(ctx.state.currRom.getBaseName())) {
				roms.add(ctx.state.currRom.getBaseName());
				if (ctx.state.currMachine != null) {
					ctx.state.currMachine.getRoms().add(ctx.state.currRom);
					ctx.state.profile.romsCnt++;
				} else if (ctx.state.currSoftware != null) {
					ctx.state.currSoftware.getRoms().add(ctx.state.currRom);
					ctx.state.profile.swromsCnt++;
				}
			}
			endRomCheckSuspiciousCRC();
		}
		ctx.state.currRom = null;
	}

	void endDisk() {
		if (ctx.state.currDisk.getBaseName() != null && !disks.contains(ctx.state.currDisk.getBaseName())) {
			disks.add(ctx.state.currDisk.getBaseName());
			if (ctx.state.currMachine != null) {
				ctx.state.currMachine.getDisks().add(ctx.state.currDisk);
				ctx.state.profile.disksCnt++;
			} else if (ctx.state.currSoftware != null) {
				ctx.state.currSoftware.getDisks().add(ctx.state.currDisk);
				ctx.state.profile.swdisksCnt++;
			}
		}
		ctx.state.currDisk = null;
	}

	private void endRomCheckSuspiciousCRC() {
		if (ctx.state.currRom.getCrc() != null) {
			final var oldRom = romsByCRC.put(ctx.state.currRom.getCrc(), ctx.state.currRom);
			if (oldRom != null) {
				if (oldRom.getSha1() != null && ctx.state.currRom.getSha1() != null && !oldRom.equals(ctx.state.currRom))
					ctx.state.profile.suspiciousCRC.add(ctx.state.currRom.getCrc());
				if (oldRom.getMd5() != null && ctx.state.currRom.getMd5() != null && !oldRom.equals(ctx.state.currRom))
					ctx.state.profile.suspiciousCRC.add(ctx.state.currRom.getCrc());
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

	void endMachineOrSoftware() {
		clear();
	}
}
