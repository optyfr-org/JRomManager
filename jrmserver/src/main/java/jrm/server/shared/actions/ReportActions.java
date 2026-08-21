package jrm.server.shared.actions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonObject.Member;
import com.eclipsesource.json.JsonValue;

import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.profile.Profile;
import jrm.profile.data.ExportMode;
import jrm.profile.manager.Export;
import jrm.profile.report.FilterOptions;
import jrm.profile.report.Report;
import jrm.security.PathAbstractor;
import jrm.server.shared.WebSession;
import jrm.server.shared.Worker;

/**
 * Action handler for managing report filter operations in the ROM manager web server.
 * <p>
 * This class processes incoming JSON action commands related to applying filter options to scan reports. It supports two report
 * modes:
 * </p>
 * <ul>
 * <li><b>Full report</b> ({@code Report.applyFilters}) - Filters applied to the session's primary report</li>
 * <li><b>Lite report</b> ({@code ReportLite.applyFilters}) - Filters applied to the session's temporary report</li>
 * </ul>
 * <p>
 * Filter options are defined in the {@link FilterOptions} enumeration and control which ROM entries are visible in the report
 * output (e.g., missing ROMs, unneeded files, fixable entries).
 * </p>
 * <p>
 * <b>Thread Safety:</b> This class is not thread-safe. All operations should be performed on the WebSocket message handling thread.
 * The underlying {@link ActionsMgr} and {@link Report} instances are shared across the session and should not be accessed
 * concurrently from multiple threads.
 * </p>
 */
public class ReportActions {

    private static final String PARAMS = "params";

    /**
     * The {@link ActionsMgr} instance used for managing session interactions and WebSocket communications.
     */
    private final ActionsMgr ws;

    /**
     * Constructs a new {@code ReportActions} instance with the specified actions manager.
     *
     * @param ws the {@link ActionsMgr} instance to use for managing session interactions and communications, must not be null
     */
    public ReportActions(ActionsMgr ws) {
        this.ws = ws;
    }

    /**
     * Applies filter options to the appropriate report and sends the updated filter state back to the client.
     * <p>
     * This method processes a JSON object containing filter option settings. Each key in the "params" object corresponds to a
     * {@link FilterOptions} enum value, and the boolean value determines whether that filter should be enabled ({@code true}) or
     * disabled ({@code false}). The method:
     * </p>
     * <ol>
     * <li>Clones the current filter options from the report handler</li>
     * <li>Adds or removes options based on the JSON parameters</li>
     * <li>Applies the updated filter set to the report handler</li>
     * <li>Sends a WebSocket message with the complete filter state back to the client</li>
     * </ol>
     * <p>
     * Unknown filter option names are silently ignored to maintain forward compatibility.
     * </p>
     *
     * @param jso the JSON object containing filter parameters, expected to have a structure like:
     * 
     *        <pre>
     *             <code class='language-json'>
     *             {
     *                 "params": {
     *                     "MISSING": true,
     *                     "UNNEEDED": false,
     *                     "FIXABLE": true
     *                 }
     *             }
     *             </code>
     *        </pre>
     * 
     * @param lite if {@code true}, applies filters to the temporary report ({@link jrm.server.shared.WebSession#getTmpReport()});
     *        if {@code false}, applies filters to the primary report ({@link jrm.server.shared.WebSession#getReport()})
     */
    public void setFilter(JsonObject jso, boolean lite) {
        final JsonObject pjso = jso.get(PARAMS).asObject();
        final Report report = lite ? ws.getSession().getTmpReport() : ws.getSession().getReport();
        Set<FilterOptions> options = ((EnumSet<FilterOptions>) report.getHandler().getFilterOptions()).clone();
        for (Member m : pjso) {
            try {
                final var option = FilterOptions.valueOf(m.getName());
                final var value = m.getValue();
                if (value.asBoolean())
                    options.add(option);
                else
                    options.remove(option);

            } catch (IllegalArgumentException _) {
                // is it even possible?
            }
        }
        report.getHandler().filter(options.toArray(new FilterOptions[0]));
        try {
            if (ws.isOpen()) {
                final var params = new JsonObject();
                EnumSet.allOf(FilterOptions.class).forEach(f -> params.add(f.toString(), options.contains(f)));
                ws.send(Json.object().add("cmd", lite ? "ReportLite.applyFilters" : "Report.applyFilters").add(PARAMS, params).toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Exports a fixDAT of missing and partial titles from the current profile.
     *
     * @param jso JSON with {@code params.path} as the destination abstract path
     */
    public void createFixDat(JsonObject jso) {
        final String path = extractFixDatPath(jso);
        ws.getSession().setWorker(new Worker(() -> performCreateFixDat(path))).start();
    }

    /**
     * Extracts the destination path from the JSON request on the calling thread, so the worker does not depend on the mutable
     * request object.
     *
     * @param jso JSON with {@code params.path} as the destination abstract path
     * @return the destination path, or {@code null} if missing
     */
    private String extractFixDatPath(JsonObject jso) {
        final JsonValue pathValue = jso.get(PARAMS) != null ? jso.get(PARAMS).asObject().get("path") : null;
        return pathValue != null && !pathValue.isNull() ? pathValue.asString() : null;
    }

    private void performCreateFixDat(String path) {
        final WebSession session = ws.getSession();
        session.getWorker().setProgress(new ProgressActions(ws));
        try {
            final Profile profile = session.getCurrProfile();
            if (profile == null) {
                new GlobalActions(ws).warn("No profile loaded.");
                return;
            }
            if (path == null) {
                new GlobalActions(ws).warn("No destination file.");
                return;
            }
            final Path dest = PathAbstractor.getWritableAbsolutePath(session, path);
            File file = dest.toFile();
            if (!file.getName().contains("."))
                file = new File(file.getParentFile(), file.getName() + ".xml");
            Export.export(profile, file, Report.resolveFixDatType(profile), EnumSet.of(ExportMode.MISSING), null, session.getWorker().getProgress());
            sendFixDatCreated(path);
        } catch (SecurityException e) {
            Log.err(() -> "Path validation failed for fixDAT export: " + e.getMessage(), e);
            new GlobalActions(ws).warn("Invalid destination file path. Operation cancelled for security reasons.");
        } catch (BreakException _) {
            // user cancelled
        } finally {
            session.getWorker().getProgress().close();
            session.getWorker().setProgress(null);
            session.setLastAction(Instant.now());
        }
    }

    private void sendFixDatCreated(String path) {
        try {
            if (ws.isOpen()) {
                final var params = new JsonObject();
                params.add("path", path);
                params.add("success", true);
                ws.send(Json.object().add("cmd", "Report.fixDatCreated").add(PARAMS, params).toString());
            }
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
    }
}
