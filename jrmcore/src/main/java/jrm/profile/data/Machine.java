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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.StringUtils;

import jrm.profile.Profile;
import jrm.profile.filter.CatVer.Category.SubCategory;
import jrm.profile.filter.NPlayer;
import jrm.xml.EnhancedXMLStreamWriter;
import jrm.xml.SimpleAttribute;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a complete Machine (or a game set in Logiqx terminology) within a Profile. Manages ROM sharing properties, BIOS
 * properties, device attributes, emulator configurations, and integration with software lists.
 *
 * @author optyfr
 */
public class Machine extends Anyware {
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * If defined, romof will tell that this machine will share rom with another machine named in that romof field.
     *
     * @param romof the parent ROM set name to set
     * 
     * @return the parent ROM set name
     */
    protected @Getter @Setter String romof = null;

    /**
     * If defined, sampleof will tell that this machine will have samples contained within a sampleset named by that sampleof field.
     *
     * @param sampleof the sample set name to set
     * 
     * @return the sample set name
     */
    protected @Getter @Setter String sampleof = null;

    /**
     * Is that machine a system bios?
     *
     * @param isbios {@code true} if this machine is a BIOS system, {@code false} otherwise
     * 
     * @return {@code true} if this machine is a BIOS system, {@code false} otherwise
     */
    protected @Getter @Setter boolean isbios = false;

    /**
     * Is that machine electro-mechanical (fruit machines, pinballs, etc.)?
     *
     * @param ismechanical {@code true} if mechanical, {@code false} otherwise
     * 
     * @return {@code true} if mechanical, {@code false} otherwise
     */
    protected @Getter @Setter boolean ismechanical = false;

    /**
     * Is that machine a device (serial interface, disk controller, etc.)?
     *
     * @param isdevice {@code true} if a device, {@code false} otherwise
     * 
     * @return {@code true} if a device, {@code false} otherwise
     */
    protected @Getter @Setter boolean isdevice = false;

    /**
     * The source file path defining this machine.
     *
     * @param sourcefile the source file path to set
     * 
     * @return the source file path
     */
    protected @Getter @Setter String sourcefile = null;

    /**
     * The manufacturer, if known.
     */
    public final StringBuilder manufacturer = new StringBuilder();

    /**
     * The {@link Driver} information.
     */
    public final Driver driver = new Driver();

    /**
     * The {@link Input} information.
     */
    public final Input input = new Input();

    /**
     * The {@link DisplayOrientation} information.
     *
     * @param orientation the display orientation to set
     * 
     * @return the display orientation
     */
    protected @Getter @Setter DisplayOrientation orientation = DisplayOrientation.any;

    /**
     * The {@link CabinetType} information.
     *
     * @param cabinetType the cabinet type to set
     * 
     * @return the cabinet type
     */
    protected @Getter @Setter CabinetType cabinetType = CabinetType.upright;

    /**
     * The software lists that this machine is linked to (if this machine is a computer or a home console).
     *
     * @return the map of software lists
     */
    private final @Getter Map<String, SWList> swlists = new HashMap<>();

    /**
     * A "machine device" references list.
     *
     * @return the list of device reference names
     */
    private final @Getter List<String> deviceRef = new ArrayList<>();

    /**
     * The mapping between each device_ref string and a {@link Machine} (with flag {@link #isdevice}).
     *
     * @return the map of device machines indexed by their name
     */
    protected transient @Getter Map<String, Machine> deviceMachines = new HashMap<>();

    /**
     * An I/O device list.
     *
     * @return the list of devices
     */
    private final @Getter List<Device> devices = new ArrayList<>();

    /**
     * A slot group of optional devices slots.
     *
     * @return the map of slots
     */
    private final @Getter Map<String, Slot> slots = new HashMap<>();

    /**
     * Category/subcategory as defined by catver.ini.
     *
     * @param subcat the category / subcategory to set
     * 
     * @return the category / subcategory
     */
    protected transient @Getter @Setter SubCategory subcat = null;

    /**
     * Nplayer as defined by nplayers.ini.
     *
     * @param nplayer the multiplayer info to set
     * 
     * @return the multiplayer info
     */
    protected transient @Getter @Setter NPlayer nplayer = null;

    /**
     * Source as defined by sourcefile property.
     *
     * @param source the source DAT reference to set
     * 
     * @return the source DAT reference
     */
    protected transient @Getter @Setter Source source = null;

    /**
     * SWList link reference with support status and filter option.
     */
    public @Data class SWList implements Serializable {
        /**
         * The name of the software list.
         *
         * @param name the name of the software list to set
         * 
         * @return the name of the software list
         */
        private String name;

        /**
         * The support status of the software list.
         *
         * @param status the status of the software list to set
         * 
         * @return the status of the software list
         */
        private SWStatus status;

        /**
         * The filter category tag.
         *
         * @param filter the filter string to set
         * 
         * @return the filter string
         */
        private String filter;

        /**
         * Constructs a new SWList instance with default values. This constructor does not perform any specific initialization and
         * can be used to create a basic software list reference for a machine. The fields of the SWList instance will need to be
         * set manually after using this constructor.
         */
        public SWList() {
            /* default constructor */ }
    }

    /**
     * Is this swlist is a compatible list or an original list of softwares for this computer/console.
     */
    public enum SWStatus {
        /**
         * Original software list natively intended for this hardware.
         */
        original, // NOSONAR
        /**
         * Compatible software list containing software runnable on this hardware.
         */
        compatible // NOSONAR
    }

    /**
     * The display orientation possibilities.
     */
    public enum DisplayOrientation {
        /**
         * Any display orientation (default).
         */
        any, // NOSONAR
        /**
         * Horizontal screen orientation.
         */
        horizontal, // NOSONAR
        /**
         * Vertical screen orientation.
         */
        vertical // NOSONAR
    }

    /**
     * The supported cabinet type.
     */
    public enum CabinetType {
        /**
         * Any cabinet type.
         */
        any, // NOSONAR
        /**
         * Upright cabinet layout.
         */
        upright, // NOSONAR
        /**
         * Cocktail table cabinet layout.
         */
        cocktail // NOSONAR
    }

    /**
     * Constructor for Machine.
     *
     * @param profile the associated profile database
     */
    public Machine(Profile profile) {
        super(profile);
    }

    /**
     * Retrieves the parent machine casted.
     *
     * @return the parent Machine
     */
    @Override
    public Machine getParent() {
        return getParent(Machine.class);
    }

    /**
     * The Serializable method for special serialization handling (in that case : initialize transient default values).
     *
     * @param in the serialization inputstream
     * 
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if class definition is missing
     */
    private void readObject(final java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initTransient();
    }

    /**
     * The method called to initialize transient and static fields.
     */
    @Override
    protected void initTransient() {
        super.initTransient();
        deviceMachines = new HashMap<>();
    }

    /**
     * Retrieves the machine base name.
     *
     * @return the machine name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Retrieves the machine full name.
     *
     * @return the machine name
     */
    @Override
    public String getFullName() {
        return name;
    }

    /**
     * Retrieves the machine full name under a given filename.
     *
     * @param filename the relative file name
     * 
     * @return the name representation
     */
    @Override
    public String getFullName(final String filename) {
        return filename;
    }

    /**
     * Indicates if this machine is a system BIOS.
     *
     * @return {@code true} if this machine is a system BIOS, {@code false} otherwise
     */
    @Override
    public boolean isBios() {
        return isbios;
    }

    /**
     * Indicates if this machine is a ROM share parent.
     *
     * @return {@code true} if it has a non-null romof field, {@code false} otherwise
     */
    @Override
    public boolean isRomOf() {
        return romof != null;
    }

    /**
     * Is this machine a machine with a software list?<br>
     * This is not 100% accurate since lot of unsupported but defined machines does not have yet software lists defined.
     *
     * @return {@code true} if it's a software machine
     */
    public boolean isSoftMachine() {
        return swlists.size() > 0 || (isClone() && getParent().swlists.size() > 0);
    }

    /**
     * Resolves and returns the structural hardware {@link Type} of this machine.
     *
     * @return the machine structural type
     */
    @Override
    public Type getType() {
        if (parent != null)
            return ((Machine) parent).getType();
        if (isbios)
            return Type.BIOS;
        if (ismechanical)
            return Type.MECHANICAL;
        if (isdevice)
            return Type.DEVICE;
        return Type.STANDARD;
    }

    /**
     * Retrieves the generic {@link Systm} category representing this machine's target group.
     *
     * @return the target Systm category
     */
    @Override
    public Systm getSystem() {
        switch (getType()) {
            case BIOS:
                if (parent != null)
                    return getParent().getSystem();
                return this;
            case DEVICE:
                return SystmDevice.DEVICE;
            case MECHANICAL:
                return SystmMechanical.MECHANICAL;
            case STANDARD:
            default:
                return SystmStandard.STANDARD;
        }
    }

    /**
     * Returns a string representation of this machine.
     *
     * @return string representing the machine type and description
     */
    @Override
    public String toString() {
        return "[" + getType() + "] " + description.toString(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Gets the machine compatibility level with a software list according its filter tag versus an optional software compatibility
     * value.
     *
     * @param softwarelist a software list name
     * @param compatibility the compatibility string (if any) declared for a software in the software list
     * 
     * @return higher is the returned int value, higher will be the level of compatibility
     */
    public int isCompatible(final String softwarelist, final String compatibility) {
        if (compatibility != null && new HashSet<>(Arrays.asList(StringUtils.split(compatibility, ','))).contains(swlists.get(softwarelist).filter)) // $NON-NLS-1$
            return swlists.get(softwarelist).status == SWStatus.original ? 20 : 10;
        return swlists.get(softwarelist).status == SWStatus.original ? 2 : 1;
    }

    /**
     * Export as dat.
     *
     * @param writer the {@link EnhancedXMLStreamWriter} used to write output file
     * @param is_mame is it mame (true) or logqix (false) format ?
     * @param modes the active export modes
     * 
     * @throws XMLStreamException if an XML stream error occurs
     */
    public void export(final EnhancedXMLStreamWriter writer, final boolean is_mame, final Set<ExportMode> modes) throws XMLStreamException {
        if (is_mame) {
            exportMame(writer, modes);
            return;
        }
        writer.writeStartElement("game", //$NON-NLS-1$
                new SimpleAttribute("name", name), //$NON-NLS-1$
                new SimpleAttribute("isbios", isbios ? "yes" : null), //$NON-NLS-1$ //$NON-NLS-2$
                new SimpleAttribute("cloneof", cloneof), //$NON-NLS-1$
                new SimpleAttribute("romof", romof), //$NON-NLS-1$
                new SimpleAttribute("sampleof", sampleof) //$NON-NLS-1$
        );
        writer.writeElement("description", description); //$NON-NLS-1$
        if (year != null && !year.isEmpty())
            writer.writeElement("year", year); //$NON-NLS-1$
        if (!manufacturer.isEmpty())
            writer.writeElement("manufacturer", manufacturer); //$NON-NLS-1$
        final var missing = modes.contains(ExportMode.MISSING);
        final var have = modes.contains(ExportMode.HAVE);
        final var all = modes.contains(ExportMode.ALL) || modes.contains(ExportMode.FILTERED);
        for (final var r : getRoms())
            if (shouldExport(r.getStatus(), all, missing, have))
                r.export(writer, is_mame);
        for (final Disk d : getDisks())
            if (shouldExport(d.getStatus(), all, missing, have))
                d.export(writer, is_mame);
        writer.writeEndElement();
    }

    /**
     * Decides whether an entity should be exported based on its status and the active export mode flags.
     *
     * @param status the entity status
     * @param all export all entities regardless of status
     * @param missing export only entities with {@link EntityStatus#KO}
     * @param have export only entities with {@link EntityStatus#OK}
     * 
     * @return {@code true} if the entity matches the requested mode
     */
    private boolean shouldExport(final EntityStatus status, final boolean all, final boolean missing, final boolean have) {
        return all || (missing && status == EntityStatus.KO) || (have && status == EntityStatus.OK);
    }

    /**
     * Internal helper to write MAME compatible elements.
     *
     * @param writer the XML writer
     * @param modes the export modes
     * 
     * @throws XMLStreamException if an error occurs
     */
    private void exportMame(final EnhancedXMLStreamWriter writer, final Set<ExportMode> modes) throws XMLStreamException {
        writer.writeStartElement("machine", //$NON-NLS-1$
                new SimpleAttribute("name", name), //$NON-NLS-1$
                new SimpleAttribute("isbios", isbios ? "yes" : null), //$NON-NLS-1$ //$NON-NLS-2$
                new SimpleAttribute("isdevice", isdevice ? "yes" : null), //$NON-NLS-1$ //$NON-NLS-2$
                new SimpleAttribute("ismechanical", ismechanical ? "yes" : null), //$NON-NLS-1$ //$NON-NLS-2$
                new SimpleAttribute("cloneof", cloneof), //$NON-NLS-1$
                new SimpleAttribute("romof", romof), //$NON-NLS-1$
                new SimpleAttribute("sampleof", sampleof) //$NON-NLS-1$
        );
        writer.writeElement("description", description); //$NON-NLS-1$
        if (year != null && !year.isEmpty())
            writer.writeElement("year", year); //$NON-NLS-1$
        if (!manufacturer.isEmpty())
            writer.writeElement("manufacturer", manufacturer); //$NON-NLS-1$
        final var all = modes.contains(ExportMode.ALL) || modes.contains(ExportMode.FILTERED);
        final var missing = modes.contains(ExportMode.MISSING);
        final var have = modes.contains(ExportMode.HAVE);
        for (final Rom r : getRoms())
            if (shouldExport(r.getStatus(), all, missing, have))
                r.export(writer, true);
        for (final Disk d : getDisks())
            if (shouldExport(d.getStatus(), all, missing, have))
                d.export(writer, true);
        exportSoftwareLists(writer);
        exportDriver(writer);
        writer.writeEndElement();
    }

    /**
     * Writes the {@code softwarelist} elements for this machine.
     *
     * @param writer the XML writer
     * 
     * @throws XMLStreamException if an error occurs
     */
    private void exportSoftwareLists(final EnhancedXMLStreamWriter writer) throws XMLStreamException {
        for (final SWList swlist : swlists.values()) {
            writer.writeElement("softwarelist", //$NON-NLS-1$
                    new SimpleAttribute("name", swlist.name), //$NON-NLS-1$
                    new SimpleAttribute("status", swlist.status), //$NON-NLS-1$
                    new SimpleAttribute("filter", swlist.filter) //$NON-NLS-1$
            );
        }
    }

    /**
     * Writes the {@code driver} element for this machine.
     *
     * @param writer the XML writer
     * 
     * @throws XMLStreamException if an error occurs
     */
    private void exportDriver(final EnhancedXMLStreamWriter writer) throws XMLStreamException {
        writer.writeElement("driver", //$NON-NLS-1$
                new SimpleAttribute("status", driver.getStatus()), //$NON-NLS-1$
                new SimpleAttribute("emulation", driver.getEmulation()), //$NON-NLS-1$
                new SimpleAttribute("cocktail", driver.getCocktail()), //$NON-NLS-1$
                new SimpleAttribute("savestate", driver.getSaveState()) //$NON-NLS-1$
        );
    }

    /**
     * Indicates whether some other object is "equal to" this machine.
     *
     * @param obj the reference object to compare with
     * 
     * @return {@code true} if machines are identical by name, {@code false} otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Machine m)
            return this.name.equals(m.name);
        return super.equals(obj);
    }

    /**
     * Returns a hash code value for this machine.
     *
     * @return the name-based hash code
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * Retrieves the text description of the machine.
     *
     * @return the description char sequence
     */
    @Override
    public CharSequence getDescription() {
        return description;
    }

    /**
     * Build the list of associated machine devices recursively.
     *
     * @param machines the set of machine to fill up with
     * @param excludeBios exclude any bios devices
     * @param partial exclude devices from slots that are not defined in device_ref
     * @param recurse also get devices of devices and so on
     */
    protected void getDevices(HashSet<Machine> machines, boolean excludeBios, boolean partial, boolean recurse) {
        if (!machines.contains(this)) {
            machines.add(this);
            if (!isBios() || !excludeBios) {
                getDevices(partial).forEach(m -> {
                    if (!recurse)
                        machines.add(m);
                    else
                        m.getDevices(machines, excludeBios, partial, recurse);
                });
            }
        }
    }

    /**
     * Stream associated machine devices.
     *
     * @param partial exclude machines devices from slots that are not defined in device_ref
     * 
     * @return a {@link Stream}&lt;{@link Machine}&gt;
     */
    private Stream<Machine> getDevices(boolean partial) {
        if (partial)
            return deviceMachines.values().stream().filter(device -> deviceRef.contains(device.name));
        return deviceMachines.values().stream();
    }

    /**
     * Streams ROMs associated with this machine, including devices recursively.
     *
     * @param excludeBios exclude BIOS rom files
     * @param partial exclude rom files from optional devices slots not defined in device_ref
     * @param recurse search devices recursively
     * 
     * @return a stream of associated ROMs
     */
    @Override
    protected Stream<Rom> streamWithDevices(boolean excludeBios, boolean partial, boolean recurse) {
        HashSet<Machine> machines = new HashSet<>();
        getDevices(machines, excludeBios, partial, recurse);
        return machines.stream().flatMap(m -> m.getRoms().stream());
    }

    /**
     * Get the selection state in profile properties according {@link #getName()}.
     *
     * @return true if selected
     */
    public boolean isSelected() {
        return profile.getProperty("filter.machine." + getName(), true);
    }

    /**
     * Set the selection state in profile properties according {@link #getName()}.
     *
     * @param selected the selection state to set
     */
    public void setSelected(final boolean selected) {
        profile.setProperty("filter.machine." + getName(), selected);
    }
}
