/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import jrm.profile.data.Container;
import jrm.profile.data.Disk;
import jrm.profile.data.Entry;
import jrm.profile.data.Rom;
import jrm.profile.fix.actions.BackupContainer;
import jrm.profile.fix.actions.OpenContainer;

/**
 * Core scanner tracking state cache.
 */
abstract class ScanData {
	/** Constructs a set of entries to be added. */
	protected final AtomicReference<OpenContainer> addSet = new AtomicReference<>();
	/** Constructs a set of entries to be removed. */
	protected final AtomicReference<OpenContainer> deleteSet = new AtomicReference<>();
	/** Constructs a set of entries to be renamed. */
	protected final AtomicReference<OpenContainer> renameBeforeSet = new AtomicReference<>();
	/** Constructs a set of entries to be renamed. */
	protected final AtomicReference<OpenContainer> renameAfterSet = new AtomicReference<>();
	/** Constructs a set of entries to be duplicated. */
	protected final AtomicReference<OpenContainer> duplicateSet = new AtomicReference<>();

	/** Constructs a set of entries found. */
	protected final List<Entry> found = new ArrayList<>();
	/** Constructs a set of entries by name. */
	protected final Map<String, Entry> entriesByName;
	/** Constructs a set of entries for renaming. */
	protected final Set<Entry> markedForRename = new HashSet<>();

	/** Constructs a new ScanData instance.
	 * 
	 * @param container the audited container
	 */
	protected ScanData(final Container container) {
		entriesByName = container.getEntriesByName();
	}
}

/**
 * Hashing details parsing cache helper.
 */
abstract class ScanHashData extends ScanData {
	protected final HashMap<String, List<Entry>> entriesBySha1 = new HashMap<>();
	protected final HashMap<String, List<Entry>> entriesByMd5 = new HashMap<>();
	protected final HashMap<String, List<Entry>> entriesByCrc = new HashMap<>();

	/**
	 * Constructs a new ScanHashData instance.
	 * 
	 * @param container the audited container
	 */
	protected ScanHashData(final Container container) {
		super(container);
		initHashesFromContainerEntries(container);
	}

	/**
	 * Populates hash lookup maps from container entries.
	 * 
	 * @param container the container to index by hash values
	 */
	private void initHashesFromContainerEntries(final Container container) {
		container.getEntries().forEach(e -> {
			if (e.getSha1() != null)
				entriesBySha1.computeIfAbsent(e.getSha1(), _ -> new ArrayList<>()).add(e);
			if (e.getMd5() != null)
				entriesByMd5.computeIfAbsent(e.getMd5(), _ -> new ArrayList<>()).add(e);
			if (e.getCrc() != null)
				entriesByCrc.computeIfAbsent(e.getCrc() + '.' + e.getSize(), _ -> new ArrayList<>()).add(e);
		});
	}
}

/**
 * ROM scanning tracking state cache.
 */
final class ScanRomsData extends ScanHashData {
	/*** Constructs a backup container. */
	protected final AtomicReference<BackupContainer> backupSet = new AtomicReference<>();

	/*** Constructs a ROMs list by name. */
	protected final Map<String, Rom> romsByName;

	/** Constructs a new ScanRomsData instance.
	 * 
	 * @param roms the ROMs list to index by name
	 * @param container the audited container
	 */
	public ScanRomsData(final List<Rom> roms, final Container container) {
		super(container);
		romsByName = Rom.getRomsByName(roms);
	}
}

/**
 * Disk CHD scanning tracking state cache.
 */
final class ScanDisksData extends ScanHashData {
	/*** Disk definitions indexed by name.  */
	final Map<String, Disk> disksByName;

	/**
	 * Constructs a new ScanDisksData instance.
	 * 
	 * @param disks the CHD disk definitions
	 * @param container the audited container
	 */
	public ScanDisksData(final List<Disk> disks, final Container container) {
		super(container);
		disksByName = Disk.getDisksByName(disks);
	}

}

/**
 * Audio samples scanning tracking state cache.
 */
final class ScanSamplesData extends ScanData {

	/** Constructs a new ScanSamplesData instance.
	 * 
	 * @param container the audited container
	 */
	public ScanSamplesData(Container container) {
		super(container);
	}

}
