/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
import jrm.profile.data.Software.Supported;
import jrm.xml.EnhancedXMLStreamWriter;
import jrm.xml.SimpleAttribute;
import lombok.Getter;

/**
 * A SoftwareList is a specialized {@link AnywareList} containing {@link Software} definitions. It maps software items by their
 * unique name, tracks their compatibility/merging properties, and handles filtering based on active profile settings (e.g.,
 * clone/disk exclusions or minimum support levels).
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public final class SoftwareList extends AnywareList<Software> implements Systm {
    /**
     * Description of the software list.
     * 
     * @return the description string builder
     */
    private final @Getter StringBuilder description = new StringBuilder();

    /**
     * The {@link ArrayList} of {@link Software} items in the list.
     */
    private final List<Software> swList = new ArrayList<>();

    /**
     * The by-name {@link HashMap} of {@link Software} items.
     */
    private final Map<String, Software> swByName = new HashMap<>();

    /**
     * The constructor, initializing transient fields.
     * 
     * @param profile the parent Profile
     */
    public SoftwareList(Profile profile) {
        super(profile);
        initTransient();
    }

    /**
     * Custom deserialization method to restore and initialize transient default values.
     * 
     * @param in the serialization input stream
     * 
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if class resolution fails
     */
    private void readObject(final java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initTransient();
    }

    /**
     * Adds a software item to the list, registering it under its unique name.
     * 
     * @param software the software item to add
     * 
     * @return true if added successfully, false otherwise
     */
    @Override
    public boolean add(final Software software) {
        software.setSl(this);
        swByName.put(software.name, software);
        return swList.add(software);
    }

    /**
     * Retrieves the raw list of software items.
     * 
     * @return the software list
     */
    @Override
    public List<Software> getList() {
        return swList;
    }

    /**
     * Retrieves the system type of software lists.
     * 
     * @return Type.SOFTWARELIST
     */
    @Override
    public Type getType() {
        return Type.SOFTWARELIST;
    }

    /**
     * Retrieves the software list self-reference as a system type.
     * 
     * @return this SoftwareList
     */
    @Override
    public Systm getSystem() {
        return this;
    }

    /**
     * Formats the software list into a descriptive string representation.
     * 
     * @return the formatted description string
     */
    @Override
    public String toString() {
        return "[" + getType() + "] " + description.toString();
    }

    /**
     * Retrieves the name of this software list.
     * 
     * @return the software list name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Private filter options struct mapping system profile options to performance flags.
     * 
     * @author optyfr
     * 
     * @since 1.0
     */
    private class FilterOptions {
        /** Indicates whether clones should be included in filters. */
        final boolean filterIncludeClones = profile.getProperty(ProfileSettingsEnum.filter_InclClones, Boolean.class);

        /** Indicates whether software with disks should be included. */
        final boolean filterIncludeDisks = profile.getProperty(ProfileSettingsEnum.filter_InclDisks, Boolean.class);

        /** The minimum supported level required for software inclusion. */
        final Supported filterMinSoftwareSupportedLevel = Supported.valueOf(profile.getProperty(ProfileSettingsEnum.filter_MinSoftwareSupportedLevel, String.class));

        /** The minimum release year boundary. */
        final String filterYearMin = profile.getProperty(ProfileSettingsEnum.filter_YearMin, String.class);

        /** The maximum release year boundary. */
        final String filterYearMax = profile.getProperty(ProfileSettingsEnum.filter_YearMax, String.class);
    }

    /**
     * Evaluates and streams software items filtered against current profile preferences.
     * 
     * @return a filtered stream of Software
     */
    @Override
    public Stream<Software> getFilteredStream() {
        final var options = new FilterOptions();

        return getList().stream().filter(t -> {
            if (!getYearFilter(options, t))
                return false;
            // Exclude support=no software if minimum software support level is partial
            if (options.filterMinSoftwareSupportedLevel == Supported.partial && t.getSupported() == Supported.no)
                return false;
            // Exclude support!=yes software if minimum software support level is yes
            if (options.filterMinSoftwareSupportedLevel == Supported.yes && t.getSupported() != Supported.yes)
                return false;
            // Exclude clone machines if clones are not included
            if (!options.filterIncludeClones && t.isClone())
                return false;
            // Exclude software with disks if disks are not included
            if (!options.filterIncludeDisks && t.getDisks().size() > 0)
                return false;
            // Exclude software whose software list is not selected in the profile
            return t.getSystem().isSelected(profile);
        });
    }

    /**
     * Applies minimum and maximum release year filters on software items.
     * 
     * @param options the active filter options
     * @param t the target software item
     * 
     * @return true if the software matches the year bounds, false otherwise
     */
    private boolean getYearFilter(final FilterOptions options, Software t) {
        if (!t.year.isEmpty()) { // exclude machines outside defined year range
            if (options.filterYearMin.compareTo(t.year.toString()) > 0)
                return false;
            if (options.filterYearMax.compareTo(t.year.toString()) < 0)
                return false;
        }
        return true;
    }

    /**
     * Resolves and caches the filtered software list matching status and year rules.
     * 
     * @return the filtered software items list
     */
    @Override
    public List<Software> getFilteredList() {
        if (filteredList == null)
            filteredList = getFilteredStream().filter(t -> profile.getFilterList().contains(t.getStatus())).sorted()
                    .collect(Collectors.toList()); // NOSONAR
        return filteredList;
    }

    /**
     * Counts all active software items matching profile filters.
     * 
     * @return the filtered items count
     */
    @Override
    public long countAll() {
        return getFilteredStream().count();
    }

    /**
     * Counts complete software items matching profile filters.
     * 
     * @return complete software items count
     */
    @Override
    public long countHave() {
        return getFilteredStream().filter(t -> t.getStatus() == AnywareStatus.COMPLETE).count();
    }

    /**
     * Export the software list inside a XML document.
     * 
     * @param writer the EnhancedXMLStreamWriter used to write the XML output
     * @param modes the export configurations
     * @param progress the UI progress indicator
     * 
     * @throws XMLStreamException if an XML serialization error occurs
     */
    public void export(final EnhancedXMLStreamWriter writer, Set<ExportMode> modes, final ProgressHandler progress) throws XMLStreamException {
        writer.writeStartElement("softwarelist",
                new SimpleAttribute("name", name),
                new SimpleAttribute("description", description)
        );
        final List<Software> list = modes.contains(ExportMode.FILTERED) ? getFilteredStream().toList() : getList();
        for (final Software s : list) {
            if (progress.isCancel())
                break;
            progress.setProgress(String.format(profile.getSession().getMsgs().getString("SoftwareList.Exporting_%s"),
                    s.getFullName()), progress.getCurrent() + 1);
            if (modes.contains(ExportMode.ALL)
                    || (modes.contains(ExportMode.FILTERED) && s.isSelected())
                    || (modes.contains(ExportMode.MISSING) && s.getStatus() != AnywareStatus.COMPLETE && s.getStatus() != AnywareStatus.UNKNOWN)
                    || (modes.contains(ExportMode.HAVE) && s.getStatus() != AnywareStatus.MISSING && s.getStatus() != AnywareStatus.UNKNOWN))
                s.export(writer, null, modes);
        }
        writer.writeEndElement();
    }

    /**
     * Checks if the list contains a software item with the specified name.
     * 
     * @param name the software name
     * 
     * @return true if found, false otherwise
     */
    @Override
    public boolean containsName(final String name) {
        return swByName.containsKey(name);
    }

    /**
     * Retrieves the software item with the specified name.
     * 
     * @param name the software name
     * 
     * @return the matching Software item, or null if not found
     */
    @Override
    public Software getByName(String name) {
        return swByName.get(name);
    }

    /**
     * Registers a software item in the name map.
     * 
     * @param t the software item to register
     * 
     * @return the previously registered Software item, or null if none
     */
    @Override
    public Software putByName(Software t) {
        return swByName.put(t.name, t);
    }

    /** Named map filtered cache. */
    private transient Map<String, Software> swFilteredByName = null;

    /**
     * Resets and populates the named filtered cache based on active filtered stream.
     */
    @Override
    public void resetFilteredName() {
        swFilteredByName = getFilteredStream().collect(Collectors.toMap(Software::getBaseName, Function.identity()));
    }

    /**
     * Checks if the list contains a software item with the specified filtered name.
     * 
     * @param name the filtered software name
     * 
     * @return true if found, false otherwise
     */
    @Override
    public boolean containsFilteredName(String name) {
        if (swFilteredByName == null)
            resetFilteredName();
        return swFilteredByName.containsKey(name);
    }

    /**
     * Retrieves the software item with the specified filtered name.
     * 
     * @param name the filtered software name
     * 
     * @return the matching Software item, or null if not found
     */
    @Override
    public Software getFilteredByName(String name) {
        if (swFilteredByName == null)
            resetFilteredName();
        return swFilteredByName.get(name);
    }

    /**
     * Compares the specified object with this software list for equality.
     * 
     * @param obj the reference object
     * 
     * @return true if equivalent, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    /**
     * Returns the hash code value for this software list.
     * 
     * @return the hash code value
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
