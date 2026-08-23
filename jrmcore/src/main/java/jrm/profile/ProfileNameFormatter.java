/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile;

import jrm.aui.status.StatusRendererFactory;

/**
 * Formats the display name (with HTML) for a Profile.
 * Extracted from Profile.getName() to reduce size of main class.
 */
class ProfileNameFormatter implements StatusRendererFactory {

	private final Profile profile;

	ProfileNameFormatter(Profile profile) {
		this.profile = profile;
	}

	String getName() {
		final var xmlpath = profile.session.getUser().getSettings().getWorkPath().resolve("xmlfiles").toAbsolutePath().normalize();
		final var fname = profile.nfo.getFile().toPath().startsWith(xmlpath)
			? xmlpath.relativize(profile.nfo.getFile().toPath()).toString()
			: profile.nfo.getFile().getName();
		final var nameBuilder = new StringBuilder("[")
			.append(toBlue(fname))
			.append("] ");
		if (profile.build != null) {
			nameBuilder.append(toBoldBlack(profile.build));
		} else if (!profile.getHeader().isEmpty()) {
			if (profile.getHeader().containsKey(Profile.DESCRIPTION)) {
				nameBuilder.append(toBoldBlack(profile.getHeader().get(Profile.DESCRIPTION)));
			} else if (profile.getHeader().containsKey("name")) {
				nameBuilder.append(toBoldBlack(profile.getHeader().get("name")));
				if (profile.getHeader().containsKey(Profile.VERSION))
					nameBuilder.append(" (").append(escape(profile.getHeader().get(Profile.VERSION))).append(")");
			}
		}
		final var strcntBuilder = new StringBuilder();
		if (!profile.machineListList.get(0).isEmpty())
			strcntBuilder.append(profile.machinesCnt).append(" Machines");
		if (!profile.machineListList.getSoftwareListList().isEmpty()) {
			if (!strcntBuilder.isEmpty())
				strcntBuilder.append(", ");
			strcntBuilder.append(profile.softwaresListCnt).append(" Software Lists, ")
				.append(profile.softwaresCnt).append(" Softwares");
		}
		nameBuilder.append("(").append(strcntBuilder).append(")");
		return toDocument(nameBuilder.toString());
	}
}
