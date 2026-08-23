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
import jrm.profile.data.Machine;
import jrm.profile.data.Machine.CabinetType;
import jrm.profile.data.Samples;

/**
 * Helper for machine-related SAX parsing elements (devices, slots, dips, displays, etc.).
 * Extracted to further split the large ProfileParseContext.
 */
class MachineParseHelper {

	private final ProfileParseContext ctx;
	private final DeviceSlotParseHelper deviceSlotHelper;

	MachineParseHelper(ProfileParseContext ctx) {
		this.ctx = ctx;
		this.deviceSlotHelper = new DeviceSlotParseHelper(ctx);
	}

	void startMachine(Attributes attributes) {
		ctx.state.currMachine = new Machine(ctx.profile);
		for (var i = 0; i < attributes.getLength(); i++) {
			final String value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "name" -> {
					ctx.state.currMachine.setName(value.trim());
					ctx.state.profile.machineListList.get(0).putByName(ctx.state.currMachine);
				}
				case "romof" -> ctx.state.currMachine.setRomof(value.trim());
				case "cloneof" -> ctx.state.currMachine.setCloneof(value.trim());
				case "sampleof" -> {
					ctx.state.currMachine.setSampleof(value.trim());
					if (!ctx.state.profile.machineListList.get(0).samplesets.containsName(ctx.state.currMachine.getSampleof())) {
						ctx.state.currSampleSet = new Samples(ctx.state.currMachine.getSampleof());
						ctx.state.profile.machineListList.get(0).samplesets.putByName(ctx.state.currSampleSet);
					} else
						ctx.state.currSampleSet = ctx.state.profile.machineListList.get(0).samplesets.getByName(ctx.state.currMachine.getSampleof());
				}
				case "isbios" -> ctx.state.currMachine.setIsbios(BooleanUtils.toBoolean(value));
				case "ismechanical" -> ctx.state.currMachine.setIsmechanical(BooleanUtils.toBoolean(value));
				case "isdevice" -> ctx.state.currMachine.setIsdevice(BooleanUtils.toBoolean(value));
				case "sourcefile" -> ctx.state.currMachine.setSourcefile(value);
				default -> { /* skip unknown */ }
			}
		}
		if (ctx.state.currMachine.getRomof() != null && ctx.state.currMachine.getRomof().equals(ctx.state.currMachine.getBaseName()))
			ctx.state.currMachine.setRomof(null);
		if (ctx.state.currMachine.getCloneof() != null && ctx.state.currMachine.getCloneof().equals(ctx.state.currMachine.getBaseName()))
			ctx.state.currMachine.setCloneof(null);
	}

	void startDevice(Attributes attributes) { deviceSlotHelper.startDevice(attributes); }
	void startInstance(Attributes attributes) { deviceSlotHelper.startInstance(attributes); }
	void startExtension(Attributes attributes) { deviceSlotHelper.startExtension(attributes); }
	void startDeviceRef(Attributes attributes) { deviceSlotHelper.startDeviceRef(attributes); }
	void startSlot(Attributes attributes) { deviceSlotHelper.startSlot(attributes); }
	void startSlotOption(Attributes attributes) { deviceSlotHelper.startSlotOption(attributes); }

	void startInput(Attributes attributes) {
		if (ctx.state.currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			final var value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "players" -> ctx.state.currMachine.input.setPlayers(value);
				case "coins" -> ctx.state.currMachine.input.setCoins(value);
				case "service" -> ctx.state.currMachine.input.setService(value);
				case "tilt" -> ctx.state.currMachine.input.setTilt(value);
				default -> { /* skip unknown */ }
			}
		}
	}

	void startSample(Attributes attributes) {
		if (ctx.state.currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if (attributes.getQName(i).equals("name")) {
				if (ctx.state.currSampleSet == null) {
					ctx.state.currMachine.setSampleof(ctx.state.currMachine.getBaseName());
					if (!ctx.state.profile.machineListList.get(0).samplesets.containsName(ctx.state.currMachine.getSampleof())) {
						ctx.state.currSampleSet = new Samples(ctx.state.currMachine.getSampleof());
						ctx.state.profile.machineListList.get(0).samplesets.putByName(ctx.state.currSampleSet);
					} else
						ctx.state.currSampleSet = ctx.state.profile.machineListList.get(0).samplesets.getByName(ctx.state.currMachine.getSampleof());
				}
				ctx.state.currMachine.getSamples().add(ctx.state.currSampleSet.add(new jrm.profile.data.Sample(ctx.state.currSampleSet, attributes.getValue(i))));
				ctx.state.profile.samplesCnt++;
			}
		}
	}

	void startDipValue(Attributes attributes) {
		if (ctx.state.currMachine == null || !ctx.state.inCabinetDipSW)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if (attributes.getQName(i).equals("name")) {
				if ("cocktail".equalsIgnoreCase(attributes.getValue(i)))
					ctx.state.cabTypeSet.add(CabinetType.cocktail);
				else if ("upright".equalsIgnoreCase(attributes.getValue(i)))
					ctx.state.cabTypeSet.add(CabinetType.upright);
			}
		}
	}

	void startDipSwitch(Attributes attributes) {
		if (ctx.state.currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("name".equals(attributes.getQName(i)) && "cabinet".equalsIgnoreCase(attributes.getValue(i)))
				ctx.state.inCabinetDipSW = true;
		}
	}

	void startDriver(Attributes attributes) {
		if (ctx.state.currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			final String value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "status" -> ctx.state.currMachine.driver.setStatus(value);
				case "emulation" -> ctx.state.currMachine.driver.setEmulation(value);
				case "cocktail" -> ctx.state.currMachine.driver.setCocktail(value);
				case "savestate" -> ctx.state.currMachine.driver.setSaveState(value);
				default -> { /* skip unknown */ }
			}
		}
	}

	void startDisplay(final Attributes attributes) {
		if (ctx.state.currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("rotate".equals(attributes.getQName(i))) {
				ExceptionUtils.unthrow(orientation -> {
					switch (orientation) {
						case 0, 180 -> ctx.state.currMachine.setOrientation(Machine.DisplayOrientation.horizontal);
						case 90, 270 -> ctx.state.currMachine.setOrientation(Machine.DisplayOrientation.vertical);
						default -> { /* ignore unknown orientation values */ }
					}
				}, Integer::parseInt, attributes.getValue(i));
			}
		}
	}

	void endDipSwitch() {
		if (!ctx.state.inCabinetDipSW || ctx.state.currMachine == null)
			return;
		if (ctx.state.cabTypeSet.contains(CabinetType.cocktail)) {
			if (ctx.state.cabTypeSet.contains(CabinetType.upright))
				ctx.state.currMachine.setCabinetType(CabinetType.any);
			else
				ctx.state.currMachine.setCabinetType(CabinetType.cocktail);
		} else
			ctx.state.currMachine.setCabinetType(CabinetType.upright);
		ctx.state.cabTypeSet.clear();
		ctx.state.inCabinetDipSW = false;
	}

	void endMachine() {
		// rom/disk clear is handled by rom helper
		ctx.state.profile.machineListList.get(0).add(ctx.state.currMachine);
		ctx.state.profile.machinesCnt++;
		ctx.state.currMachine = null;
		ctx.state.currSampleSet = null;
	}
}
