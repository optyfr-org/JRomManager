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
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

import jrm.aui.progress.ProgressHandler;
import jrm.aui.status.StatusRendererFactory;
import jrm.locale.Messages;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.misc.ProfileSettings;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.data.AnywareStatus;
import jrm.profile.data.EntityStatus;
import jrm.profile.data.MachineListList;
import jrm.profile.data.SoftwareList;
import jrm.profile.data.Sources;
import jrm.profile.data.Systms;
import jrm.profile.filter.CatVer;
import jrm.profile.filter.NPlayers;
import jrm.profile.manager.ProfileNFO;
import jrm.security.PathAbstractor;
import jrm.security.Session;
import jrm.xml.XMLTools;
import lombok.Getter;
import lombok.Setter;

/**
 * Parses and models retro system profile databases from DAT catalogs. Loads dat files, matches system constraints, serializes
 * resulting profiles for caching, and configures global filter structures (systems, release years, catver.ini, nplayers.ini).
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public class Profile implements Serializable, StatusRendererFactory {
    private static final long serialVersionUID = 3L;

    static final String DESCRIPTION = "description";
    static final String VERSION = "version";

    /**
     * Scanned machines count.
     * 
     * @return count of processed machines
     */
     @Getter long machinesCnt = 0;
    /**
     * Scanned software lists count.
     * 
     * @return count of software lists
     */
     @Getter long softwaresListCnt = 0;
    /**
     * Scanned software entries count.
     * 
     * @return count of software entries
     */
     @Getter long softwaresCnt = 0;
    /**
     * Scanned ROM files count.
     * 
     * @return count of ROM files
     */
     @Getter long romsCnt = 0;
    /**
     * Scanned software ROM files count.
     * 
     * @return count of software ROM files
     */
     @Getter long swromsCnt = 0;
    /**
     * Scanned disk files count.
     * 
     * @return count of disk files
     */
     @Getter long disksCnt = 0;
    /**
     * Scanned software disk files count.
     * 
     * @return count of software disk files
     */
     @Getter long swdisksCnt = 0;
    /**
     * Scanned sound sample files count.
     * 
     * @return count of processed sample files
     */
     @Getter long samplesCnt = 0;

    /**
     * Whether MD5 checksum values are declared on ROM elements in the profile.
     * 
     * @return true if MD5 values are present on ROMs
     */
     @Getter boolean md5Roms = false;
    /**
     * Whether MD5 checksum values are declared on CHD elements in the profile.
     * 
     * @return true if MD5 values are present on CHDs
     */
     @Getter boolean md5Disks = false;
    /**
     * Whether SHA-1 checksum values are declared on ROM elements in the profile.
     * 
     * @return true if SHA-1 values are present on ROMs
     */
     @Getter boolean sha1Roms = false;
    /**
     * Whether SHA-1 checksum values are declared on CHD elements in the profile.
     * 
     * @return true if SHA-1 values are present on CHDs
     */
     @Getter boolean sha1Disks = false;

    /**
     * Build timestamp or identifier string.
     * 
     * @return the build version string
     */
     @Getter String build = null;
    /**
     * Custom DAT properties read from the XML header elements block.
     * 
     * @return map containing XML header elements key-value associations
     */
     final @Getter Map<String, StringBuilder> header = new HashMap<>();

    /**
     * Global collection grouping parsed machines, computer clones, and associated software catalogs.
     * 
     * @return the unified target machines listing representation
     */
     final @Getter MachineListList machineListList = new MachineListList(this);

    /**
     * Set storing ROM CRCs which resolve to distinct SHA1/MD5 signatures.
     * 
     * @return suspicious CRC checksum values set
     */
     final @Getter Set<String> suspiciousCRC = new HashSet<>();

    /**
     * Dynamic anyware lists visibility status filter settings.
     * 
     * @param filterListLists anyware list visibility filters
     * 
     * @return visibility filters set
     */
    transient @Getter @Setter Set<AnywareStatus> filterListLists = null;

    /**
     * Dynamic single machine anyware visibility status filter settings.
     * 
     * @param filterList single anyware item filters
     * 
     * @return visibility filters set
     */
    transient @Getter @Setter Set<AnywareStatus> filterList = null;

    /**
     * Dynamic physical entities visibility status filter settings.
     * 
     * @param filterEntities physical item visibility filters
     * 
     * @return visibility filters set
     */
    transient @Getter @Setter Set<EntityStatus> filterEntities = null;

    /**
     * Local profiles Settings parameters.
     * 
     * @return profiles settings container
     */
    private transient @Getter ProfileSettings settings = null;
    /**
     * Categorized and grouped system boundaries filter.
     * 
     * @return standard and custom systems filters
     */
    transient @Getter Systms systems = null;
    /**
     * Dynamic years list collected from scanned elements.
     * 
     * @return sorted collection of years
     */
    transient @Getter Collection<String> years = null;
    /**
     * JRomManager database profile information stats summary.
     * 
     * @return profile NFO summary reference
     */
    transient @Getter ProfileNFO nfo = null;
    /**
     * Parsed categories configuration mapping.
     * 
     * @param catver parsed category ruleset mapping
     * 
     * @return categories config mapping
     */
    transient @Getter @Setter CatVer catver = null;
    /**
     * Parsed multiplayer specifications configuration mapping.
     * 
     * @param nplayers parsed multiplayer capabilities mapping
     * 
     * @return multiplayer config mapping
     */
    transient @Getter @Setter NPlayers nplayers = null;
    /**
     * Active execution context workspace session.
     * 
     * @return active workspace session
     */
    transient @Getter Session session = null;
    /**
     * Parsed metadata DAT catalogs specifications tracking metrics.
     * 
     * @return standard dat definitions tracking metrics
     */
    transient @Getter Sources sources = null;

    private transient ProfileProperties properties;

    /**
     * Protected zero-argument constructor initializing an empty profile.
     */
    Profile() {
        properties = new ProfileProperties(this);
    }

    /**
     * Reinitializes transient list/entity state after Fory deserialization.
     */
    public void afterLoad() {
        machineListList.afterLoad();
        if (properties == null) properties = new ProfileProperties(this);
    }

    /**
     * Private internal load parser orchestration.
     * 
     * @param file the source xml catalog dat
     * @param handler progressive feedback reporter
     * 
     * @return true on success, false on errors or cancellations
     */
    boolean internalLoad(final File file, final ProgressHandler handler) {
        handler.setProgress(String.format(Messages.getString("Profile.Parsing"), new PathAbstractor(session).getRelativePath(file.toPath())), -1); //$NON-NLS-1$
        try (var in = handler.getInputStream(new FileInputStream(file), (int) file.length())) {
            XMLTools.getSaxParser().parse(in, new ProfileHandler(this, handler));
            return true;
        } catch (final ParserConfigurationException | SAXException e) {
            handler.addError(e.getMessage());
            Log.err("Parser Exception", e); //$NON-NLS-1$
        } catch (final IOException e) {
            handler.addError(e.getMessage());
            Log.err("IO Exception", e); //$NON-NLS-1$
        } catch (final BreakException _) {
            return false;
        } catch (final Exception e) {
            handler.addError(e.getMessage());
            Log.err("Other Exception", e); //$NON-NLS-1$
        }
        return false;
    }

    /**
     * Serializes current profile state properties to cached binary files.
     */
    public void save() {
        ProfileLoader.save(this);
    }

    /**
     * Loads profile database configurations from physical file descriptors.
     */
    public static Profile load(final Session session, final File file, final ProgressHandler handler) {
        return ProfileLoader.load(session, file, handler);
    }

    /**
     * Loads profile properties matching cached descriptors or walk parsers.
     */
    public static Profile load(final Session session, final ProfileNFO nfo, final ProgressHandler handler) {
        return ProfileLoader.load(session, nfo, handler);
    }

    /**
     * Maps parent-clones database relationships sequentially after loading metadata catalog elements.
     */
    void buildParentClonesRelations() {
        ProfileLoader.buildParentClonesRelations(this);
    }

    /**
     * Saves profile XML settings files.
     */
    public void saveSettings() {
        saveSettings(nfo.getFile());
    }

    /**
     * Saves profile XML settings files to custom locations.
     * 
     * @param file destination configurations XML path
     */
    public void saveSettings(File file) {
        settings = session.getUser().getSettings().saveProfileSettings(file, settings);
        nfo.save(session);
    }

    /**
     * Loads profiles XML settings properties.
     */
    public void loadSettings() {
        loadSettings(nfo.getFile());
    }

    /**
     * Loads profiles XML settings properties from specific files.
     * 
     * @param file source configuration xml file
     */
    public void loadSettings(File file) {
        settings = session.getUser().getSettings().loadProfileSettings(file, settings);
    }

    /**
     * Updates Boolean property associations in local profiles configuration maps.
     * 
     * @param property target configuration option key
     * @param value target option state value
     */
    public void setProperty(final ProfileSettingsEnum property, final boolean value) {
        properties.setProperty(property, value);
    }

    /**
     * Updates string-mapped Boolean property associations.
     * 
     * @param property target option key
     * @param value target option state value
     */
    public void setProperty(final String property, final boolean value) {
        properties.setProperty(property, value);
    }

    /**
     * Updates string property associations.
     * 
     * @param property target configuration option key
     * @param value target option text value
     */
    public void setProperty(final ProfileSettingsEnum property, final String value) {
        properties.setProperty(property, value);
    }

    /**
     * Updates string property associations using text keys.
     * 
     * @param property target option text key
     * @param value target option text value
     */
    public void setProperty(final String property, final String value) {
        properties.setProperty(property, value);
    }

    /**
     * Resolves Boolean settings values or returns defaults if keys are missing.
     * 
     * @param property target option text key
     * @param def the default option state value
     * 
     * @return option state value
     */
    public boolean getProperty(final String property, final boolean def) {
        return properties.getProperty(property, def);
    }

    /**
     * Resolves integer settings values.
     * 
     * @param property target option text key
     * @param def the default integer option value
     * 
     * @return option value
     */
    public int getProperty(final String property, final int def) {
        return properties.getProperty(property, def);
    }

    /**
     * Resolves text settings values.
     * 
     * @param property target option key
     * @param def the default text value
     * 
     * @return option value
     */
    public String getProperty(final String property, final String def) {
        return properties.getProperty(property, def);
    }

    /**
     * Resolves settings values matching expected output classes.
     * 
     * @param property target option key
     * @param cls expected output class type
     * @param <T> class template argument
     * 
     * @return option value
     */
    public <T> T getProperty(final ProfileSettingsEnum property, Class<T> cls) {
        return properties.getProperty(property, cls);
    }

    /**
     * Resolves settings string values.
     * 
     * @param property target option key
     * 
     * @return option text value
     */
    public String getProperty(final ProfileSettingsEnum property) {
        return properties.getProperty(property);
    }

    /**
     * Saves properties checkpoints hash codes to track modifications state.
     */
    public void setPropsCheckPoint() {
        properties.setPropsCheckPoint();
    }

    /**
     * Compares active properties configurations against saved checkpoints.
     * 
     * @return true if properties changed, false otherwise
     */
    public boolean hasPropsChanged() {
        return properties.hasPropsChanged();
    }

    /**
     * Generates detailed HTML formatted text representation of profile catalogs counts.
     * 
     * @return HTML format text summary
     */
    public String getName() {
        return new ProfileNameFormatter(this).getName();
    }

    /**
     * Populates standard, mechanical, bios, or software lists categories inside System filter sets.
     */
    public void loadSystems() {
        ProfileFilters.loadSystems(this);
    }

    /**
     * Compiles distinct release years across parsed machines or software catalogs.
     */
    public void loadYears() {
        ProfileFilters.loadYears(this);
    }

    /**
     * Loads catver.ini mappings if files are found, linking machines back to subcategories.
     * 
     * @param handler progress reporting monitor
     */
    public void loadCatVer(ProgressHandler handler) {
        ProfileFilters.loadCatVer(this, handler);
    }

    /**
     * Loads nplayers.ini capability configurations if files are present.
     * 
     * @param handler progress reporting monitor
     */
    public void loadNPlayers(ProgressHandler handler) {
        ProfileFilters.loadNPlayers(this, handler);
    }

    /**
     * Computes the cumulative size across all parsed software lists and target machines.
     * 
     * @return cumulative size count
     */
    public int size() {
        return machineListList.size() + machineListList.getSoftwareListList().size();
    }

    /**
     * Computes cumulative size count across filtered visible machine items and software catalogs.
     * 
     * @return filtered visibility size count
     */
    public int filteredSubsize() {
        return (int) machineListList.get(0).getFilteredStream().count() + machineListList.get(0).samplesets.size()
                + (int) machineListList.getSoftwareListList().getFilteredStream().mapToLong(sl -> sl.getFilteredStream().count()).sum();
    }

    /**
     * Computes cumulative size count across all raw machine elements and software lists.
     * 
     * @return raw cumulative entries count
     */
    public int subsize() {
        return machineListList.get(0).size() + machineListList.get(0).samplesets.size() + machineListList.getSoftwareListList().stream().mapToInt(SoftwareList::size).sum();
    }
}
