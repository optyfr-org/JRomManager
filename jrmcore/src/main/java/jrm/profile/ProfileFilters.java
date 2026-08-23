/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeMap;

import jrm.aui.progress.ProgressHandler;
import jrm.profile.data.Machine;
import jrm.profile.data.SoftwareList;
import jrm.profile.data.Source;
import jrm.profile.data.Sources;
import jrm.profile.data.SystmDevice;
import jrm.profile.data.SystmMechanical;
import jrm.profile.data.SystmStandard;
import jrm.profile.data.Systms;
import jrm.profile.filter.CatVer;
import jrm.profile.filter.CatVer.Category;
import jrm.profile.filter.CatVer.Category.SubCategory;
import jrm.profile.filter.NPlayer;
import jrm.profile.filter.NPlayers;
import jrm.misc.ProfileSettingsEnum;
import jrm.security.PathAbstractor;

/**
 * Handles loading of system/year/catver/nplayer filters for a Profile.
 * Extracted to keep Profile.java smaller.
 */
final class ProfileFilters {

	private ProfileFilters() {}

	static void loadSystems(Profile profile) {
		profile.systems = new Systms();
		profile.systems.add(SystmStandard.STANDARD);
		profile.systems.add(SystmMechanical.MECHANICAL);
		profile.systems.add(SystmDevice.DEVICE);
		final ArrayList<Machine> machines = new ArrayList<>();
		profile.sources = new Sources();
		final var srces = new TreeMap<String, Source>();
		profile.machineListList.get(0).forEach(m -> {
			if (m.isIsbios())
				machines.add(m);
			java.util.Optional.ofNullable(m.getSourcefile()).ifPresent(s -> srces.compute(s, (k, v) -> v == null ? new Source(k) : v.inc()));
		});
		machines.sort((a, b) -> a.getName().compareTo(b.getName()));
		machines.forEach(profile.systems::add);
		srces.forEach((_, src) -> profile.sources.add(src));
		profile.machineListList.get(0).stream().filter(m -> m.getSourcefile() != null).forEach(m -> m.setSource(srces.get(m.getSourcefile())));

		final ArrayList<SoftwareList> softwarelists = new ArrayList<>();
		profile.machineListList.getSoftwareListList().forEach(softwarelists::add);
		softwarelists.sort((a, b) -> a.getName().compareTo(b.getName()));
		softwarelists.forEach(profile.systems::add);
	}

	static void loadYears(Profile profile) {
		final var y = new HashSet<String>();
		y.add("");
		profile.machineListList.get(0).forEach(m -> y.add(m.year.toString()));
		profile.machineListList.getSoftwareListList().forEach(sl -> sl.forEach(s -> y.add(s.year.toString())));
		y.add("????");
		profile.years = y;
	}

	static void loadCatVer(Profile profile, ProgressHandler handler) {
		try {
			final var file = PathAbstractor.getAbsolutePath(profile.session, profile.getProperty(ProfileSettingsEnum.filter_catver_ini, String.class)).toFile();
			if (!file.exists()) {
				profile.catver = null;
				return;
			}
			if (handler != null)
				handler.setProgress("Loading catver.ini ...", -1);
			profile.catver = CatVer.read(profile, file);
			for (final Category cat : profile.catver) {
				for (final SubCategory subcat : cat) {
					for (final String game : subcat) {
						final Machine m = profile.machineListList.get(0).getByName(game);
						if (m != null)
							m.setSubcat(subcat);
					}
				}
			}
		} catch (final Exception _) {
			profile.catver = null;
		}
	}

	static void loadNPlayers(Profile profile, ProgressHandler handler) {
		try {
			final var file = PathAbstractor.getAbsolutePath(profile.session, profile.getProperty(ProfileSettingsEnum.filter_nplayers_ini, String.class)).toFile();
			if (file.exists()) {
				if (handler != null)
					handler.setProgress("Loading nplayers.ini ...", -1);
				profile.nplayers = NPlayers.read(file);
				for (final NPlayer nplayer : profile.nplayers) {
					for (final String game : nplayer) {
						final Machine m = profile.machineListList.get(0).getByName(game);
						if (m != null)
							m.setNplayer(nplayer);
					}
				}
			} else
				profile.nplayers = null;
		} catch (final Exception _) {
			profile.nplayers = null;
		}
	}
}
