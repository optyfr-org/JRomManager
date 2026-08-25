/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import jrm.profile.Profile;
import lombok.Getter;

/**
 * Samples is a set of unique {@link Sample} instances representing an audio sample set.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public final class Samples extends AnywareBase implements Iterable<Sample> {
    /**
     * The internal map of {@link Sample} instances with {@link NameBase#name} as key.
     * 
     * @return the map of samples indexed by their names
     */
    private @Getter Map<String, Sample> samplesMap = new HashMap<>();

    /**
     * Constructor for the Samples set.
     * 
     * @param name the sample set name
     */
    public Samples(String name) {
        setName(name);
    }

    /**
     * Adds a unique sample to the set, only adding it if it does not already exist.
     * 
     * @param sample the {@link Sample} to add
     * 
     * @return the added {@link Sample}, or the already existing one if present
     */
    public Sample add(Sample sample) {
        if (!samplesMap.containsKey(sample.name)) {
            samplesMap.put(sample.name, sample);
            return sample;
        }
        return samplesMap.get(sample.name);
    }

    /**
     * Get the parent anyware container of the sample set.
     * 
     * @return the parent container base
     */
    @Override
    public AnywareBase getParent() {
        return parent;
    }

    /**
     * Get the name of this sample set.
     * 
     * @return the name of the set
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Get the full name of this sample set.
     * 
     * @return the full name
     */
    @Override
    public String getFullName() {
        return name;
    }

    /**
     * Get the full name with a specific filename appended or resolved.
     * 
     * @param filename the file name to format
     * 
     * @return the full path or filename
     */
    @Override
    public String getFullName(String filename) {
        return filename;
    }

    /**
     * Get the description of the samples.
     * 
     * @return an empty description or CharSequence
     */
    @Override
    public CharSequence getDescription() {
        return ""; //$NON-NLS-1$
    }

    /**
     * Returns an iterator over the samples in this set.
     * 
     * @return an iterator over {@link Sample}
     */
    @Override
    public Iterator<Sample> iterator() {
        return samplesMap.values().iterator();
    }

    /**
     * Evaluates and returns the aggregated status of the sample set based on its individual samples.
     * 
     * @return the evaluated {@link AnywareStatus}
     */
    @Override
    public AnywareStatus getStatus() {
        AnywareStatus status = AnywareStatus.COMPLETE;
        var ok = false;
        for (final Sample sample : this) {
            final EntityStatus estatus = sample.getStatus();
            if (estatus == EntityStatus.KO)
                status = AnywareStatus.PARTIAL;
            else if (estatus == EntityStatus.OK)
                ok = true;
            else if (estatus == EntityStatus.UNKNOWN) {
                status = AnywareStatus.UNKNOWN;
                break;
            }
        }
        if (status == AnywareStatus.PARTIAL && !ok)
            status = AnywareStatus.MISSING;
        return status;
    }

    /**
     * Get the profile associated with this set.
     * 
     * @return always null as sample sets do not have a direct profile association
     */
    @Override
    public Profile getProfile() {
        return null;
    }
}
