/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import jrm.profile.Profile;
import jrm.profile.data.Software.Part.DataArea;
import jrm.profile.data.Software.Part.DiskArea;
import jrm.xml.EnhancedXMLStreamWriter;
import jrm.xml.SimpleAttribute;
import lombok.Getter;
import lombok.Setter;

/**
 * Defines a MESS or MAME software list software item. Encapsulates publisher, release year, compatibility flags, and underlying
 * parts like roms/disks.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public class Software extends Anyware {
    /**
     * Constructor for Software.
     * 
     * @param profile the associated profile
     */
    public Software(Profile profile) {
        super(profile);
    }

    /**
     * The publisher name.
     * 
     * @return the publisher name builder
     */
    private final @Getter StringBuilder publisher = new StringBuilder();

    /**
     * Is this software supported (defaults to yes).
     * 
     * @param supported the support status to set
     * 
     * @return the support status
     */
    private @Getter @Setter Supported supported = Supported.yes;

    /**
     * The software compatibility string (a list of machine dependent tags separated by commas).
     * 
     * @param compatibility the compatibility string to set
     * 
     * @return the compatibility string
     */
    private @Getter @Setter String compatibility = null;

    /**
     * The {@link Part}s list associated with the software.
     * 
     * @return the list of parts
     */
    private final @Getter List<Part> parts = new ArrayList<>();

    /**
     * The software list from which this software originated.
     * 
     * @param sl the software list to set
     * 
     * @return the software list
     */
    private @Getter @Setter SoftwareList sl = null;

    /**
     * The Supported values definition.
     * 
     * @author optyfr
     * 
     * @since 1.0
     */
    public enum Supported {
        /** Not supported. */
        no, // NOSONAR
        /** Partially supported. */
        partial, // NOSONAR
        /** Fully supported. */
        yes; // NOSONAR

        /**
         * Retrieves the XML-ready value representation.
         * 
         * @return the enum value, or null if default ('yes')
         */
        public Supported getXML() {
            return this == yes ? null : this;
        }
    }

    /**
     * Part of Data/Disk areas inside a software.
     * 
     * @author optyfr
     * 
     * @since 1.0
     */
    public static class Part implements Serializable {
        /**
         * Data area containing {@link Rom}s and various definitions of the area.
         * 
         * @author optyfr
         * 
         * @since 1.0
         */
        public static class DataArea implements Serializable {
            /**
             * Words endianness.
             * 
             * @author optyfr
             * 
             * @since 1.0
             */
            public enum Endianness {
                /** Big endian ordering. */
                big, // NOSONAR
                /** Little endian ordering. */
                little; // NOSONAR

                /**
                 * Retrieves the XML representation of endianness.
                 * 
                 * @return the XML-ready value, or null if little-endian (default)
                 */
                public Endianness getXML() {
                    return this == little ? null : this;
                }

            }

            /**
             * Name of this data area.
             * 
             * @param name the data area name to set
             */
            private @Setter String name;

            /**
             * Total rom size in this data area.
             * 
             * @param size the data area size to set
             */
            private @Setter int size;

            /**
             * Number of bits for the data bus.
             * 
             * @param databits the databits count to set
             */
            private @Setter int databits = 8;

            /**
             * Byte ordering format.
             * 
             * @param endianness the endianness to set
             */
            private @Setter Endianness endianness = Endianness.little;

            /**
             * List of ROMs inside this data area.
             * 
             * @return the list of ROMs
             */
            private @Getter List<Rom> roms = new ArrayList<>();

            /** Default constructor for DataArea. Initializes the data area with default values. */
            public DataArea() {
                /* default constructor */ }
        }

        /**
         * Disk area containing {@link Disk}s.
         * 
         * @author optyfr
         * 
         * @since 1.0
         */
        public static class DiskArea implements Serializable {
            /**
             * Name of this disk area.
             * 
             * @param name the disk area name to set
             */
            private @Setter String name;

            /**
             * List of disks in this area.
             * 
             * @return the list of disks
             */
            private @Getter List<Disk> disks = new ArrayList<>();

            /** Default constructor for DiskArea. Initializes the disk area with default values. */
            public DiskArea() {
                /* default constructor */ }
        }

        /**
         * Name of the part.
         * 
         * @param name the part name to set
         */
        private @Setter String name;

        /**
         * The interface used to load this part.
         * 
         * @param intrface the interface to set
         * 
         * @return the interface string
         */
        private @Getter @Setter String intrface;

        /**
         * The {@link List} of {@link DataArea}s.
         * 
         * @return the list of data areas
         */
        private @Getter List<DataArea> dataareas = new ArrayList<>();

        /**
         * The {@link List} of {@link DiskArea}s.
         * 
         * @return the list of disk areas
         */
        private @Getter List<DiskArea> diskareas = new ArrayList<>();

        /** Default constructor for Part. Initializes the part with default values. */
        public Part() {
            /* default constructor */ }
    }

    /**
     * Retrieves the parent software.
     * 
     * @return the parent software item
     */
    @Override
    public Software getParent() {
        return getParent(Software.class);
    }

    /**
     * Retrieves the name of the software.
     * 
     * @return the software name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Retrieves the full name, which includes the parent software list name.
     * 
     * @return the full name
     */
    @Override
    public String getFullName() {
        return sl.name + File.separator + name;
    }

    /**
     * Retrieves the full name for a specific filename within this software.
     * 
     * @param filename the relative filename
     * 
     * @return the full resolved path/filename
     */
    @Override
    public String getFullName(final String filename) {
        return sl.name + File.separator + filename;
    }

    /**
     * Returns whether this software is a BIOS.
     * 
     * @return false always for standard software lists
     */
    @Override
    public boolean isBios() {
        return false;
    }

    /**
     * Returns whether this software is rom-of.
     * 
     * @return false always
     */
    @Override
    public boolean isRomOf() {
        return false;
    }

    /**
     * Retrieves the type of system.
     * 
     * @return standard Type.SOFTWARELIST
     */
    @Override
    public Type getType() {
        return Type.SOFTWARELIST;
    }

    /**
     * Retrieves the associated system structure.
     * 
     * @return the software list system
     */
    @Override
    public Systm getSystem() {
        return sl;
    }

    /**
     * Export as dat entry.
     * 
     * @param writer the {@link EnhancedXMLStreamWriter} used to write the output file
     * @param entries filtered entries list (can be null)
     * @param modes active export modes
     * 
     * @throws XMLStreamException if an XML writing error occurs
     */
    public void export(final EnhancedXMLStreamWriter writer, Collection<Entry> entries, Set<ExportMode> modes) throws XMLStreamException {
        writer.writeStartElement("software",
                new SimpleAttribute("name", name),
                new SimpleAttribute("cloneof", cloneof),
                new SimpleAttribute("supported", supported.getXML())
        );
        writer.writeElement("description", description);
        if (!year.isEmpty())
            writer.writeElement("year", year);
        if (!publisher.isEmpty())
            writer.writeElement("publisher", publisher);
        for (final Part part : parts) {
            writer.writeStartElement("part",
                    new SimpleAttribute("name", part.name),
                    new SimpleAttribute("interface", part.intrface)
            );
            exportRoms(writer, entries, part, modes);
            exportDisks(writer, entries, part, modes);
            writer.writeEndElement();
        }
        writer.writeEndElement();

    }

    /**
     * Internal helper to export ROMs inside a software part.
     * 
     * @param writer the XML writer
     * @param entries active entries collection
     * @param part the target part
     * @param modes active export modes
     * 
     * @throws XMLStreamException if an XML writing error occurs
     */
    private void exportRoms(final EnhancedXMLStreamWriter writer, Collection<Entry> entries, final Part part, Set<ExportMode> modes) throws XMLStreamException {
        final var missing = modes.contains(ExportMode.MISSING);
        final var have = modes.contains(ExportMode.HAVE);
        final var all = modes.contains(ExportMode.ALL) || modes.contains(ExportMode.FILTERED);
        for (final DataArea dataarea : part.dataareas) {
            writer.writeStartElement("dataarea",
                    new SimpleAttribute("name", dataarea.name),
                    new SimpleAttribute("size", dataarea.size),
                    new SimpleAttribute("width", dataarea.databits),
                    new SimpleAttribute("endianness", dataarea.endianness.getXML())
            );
            exportDataAreaRoms(writer, entries, dataarea, all, missing, have);
            writer.writeEndElement();
        }
    }

    /**
     * Internal helper to export ROMs inside a data area.
     * 
     * @param writer the XML writer
     * @param entries active entries collection
     * @param dataarea the target data area
     * @param all export all flag
     * @param missing export missing flag
     * @param have export have flag
     * 
     * @throws XMLStreamException if an XML writing error occurs
     */
    @SuppressWarnings("unlikely-arg-type")
    private void exportDataAreaRoms(final EnhancedXMLStreamWriter writer, Collection<Entry> entries, final DataArea dataarea, boolean all, boolean missing, boolean have)
            throws XMLStreamException {
        for (final Rom r : dataarea.roms)
            if (entries == null || entries.contains(r)) // NOSONAR
                if (all || (missing && r.getStatus() == EntityStatus.KO) || (have && r.getStatus() == EntityStatus.OK)) //NOSONAR
                    r.export(writer, true);
    }

    /**
     * Internal helper to export Disks inside a software part.
     * 
     * @param writer the XML writer
     * @param entries active entries collection
     * @param part the target part
     * @param modes active export modes
     * 
     * @throws XMLStreamException if an XML writing error occurs
     */
    private void exportDisks(final EnhancedXMLStreamWriter writer, Collection<Entry> entries, final Part part, Set<ExportMode> modes) throws XMLStreamException {
        final var missing = modes.contains(ExportMode.MISSING);
        final var have = modes.contains(ExportMode.HAVE);
        final var all = modes.contains(ExportMode.ALL) || modes.contains(ExportMode.FILTERED);
        for (final DiskArea diskarea : part.diskareas) {
            writer.writeStartElement("diskarea",
                    new SimpleAttribute("name", diskarea.name)
            );
            exportDiskAreaDisks(writer, entries, diskarea, all, missing, have);
            writer.writeEndElement();
        }
    }

    /**
     * Internal helper to export Disks inside a disk area.
     * 
     * @param writer the XML writer
     * @param entries active entries collection
     * @param diskarea the target disk area
     * @param all export all flag
     * @param missing export missing flag
     * @param have export have flag
     * 
     * @throws XMLStreamException if an XML writing error occurs
     */
    @SuppressWarnings("unlikely-arg-type")
    private void exportDiskAreaDisks(final EnhancedXMLStreamWriter writer, Collection<Entry> entries, final DiskArea diskarea, boolean all, boolean missing, boolean have)
            throws XMLStreamException {
        for (final Disk d : diskarea.disks)
            if (entries == null || entries.contains(d)) // NOSONAR
                if (all || (missing && d.getStatus() == EntityStatus.KO) || (have && d.getStatus() == EntityStatus.OK)) //NOSONAR
                    d.export(writer, true);
    }

    /**
     * Retrieves the description text.
     * 
     * @return the software description
     */
    @Override
    public CharSequence getDescription() {
        return description;
    }

    /**
     * Internal streaming provider. Excludes devices because software lists are treated separately from main emulator bios/devices.
     * 
     * @param excludeBios exclude bios flag
     * @param partial partial flag
     * @param recurse recurse flag
     * 
     * @return ROMs stream
     */
    @Override
    Stream<Rom> streamWithDevices(boolean excludeBios, boolean partial, boolean recurse) {
        return getRoms().stream();
    }

    /**
     * Compares the specified object with this software for equality.
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
     * Returns the hash code.
     * 
     * @return the hash code value
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * Checks the selection state of this software in profile properties.
     * 
     * @return true if selected, false otherwise
     */
    public boolean isSelected() {
        return profile.getProperty("filter.swlist." + getSl().getName() + ".software." + getName(), true);
    }

    /**
     * Sets the selection state of this software in profile properties.
     * 
     * @param selected true to select, false to deselect
     */
    public void setSelected(final boolean selected) {
        profile.setProperty("filter.swlist." + getSl().getName() + ".software." + getName(), selected);
    }

}
