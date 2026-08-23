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
import jrm.profile.data.Device;
import jrm.profile.data.Machine;
import jrm.profile.data.Machine.CabinetType;
import jrm.profile.data.Samples;
import jrm.profile.data.Slot;
import jrm.profile.data.SlotOption;

/**
 * Helper for machine-related SAX parsing elements (devices, slots, dips, displays, etc.).
 * Extracted to further split the large ProfileParseContext.
 */
class MachineParseHelper {

	private final ProfileParseContext ctx;

	MachineParseHelper(ProfileParseContext ctx) {
		this.ctx = ctx;
	}

	void startMachine(Attributes attributes) {
		ctx.currMachine = new Machine(ctx.profile);
		for (var i = 0; i < attributes.getLength(); i++) {
			final String value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "name" -> {
					ctx.currMachine.setName(value.trim());
					ctx.profile.machineListList.get(0).putByName(ctx.currMachine);
				}
				case "romof" -> ctx.currMachine.setRomof(value.trim());
				case "cloneof" -> ctx.currMachine.setCloneof(value.trim());
				case "sampleof" -> {
					ctx.currMachine.setSampleof(value.trim());
					if (!ctx.profile.machineListList.get(0).samplesets.containsName(ctx.currMachine.getSampleof())) {
						ctx.currSampleSet = new Samples(ctx.currMachine.getSampleof());
						ctx.profile.machineListList.get(0).samplesets.putByName(ctx.currSampleSet);
					} else
						ctx.currSampleSet = ctx.profile.machineListList.get(0).samplesets.getByName(ctx.currMachine.getSampleof());
				}
				case "isbios" -> ctx.currMachine.setIsbios(BooleanUtils.toBoolean(value));
				case "ismechanical" -> ctx.currMachine.setIsmechanical(BooleanUtils.toBoolean(value));
				case "isdevice" -> ctx.currMachine.setIsdevice(BooleanUtils.toBoolean(value));
				case "sourcefile" -> ctx.currMachine.setSourcefile(value);
				default -> { /* skip unknown */ }
			}
		}
		if (ctx.currMachine.getRomof() != null && ctx.currMachine.getRomof().equals(ctx.currMachine.getBaseName()))
			ctx.currMachine.setRomof(null);
		if (ctx.currMachine.getCloneof() != null && ctx.currMachine.getCloneof().equals(ctx.currMachine.getBaseName()))
			ctx.currMachine.setCloneof(null);
	}

	void startDevice(Attributes attributes) {
		if (ctx.currMachine == null)
			return;
		ctx.currDevice = new Device();
		ctx.currMachine.getDevices().add(ctx.currDevice);
		for (var i = 0; i < attributes.getLength(); i++) {
			final String value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "type" -> ctx.currDevice.setType(value.trim());
				case "tag" -> ctx.currDevice.setTag(value.trim());
				case "interface" -> ctx.currDevice.setIntrface(value.trim());
				case "fixed_image" -> ctx.currDevice.setFixedImage(value.trim());
				case "mandatory" -> ctx.currDevice.setMandatory(value.trim());
				default -> { /* skip unknown */ }
			}
		}
	}

	void startInstance(Attributes attributes) {
		if (ctx.currMachine == null || ctx.currDevice == null)
			return;
		ctx.currDevice.setInstance(ctx.currDevice.new Instance());
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("name".equals(attributes.getQName(i)))
				ctx.currDevice.getInstance().setName(attributes.getValue(i).trim());
			else if ("briefname".equals(attributes.getQName(i)))
				ctx.currDevice.getInstance().setBriefname(attributes.getValue(i).trim());
		}
	}

	void startExtension(Attributes attributes) {
		if (ctx.currMachine == null || ctx.currDevice == null)
			return;
		final var ext = ctx.currDevice.new Extension();
		ctx.currDevice.getExtensions().add(ext);
		for (var i = 0; i < attributes.getLength(); i++) {
			if (attributes.getQName(i).equals("name"))
				ext.setName(attributes.getValue(i).trim());
		}
	}

	void startDeviceRef(Attributes attributes) {
		if (ctx.currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("name".equals(attributes.getQName(i)))
				ctx.currMachine.getDeviceRef().add(attributes.getValue(i));
		}
	}

	void startSlot(Attributes attributes) {
		if (ctx.currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("name".equals(attributes.getQName(i))) {
				ctx.currSlot = new Slot();
				ctx.currSlot.setName(attributes.getValue(i));
				ctx.currMachine.getSlots().put(ctx.currSlot.getName(), ctx.currSlot);
			}
		}
	}

	void startSlotOption(Attributes attributes) {
		if (ctx.currMachine == null || ctx.currSlot == null)
			return;
		final var slotoption = new SlotOption();
		for (var i = 0; i < attributes.getLength(); i++) {
			String value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "name" -> {
					slotoption.setName(value);
					ctx.currSlot.add(slotoption);
				}
				case "devname" -> slotoption.setDevName(value);
				case "default" -> slotoption.setDef(BooleanUtils.toBoolean(value));
				default -> { /* skip unknown */ }
			}
		}
	}

	void startInput(Attributes attributes) {
		if (ctx.currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			final var value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "players" -> ctx.currMachine.input.setPlayers(value);
				case "coins" -> ctx.currMachine.input.setCoins(value);
				case "service" -> ctx.currMachine.input.setService(value);
				case "tilt" -> ctx.currMachine.input.setTilt(value);
				default -> { /* skip unknown */ }
			}
		}
	}

	void startSample(Attributes attributes) {
		if (ctx.currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if (attributes.getQName(i).equals("name")) {
				if (ctx.currSampleSet == null) {
					ctx.currMachine.setSampleof(ctx.currMachine.getBaseName());
					if (!ctx.profile.machineListList.get(0).samplesets.containsName(ctx.currMachine.getSampleof())) {
						ctx.currSampleSet = new Samples(ctx.currMachine.getSampleof());
						ctx.profile.machineListList.get(0).samplesets.putByName(ctx.currSampleSet);
					} else
						ctx.currSampleSet = ctx.profile.machineListList.get(0).samplesets.getByName(ctx.currMachine.getSampleof());
				}
				ctx.currMachine.getSamples().add(ctx.currSampleSet.add(new jrm.profile.data.Sample(ctx.currSampleSet, attributes.getValue(i))));
				ctx.profile.samplesCnt++;
			}
		}
	}

	void startDipValue(Attributes attributes) {
		if (ctx.currMachine == null || !ctx.inCabinetDipSW)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if (attributes.getQName(i).equals("name")) {
				if ("cocktail".equalsIgnoreCase(attributes.getValue(i)))
					ctx.cabTypeSet.add(CabinetType.cocktail);
				else if ("upright".equalsIgnoreCase(attributes.getValue(i)))
					ctx.cabTypeSet.add(CabinetType.upright);
			}
		}
	}

	void startDipSwitch(Attributes attributes) {
		if (ctx.currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("name".equals(attributes.getQName(i)) && "cabinet".equalsIgnoreCase(attributes.getValue(i)))
				ctx.inCabinetDipSW = true;
		}
	}

	void startDriver(Attributes attributes) {
		if (ctx.currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			final String value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "status" -> ctx.currMachine.driver.setStatus(value);
				case "emulation" -> ctx.currMachine.driver.setEmulation(value);
				case "cocktail" -> ctx.currMachine.driver.setCocktail(value);
				case "savestate" -> ctx.currMachine.driver.setSaveState(value);
				default -> { /* skip unknown */ }
			}
		}
	}

	void startDisplay(final Attributes attributes) {
		if (ctx.currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("rotate".equals(attributes.getQName(i))) {
				ExceptionUtils.unthrow(orientation -> {
					switch (orientation) {
						case 0, 180 -> ctx.currMachine.setOrientation(Machine.DisplayOrientation.horizontal);
						case 90, 270 -> ctx.currMachine.setOrientation(Machine.DisplayOrientation.vertical);
						default -> { /* ignore unknown orientation values */ }
					}
				}, Integer::parseInt, attributes.getValue(i));
			}
		}
	}

	void endDipSwitch() {
		if (!ctx.inCabinetDipSW || ctx.currMachine == null)
			return;
		if (ctx.cabTypeSet.contains(CabinetType.cocktail)) {
			if (ctx.cabTypeSet.contains(CabinetType.upright))
				ctx.currMachine.setCabinetType(CabinetType.any);
			else
				ctx.currMachine.setCabinetType(CabinetType.cocktail);
		} else
			ctx.currMachine.setCabinetType(CabinetType.upright);
		ctx.cabTypeSet.clear();
		ctx.inCabinetDipSW = false;
	}

	void endMachine() {
		// rom/disk clear is handled by rom helper
		ctx.profile.machineListList.get(0).add(ctx.currMachine);
		ctx.profile.machinesCnt++;
		ctx.currMachine = null;
		ctx.currSampleSet = null;
	}
}
