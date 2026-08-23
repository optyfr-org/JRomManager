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
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

import jrm.aui.progress.ProgressHandler;
import jrm.aui.status.StatusRendererFactory;
import jrm.locale.Messages;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.misc.ProfileSettings;
import jrm.misc.ProfileSettingsEnum;
import jrm.misc.SettingsEnum;
import jrm.profile.data.AnywareStatus;
import jrm.profile.data.EntityStatus;
import jrm.profile.data.Machine;
import jrm.profile.data.MachineListList;
import jrm.profile.data.SoftwareList;
import jrm.profile.data.Source;
import jrm.profile.data.Sources;
import jrm.profile.data.SystmDevice;
import jrm.profile.data.SystmMechanical;
import jrm.profile.data.SystmStandard;
import jrm.profile.data.Systms;
import jrm.profile.filter.CatVer;
import jrm.profile.filter.CatVer.Category;
import jrm.profile.filter.CatVer.Category.SubCategory;
import jrm.profile.filter.NPlayer;
import jrm.profile.filter.NPlayers;
import jrm.profile.manager.ProfileNFO;
import jrm.security.PathAbstractor;
import jrm.security.Session;
import jrm.security.SignedObjectStore;
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
    private transient @Getter @Setter Set<AnywareStatus> filterListLists = null;

    /**
     * Dynamic single machine anyware visibility status filter settings.
     * 
     * @param filterList single anyware item filters
     * 
     * @return visibility filters set
     */
    private transient @Getter @Setter Set<AnywareStatus> filterList = null;

    /**
     * Dynamic physical entities visibility status filter settings.
     * 
     * @param filterEntities physical item visibility filters
     * 
     * @return visibility filters set
     */
    private transient @Getter @Setter Set<EntityStatus> filterEntities = null;

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
    private transient @Getter Systms systems = null;
    /**
     * Dynamic years list collected from scanned elements.
     * 
     * @return sorted collection of years
     */
    private transient @Getter Collection<String> years = null;
    /**
     * JRomManager database profile information stats summary.
     * 
     * @return profile NFO summary reference
     */
    private transient @Getter ProfileNFO nfo = null;
    /**
     * Parsed categories configuration mapping.
     * 
     * @param catver parsed category ruleset mapping
     * 
     * @return categories config mapping
     */
    private transient @Getter @Setter CatVer catver = null;
    /**
     * Parsed multiplayer specifications configuration mapping.
     * 
     * @param nplayers parsed multiplayer capabilities mapping
     * 
     * @return multiplayer config mapping
     */
    private transient @Getter @Setter NPlayers nplayers = null;
    /**
     * Active execution context workspace session.
     * 
     * @return active workspace session
     */
    private transient @Getter Session session = null;
    /**
     * Parsed metadata DAT catalogs specifications tracking metrics.
     * 
     * @return standard dat definitions tracking metrics
     */
    private transient @Getter Sources sources = null;

    /**
     * Protected zero-argument constructor initializing an empty profile.
     */
    private Profile() {

    }

    /**
     * Reinitializes transient list/entity state after Fory deserialization.
     */
    public void afterLoad() {
        machineListList.afterLoad();
    }

    /**
     * Private internal load parser orchestration.
     * 
     * @param file the source xml catalog dat
     * @param handler progressive feedback reporter
     * 
     * @return true on success, false on errors or cancellations
     */
    private boolean internalLoad(final File file, final ProgressHandler handler) {
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
        try {
            SignedObjectStore.write(session, session.getUser().getSettings().getCacheFile(nfo.getFile()), this, SignedObjectStore.Codec.CACHE);
        } catch (final Exception _) {
            // do nothing
        }
    }

    /**
     * Loads profile database configurations from physical file descriptors.
     * 
     * @param session execution workspace context
     * @param file target source config catalog file (.jrm, .dat, .xml)
     * @param handler progressive feedback reporter
     * 
     * @return parsed profile metadata container
     */
    public static Profile load(final Session session, final File file, final ProgressHandler handler) {
        return Profile.load(session, ProfileNFO.load(session, file), handler);
    }

    /**
     * Loads profile properties matching cached descriptors or walk parsers.
     * 
     * @param session execution workspace context
     * @param nfo JRomManager database profile information stats summary
     * @param handler progressive feedback reporter
     * 
     * @return parsed profile metadata container
     */
    public static Profile load(final Session session, final ProfileNFO nfo, final ProgressHandler handler) {
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
     * 
     * @param session execution workspace context
     * @param nfo JRomManager database profile information stats summary
     * @param handler progressive feedback reporter
     * 
     * @return loaded profile or null if loading failed
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
     * 
     * @param cachefile cached profile file
     * @param nfo JRomManager database profile information stats summary
     * @param session execution workspace context
     * 
     * @return true if cache should be used, false otherwise
     */
    private static boolean shouldLoadFromCache(final File cachefile, final ProfileNFO nfo, final Session session) {
        return cachefile.lastModified() >= nfo.getFile().lastModified()
            && (!nfo.isJRM() || cachefile.lastModified() >= nfo.getMame().getFileroms().lastModified())
            && Boolean.TRUE.equals(!session.getUser().getSettings().getProperty(SettingsEnum.debug_nocache, Boolean.class)); // $NON-NLS-1$
    }

    /**
     * Initializes loaded profile by building relationships, updating statistics, and loading components.
     * 
     * @param profile profile to initialize
     * @param handler progressive feedback reporter
     */
    private static void initializeProfile(final Profile profile, final ProgressHandler handler) {
        handler.setProgress(Messages.getString("Profile.BuildingParentClonesRelations"), -1); //$NON-NLS-1$
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
     * 
     * @param profile profile containing statistics to update
     */
    private static void updateNfoStats(final Profile profile) {
        final var stats = profile.nfo.getStats();
        final String version;
        if (profile.build != null) {
            version = profile.build;
        } else if (profile.header.containsKey(VERSION)) {
            version = profile.header.get(VERSION).toString();
        } else {
            version = null;
        }
        stats.setVersion(version); // $NON-NLS-1$
        stats.setTotalSets(profile.softwaresCnt + profile.machinesCnt);
        stats.setTotalRoms(profile.romsCnt + profile.swromsCnt);
        stats.setTotalDisks(profile.disksCnt + profile.swdisksCnt);
    }

    /**
     * Loads all profile components including settings, filters, and configuration files.
     * 
     * @param profile profile to load components for
     * @param handler progressive feedback reporter
     */
    private static void loadProfileComponents(final Profile profile, final ProgressHandler handler) {
        handler.setProgress("Loading settings...", -1); //$NON-NLS-1$
        profile.loadSettings();
        
        handler.setProgress("Creating Systems filters...", -1); //$NON-NLS-1$
        profile.loadSystems();
        
        handler.setProgress("Creating Years filters...", -1); //$NON-NLS-1$
        profile.loadYears();
        
        profile.loadCatVer(handler);
        profile.loadNPlayers(handler);
    }

    /**
     * Parses profile catalog DAT file content and serializes binary database states.
     * 
     * @param session active session context
     * @param nfo catalogs info summary
     * @param handler progressive feedback reporter
     * 
     * @return parsed profile metadata database
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
        handler.setProgress(Messages.getString("Profile.SavingCache"), -1); //$NON-NLS-1$
        profile.save();
        return profile;
    }

    /**
     * Triggers XML parsing on single dat files or paired ROMs + SoftwareLists.
     * 
     * @param nfo catalogs details stats
     * @param profile parent target empty database
     * @param handler progressive feedback reporter
     * 
     * @return true on success, false on failure or cancellation
     */
    private static boolean load(final ProfileNFO nfo, Profile profile, final ProgressHandler handler) {
        if (!nfo.isJRM()) // load DAT file not attached to a JRM
            return (nfo.getFile().exists() && profile.internalLoad(nfo.getFile(), handler));

        // we use JRM file keep ROMs/SL DATs in relation
        if (nfo.getMame().getFileroms() != null) { // load ROMs dat
            if (!nfo.getMame().getFileroms().exists() || !profile.internalLoad(nfo.getMame().getFileroms(), handler))
                return false;
            if (nfo.getMame().getFilesl() != null && (!nfo.getMame().getFilesl().exists() || !profile.internalLoad(nfo.getMame().getFilesl(), handler))) {
                // load SL dat (note that loading software list without ROMs dat is NOT
                // recommended)
                return false;
            }
        }
        return true;
    }

    /**
     * Retrieves profile database state properties from standard cache files.
     * 
     * @param session active session context
     * @param nfo catalogs info stats
     * @param handler progressive reporter
     * @param profile target empty profile object
     * @param cachefile target cache binary file
     * 
     * @return loaded profile, or null on cache mismatches
     */
    private static Profile loadCache(final Session session, final ProfileNFO nfo, final ProgressHandler handler, Profile profile, final File cachefile) {
        handler.setInfos(1, null);
        handler.setProgress(Messages.getString("Profile.LoadingCache"), -1); //$NON-NLS-1$
        try (final var in = handler.getInputStream(new java.io.FileInputStream(cachefile), (int) cachefile.length())) {
            profile = (Profile) SignedObjectStore.read(session, in, (int) cachefile.length(), SignedObjectStore.Codec.CACHE);
            profile.session = session;
            profile.nfo = nfo;
        } catch (final Exception e) {
            // may fail to load because serialized classes did change since last cache save
            // or if deserialization filter rejected untrusted classes
            Log.debug(() -> "Failed to load cache file: " + e.getMessage());
        }
        return profile;
    }

    /**
     * Maps parent-clones database relationships sequentially after loading metadata catalog elements.
     */
    private void buildParentClonesRelations() {
        machineListList.forEach(machineList -> machineList.forEach(machine -> {
            if (machine.getRomof() != null) {
                machine.setParent(machineList.getByName(machine.getRomof()));
                if (machine.getParent() != null && !machine.getParent().isIsbios())
                    machine.getParent().getClones().put(machine.getName(), machine);
            }
            machine.getDeviceRef().forEach(deviceRef -> machine.getDeviceMachines().putIfAbsent(deviceRef, machineList.getByName(deviceRef)));
            machine.getSlots().values()
                    .forEach(slot -> slot.forEach(slotoption -> machine.getDeviceMachines().putIfAbsent(slotoption.getDevName(), machineList.getByName(slotoption.getDevName()))));
        }));
        machineListList.getSoftwareListList().forEach(softwareList -> softwareList.forEach(software -> {
            if (software.getCloneof() != null) {
                software.setParent(softwareList.getByName(software.getCloneof()));
                if (software.getParent() != null)
                    software.getParent().getClones().put(software.getName(), software);
            }
        }));
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
        Log.info(() -> "%s : %b".formatted(property, value));
        settings.setProperty(property, Boolean.toString(value));
    }

    /**
     * Updates string-mapped Boolean property associations.
     * 
     * @param property target option key
     * @param value target option state value
     */
    public void setProperty(final String property, final boolean value) {
        Log.info(() -> "%s : %b".formatted(property, value));
        settings.setProperty(property, Boolean.toString(value));
    }

    /**
     * Updates string property associations.
     * 
     * @param property target configuration option key
     * @param value target option text value
     */
    public void setProperty(final ProfileSettingsEnum property, final String value) {
        settings.setProperty(property, value);
    }

    /**
     * Updates string property associations using text keys.
     * 
     * @param property target option text key
     * @param value target option text value
     */
    public void setProperty(final String property, final String value) {
        settings.setProperty(property, value);
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
        return Boolean.parseBoolean(settings.getProperty(property, Boolean.toString(def)));
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
        return Integer.parseInt(settings.getProperty(property, Integer.toString(def)));
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
        return settings.getProperty(property, def);
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
        return settings.getProperty(property, cls);
    }

    /**
     * Resolves settings string values.
     * 
     * @param property target option key
     * 
     * @return option text value
     */
    public String getProperty(final ProfileSettingsEnum property) {
        return settings.getProperty(property, String.class);
    }

    /**
     * Cached hash code of the profile settings used to detect modifications.
     */
    private int propsHashCode = 0;

    /**
     * Saves properties checkpoints hash codes to track modifications state.
     */
    public void setPropsCheckPoint() {
        propsHashCode = settings.hashCode();
    }

    /**
     * Compares active properties configurations against saved checkpoints.
     * 
     * @return true if properties changed, false otherwise
     */
    public boolean hasPropsChanged() {
        return propsHashCode != settings.hashCode();
    }

    /**
     * Generates detailed HTML formatted text representation of profile catalogs counts.
     * 
     * @return HTML format text summary
     */
    public String getName() {
        final var xmlpath = session.getUser().getSettings().getWorkPath().resolve("xmlfiles").toAbsolutePath().normalize();
        final var fname = nfo.getFile().toPath().startsWith(xmlpath)
            ? xmlpath.relativize(nfo.getFile().toPath()).toString()
            : nfo.getFile().getName();
        final var nameBuilder = new StringBuilder("[")
            .append(toBlue(fname))
            .append("] "); //$NON-NLS-1$ //$NON-NLS-2$
        if (build != null) {
            nameBuilder.append(toBoldBlack(build)); // $NON-NLS-1$
        } else if (!header.isEmpty()) {
            if (header.containsKey(DESCRIPTION)) { // $NON-NLS-1$
                nameBuilder.append(toBoldBlack(header.get(DESCRIPTION))); // $NON-NLS-1$
            } else if (header.containsKey("name")) { //$NON-NLS-1$
                nameBuilder.append(toBoldBlack(header.get("name"))); //$NON-NLS-1$
                if (header.containsKey(VERSION)) // $NON-NLS-1$
                    nameBuilder.append(" (").append(escape(header.get(VERSION))).append(")"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
        final var strcntBuilder = new StringBuilder();
        if (!machineListList.get(0).isEmpty())
            strcntBuilder.append(machinesCnt).append(" Machines"); //$NON-NLS-1$
        if (!machineListList.getSoftwareListList().isEmpty()) {
            if (!strcntBuilder.isEmpty())
                strcntBuilder.append(", "); //$NON-NLS-1$
            strcntBuilder.append(softwaresListCnt).append(" Software Lists, ") //$NON-NLS-1$
                .append(softwaresCnt).append(" Softwares"); //$NON-NLS-1$
        }
        nameBuilder.append("(").append(strcntBuilder).append(")"); //$NON-NLS-1$ //$NON-NLS-2$
        return toDocument(nameBuilder.toString());
    }

    /**
     * Populates standard, mechanical, bios, or software lists categories inside System filter sets.
     */
    public void loadSystems() {
        systems = new Systms();
        systems.add(SystmStandard.STANDARD);
        systems.add(SystmMechanical.MECHANICAL);
        systems.add(SystmDevice.DEVICE);
        final ArrayList<Machine> machines = new ArrayList<>();
        this.sources = new Sources();
        final var srces = new TreeMap<String, Source>();
        machineListList.get(0).forEach(m -> {
            if (m.isIsbios())
                machines.add(m);
            Optional.ofNullable(m.getSourcefile()).ifPresent(s -> srces.compute(s, (k, v) -> v == null ? new Source(k) : v.inc()));
        });
        machines.sort((a, b) -> a.getName().compareTo(b.getName()));
        machines.forEach(systems::add);
        srces.forEach((_, src) -> sources.add(src));
        machineListList.get(0).stream().filter(m -> m.getSourcefile() != null).forEach(m -> m.setSource(srces.get(m.getSourcefile())));

        final ArrayList<SoftwareList> softwarelists = new ArrayList<>();
        machineListList.getSoftwareListList().forEach(softwarelists::add);
        softwarelists.sort((a, b) -> a.getName().compareTo(b.getName()));
        softwarelists.forEach(systems::add);
    }

    /**
     * Compiles distinct release years across parsed machines or software catalogs.
     */
    public void loadYears() {
        final var y = new HashSet<String>();
        y.add(""); //$NON-NLS-1$
        machineListList.get(0).forEach(m -> y.add(m.year.toString()));
        machineListList.getSoftwareListList().forEach(sl -> sl.forEach(s -> y.add(s.year.toString())));
        y.add("????"); //$NON-NLS-1$
        this.years = y;
    }

    /**
     * Loads catver.ini mappings if files are found, linking machines back to subcategories.
     * 
     * @param handler progress reporting monitor
     */
    public void loadCatVer(ProgressHandler handler) {
        try {
            final var file = PathAbstractor.getAbsolutePath(session, getProperty(ProfileSettingsEnum.filter_catver_ini, String.class)).toFile();
            if (!file.exists()) {
                catver = null;
                return;
            }
            if (handler != null)
                handler.setProgress("Loading catver.ini ...", -1); //$NON-NLS-1$
            catver = CatVer.read(this, file); // $NON-NLS-1$
            for (final Category cat : catver) {
                for (final SubCategory subcat : cat) {
                    for (final String game : subcat) {
                        final Machine m = machineListList.get(0).getByName(game);
                        if (m != null)
                            m.setSubcat(subcat);
                    }
                }
            }
        } catch (final Exception _) {
            catver = null;
        }
    }

    /**
     * Loads nplayers.ini capability configurations if files are present.
     * 
     * @param handler progress reporting monitor
     */
    public void loadNPlayers(ProgressHandler handler) {
        try {
            final var file = PathAbstractor.getAbsolutePath(session, getProperty(ProfileSettingsEnum.filter_nplayers_ini, String.class)).toFile();
            if (file.exists()) {
                if (handler != null)
                    handler.setProgress("Loading nplayers.ini ...", -1); //$NON-NLS-1$
                nplayers = NPlayers.read(file); // $NON-NLS-1$
                for (final NPlayer nplayer : nplayers) {
                    for (final String game : nplayer) {
                        final Machine m = machineListList.get(0).getByName(game);
                        if (m != null)
                            m.setNplayer(nplayer);
                    }
                }
            } else
                nplayers = null;
        } catch (final Exception _) {
            nplayers = null;
        }
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
