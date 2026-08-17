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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import jrm.aui.progress.ProgressHandler;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.Profile;
import jrm.profile.data.Driver.StatusType;
import jrm.profile.data.Machine.CabinetType;
import jrm.profile.data.Machine.DisplayOrientation;
import jrm.xml.EnhancedXMLStreamWriter;

/**
 * A list of {@link Machine}
 * 
 * @author optyfr
 */
public final class MachineList extends AnywareList<Machine> {
    /**
     * The {@link ArrayList} of {@link Machine}
     */
    private final ArrayList<Machine> mList = new ArrayList<>();
    /**
     * The by name {@link HashMap} of {@link Machine}
     */
    private final HashMap<String, Machine> mByName = new HashMap<>();
    /**
     * The associated Samples set as a {@link SamplesList}
     */
    public final SamplesList samplesets = new SamplesList();

    /**
     * The constructor, will initialize transients fields
     *
     * @param profile the associated profile database
     */
    public MachineList(Profile profile) {
        super(profile);
        initTransient();
    }

    /**
     * Reinitializes this list, each machine, and sample-set parents after Fory load.
     */
    public void afterLoad() {
        initTransient();
        for (final Machine machine : getList())
            machine.initTransient();
        for (final Samples samples : samplesets) {
            for (final Sample sample : samples)
                sample.parent = samples;
        }
    }

    @Override
    public List<Machine> getList() {
        return mList;
    }

    /**
     * Bundles the various filtering option values retrieved from the current profile configurations.
     */
    private class FilterOptions {
        final boolean excludeGames = profile.getProperty(ProfileSettingsEnum.exclude_games, Boolean.class);
        final boolean excludeMachines = profile.getProperty(ProfileSettingsEnum.exclude_machines, Boolean.class);
        final boolean filterIncludeClones = profile.getProperty(ProfileSettingsEnum.filter_InclClones, Boolean.class);
        final boolean filterIncludeDisks = profile.getProperty(ProfileSettingsEnum.filter_InclDisks, Boolean.class);
        final boolean filterIncludeSamples = profile.getProperty(ProfileSettingsEnum.filter_InclSamples, Boolean.class);
        final Driver.StatusType filterMinDriverStatus = Driver.StatusType.valueOf(profile.getProperty(ProfileSettingsEnum.filter_DriverStatus, String.class));
        final DisplayOrientation filterDisplayOrientation = DisplayOrientation.valueOf(profile.getProperty(ProfileSettingsEnum.filter_DisplayOrientation, String.class));
        final CabinetType filterCabinetType = CabinetType.valueOf(profile.getProperty(ProfileSettingsEnum.filter_CabinetType, String.class));
        final String filterYearMin = profile.getProperty(ProfileSettingsEnum.filter_YearMin, String.class);
        final String filterYearMax = profile.getProperty(ProfileSettingsEnum.filter_YearMax, String.class);
    }

    @Override
    public Stream<Machine> getFilteredStream() {
        final var options = new FilterOptions();

        if (options.excludeGames && !options.excludeMachines)
            return getMessFilteredStream();

        /*
         * get all needed profile options
         */
        return getList().stream().filter(t -> {
            // exclude pure games (pure means not bios nor devices)
            if (options.excludeGames && !t.isdevice && !t.isbios && !t.isSoftMachine())
                return false;
            // exclude computer/console
            if (options.excludeMachines && !t.isdevice && !t.isbios && t.isSoftMachine())
                return false;
            /*
             * Apply simple filters
             */
            if (!getSimpleFilters(options, t))
                return false;
            /*
             * Apply advanced filters
             */
            if (!getAdvancedFilters(t)) // NOSONAR
                return false;

            return true; // otherwise include
        });
    }

    /**
     * Applies more complex/advanced filters (like subcategory and player counts).
     *
     * @param t the machine to evaluate
     * 
     * @return {@code true} if the machine passes advanced filters, {@code false} otherwise
     */
    private boolean getAdvancedFilters(Machine t) {
        if (t.subcat != null && !t.subcat.isSelected()) // exclude if subcat is not selected
            return false;
        if (t.nplayer != null && !t.nplayer.isSelected(profile)) // NOSONAR // exclude if nplayer is not selected
            return false;
        return true;
    }

    /**
     * Applies standard simple filters (like clones exclusion, mechanical state, and active disk/samples criteria).
     *
     * @param options the retrieved filter options
     * @param t the machine to evaluate
     * 
     * @return {@code true} if the machine passes simple filters, {@code false} otherwise
     */
    private boolean getSimpleFilters(final FilterOptions options, Machine t) {
        if (!getNonDeviceFilter(options, t))
            return false;
        if (!getYearFilter(options, t))
            return false;
        // exclude clones machines
        if (!options.filterIncludeClones && t.isClone())
            return false;
        // exclude machines with disks
        if (!options.filterIncludeDisks && t.getDisks().size() > 0)
            return false;
        // exclude machines with samples
        if (!options.filterIncludeSamples && t.getSamples().size() > 0)
            return false;
        // exclude machines for which their BIOS system were not selected
        if (!t.getSystem().isSelected(profile))
            return false;
        // exclude machines for which their source file were not selected
        if (t.getSource() != null && !t.getSource().isSelected(profile)) // NOSONAR
            return false;
        return true;
    }

    /**
     * Filters machines based on their release years range.
     *
     * @param options the active filter configurations
     * @param t the machine to evaluate
     * 
     * @return {@code true} if the machine's release year is within the bounds, {@code false} otherwise
     */
    private boolean getYearFilter(final FilterOptions options, Machine t) {
        if (!t.year.isEmpty()) { // exclude machines outside defined year range
            if (options.filterYearMin.compareTo(t.year.toString()) > 0)
                return false;
            if (options.filterYearMax.compareTo(t.year.toString()) < 0)
                return false;
        }
        return true;
    }

    /**
     * Resolves a special MESS filter stream retaining console/computers devices but omitting pure arcade machines.
     *
     * @return a filtered {@link Stream} of machines
     */
    private Stream<Machine> getMessFilteredStream() {
        // special case where we want to keep computers & consoles machines but not
        // arcade games machines (let's call it mess mode)
        HashSet<Machine> machines = new HashSet<>();
        getList().stream().filter(Machine::isSoftMachine).forEach(m -> m.getDevices(machines, false, false, true));
        final HashSet<Machine> allDevices = new HashSet<>();
        getList().stream().filter(t -> !t.isdevice).forEach(m -> m.getDevices(allDevices, false, false, true));
        allDevices.removeAll(allDevices.stream().filter(t -> !t.isdevice).collect(Collectors.toSet()));
        return Stream.concat(machines.stream().filter(t -> !t.isSoftMachine()), getList().stream().filter(t -> t.isdevice && !allDevices.contains(t)));
    }

    /**
     * Filters non-device machines based on driver support status levels.
     *
     * @param options the filter configurations
     * @param t the machine to evaluate
     * 
     * @return {@code true} if the non-device machine is accepted, {@code false} otherwise
     */
    private boolean getNonDeviceFilter(final FilterOptions options, Machine t) {
        if (!t.isdevice) // exception on devices
        {
            // exclude preliminary when min driver status is imperfect
            if (options.filterMinDriverStatus == StatusType.imperfect && t.driver.getStatus() == StatusType.preliminary)
                return false;
            // exclude non-good status when min driver status is good
            if (options.filterMinDriverStatus == StatusType.good && t.driver.getStatus() != StatusType.good)
                return false;
            if (!getNonMechanicalFilter(options, t))
                return false;
        }
        return true;
    }

    /**
     * Filters non-mechanical machines based on display orientations.
     *
     * @param options the filter configurations
     * @param t the machine to evaluate
     * 
     * @return {@code true} if accepted, {@code false} otherwise
     */
    private boolean getNonMechanicalFilter(final FilterOptions options, Machine t) {
        if (!t.ismechanical) // exception on mechanical
        {
            // exclude "horizontal only" when display filter is "vertical only"
            if (options.filterDisplayOrientation == DisplayOrientation.vertical && t.orientation == DisplayOrientation.horizontal)
                return false;
            // exclude "vertical only" when display filter is "horizontal only"
            if (options.filterDisplayOrientation == DisplayOrientation.horizontal && t.orientation == DisplayOrientation.vertical)
                return false;
            if (!getNonBiosFilter(options, t))
                return false;
        }
        return true;
    }

    /**
     * Filters non-BIOS machines based on cabinet types.
     *
     * @param options the filter configurations
     * @param t the machine to evaluate
     * 
     * @return {@code true} if accepted, {@code false} otherwise
     */
    private boolean getNonBiosFilter(final FilterOptions options, Machine t) {
        if (!t.isbios) // exception on bios
        {
            // exclude "cocktail only" if cabinet filter is "upright only"
            if (options.filterCabinetType == CabinetType.upright && t.cabinetType == CabinetType.cocktail)
                return false;
            // exclude "upright only" if cabinet filter is "cocktail only"
            if (options.filterCabinetType == CabinetType.cocktail && t.cabinetType == CabinetType.upright)
                return false;
        }
        return true;
    }

    @Override
    public List<Machine> getFilteredList() {
        if (filteredList == null)
            filteredList = getFilteredStream().filter(machine -> profile.getFilterList().contains(machine.getStatus())).sorted()
                .collect(Collectors.toList()); //NOSONAR
        return filteredList;
    }

    @Override
    public long countAll() {
        return getFilteredStream().count();
    }

    @Override
    public long countHave() {
        return getFilteredStream().filter(t -> t.getStatus() == AnywareStatus.COMPLETE).count();
    }

    /**
     * Export as dat
     * 
     * @param writer the {@link EnhancedXMLStreamWriter} used to write output file
     * @param progress the {@link ProgressHandler} to show the current progress
     * @param is_mame is it mame (true) or logqix (false) format ?
     * @param modes the export modes
     * 
     * @throws XMLStreamException if an XML writing error occurs
     */
    public void export(final EnhancedXMLStreamWriter writer, final ProgressHandler progress, final boolean is_mame, final Set<ExportMode> modes) throws XMLStreamException {
        if (is_mame)
            writer.writeStartElement("mame");
        else
            writer.writeStartElement("datafile");
        final List<Machine> list = modes.contains(ExportMode.FILTERED) ? getFilteredStream().toList() : getList();
        var i = 0;
        progress.setProgress(profile.getSession().getMsgs().getString("MachineList.Exporting"), i, list.size());
        for (final Machine m : list) {
            if (progress.isCancel())
                break;
            progress.setProgress(String.format(profile.getSession().getMsgs().getString("MachineList.Exporting_%s"), m.name), ++i);
            if (modes.contains(ExportMode.ALL)
                    || (modes.contains(ExportMode.FILTERED) && m.isSelected())
                    || (modes.contains(ExportMode.MISSING) && m.getStatus() != AnywareStatus.COMPLETE && m.getStatus() != AnywareStatus.UNKNOWN)
                    || (modes.contains(ExportMode.HAVE) && m.getStatus() != AnywareStatus.MISSING && m.getStatus() != AnywareStatus.UNKNOWN))
                m.export(writer, is_mame, modes);
        }
        writer.writeEndElement();
    }

    @Override
    public boolean containsName(final String name) {
        return mByName.containsKey(name);
    }

    @Override
    public Machine getByName(String name) {
        return mByName.get(name);
    }

    @Override
    public Machine putByName(Machine t) {
        return mByName.put(t.getName(), t);
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * named map filtered cache
     */
    private transient Map<String, Machine> mFilteredByName = null;

    @Override
    public void resetFilteredName() {
        mFilteredByName = getFilteredStream().collect(Collectors.toMap(Machine::getBaseName, Function.identity()));
    }

    @Override
    public boolean containsFilteredName(String name) {
        if (mFilteredByName == null)
            resetFilteredName();
        return mFilteredByName.containsKey(name);
    }

    @Override
    public Machine getFilteredByName(String name) {
        if (mFilteredByName == null)
            resetFilteredName();
        return mFilteredByName.get(name);
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
