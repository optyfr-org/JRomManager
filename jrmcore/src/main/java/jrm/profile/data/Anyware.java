/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import jrm.misc.ProfileSettingsEnum;
import jrm.profile.Profile;
import jrm.profile.data.Entity.Status;
import jrm.profile.scan.options.HashCollisionOptions;
import jrm.profile.scan.options.MergeOptions;
import lombok.Getter;
import lombok.Setter;
import one.util.streamex.StreamEx;

/**
 * Common abstract base class for emulator/retro-gaming systems, machines, and software sets.
 * <p>
 * This class provides state tracking, relationship models, and stream-filtering logic to accommodate various merging strategies
 * (split, merge, no-merge, full-merge, etc.) when scanning, validating, or rebuilding gaming romsets.
 * </p>
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public abstract class Anyware extends AnywareBase implements Systm {

    /**
     * The active execution profile configuration containing settings and filter rules.
     */
    Profile profile;

    /**
     * The name of the parent from which this instance is a clone, or {@code null} if the instance is not a clone.
     * 
     * @return the name of the parent from which this instance is a clone, or {@code null}
     * 
     * @param cloneof the new name of the parent from which this instance is a clone
     */
    protected @Getter @Setter String cloneof = null;

    /**
     * The description field, generally representing the complete display name of the game/system.
     */
    public final StringBuilder description = new StringBuilder();

    /**
     * The release year of this game or system (may contain non-numeric characters like a question mark '?').
     */
    public final StringBuilder year = new StringBuilder();

    /**
     * The collection of {@link Rom} entities related to this object.
     * 
     * @return the collection of {@link Rom} entities
     */
    private final @Getter Collection<Rom> roms = new ArrayList<>();

    /**
     * The collection of {@link Disk} entities related to this object.
     * 
     * @return the collection of {@link Disk} entities
     */
    private final @Getter Collection<Disk> disks = new ArrayList<>();

    /**
     * The collection of audio {@link Sample} entities related to this object.
     * 
     * @return the collection of {@link Sample} entities
     */
    private final @Getter Collection<Sample> samples = new ArrayList<>();

    /**
     * A hash table mapping unique names to their respective {@link Anyware} clone instances.
     * 
     * @return the map of clones by their name keys
     */
    protected transient @Getter Map<String, Anyware> clones = new HashMap<>();

    /**
     * Indicates whether a name/hash collision has occurred for this system or game.
     */
    protected transient boolean collision;

    /**
     * Cached list of entities, filtered and formatted according to the current profile configurations.
     */
    private transient List<EntityBase> tableEntities;

    /**
     * Constructs an {@code Anyware} instance and initializes transient fields.
     * 
     * @param profile the active execution profile
     */
    protected Anyware(Profile profile) {
        this.profile = profile;
        initTransient();
    }

    /**
     * Initializes transient, static, and relational parent fields to their default starting states.
     */
    public void initTransient() {
        collision = false;
        tableEntities = null;
        roms.forEach(r -> r.parent = this);
        disks.forEach(d -> d.parent = this);
        samples.forEach(s -> s.parent = this);
        clones = new HashMap<>();
    }

    /**
     * Checks if the collision mode is active for this instance.
     * 
     * @return {@code true} if collision mode is enabled, {@code false} otherwise
     */
    public boolean isCollisionMode() {
        return collision;
    }

    /**
     * Enables or disables collision mode.
     * 
     * @param parent if {@code true}, propagates the collision mode setting to all registered clones
     */
    public void setCollisionMode(final boolean parent) {
        if (parent)
            getDest().clones.forEach((_, m) -> m.collision = true);
        collision = true;
    }

    /**
     * Resets collision mode for this instance as well as all associated ROM and Disk entities.
     */
    public void resetCollisionMode() {
        collision = false;
        roms.forEach(Rom::resetCollisionMode);
        disks.forEach(Disk::resetCollisionMode);
    }

    /**
     * Checks if this instance represents a clone of another system.
     * 
     * @return {@code true} if this instance is a clone (i.e. has a non-null parent which is not a BIOS), {@code false} otherwise
     */
    public boolean isClone() {
        return (parent != null && !getParent().isBios());
    }

    /**
     * Resolves the destination game/system instance based on the active merge configuration.
     * <p>
     * If merging is enabled and this instance is a clone, this method will recursively delegate to the parent's destination;
     * otherwise, it returns {@code this}.
     * </p>
     * 
     * @return the resolved destination {@link Anyware} instance
     */
    public Anyware getDest() {
        if (profile.getSettings() != null && profile.getSettings().getMergeMode().isMerge() && isClone())
            return getParent().getDest();
        return this;
    }

    /**
     * Abstract check to determine if this instance represents a BIOS system.
     * 
     * @return {@code true} if this is a BIOS system, {@code false} otherwise
     */
    public abstract boolean isBios();

    /**
     * Abstract check to determine if this instance relies on ROMs from another system.
     * 
     * @return {@code true} if this system depends on another system's ROM files, {@code false} otherwise
     */
    public abstract boolean isRomOf();

    /**
     * Filters and returns the list of {@link Disk} entities based on active profile settings and parent associations.
     * 
     * @return the filtered {@link List} of disks
     */
    public List<Disk> filterDisks() {
        Stream<Disk> stream = getFilterDisksStream();

        /*
         * Stream filtering
         */
        return stream.filter(d -> {
            if (d.dumpStatus == Status.nodump) // exclude nodump disks
                return false;
            if (profile.getSettings().getMergeMode() == MergeOptions.SPLIT && containsInParent(this, d)) // exclude if splitting and
                                                                                                         // the disk is in parent
                return false;
            if (profile.getSettings().getMergeMode() == MergeOptions.NOMERGE && containsInParent(this, d)) // explicitly include if
                                                                                                           // nomerge and the disk
                                                                                                           // is in parent
                return true;
            return isBios() || !containsInParent(this, d); // otherwise include if I'm bios or the disk is not in parent
        }).collect(Collectors.toList()); // NOSONAR
    }

    /**
     * Generates a stream of {@link Disk} entities preparing for filter evaluations.
     * 
     * @return a {@link Stream} of disk entities
     */
    private Stream<Disk> getFilterDisksStream() {
        /*
         * / Stream initialization
         */
        Stream<Disk> stream;
        if (!isSelected()) // skip if not selected
            stream = Stream.empty();
        else if (profile.getSettings().getMergeMode().isMerge()) // if merging
        {
            if (isClone() && getParent().isSelected()) // skip if I'm a clone and my parent is selected for inclusion
                stream = Stream.empty();
            else // otherwise...
            {
                // concatenate my disks and all my clones disks into a stream
                final List<Disk> disksWithClones = Stream.concat(disks.stream(), clones.values().stream().flatMap(m -> m.disks.stream())).toList();
                // and mark for collision disks with same names but with different hash (this will change the way getName returns
                // disk names)
                StreamEx.of(disksWithClones).groupingBy(Disk::getName).forEach((_, l) -> {
                    if (l.size() > 1 && StreamEx.of(l).distinct(Disk::hashString).count() > 1)
                        l.forEach(Disk::setCollisionMode);
                });
                // and finally remove real duplicate disks
                stream = StreamEx.of(disksWithClones).distinct(Disk::getName);
            }
        } else // if not merging
            stream = disks.stream(); // simply initialize stream to current object disks list
        return stream;
    }

    /**
     * Filters and returns the list of {@link Rom} entities based on active profile settings, merge rules, and BIOS properties.
     * 
     * @return the filtered {@link List} of ROMs
     */
    public List<Rom> filterRoms() {
        final var list = getFilterRomsStream().filter(this::getRomsFilter)
                .collect(Collectors.toCollection(ArrayList::new));
        if (profile.getSettings().getMergeMode() == MergeOptions.SUPERFULLNOMERGE) {
            final var map = new HashMap<String, Rom>();
            list.forEach(r -> map.merge(r.getName(), r, (oldR, newR) -> oldR.parent == this ? oldR : newR));
            return new ArrayList<>(map.values());
        }
        return list;
    }

    /**
     * Checks whether a ROM should be excluded based on basic validity criteria (dump status, CRC, name, and zero-entry rules).
     * 
     * @param r the ROM entity to test
     * 
     * @return {@code true} if the ROM is invalid and should be excluded
     */
    private boolean isExcludedRom(Rom r) {
        if (r.dumpStatus == Status.nodump || r.crc == null || r.name.isEmpty())
            return true;
        return Boolean.FALSE.equals(profile.getSettings().getProperty(ProfileSettingsEnum.zero_entry_matters, Boolean.class)) && "00000000".equals(r.crc) && r.size == 0;
    }

    /**
     * Evaluates individual {@link Rom} criteria during stream filtering.
     * 
     * @param r the ROM entity to test
     * 
     * @return {@code true} if the ROM should be included under current filter schemes, {@code false} otherwise
     */
    private boolean getRomsFilter(Rom r) {
        if (isExcludedRom(r))
            return false;
        final MergeOptions mode = profile.getSettings().getMergeMode();
        if (mode == MergeOptions.SUPERFULLNOMERGE || mode == MergeOptions.FULLNOMERGE || mode == MergeOptions.FULLMERGE)
            return true; // Unconditionally include roms in SUPERFULLNOMERGE, FULLNOMERGE, FULLMERGE modes
        if (mode == MergeOptions.SPLIT && containsInParent(this, r, false))
            return false; // exclude if splitting and the rom is in parent
        if ((mode == MergeOptions.NOMERGE || mode == MergeOptions.MERGE) && containsInParent(this, r, true))
            return false; // exclude if NOMERGE or MERGE and the rom is in BIOS
        if ((mode == MergeOptions.NOMERGE || mode == MergeOptions.MERGE) && Anyware.wouldMerge(this, r))
            return true; // include if NOMERGE or MERGE and the rom would be merged in parent (when selected)
        return isBios() || !containsInParent(this, r, false); // otherwise include if I'm bios or the rom is not in parent
    }

    /**
     * Generates a stream of {@link Rom} entities preparing for filter evaluations.
     * 
     * @return a {@link Stream} of ROM entities
     */
    private Stream<Rom> getFilterRomsStream() {
        /*
         * Stream initialization
         */
        Stream<Rom> stream;
        if (!isSelected()) // skip if not selected
            stream = Stream.empty();
        else if (profile.getSettings().getMergeMode().isMerge()) // if merging
        {
            stream = getFilterRomsMergingStream();
        } else // if not merging
        {
            if (profile.getSettings().getMergeMode().equals(MergeOptions.SUPERFULLNOMERGE)) // also include
                                                                                            // devices
            {
                if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.exclude_games, Boolean.class))) {
                    if (profile.getProperty(ProfileSettingsEnum.exclude_machines, Boolean.class)) // NOSONAR
                        stream = streamWithDevices(true, false, true); // bios-devices
                    else
                        stream = streamWithDevices(true, false, true); // machine-bios-devices
                } else
                    stream = streamWithDevices(false, true, false); // all
            } else // simply initialize stream to current object disks list
                stream = roms.stream();
        }
        return stream;
    }

    /**
     * Specialized ROM stream setup when the active merge configuration calls for merge/split processing.
     * 
     * @return a {@link Stream} of merged ROM entities
     */
    private Stream<Rom> getFilterRomsMergingStream() {
        Stream<Rom> stream;
        if (isClone() && getParent().isSelected()) // skip if I'm a clone and my parent is selected for inclusion
            stream = Stream.empty();
        else // otherwise...
        {
            // concatenate my roms and all my clones roms into a stream
            final List<Rom> romsWithClones = Stream.concat(roms.stream(), clones.values().stream().flatMap(m -> m.roms.stream())).toList();
            // and mark for collision roms with same names but with different hash (this will change the way getName returns rom
            // names)
            StreamEx.of(romsWithClones).groupingBy(Rom::getName).forEach((_, l) -> {
                if (l.size() > 1 && StreamEx.of(l).distinct(Rom::hashString).count() > 1)
                    l.forEach(Rom::setCollisionMode);
            });
            if (HashCollisionOptions.HALFDUMB == profile.getSettings().getHashCollisionMode()) // HALFDUMB extra stuffs for PD
            {
                /*
                 * This will filter roms with same hash between clones (the encountered first clone collisioning rom is kept), and
                 * also remove parent roms from clone (implicit merge or not) note that roms dat declaration order is important, as
                 * well that clone name alphabetical order
                 */
                final LinkedHashMap<String, Rom> map = new LinkedHashMap<>();
                roms.forEach(r -> map.put(r.hashString(), r));
                clones.values().stream().sorted()
                        .forEach(w -> w.roms.stream().sorted().forEach(r -> map.putIfAbsent(r.hashString(), r)));
                final List<Rom> clonesRoms = new ArrayList<>(map.values());
                clonesRoms.removeAll(roms);
                stream = Stream.concat(roms.stream(), clonesRoms.stream());
            } else // finally remove real duplicate roms
                stream = StreamEx.of(romsWithClones).distinct(Rom::getName);
        }
        return stream;
    }

    /**
     * Retrieves a stream of ROMs for this instance, optionally expanding references to related BIOS systems and physical hardware
     * devices.
     * 
     * @param excludeBios if {@code true}, bios roms will be omitted
     * @param partial if {@code true}, hardware device references are excluded
     * @param recurse if {@code true}, nested devices of devices will also be fetched
     * 
     * @return a {@link Stream} of ROM entities
     */
    abstract Stream<Rom> streamWithDevices(boolean excludeBios, boolean partial, boolean recurse);

    /**
     * Utility method returning a reversed order stream of a given list.
     * 
     * @param <T> any object type
     * @param input the backing list to reverse
     * 
     * @return a {@link Stream} traversing the list elements from last to first
     */
    public static <T> Stream<T> streamInReverse(List<T> input) {
        return IntStream.range(1, input.size() + 1).mapToObj(i -> input.get(input.size() - i));
    }

    /**
     * Evaluates if a given {@link Entity} (ROM or Disk) is eligible for merging.
     * 
     * @param ware the reference system/software being evaluated
     * @param e the specific entity to check
     * 
     * @return {@code true} if the entity should be merged, {@code false} otherwise
     */
    public static boolean wouldMerge(final Anyware ware, final Entity e) {
        return e.merge != null && ware.isRomOf() && ware.getParent() != null && ware.getParent().isSelected();
    }

    /**
     * Recursively evaluates whether a {@link Rom} is declared in the parent lineages of this system.
     * 
     * @param ware the start system to check against
     * @param r the ROM entity to locate
     * @param onlyBios if {@code true}, searches only within parents categorized as a BIOS
     * 
     * @return {@code true} if the ROM exists in parent lines, {@code false} otherwise
     */
    public boolean containsInParent(final Anyware ware, final Rom r, final boolean onlyBios) {
        if ((r.merge != null || profile.getSettings().getImplicitMerge()) && ware.getParent() != null
                && ware.getParent().isSelected()) {
            if ((!onlyBios || ware.getParent().isBios()) && ware.getParent().roms.contains(r))
                return true;
            return containsInParent(ware.getParent(), r, onlyBios);
        }
        return false;
    }

    /**
     * Recursively evaluates whether a {@link Disk} is declared in the parent lineages of this system.
     * 
     * @param ware the start system to check against
     * @param d the Disk entity to locate
     * 
     * @return {@code true} if the Disk exists in parent lines, {@code false} otherwise
     */
    public boolean containsInParent(final Anyware ware, final Disk d) {
        if ((d.merge != null || profile.getSettings().getImplicitMerge()) && ware.getParent() != null
                && ware.getParent().isSelected()) {
            if (ware.getParent().disks.contains(d))
                return true;
            return containsInParent(ware.getParent(), d);
        }
        return false;
    }

    /**
     * Clears the internal cached list of entities, prompting recalculation upon the next fetch.
     */
    public void resetCache() {
        tableEntities = null;
    }

    /**
     * Updates the active entity status filters and triggers a cache invalidation.
     * 
     * @param filter the set of allowed entity statuses
     */
    public void setFilterCache(final Set<EntityStatus> filter) {
        profile.setFilterEntities(filter);
    }

    /**
     * Retrieves the cached collection of entities, evaluating and rebuilding the cache if empty.
     * 
     * @return the filtered and sorted {@link List} of child entities
     */
    public List<EntityBase> getEntities() {
        if (tableEntities == null)
            tableEntities = Stream.of(roms.stream(), disks.stream(), samples.stream()).flatMap(s -> s)
                    .filter(t -> profile.getFilterEntities().contains(t.getStatus())).sorted()
                    .collect(Collectors.toList());
        return tableEntities;
    }

    /**
     * Evaluates the absolute status of this system based on status aggregates of its child entities.
     * 
     * @return the computed {@link AnywareStatus}
     */
    @Override
    public AnywareStatus getStatus() {
        AnywareStatus status = AnywareStatus.COMPLETE;
        final var ok = new AtomicBoolean();
        status = getDisksStatus(status, ok);
        status = getRomsStatus(status, ok);
        status = getSamplesStatus(status, ok);
        if (status == AnywareStatus.PARTIAL && !ok.get())
            status = AnywareStatus.MISSING;
        return status;
    }

    /**
     * Analyzes audio samples status to determine system level status updates.
     * 
     * @param status the existing status level
     * @param ok tracks if at least one valid entity is resolved
     * 
     * @return the updated system status
     */
    private AnywareStatus getSamplesStatus(AnywareStatus status, final AtomicBoolean ok) {
        for (final Sample sample : samples) {
            final EntityStatus estatus = sample.getStatus();
            if (estatus == EntityStatus.KO)
                status = AnywareStatus.PARTIAL;
            else if (estatus == EntityStatus.OK)
                ok.set(true);
            else if (estatus == EntityStatus.UNKNOWN) {
                status = AnywareStatus.UNKNOWN;
                break;
            }
        }
        return status;
    }

    /**
     * Analyzes ROM status to determine system level status updates.
     * 
     * @param status the existing status level
     * @param ok tracks if at least one valid entity is resolved
     * 
     * @return the updated system status
     */
    private AnywareStatus getRomsStatus(AnywareStatus status, final AtomicBoolean ok) {
        for (final Rom rom : roms) {
            final EntityStatus estatus = rom.getStatus();
            if (estatus == EntityStatus.KO)
                status = AnywareStatus.PARTIAL;
            else if (estatus == EntityStatus.OK)
                ok.set(true);
            else if (estatus == EntityStatus.UNKNOWN) {
                status = AnywareStatus.UNKNOWN;
                break;
            }
        }
        return status;
    }

    /**
     * Analyzes Disk status to determine system level status updates.
     * 
     * @param status the existing status level
     * @param ok tracks if at least one valid entity is resolved
     * 
     * @return the updated system status
     */
    private AnywareStatus getDisksStatus(AnywareStatus status, final AtomicBoolean ok) {
        for (final Disk disk : disks) {
            final EntityStatus estatus = disk.getStatus();
            if (estatus == EntityStatus.KO)
                status = AnywareStatus.PARTIAL;
            else if (estatus == EntityStatus.OK)
                ok.set(true);
            else if (estatus == EntityStatus.UNKNOWN) {
                status = AnywareStatus.UNKNOWN;
                break;
            }
        }
        return status;
    }

    /**
     * Calculates the total number of successfully resolved child entities in this system.
     * 
     * @return the count of successful entities
     */
    public int countHave() {
        return countHaveRoms() + countHaveDisks() + countHaveSamples();
    }

    /**
     * Calculates the number of successfully resolved ROM entities.
     * 
     * @return the count of successful ROMs
     */
    public int countHaveRoms() {
        return roms.stream().mapToInt(r -> r.getStatus() == EntityStatus.OK ? 1 : 0).sum();
    }

    /**
     * Calculates the number of successfully resolved Disk entities.
     * 
     * @return the count of successful Disks
     */
    public int countHaveDisks() {
        return disks.stream().mapToInt(d -> d.getStatus() == EntityStatus.OK ? 1 : 0).sum();
    }

    /**
     * Calculates the number of successfully resolved audio Sample entities.
     * 
     * @return the count of successful Samples
     */
    public int countHaveSamples() {
        return samples.stream().mapToInt(s -> s.getStatus() == EntityStatus.OK ? 1 : 0).sum();
    }

    /**
     * Counts all child entities regardless of physical presence status.
     * 
     * @return the total count of ROMs, Disks, and Samples
     */
    public int countAll() {
        return roms.size() + disks.size() + samples.size();
    }

    /**
     * Cached map tracking status of clone ROMs to bypass redundant processing passes.
     */
    private transient HashMap<String, EntityStatus> clonesRomsStatus = null;

    /**
     * Resets the transient clones ROMs status map.
     */
    public void resetClonesRomsStatus() {
        clonesRomsStatus = null;
    }

    /**
     * Resolves or populates the status tracker for a specific clone ROM.
     * 
     * @param rom the target clone ROM to check
     * 
     * @return the resolved {@link EntityStatus} of the clone ROM
     */
    EntityStatus getCloneRomStatus(Rom rom) {
        if (clonesRomsStatus == null) {
            clonesRomsStatus = new HashMap<>();
            if (clones.size() > 0) {
                clones.values().stream().flatMap(aw -> aw.roms.stream()).filter(r -> r.ownStatus != EntityStatus.UNKNOWN)
                        .forEach(r -> clonesRomsStatus.put(r.hashString(), r.ownStatus));
            }
        }
        return clonesRomsStatus.get(rom.hashString());
    }

    /**
     * Retrieves the parent system context associated with this instance.
     * 
     * @return the parent {@link Anyware} instance
     */
    @Override
    public Anyware getParent() {
        return getParent(Anyware.class);
    }

    /**
     * Calculates the hash code for this instance.
     * 
     * @return the computed hash code
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * Evaluates equality with another object based on unique names.
     * 
     * @param obj the reference object to compare with
     * 
     * @return {@code true} if equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Anyware aw)
            return this.name.equals(aw.name);
        return super.equals(obj);
    }

    /**
     * Retrieves the associated active profile containing configuration states.
     * 
     * @return the associated {@link Profile}
     */
    @Override
    public Profile getProfile() {
        return profile;
    }

    /**
     * Returns the size of the cached, filtered entities collection.
     * 
     * @return the number of cached entities
     */
    public int count() {
        return getEntities().size();
    }

    /**
     * Retrieves a specific entity by its index within the cached list.
     * 
     * @param i the index of the entity
     * 
     * @return the resolved {@link EntityBase}
     */
    public EntityBase getObject(int i) {
        return getEntities().get(i);
    }

    /**
     * Abstract check to see if this system is selected for inclusion.
     * 
     * @return {@code true} if selected, {@code false} otherwise
     */
    public abstract boolean isSelected();

    /**
     * Abstract setter to update the selection status for inclusion.
     * 
     * @param selected the new selection state
     */
    public abstract void setSelected(final boolean selected);

}
