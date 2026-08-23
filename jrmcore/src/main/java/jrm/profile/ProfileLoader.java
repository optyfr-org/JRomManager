/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.TreeMap;

import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

import jrm.aui.progress.ProgressHandler;
import jrm.locale.Messages;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.misc.SettingsEnum;
import jrm.profile.data.AnywareStatus;
import jrm.profile.data.EntityStatus;
import jrm.profile.data.Machine;
import jrm.profile.data.SoftwareList;
import jrm.profile.manager.ProfileNFO;
import jrm.security.PathAbstractor;
import jrm.security.Session;
import jrm.security.SignedObjectStore;
import jrm.xml.XMLTools;

/**
 * Handles loading, caching, and persistence for Profile instances.
 * Extracted to keep Profile.java smaller and focused on model + filters.
 */
final class ProfileLoader {

	private ProfileLoader() {}

	/**
	 * Serializes current profile state properties to cached binary files.
	 */
	static void save(Profile profile) {
		try {
			SignedObjectStore.write(profile.session, profile.session.getUser().getSettings().getCacheFile(profile.nfo.getFile()), profile, SignedObjectStore.Codec.CACHE);
		} catch (final Exception _) {
			// do nothing
		}
	}

	/**
	 * Loads profile database configurations from physical file descriptors.
	 */
	static Profile load(final Session session, final File file, final ProgressHandler handler) {
		return Profile.load(session, ProfileNFO.load(session, file), handler);
	}

	/**
	 * Loads profile properties matching cached descriptors or walk parsers.
	 */
	static Profile load(final Session session, final ProfileNFO nfo, final ProgressHandler handler) {
		Profile profile = loadProfile(session, nfo, handler);
		if (profile == null) {
			session.setCurrProfile(null);
			return null;
		}

		initializeProfile(profile, handler);
		session.setCurrProfile(profile);
		return profile;
	}

	/**
	 * Loads profile from cache or creates new profile from source files.
	 */
	private static Profile loadProfile(final Session session, final ProfileNFO nfo, final ProgressHandler handler) {
		final var cachefile = session.getUser().getSettings().getCacheFile(nfo.getFile());
		Profile profile = null;
		if (shouldLoadFromCache(cachefile, nfo, session)) {
			profile = loadCache(session, nfo, handler, null, cachefile);
		}
		// Cache may be missing, corrupt, unsigned, or rejected by the deserialization filter
		if (profile == null) {
			profile = loadThenSaveToCache(session, nfo, handler);
		}
		return profile;
	}

	/**
	 * Determines whether profile should be loaded from cache based on file timestamps and cache settings.
	 */
	private static boolean shouldLoadFromCache(final File cachefile, final ProfileNFO nfo, final Session session) {
		return cachefile.lastModified() >= nfo.getFile().lastModified()
			&& (!nfo.isJRM() || cachefile.lastModified() >= nfo.getMame().getFileroms().lastModified())
			&& Boolean.TRUE.equals(!session.getUser().getSettings().getProperty(SettingsEnum.debug_nocache, Boolean.class));
	}

	/**
	 * Initializes loaded profile by building relationships, updating statistics, and loading components.
	 */
	private static void initializeProfile(final Profile profile, final ProgressHandler handler) {
		handler.setProgress(Messages.getString("Profile.BuildingParentClonesRelations"), -1);
		profile.buildParentClonesRelations();
		updateNfoStats(profile);
		profile.nfo.save(profile.session);

		loadProfileComponents(profile, handler);

		profile.filterEntities = EnumSet.allOf(EntityStatus.class);
		profile.filterList = EnumSet.allOf(AnywareStatus.class);
		profile.filterListLists = EnumSet.allOf(AnywareStatus.class);
	}

	/**
	 * Updates profile NFO statistics with current counts and version information.
	 */
	private static void updateNfoStats(final Profile profile) {
		final var stats = profile.nfo.getStats();
		final String version;
		if (profile.build != null) {
			version = profile.build;
		} else if (profile.header.containsKey(Profile.VERSION)) {
			version = profile.header.get(Profile.VERSION).toString();
		} else {
			version = null;
		}
		stats.setVersion(version);
		stats.setTotalSets(profile.softwaresCnt + profile.machinesCnt);
		stats.setTotalRoms(profile.romsCnt + profile.swromsCnt);
		stats.setTotalDisks(profile.disksCnt + profile.swdisksCnt);
	}

	/**
	 * Loads all profile components including settings, filters, and configuration files.
	 */
	private static void loadProfileComponents(final Profile profile, final ProgressHandler handler) {
		handler.setProgress("Loading settings...", -1);
		profile.loadSettings();

		handler.setProgress("Creating Systems filters...", -1);
		profile.loadSystems();

		handler.setProgress("Creating Years filters...", -1);
		profile.loadYears();

		profile.loadCatVer(handler);
		profile.loadNPlayers(handler);
	}

	/**
	 * Parses profile catalog DAT file content and serializes binary database states.
	 */
	private static Profile loadThenSaveToCache(final Session session, final ProfileNFO nfo, final ProgressHandler handler) {
		Profile profile;
		handler.setInfos(1, true);
		profile = new Profile();
		profile.session = session;
		profile.nfo = nfo;
		if (!load(nfo, profile, handler))
			return null;
		// save cache
		handler.setInfos(1, null);
		handler.setProgress(Messages.getString("Profile.SavingCache"), -1);
		profile.save();
		return profile;
	}

	/**
	 * Triggers XML parsing on single dat files or paired ROMs + SoftwareLists.
	 */
	private static boolean load(final ProfileNFO nfo, Profile profile, final ProgressHandler handler) {
		if (!nfo.isJRM()) // load DAT file not attached to a JRM
			return (nfo.getFile().exists() && profile.internalLoad(nfo.getFile(), handler));

		// we use JRM file keep ROMs/SL DATs in relation
		if (nfo.getMame().getFileroms() != null) { // load ROMs dat
			if (!nfo.getMame().getFileroms().exists() || !profile.internalLoad(nfo.getMame().getFileroms(), handler))
				return false;
			if (nfo.getMame().getFilesl() != null && (!nfo.getMame().getFilesl().exists() || !profile.internalLoad(nfo.getMame().getFilesl(), handler))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Retrieves profile database state properties from standard cache files.
	 */
	private static Profile loadCache(final Session session, final ProfileNFO nfo, final ProgressHandler handler, Profile profile, final File cachefile) {
		handler.setInfos(1, null);
		handler.setProgress(Messages.getString("Profile.LoadingCache"), -1);
		try (final var in = handler.getInputStream(new FileInputStream(cachefile), (int) cachefile.length())) {
			profile = (Profile) SignedObjectStore.read(session, in, (int) cachefile.length(), SignedObjectStore.Codec.CACHE);
			profile.session = session;
			profile.nfo = nfo;
		} catch (final Exception e) {
			Log.debug(() -> "Failed to load cache file: " + e.getMessage());
		}
		return profile;
	}

	/**
	 * Maps parent-clones database relationships sequentially after loading metadata catalog elements.
	 */
	static void buildParentClonesRelations(Profile profile) {
		profile.machineListList.forEach(machineList -> machineList.forEach(machine -> {
			if (machine.getRomof() != null) {
				machine.setParent(machineList.getByName(machine.getRomof()));
				if (machine.getParent() != null && !machine.getParent().isIsbios())
					machine.getParent().getClones().put(machine.getName(), machine);
			}
			machine.getDeviceRef().forEach(deviceRef -> machine.getDeviceMachines().putIfAbsent(deviceRef, machineList.getByName(deviceRef)));
			machine.getSlots().values()
					.forEach(slot -> slot.forEach(slotoption -> machine.getDeviceMachines().putIfAbsent(slotoption.getDevName(), machineList.getByName(slotoption.getDevName()))));
		}));
		profile.machineListList.getSoftwareListList().forEach(softwareList -> softwareList.forEach(software -> {
			if (software.getCloneof() != null) {
				software.setParent(softwareList.getByName(software.getCloneof()));
				if (software.getParent() != null)
					software.getParent().getClones().put(software.getName(), software);
			}
		}));
	}
}
