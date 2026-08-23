/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile;

import java.util.Optional;

import org.xml.sax.Attributes;

/**
 * Handles character data accumulation for description/year/manufacturer etc during profile XML parsing.
 * Extracted to reduce ProfileParseContext size.
 */
class ParseCharacterHandler {

	private final ProfileParseContext ctx;

	ParseCharacterHandler(ProfileParseContext ctx) {
		this.ctx = ctx;
	}

	void characters(final char[] ch, final int start, final int length) {
		final var value = new String(ch, start, length);
		if (!value.isBlank())
			Optional.ofNullable(getCharacterTarget()).ifPresent(target -> target.append(value));
	}

	private StringBuilder getCharacterTarget() {
		final var state = ctx.state;
		if (state.inDescription) {
			return getDescriptionTarget();
		} else if (state.inYear) {
			return getYearTarget();
		} else if (state.inManufacturer && state.currMachine != null) {
			return state.currMachine.manufacturer;
		} else if (state.inPublisher && state.currSoftware != null) {
			return state.currSoftware.getPublisher();
		} else if (state.inHeader) {
			return ctx.profile.getHeader().computeIfAbsent(state.currTag, _ -> new StringBuilder());
		}
		return null;
	}

	private StringBuilder getDescriptionTarget() {
		final var state = ctx.state;
		if (state.currMachine != null)
			return state.currMachine.description;
		if (state.currSoftware != null)
			return state.currSoftware.description;
		if (state.currSoftwareList != null)
			return state.currSoftwareList.getDescription();
		return null;
	}

	private StringBuilder getYearTarget() {
		final var state = ctx.state;
		if (state.currMachine != null)
			return state.currMachine.year;
		if (state.currSoftware != null)
			return state.currSoftware.year;
		return null;
	}

	String getDebugMsg(Attributes attributes, String qName, Exception e) {
		final var state = ctx.state;
		final var msg = new StringBuilder("Error");

		if (state.currMachine != null) {
			msg.append(" for machine ").append(state.currMachine.getName());
		} else if (state.currSoftwareList != null) {
			msg.append(" for software list ").append(state.currSoftwareList.getName());
			if (state.currSoftware != null)
				msg.append(", software ").append(state.currSoftware.getName());
		}

		if (state.currRom != null)
			msg.append(", rom ").append(state.currRom.getName());
		if (state.currDisk != null)
			msg.append(", disk ").append(state.currDisk.getName());

		msg.append(", xmltag=").append(qName);
		final var attrs = java.util.stream.IntStream.range(0, attributes.getLength())
				.mapToObj(i -> attributes.getQName(i) + "=" + attributes.getValue(i))
				.collect(java.util.stream.Collectors.joining(", "));
		msg.append(", xmlattributes={").append(attrs).append("}");

		msg.append("\nOriginal exception=").append(e.getClass().getSimpleName())
				.append(" ").append(e.getMessage());
		return msg.toString();
	}
}
