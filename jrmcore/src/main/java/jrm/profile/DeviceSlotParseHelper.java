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

import jrm.profile.data.Device;
import jrm.profile.data.Samples;
import jrm.profile.data.Slot;
import jrm.profile.data.SlotOption;

/**
 * Helper for device/slot SAX parsing elements (devices, slots, extensions, refs).
 * Extracted to shrink MachineParseHelper and ProfileParseContext.
 */
class DeviceSlotParseHelper {

	private final ProfileParseContext ctx;

	DeviceSlotParseHelper(ProfileParseContext ctx) {
		this.ctx = ctx;
	}

	void startDevice(Attributes attributes) {
		if (ctx.state.currMachine == null)
			return;
		ctx.state.currDevice = new Device();
		ctx.state.currMachine.getDevices().add(ctx.state.currDevice);
		for (var i = 0; i < attributes.getLength(); i++) {
			final String value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "type" -> ctx.state.currDevice.setType(value.trim());
				case "tag" -> ctx.state.currDevice.setTag(value.trim());
				case "interface" -> ctx.state.currDevice.setIntrface(value.trim());
				case "fixed_image" -> ctx.state.currDevice.setFixedImage(value.trim());
				case "mandatory" -> ctx.state.currDevice.setMandatory(value.trim());
				default -> { /* skip unknown */ }
			}
		}
	}

	void startInstance(Attributes attributes) {
		if (ctx.state.currMachine == null || ctx.state.currDevice == null)
			return;
		ctx.state.currDevice.setInstance(ctx.state.currDevice.new Instance());
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("name".equals(attributes.getQName(i)))
				ctx.state.currDevice.getInstance().setName(attributes.getValue(i).trim());
			else if ("briefname".equals(attributes.getQName(i)))
				ctx.state.currDevice.getInstance().setBriefname(attributes.getValue(i).trim());
		}
	}

	void startExtension(Attributes attributes) {
		if (ctx.state.currMachine == null || ctx.state.currDevice == null)
			return;
		final var ext = ctx.state.currDevice.new Extension();
		ctx.state.currDevice.getExtensions().add(ext);
		for (var i = 0; i < attributes.getLength(); i++) {
			if (attributes.getQName(i).equals("name"))
				ext.setName(attributes.getValue(i).trim());
		}
	}

	void startDeviceRef(Attributes attributes) {
		if (ctx.state.currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("name".equals(attributes.getQName(i)))
				ctx.state.currMachine.getDeviceRef().add(attributes.getValue(i));
		}
	}

	void startSlot(Attributes attributes) {
		if (ctx.state.currMachine == null)
			return;
		for (var i = 0; i < attributes.getLength(); i++) {
			if ("name".equals(attributes.getQName(i))) {
				ctx.state.currSlot = new Slot();
				ctx.state.currSlot.setName(attributes.getValue(i));
				ctx.state.currMachine.getSlots().put(ctx.state.currSlot.getName(), ctx.state.currSlot);
			}
		}
	}

	void startSlotOption(Attributes attributes) {
		if (ctx.state.currMachine == null || ctx.state.currSlot == null)
			return;
		final var slotoption = new SlotOption();
		for (var i = 0; i < attributes.getLength(); i++) {
			String value = attributes.getValue(i);
			switch (attributes.getQName(i)) {
				case "name" -> {
					slotoption.setName(value);
					ctx.state.currSlot.add(slotoption);
				}
				case "devname" -> slotoption.setDevName(value);
				case "default" -> slotoption.setDef(BooleanUtils.toBoolean(value));
				default -> { /* skip unknown */ }
			}
		}
	}
}
