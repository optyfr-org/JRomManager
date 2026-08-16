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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.IOUtils;

import jrm.aui.progress.ProgressHandler;
import jrm.profile.Profile;
import jrm.profile.manager.Export;
import jrm.xml.EnhancedXMLStreamWriter;

/**
 * List of {@link SoftwareList} collections representing multiple DAT categories. Integrates serialization, XML exporter triggers,
 * and progress tracking hooks for UI.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public final class SoftwareListList extends AnywareListList<SoftwareList> implements ByName<SoftwareList> {
    /**
     * Progress string formatter template representing progress state like "A/B".
     */
    private static final String N_OF_T = "%d/%d";

    /**
     * The {@link List} of {@link SoftwareList} collections.
     */
    private final ArrayList<SoftwareList> swListList = new ArrayList<>();

    /**
     * The by name {@link HashMap} of {@link SoftwareList} collections.
     */
    private final HashMap<String, SoftwareList> swListByName = new HashMap<>();

    /**
     * The constructor, initializing profile association and transient structures.
     * 
     * @param profile the parent profile instance
     */
    public SoftwareListList(Profile profile) {
        super(profile);
        initTransient();
    }

    /**
     * The Serializable method for special serialization handling (initializing transient default values).
     * 
     * @param in the serialization inputstream
     * 
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if the class could not be resolved
     */
    private void readObject(final java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initTransient();
    }

    /**
     * Resets cache pointers.
     */
    @Override
    public void resetCache() {
        this.filteredList = null;
    }

    /**
     * Set filter cache. Currently unused.
     * 
     * @param filter the active filters
     */
    @Override
    public void setFilterCache(final Set<AnywareStatus> filter) {
        // Intentionally empty - filter cache not used for software lists
    }

    /**
     * Retrieves the raw backing software list collection.
     * 
     * @return backing list of SoftwareList
     */
    @Override
    public List<SoftwareList> getList() {
        return swListList;
    }

    /**
     * Filters software lists matching the profile selections and returns them as a stream.
     * 
     * @return filtered SoftwareList stream
     */
    @Override
    public Stream<SoftwareList> getFilteredStream() {
        return getList().stream().filter(sl -> sl.getSystem().isSelected(sl.profile));
    }

    /**
     * Resolves and caches the filtered software list collection.
     * 
     * @return filtered list of SoftwareList
     */
    @Override
    public List<SoftwareList> getFilteredList() {
        if (filteredList == null)
            filteredList = getFilteredStream().filter(t -> profile.getFilterListLists().contains(t.getStatus())).sorted()
                .collect(Collectors.toList()); //NOSONAR
        return filteredList;
    }

    /**
     * Export active software lists as standard XML DAT files.
     * 
     * @param writer the {@link EnhancedXMLStreamWriter} used to write the output file
     * @param progress the {@link ProgressHandler} to show progress in the UI
     * @param modes active export modes
     * @param selection selected software list (null to export all)
     * 
     * @throws XMLStreamException if an XML stream error occurs
     * @throws IOException if a file I/O error occurs
     */
    public void export(final EnhancedXMLStreamWriter writer, final ProgressHandler progress, final Set<ExportMode> modes, final SoftwareList selection)
            throws XMLStreamException, IOException {
        final List<SoftwareList> lists;
        if (selection != null)
            lists = Collections.singletonList(selection);
        else {
            if (modes.contains(ExportMode.FILTERED))
                lists = getFilteredStream().toList();
            else
                lists = getList();
        }
        if (lists.size() > 0) {
            writer.writeStartDocument("UTF-8", "1.0");
            if (lists.size() > 1) {
                writer.writeDTD("<!DOCTYPE softwarelists [\n" + IOUtils.toString(Export.class.getResourceAsStream("/jrm/resources/dtd/softwarelists.dtd"), StandardCharsets.UTF_8)
                        + "\n]>\n");
                writer.writeStartElement("softwarelists");
            } else
                writer.writeDTD("<!DOCTYPE softwarelist [\n" + IOUtils.toString(Export.class.getResourceAsStream("/jrm/resources/dtd/softwarelist.dtd"), StandardCharsets.UTF_8)
                        + "\n]>\n");
            progress.setProgress("Exporting", 0, lists.stream().flatMapToInt(sl -> IntStream.of(sl.size())).sum());
            progress.setProgress2(String.format(N_OF_T, 0, lists.size()), 0, lists.size());
            for (final SoftwareList list : lists) {
                if (progress.isCancel())
                    break;
                list.export(writer, modes, progress);
                progress.setProgress2(String.format(N_OF_T, progress.getCurrent2() + 1, lists.size()), progress.getCurrent2() + 1);
            }
            writer.writeEndDocument();
        }
    }

    /**
     * Checks if a software list name is present in this collection.
     * 
     * @param name the target list name
     * 
     * @return true if the name matches, false otherwise
     */
    @Override
    public boolean containsName(String name) {
        return swListByName.containsKey(name);
    }

    /**
     * Get a software list by its unique name.
     * 
     * @param name the target list name
     * 
     * @return the matching software list or null
     */
    @Override
    public SoftwareList getByName(String name) {
        return swListByName.get(name);
    }

    /**
     * Puts a software list into the name map.
     * 
     * @param t the software list to map
     * 
     * @return the previously mapped software list if any, or null
     */
    @Override
    public SoftwareList putByName(SoftwareList t) {
        return swListByName.put(t.name, t);
    }

    /**
     * Named map filtered cache.
     */
    private transient Map<String, SoftwareList> swListFilteredByName = null;

    /**
     * Resets and populates the named filtered cache.
     */
    @Override
    public void resetFilteredName() {
        swListFilteredByName = getFilteredStream().collect(Collectors.toMap(SoftwareList::getBaseName, Function.identity()));
    }

    /**
     * Checks if a filtered software list name is present in this collection.
     * 
     * @param name the target list name
     * 
     * @return true if the name matches, false otherwise
     */
    @Override
    public boolean containsFilteredName(String name) {
        if (swListFilteredByName == null)
            resetFilteredName();
        return swListFilteredByName.containsKey(name);
    }

    /**
     * Get a filtered software list by its unique name.
     * 
     * @param name the target list name
     * 
     * @return the matching software list or null
     */
    @Override
    public SoftwareList getFilteredByName(String name) {
        if (swListFilteredByName == null)
            resetFilteredName();
        return swListFilteredByName.get(name);
    }

    /**
     * Counts filtered software lists.
     * 
     * @return filtered list count
     */
    @Override
    public int count() {
        return getFilteredList().size();
    }

    /**
     * Retrieves the software list at the specified index.
     * 
     * @param i the index
     * 
     * @return the SoftwareList
     */
    @Override
    public SoftwareList getObject(int i) {
        return getFilteredList().get(i);
    }

    /**
     * Retrieves the description of the software list at the specified index.
     * 
     * @param i the index
     * 
     * @return the description string representation
     */
    @Override
    public String getDescription(int i) {
        return getObject(i).getDescription().toString();
    }

    /**
     * Retrieves formatted statistics (complete count / total count) for a software list.
     * 
     * @param i the index
     * 
     * @return formatted stats string (e.g., "15/50")
     */
    @Override
    public String getHaveTot(int i) {
        return String.format(N_OF_T, getFilteredList().get(i).countHave(), getFilteredList().get(i).countAll()); // $NON-NLS-1$
    }
}
