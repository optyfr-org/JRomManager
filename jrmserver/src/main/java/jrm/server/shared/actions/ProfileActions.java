package jrm.server.shared.actions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import org.apache.commons.io.FileUtils;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonObject.Member;
import com.eclipsesource.json.JsonValue;

import jrm.misc.BreakException;
import jrm.misc.FindCmd;
import jrm.misc.Log;
import jrm.misc.ProfileSettings;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.fix.Fix;
import jrm.profile.manager.Import;
import jrm.profile.manager.ProfileNFO;
import jrm.profile.scan.Scan;
import jrm.profile.scan.ScanException;
import jrm.profile.scan.options.ScanAutomation;
import jrm.security.PathAbstractor;
import jrm.server.shared.WebSession;
import jrm.server.shared.Worker;
import lombok.val;

/**
 * Action handler for profile management operations in the ROM manager web server.
 * <p>
 * This class processes incoming JSON action commands related to ROM profile lifecycle management, including importing profiles from
 * MAME, loading existing profiles, scanning ROM collections against profile definitions, fixing ROM organization issues, and
 * managing profile settings.
 * </p>
 * <p>
 * <b>Retro-Gaming Context:</b>
 * </p>
 * <ul>
 * <li><b>Profile:</b> A configuration file (typically .nfo format) that defines expected ROM sets, systems, sources, and filtering
 * rules for a ROM collection</li>
 * <li><b>Import:</b> Creating a new profile from MAME's built-in ROM database, optionally including software lists</li>
 * <li><b>Scan:</b> Comparing actual ROM files against the profile's expected ROM set to identify missing, unneeded, or incorrect
 * files</li>
 * <li><b>Fix:</b> Automatically organizing, renaming, or moving ROM files to match the profile's expected structure</li>
 * </ul>
 * <p>
 * <b>Thread Safety:</b> Long-running operations (import, load, scan, fix) execute in background worker threads via the
 * {@link Worker} class. Progress updates are sent to the client via WebSocket messages. The main thread remains responsive during
 * these operations.
 * </p>
 *
 * @author optyfr
 * 
 * @see ActionsMgr
 * @see WebSession
 * @see PathAbstractor
 * @see ProfileNFO
 * @see Scan
 * @see Fix
 */
public class ProfileActions extends PathAbstractor {
    /** JSON key for success status in response messages. */
    private static final String SUCCESS = "success";

    /** JSON key for parent directory path in request and response messages. */
    private static final String PARENT = "parent";

    /** JSON key for parameter object containing operation-specific data. */
    private static final String PARAMS = "params";

    /**
     * The {@link ActionsMgr} instance used for managing session interactions and WebSocket communications.
     */
    private final ActionsMgr ws;

    /**
     * Constructs a new {@code ProfileActions} instance with the specified actions manager.
     * <p>
     * This constructor also initializes the parent {@link PathAbstractor} with the session context from the actions manager,
     * enabling path resolution and security validation.
     * </p>
     *
     * @param ws the {@link ActionsMgr} instance to use for managing session interactions and communications, must not be null
     */
    public ProfileActions(ActionsMgr ws) {
        super(ws.getSession());
        this.ws = ws;
    }

    /**
     * Imports a ROM profile from MAME's built-in database.
     * <p>
     * This method launches a background worker thread that:
     * </p>
     * <ol>
     * <li>Locates the MAME executable in the system PATH</li>
     * <li>Creates a new profile by importing MAME's ROM database</li>
     * <li>Optionally imports software lists (if the "sl" parameter is true)</li>
     * <li>Copies the profile and ROM files to the specified parent directory</li>
     * <li>Saves the profile configuration and notifies the client</li>
     * </ol>
     * <p>
     * <b>Incoming WebSocket message:</b>
     * </p>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *     "cmd": "Profile.import",
     *     "params": {
     *         "parent": "%work/profiles",
     *         "sl": true
     *     }
     * }
     * </code>
     * </pre>
     * <p>
     * <b>Outgoing WebSocket messages:</b>
     * </p>
     * <ul>
     * <li>{@code Progress.*} - Progress updates during import</li>
     * <li>{@code Profile.imported} - Notification that import completed successfully</li>
     * <li>{@code Global.warn} - Warning if MAME not found or import failed</li>
     * </ul>
     * <p>
     * <b>Error Handling:</b>
     * </p>
     * <ul>
     * <li>{@link BreakException} - User cancelled the operation (silently ignored)</li>
     * <li>{@link IOException} - File system errors are logged and reported as warnings</li>
     * <li>MAME not found - Warning message sent to client</li>
     * </ul>
     *
     * @param jso the JSON object containing import parameters
     */
    public void imprt(JsonObject jso) {
        (ws.getSession().setWorker(new Worker(() -> performMameImport(jso)))).start();
    }

    /**
     * Performs the MAME profile import operation.
     * <p>
     * This method handles the import process, including locating the MAME executable, creating a new profile, and copying files to
     * the specified directory. It also manages progress updates and error handling.
     * </p>
     * 
     * @param jso the JSON object containing import parameters
     */
    private void performMameImport(JsonObject jso) {
        WebSession session = ws.getSession();
        session.getWorker().setProgress(new ProgressActions(ws));
        session.getWorker().getProgress().canCancel(false);
        session.getWorker().getProgress().setProgress(session.getMsgs().getString("MainFrame.ImportingFromMame"), -1); //$NON-NLS-1$
        try {
            JsonObject jsobj = jso.get(PARAMS).asObject();
            String filename = FindCmd.findMame();
            if (filename != null) {
                final var sl = jsobj.getBoolean("sl", false);
                final var imprt = new Import(session, new File(filename), sl, session.getWorker().getProgress());
                if (imprt.getFile() != null)
                    doImport(session, jsobj, sl, imprt);
                else
                    new GlobalActions(ws).warn("Could not import anything from Mame");
            } else
                new GlobalActions(ws).warn("Mame not found in system's search path");
        } catch (BreakException _) {
            // user cancelled action
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
            new GlobalActions(ws).warn(e.getMessage());
        } finally {
            session.getWorker().getProgress().close();
            session.getWorker().setProgress(null);
            session.setLastAction(Instant.now());
        }
    }

    /**
     * Performs the actual profile import operation.
     * <p>
     * This method copies the imported profile file and associated ROM/software list files to the target directory, updates the
     * profile configuration with the original MAME file paths, and notifies the client upon successful completion.
     * </p>
     *
     * @param session the active web session
     * @param jsobj the JSON parameters object containing parent directory and software list settings
     * @param sl {@code true} to import software lists, {@code false} otherwise
     * @param imprt the import handler containing the generated profile and ROM files
     * 
     * @throws SecurityException if path validation fails (potential directory traversal attack)
     * @throws IOException if file copying or deletion fails
     */
    private void doImport(WebSession session, JsonObject jsobj, final boolean sl, final Import imprt) throws SecurityException, IOException {
        final var parent = getAbsolutePath(
                Optional.ofNullable(jsobj.get(PARENT)).filter(JsonValue::isString).map(JsonValue::asString).orElse(session.getUser().getSettings().getWorkPath().toString()))
                .toFile();
        final var file = new File(parent, imprt.getFile().getName());
        FileUtils.copyFile(imprt.getFile(), file);
        final var pnfo = ProfileNFO.load(session, file);
        pnfo.getMame().set(imprt.getOrgFile(), sl);
        if (imprt.getRomsFile() != null) {
            FileUtils.copyFileToDirectory(imprt.getRomsFile(), parent);
            pnfo.getMame().setFileroms(new File(parent, imprt.getRomsFile().getName()));
            if (sl) {
                if (imprt.getSlFile() != null) {
                    FileUtils.copyFileToDirectory(imprt.getSlFile(), parent);
                    pnfo.getMame().setFilesl(new File(parent, imprt.getSlFile().getName()));
                } else
                    new GlobalActions(ws).warn("Could not import softwares list");
            }
            pnfo.save(session);
            imported(pnfo.getFile());
        } else {
            new GlobalActions(ws).warn("Could not import roms list");
            Files.delete(file.toPath());
        }
    }

    /**
     * Loads an existing ROM profile from disk.
     * <p>
     * This method launches a background worker thread that:
     * </p>
     * <ol>
     * <li>Saves the current profile's settings (if one is loaded)</li>
     * <li>Resolves the profile file path from the JSON parameters</li>
     * <li>Loads the profile and initializes the report handler</li>
     * <li>Loads associated CatVer and NPlayers metadata files</li>
     * <li>Notifies the client with the loaded profile's structure and settings</li>
     * </ol>
     * <p>
     * <b>Incoming WebSocket message:</b>
     * </p>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *     "cmd": "Profile.load",
     *     "params": {
     *         "parent": "%work/profiles",
     *         "file": "myprofile.nfo"
     *     }
     * }
     * </code>
     * </pre>
     * <p>
     * <b>Outgoing WebSocket messages:</b>
     * </p>
     * <ul>
     * <li>{@code Progress.*} - Progress updates during profile loading</li>
     * <li>{@code Profile.loaded} - Notification with profile structure (systems, sources, years, settings)</li>
     * <li>{@code CatVer.loaded} - Notification that CatVer metadata was loaded (if available)</li>
     * <li>{@code NPlayers.loaded} - Notification that NPlayers metadata was loaded (if available)</li>
     * </ul>
     * <p>
     * <b>Error Handling:</b>
     * </p>
     * <ul>
     * <li>{@link BreakException} - User cancelled the operation (silently ignored)</li>
     * </ul>
     *
     * @param jso the JSON object containing load parameters
     */
    public void load(JsonObject jso) {
        (ws.getSession().setWorker(new Worker(() -> performProfileLoad(jso)))).start();
    }

    /**
     * Performs the actual loading of a ROM profile, including saving current settings, resolving file paths, loading the profile,
     * initializing the report handler, and loading associated metadata files. It also handles progress updates and notifications to
     * the client.
     *
     * @param jso the JSON object containing load parameters
     */
    private void performProfileLoad(JsonObject jso) {
        WebSession session = ws.getSession();
        if (session.getCurrProfile() != null)
            session.getCurrProfile().saveSettings();
        session.getWorker().setProgress(new ProgressActions(ws));
        try {
            JsonObject jsobj = jso.get(PARAMS).asObject();
            val file = getAbsolutePath(jsobj.getString(PARENT, null)).resolve(jsobj.getString("file", null));
            session.setCurrProfile(jrm.profile.Profile.load(session, file.toFile(), session.getWorker().getProgress()));
            if (session.getCurrProfile() != null) {
                session.getCurrProfile().getNfo().save(session);
                session.getReport().setProfile(session.getCurrProfile());
                loaded(session.getCurrProfile());
                new CatVerActions(ws).loaded(session.getCurrProfile());
                new NPlayersActions(ws).loaded(session.getCurrProfile());
            }
        } catch (BreakException _) {
            // user cancelled action
        } finally {
            session.getWorker().getProgress().close();
            session.getWorker().setProgress(null);
            session.setLastAction(Instant.now());
        }
    }

    /**
     * Imports profile settings from an external configuration file.
     * <p>
     * This method loads scan and filter settings from the specified path and applies them to the current profile. It also reloads
     * CatVer and NPlayers metadata files if they are referenced in the imported settings.
     * </p>
     * <p>
     * <b>Incoming WebSocket message:</b>
     * </p>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *     "cmd": "Profile.importSettings",
     *     "params": {
     *         "path": "%work/settings/myprofile.settings"
     *     }
     * }
     * </code>
     * </pre>
     * <p>
     * <b>Outgoing WebSocket messages:</b>
     * </p>
     * <ul>
     * <li>{@code Profile.loaded} - Notification with updated profile settings</li>
     * <li>{@code CatVer.loaded} - Notification that CatVer metadata was reloaded (if applicable)</li>
     * <li>{@code NPlayers.loaded} - Notification that NPlayers metadata was reloaded (if applicable)</li>
     * </ul>
     *
     * @param jso the JSON object containing the settings file path
     */
    public void importSettings(JsonObject jso) {
        WebSession session = ws.getSession();
        if (session.getCurrProfile() != null) {
            final JsonValue jsv = jso.get(PARAMS).asObject().get("path");
            if (jsv != null && !jsv.isNull()) {
                session.getCurrProfile().loadSettings(PathAbstractor.getAbsolutePath(session, jsv.asString()).toFile());
                session.getCurrProfile().loadCatVer(null);
                session.getCurrProfile().loadNPlayers(null);
                loaded(session.getCurrProfile());
                new CatVerActions(ws).loaded(session.getCurrProfile());
                new NPlayersActions(ws).loaded(session.getCurrProfile());
            }
        }
    }

    /**
     * Exports the current profile's settings to an external configuration file.
     * <p>
     * This method saves the current profile's scan and filter settings to the specified path, allowing them to be imported later or
     * shared with other users.
     * </p>
     * <p>
     * <b>Incoming WebSocket message:</b>
     * </p>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *     "cmd": "Profile.exportSettings",
     *     "params": {
     *         "path": "%work/settings/myprofile.settings"
     *     }
     * }
     * </code>
     * </pre>
     *
     * @param jso the JSON object containing the export file path
     */
    public void exportSettings(JsonObject jso) {
        WebSession session = ws.getSession();
        if (session.getCurrProfile() != null) {
            final JsonValue jsv = jso.get(PARAMS).asObject().get("path");
            if (jsv != null && !jsv.isNull()) {
                session.getCurrProfile().saveSettings(PathAbstractor.getAbsolutePath(session, jsv.asString()).toFile());
            }
        }
    }

    /**
     * Scans the ROM collection against the current profile to identify missing, unneeded, or incorrect files.
     * <p>
     * This method launches a background worker thread that:
     * </p>
     * <ol>
     * <li>Creates a new {@link Scan} instance for the current profile</li>
     * <li>Executes the scan operation, comparing actual ROM files against the profile's expected ROM set</li>
     * <li>Generates a list of actions needed to fix the ROM collection (rename, move, delete, etc.)</li>
     * <li>Notifies the client with the scan results and action count</li>
     * <li>Optionally triggers an automatic fix operation based on automation settings</li>
     * </ol>
     * <p>
     * <b>Incoming WebSocket message:</b>
     * </p>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *     "cmd": "Profile.scan",
     *     "params": {}
     * }
     * </code>
     * </pre>
     * <p>
     * <b>Outgoing WebSocket messages:</b>
     * </p>
     * <ul>
     * <li>{@code Progress.*} - Progress updates during scanning</li>
     * <li>{@code Profile.scanned} - Notification with scan results and action count</li>
     * </ul>
     * <p>
     * <b>Error Handling:</b>
     * </p>
     * <ul>
     * <li>{@link BreakException} - User cancelled the operation (silently ignored)</li>
     * <li>{@link ScanException} - Scan errors are added to the progress handler's error list</li>
     * </ul>
     * <p>
     * <b>Automation:</b> If the profile's automation settings specify {@link ScanAutomation#hasFix()}, and the scan found actions
     * to perform, this method automatically calls {@link #fix(JsonObject)} after the scan completes.
     * </p>
     *
     * @param jso the JSON object containing scan parameters (currently unused)
     * @param automate {@code true} to enable automatic fix after scan, {@code false} to scan only
     */
    public void scan(JsonObject jso, final boolean automate) {
        ws.getSession().setWorker(new Worker(() -> performScan(jso, automate))).start();
    }

    /**
     * Performs the actual scan operation, handling progress updates and error notifications.
     * <p>
     * This method is called by {@link #scan(JsonObject, boolean)} to execute the scan in a background thread. It handles progress
     * updates and error notifications, and optionally triggers an automatic fix operation if specified.
     * </p>
     * 
     * @param jso the JSON object containing scan parameters (currently unused)
     * @param automate {@code true} to enable automatic fix after scan, {@code false} to scan only
     * 
     * @throws BreakException if the user cancels the operation
     * @throws ScanException if an error occurs during the scan operation
     */
    private void performScan(JsonObject jso, final boolean automate) {
        WebSession session = ws.getSession();
        session.getWorker().setProgress(new ProgressActions(ws));
        try {
            session.setCurrScan(new Scan(session.getCurrProfile(), session.getWorker().getProgress()));
        } catch (BreakException _) {
            // user cancelled action
        } catch (ScanException ex) {
            session.getWorker().getProgress().addError(ex.getMessage());
        }
        session.getWorker().getProgress().close();
        session.getWorker().setProgress(null);
        session.setLastAction(Instant.now());
        final var automation = ScanAutomation.valueOf(session.getCurrProfile().getSettings().getProperty(ProfileSettingsEnum.automation_scan));
        scanned(session.getCurrScan(), automation.hasReport());
        if (automate && session.getCurrScan() != null && session.getCurrScan().actions.stream().mapToInt(Collection::size).sum() > 0 && automation.hasFix())
            fix(jso);
    }

    /**
     * Fixes ROM collection issues identified by the previous scan operation.
     * <p>
     * This method launches a background worker thread that:
     * </p>
     * <ol>
     * <li>Rescans the profile if properties have changed since the last scan</li>
     * <li>Creates a new {@link Fix} instance to process the scan's action list</li>
     * <li>Executes the fix operation, performing file operations (rename, move, delete, etc.)</li>
     * <li>Notifies the client with the remaining action count</li>
     * <li>Optionally triggers a rescan based on automation settings</li>
     * </ol>
     * <p>
     * <b>Incoming WebSocket message:</b>
     * </p>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *     "cmd": "Profile.fix",
     *     "params": {}
     * }
     * </code>
     * </pre>
     * <p>
     * <b>Outgoing WebSocket messages:</b>
     * </p>
     * <ul>
     * <li>{@code Progress.*} - Progress updates during fixing</li>
     * <li>{@code Profile.fixed} - Notification with remaining action count</li>
     * </ul>
     * <p>
     * <b>Error Handling:</b>
     * </p>
     * <ul>
     * <li>{@link ScanException} - Scan errors during rescan are added to the progress handler's error list</li>
     * </ul>
     * <p>
     * <b>Automation:</b> If the profile's automation settings specify {@link ScanAutomation#hasScanAgain()}, this method
     * automatically calls {@link #scan(JsonObject, boolean)} after the fix completes to verify the ROM collection state.
     * </p>
     *
     * @param jso the JSON object containing fix parameters (currently unused)
     */
    public void fix(JsonObject jso) {
        ws.getSession().setWorker(new Worker(() -> performFix(jso))).start();
    }

    /**
     * Performs the actual fix operation in a background thread. It rescans the profile if necessary, creates a Fix instance to process the
     * scan's action list, and executes the fix operation. It also handles progress updates and notifications.
     *
     * @param jso the JSON object containing fix parameters (currently unused)
     */
    private void performFix(final JsonObject jso) {
        final var session = ws.getSession();
        session.getWorker().setProgress(new ProgressActions(ws));
        try {
            if (session.getCurrProfile().hasPropsChanged()) {
                session.setCurrScan(new Scan(session.getCurrProfile(), session.getWorker().getProgress()));
                boolean needfix = session.getCurrScan().actions.stream().mapToInt(Collection::size).sum() > 0;
                if (!needfix)
                    return;
            }
            final var fix = new Fix(session.getCurrProfile(), session.getCurrScan(), session.getWorker().getProgress());
            fixed(fix);
        } catch (ScanException ex) {
            session.getWorker().getProgress().addError(ex.getMessage());
        } finally {
            final var automation = ScanAutomation.valueOf(session.getCurrProfile().getSettings().getProperty(ProfileSettingsEnum.automation_scan));
            if (automation.hasScanAgain())
                scan(jso, false);
            session.getWorker().getProgress().close();
            session.getWorker().setProgress(null);
            session.setLastAction(Instant.now());
        }
    }

    /**
     * Updates profile settings properties from a JSON object.
     * <p>
     * This method processes a JSON object containing key-value pairs representing profile settings. It supports multiple JSON value
     * types:
     * </p>
     * <ul>
     * <li><b>Boolean:</b> {@code true} or {@code false}</li>
     * <li><b>Number:</b> Integer values</li>
     * <li><b>String:</b> Text values</li>
     * <li><b>Other:</b> Serialized to string via {@code toString()}</li>
     * </ul>
     * <p>
     * If a "profile" parameter is provided, the settings are saved to a standalone profile settings file. Otherwise, the settings
     * are applied to the current profile and saved.
     * </p>
     * <p>
     * <b>Incoming WebSocket message:</b>
     * </p>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *     "cmd": "Profile.setProperty",
     *     "profile": "%work/profiles/myprofile.settings",
     *     "params": {
     *         "filter_missing": true,
     *         "filter_unneeded": false,
     *         "source_path": "%work/roms"
     *     }
     * }
     * </code>
     * </pre>
     *
     * @param jso the JSON object containing the profile path (optional) and property settings
     */
    public void setProperty(JsonObject jso) {
        final var profile = jso.getString("profile", null);
        ProfileSettings settings = profile != null ? new ProfileSettings() : ws.getSession().getCurrProfile().getSettings();
        JsonObject pjso = jso.get(PARAMS).asObject();
        try {
            for (Member m : pjso) {
                JsonValue value = m.getValue();
                if (value.isString())
                    rejectUnwritableDestPath(m.getName(), value.asString());
            }
            for (Member m : pjso) {
                JsonValue value = m.getValue();
                if (value.isBoolean())
                    settings.setProperty(m.getName(), value.asBoolean());
                else if (value.isNumber())
                    settings.setProperty(m.getName(), value.asInt());
                else if (value.isString())
                    settings.setProperty(m.getName(), value.asString());
                else
                    settings.setProperty(m.getName(), value.toString());
            }
            if (profile != null)
                ws.getSession().getUser().getSettings().saveProfileSettings(getAbsolutePath(profile).toFile(), settings);
            else
                ws.getSession().getCurrProfile().saveSettings();
        } catch (SecurityException e) {
            Log.err(() -> "Profile.setProperty rejected: " + e.getMessage());
            new GlobalActions(ws).warn("Write access denied for destination path. Settings were not saved.");
        } catch (Exception e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Rejects destination/backup path properties that point at non-writeable locations (e.g. {@code %shared} for non-admins).
     */
    private void rejectUnwritableDestPath(final String name, final String value) {
        if (value == null || value.isEmpty())
            return;
        if (!isDestPathProperty(name))
            return;
        requireWriteable(ws.getSession(), value);
    }

    private static boolean isDestPathProperty(final String name) {
        return ProfileSettingsEnum.roms_dest_dir.toString().equals(name)
                || ProfileSettingsEnum.disks_dest_dir.toString().equals(name)
                || ProfileSettingsEnum.swroms_dest_dir.toString().equals(name)
                || ProfileSettingsEnum.swdisks_dest_dir.toString().equals(name)
                || ProfileSettingsEnum.samples_dest_dir.toString().equals(name)
                || ProfileSettingsEnum.backup_dest_dir.toString().equals(name);
    }

    /**
     * Notifies the client that a profile has been loaded.
     * <p>
     * This method sends a WebSocket message with the command {@code "Profile.loaded"} containing the profile's structure and
     * settings. The message includes:
     * </p>
     * <ul>
     * <li><b>success:</b> Boolean indicating whether a profile was loaded</li>
     * <li><b>name:</b> Profile name (if loaded)</li>
     * <li><b>systems:</b> Array of ROM systems with name, selection state, property name, and type</li>
     * <li><b>sources:</b> Array of ROM sources with name, selection state, and property name</li>
     * <li><b>years:</b> Array of years present in the ROM collection (sorted)</li>
     * <li><b>settings:</b> Current profile settings as a JSON object</li>
     * </ul>
     * <p>
     * Example outgoing WebSocket message:
     * </p>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *     "cmd": "Profile.loaded",
     *     "params": {
     *         "success": true,
     *         "name": "MAME 0.230",
     *         "systems": [
     *             {"name": "Arcade", "selected": true, "property": "system.arcade", "type": "ARCADE"}
     *         ],
     *         "sources": [
     *             {"name": "ROMs", "selected": true, "property": "source.roms"}
     *         ],
     *         "years": ["1980", "1981", "1982"],
     *         "settings": {...}
     *     }
     * }
     * </code>
     * </pre>
     *
     * @param profile the loaded profile, or {@code null} if no profile was loaded
     */
    public void loaded(final jrm.profile.Profile profile) {
        try {
            if (ws.isOpen()) {
                final var rjso = new JsonObject();
                rjso.add("cmd", "Profile.loaded");
                final var params = new JsonObject();
                params.add(SUCCESS, profile != null);
                if (profile != null) {
                    params.add("name", profile.getName());
                    if (profile.getSystems() != null) {
                        final var systems = new JsonArray();
                        profile.getSystems().forEach(s -> {
                            final var systm = new JsonObject();
                            systm.add("name", s.toString());
                            systm.add("selected", s.isSelected(profile));
                            systm.add("property", s.getPropertyName());
                            systm.add("type", s.getType().toString());
                            systems.add(systm);
                        });
                        params.add("systems", systems);
                    }
                    final var sources = new JsonArray();
                    profile.getSources().forEach(s -> {
                        final var source = new JsonObject();
                        source.add("name", s.toString());
                        source.add("selected", s.isSelected(profile));
                        source.add("property", s.getPropertyName());
                        sources.add(source);
                    });
                    params.add("sources", sources);
                    final var years = new JsonArray();
                    final ArrayList<String> arrlst = new ArrayList<>(profile.getYears());
                    arrlst.sort(String::compareTo);
                    arrlst.forEach(years::add);
                    params.add("years", years);
                    if (profile.getSettings() != null)
                        params.add("settings", profile.getSettings().asJSO());
                }
                rjso.add(PARAMS, params);
                ws.send(rjso.toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Notifies the client that a scan operation has completed.
     * <p>
     * This method sends a WebSocket message with the command {@code "Profile.scanned"} containing the scan results. The message
     * includes:
     * </p>
     * <ul>
     * <li><b>success:</b> Boolean indicating whether the scan completed successfully</li>
     * <li><b>actions:</b> Total number of actions identified by the scan (if successful)</li>
     * <li><b>report:</b> Boolean indicating whether a detailed report was generated</li>
     * </ul>
     * <p>
     * Example outgoing WebSocket message:
     * </p>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *     "cmd": "Profile.scanned",
     *     "params": {
     *         "success": true,
     *         "actions": 42,
     *         "report": true
     *     }
     * }
     * </code>
     * </pre>
     *
     * @param scan the completed scan, or {@code null} if the scan failed or was cancelled
     * @param hasReport {@code true} if a detailed report was generated, {@code false} otherwise
     */
    void scanned(final Scan scan, final boolean hasReport) {
        try {
            if (ws.isOpen()) {
                final var rjso = new JsonObject();
                rjso.add("cmd", "Profile.scanned");
                final var params = new JsonObject();
                params.add(SUCCESS, scan != null);
                if (scan != null) {
                    params.add("actions", scan.actions.stream().mapToInt(Collection::size).sum());
                    params.add("report", hasReport);
                }
                rjso.add(PARAMS, params);
                ws.send(rjso.toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Notifies the client that a fix operation has completed.
     * <p>
     * This method sends a WebSocket message with the command {@code "Profile.fixed"} containing the fix results. The message
     * includes:
     * </p>
     * <ul>
     * <li><b>success:</b> Boolean indicating whether the fix completed successfully</li>
     * <li><b>actions:</b> Number of remaining actions that could not be fixed (if successful)</li>
     * </ul>
     * <p>
     * Example outgoing WebSocket message:
     * </p>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *     "cmd": "Profile.fixed",
     *     "params": {
     *         "success": true,
     *         "actions": 5
     *     }
     * }
     * </code>
     * </pre>
     *
     * @param fix the completed fix operation, or {@code null} if the fix failed or was cancelled
     */
    void fixed(final Fix fix) {
        try {
            if (ws.isOpen()) {
                final var rjso = new JsonObject();
                rjso.add("cmd", "Profile.fixed");
                final var params = new JsonObject();
                params.add(SUCCESS, fix != null);
                if (fix != null)
                    params.add("actions", fix.getActionsRemain());
                rjso.add(PARAMS, params);
                ws.send(rjso.toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Notifies the client that a profile import operation has completed.
     * <p>
     * This method sends a WebSocket message with the command {@code "Profile.imported"} containing the imported profile file
     * information. The message includes:
     * </p>
     * <ul>
     * <li><b>path:</b> Full path to the imported profile file</li>
     * <li><b>parent:</b> Parent directory path</li>
     * <li><b>name:</b> Profile file name</li>
     * </ul>
     * <p>
     * Example outgoing WebSocket message:
     * </p>
     * 
     * <pre>
     * <code class='language-json'>
     * {
     *     "cmd": "Profile.imported",
     *     "params": {
     *         "path": "/home/user/profiles/mame.nfo",
     *         "parent": "/home/user/profiles",
     *         "name": "mame.nfo"
     *     }
     * }
     * </code>
     * </pre>
     *
     * @param file the imported profile file
     */
    void imported(final File file) {
        try {
            if (ws.isOpen()) {
                final var rjso = new JsonObject();
                rjso.add("cmd", "Profile.imported");
                final var params = new JsonObject();
                params.add("path", file.getPath());
                params.add(PARENT, file.getParent());
                params.add("name", file.getName());
                rjso.add(PARAMS, params);
                ws.send(rjso.toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }
}
