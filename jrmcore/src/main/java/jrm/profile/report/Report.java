/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.report;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.PrintWriter;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.eclipsesource.json.Json;

import jrm.aui.profile.report.ReportTreeDefaultHandler;
import jrm.aui.profile.report.ReportTreeHandler;
import jrm.aui.progress.StatusHandler;
import jrm.aui.status.StatusRendererFactory;
import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.misc.SettingsEnum;
import jrm.profile.Profile;
import jrm.profile.data.Anyware;
import jrm.security.ReportDeserializationFilter;
import jrm.security.Session;
import lombok.Getter;
import one.util.streamex.IntStreamEx;

/**
 * The root node of a report hierarchy, managing a list of {@link Subject}s and tracking overall profile validation metrics.
 * <p>
 * This class coordinates the dynamic UI filtering model, supports serialization of validation state caches, and outputs
 * text-formatted reports.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
public class Report extends AbstractList<Subject> implements StatusRendererFactory, Serializable, ReportIntf<Report> {
    /**
     * Field serialization key for the report file destination.
     */
    private static final String REPORT_FILE_STR = "reportFile";

    /**
     * Field serialization key for the scanning statistics.
     */
    private static final String STATS_STR = "stats";

    /**
     * Field serialization key for the list of subjects.
     */
    private static final String SUBJECTS_STR = "subjects";

    /**
     * Serial version identifier for object serialization compatibility.
     */
    private static final long serialVersionUID = 3L;

    /**
     * The related profile associated with this report.
     *
     * @return the Profile instance
     */
    @Getter
    private transient Profile profile = null;

    /**
     * The physical file location of the original profile catalog.
     */
    private transient File file = null;

    /**
     * The timestamp indicating when the profile file was last modified.
     */
    private transient long fileModified = 0L;

    /**
     * The physical file destination where the compiled report log is saved.
     *
     * @return the report log File
     */
    @Getter
    private File reportFile = null;

    /**
     * The logical collection of scanned report subjects managed by this report.
     *
     * @return the list of Subject instances
     */
    private @Getter List<Subject> subjects;

    /**
     * Map indexing scanned report subjects by their case-insensitive full names for fast retrieval.
     */
    private transient Map<String, Subject> subjectHash;

    /**
     * The runtime identity code assigned to this report.
     */
    private transient int id;

    /**
     * Atomic counter providing auto-incrementing ID sequences for subjects and notes in the active session.
     */
    private transient AtomicInteger idCnt;

    /**
     * Flat lookup directory storing all subjects and notes indexed by their unique integer identifiers.
     */
    private transient Map<Integer, Object> all;

    /**
     * The aggregated validation statistics tracked during directory and ROM scanning.
     *
     * @return the compiled Stats metrics
     */
    private @Getter Stats stats;

    /**
     * The linked UI presentation tree handler responsible for notifying components of structural changes.
     */
    private transient ReportTreeHandler<Report> handler = null;

    /**
     * Declares the persistent Java serialization fields.
     */
    private static final ObjectStreamField[] serialPersistentFields = { // NOSONAR
            new ObjectStreamField(SUBJECTS_STR, List.class),
            new ObjectStreamField(STATS_STR, Stats.class),
            new ObjectStreamField(REPORT_FILE_STR, File.class)
    };

    /**
     * Writes the persistent report state parameters to a Java serialization stream.
     *
     * @param stream the target ObjectOutputStream
     * 
     * @throws IOException if an input/output exception occurs during serialization
     */
    private void writeObject(final java.io.ObjectOutputStream stream) throws IOException {
        final ObjectOutputStream.PutField fields = stream.putFields();
        fields.put(SUBJECTS_STR, subjects); // $NON-NLS-1$
        fields.put(STATS_STR, stats); // $NON-NLS-1$
        fields.put(REPORT_FILE_STR, reportFile); // $NON-NLS-1$
        stream.writeFields();
    }

    /**
     * Restores the persistent report state parameters from a Java serialization stream.
     *
     * @param stream the source ObjectInputStream
     * 
     * @throws IOException if an input/output exception occurs during deserialization
     * @throws ClassNotFoundException if the target serializable class cannot be loaded
     */
    @SuppressWarnings("unchecked")
    private void readObject(final java.io.ObjectInputStream stream) throws IOException, ClassNotFoundException {
        final ObjectInputStream.GetField fields = stream.readFields();
        subjects = (List<Subject>) fields.get(SUBJECTS_STR, Collections.synchronizedList(new ArrayList<>())); // $NON-NLS-1$
        stats = (Stats) fields.get(STATS_STR, new Stats()); // $NON-NLS-1$
        reportFile = (File) fields.get(REPORT_FILE_STR, (File) null);
        subjectHash = subjects.stream()
                .peek(s -> s.parent = this) // NOSONAR
                .collect(Collectors.toMap(Subject::getWareName, Function.identity(), (_, _) -> null));
        filterPredicate = new FilterPredicate(new HashSet<>());
        handler = new ReportTreeDefaultHandler(this);
    }

    /**
     * Aggregates summary statistics, missing counts, and repair indicators resolved during ROM scanning.
     */
    public static class Stats implements Serializable {
        private static final long serialVersionUID = 2L;

        /**
         * Counts total missing retro-gaming sets.
         *
         * @return the missing sets count
         */
        private @Getter int missingSetCnt = 0;

        /**
         * Counts total missing ROM files.
         *
         * @return the missing ROMs count
         */
        private @Getter int missingRomsCnt = 0;

        /**
         * Counts total missing CHD disks.
         *
         * @return the missing disks count
         */
        private @Getter int missingDisksCnt = 0;

        /**
         * Counts total missing synthesized audio samples.
         *
         * @return the missing samples count
         */
        private @Getter int missingSamplesCnt = 0;

        /**
         * Counts total missing ROM files that are fixable using local resources.
         *
         * @return the fixable ROMs count
         */
        private @Getter int fixableRomsCnt = 0;

        /**
         * Counts total missing CHD disks that are fixable using local resources.
         *
         * @return the fixable disks count
         */
        private @Getter int fixableDisksCnt = 0;

        /**
         * Counts total unneeded sets found locally.
         *
         * @return the unneeded sets count
         */
        private @Getter int setUnneeded = 0;

        /**
         * Counts total missing sets.
         *
         * @return the missing sets count
         */
        private @Getter int setMissing = 0;

        /**
         * Counts total sets found locally in any condition.
         *
         * @return the found sets count
         */
        private @Getter int setFound = 0;

        /**
         * Counts total sets that perfectly match expectations and are 100% OK.
         *
         * @return the OK sets count
         */
        private @Getter int setFoundOk = 0;

        /**
         * Counts total sets found that can be partially repaired.
         *
         * @return the partially fixable found sets count
         */
        private @Getter int setFoundFixPartial = 0;

        /**
         * Counts total sets found that can be fully repaired.
         *
         * @return the fully fixable found sets count
         */
        private @Getter int setFoundFixComplete = 0;

        /**
         * Counts total sets that do not exist locally but can be newly created.
         *
         * @return the sets to create count
         */
        private @Getter int setCreate = 0;

        /**
         * Counts total sets that do not exist locally but can be partially created.
         *
         * @return the partially constructible sets count
         */
        private @Getter int setCreatePartial = 0;

        /**
         * Counts total sets that do not exist locally but can be fully created.
         *
         * @return the fully constructible sets count
         */
        private @Getter int setCreateComplete = 0;

        /**
         * Copy constructor creating a duplicate Stats instance.
         *
         * @param org the original Stats instance to replicate
         */
        public Stats(Stats org) {
            this.missingSetCnt = org.missingSetCnt;
            this.missingRomsCnt = org.missingRomsCnt;
            this.missingDisksCnt = org.missingDisksCnt;
            this.missingSamplesCnt = org.missingSamplesCnt;

            this.fixableRomsCnt = org.fixableRomsCnt;
            this.fixableDisksCnt = org.fixableDisksCnt;

            this.setUnneeded = org.setUnneeded;
            this.setMissing = org.setMissing;
            this.setFound = org.setFound;
            this.setFoundOk = org.setFoundOk;
            this.setFoundFixPartial = org.setFoundFixPartial;
            this.setFoundFixComplete = org.setFoundFixComplete;
            this.setCreate = org.setCreate;
            this.setCreatePartial = org.setCreatePartial;
            this.setCreateComplete = org.setCreateComplete;
        }

        /**
         * Default constructor initializing all stats to zero.
         */
        public Stats() {
            // Default empty constructor
        }

        /**
         * Increments the counter of totally missing sets.
         */
        public void incMissingSetCnt() {
            ++missingSetCnt;
        }

        /**
         * Increments the counter of missing ROMs.
         */
        public void incMissingRomsCnt() {
            ++missingRomsCnt;
        }

        /**
         * Increments the counter of missing CHD disks.
         */
        public void incMissingDisksCnt() {
            ++missingDisksCnt;
        }

        /**
         * Increments the counter of missing audio samples.
         */
        public void incMissingSamplesCnt() {
            ++missingSamplesCnt;
        }

        /**
         * Increments the counter of fixable ROMs.
         */
        public void incFixableRomsCnt() {
            ++fixableRomsCnt;
        }

        /**
         * Increments the counter of fixable CHD disks.
         */
        public void incFixableDisksCnt() {
            ++fixableDisksCnt;
        }

        /**
         * Increments the counter of unneeded sets.
         */
        public void incSetUnneeded() {
            ++setUnneeded;
        }

        /**
         * Increments the counter of missing sets.
         */
        public void incSetMissing() {
            ++setMissing;
        }

        /**
         * Increments the counter of sets found.
         */
        public void incSetFound() {
            ++setFound;
        }

        /**
         * Increments the counter of perfectly matching sets.
         */
        public void incSetFoundOk() {
            ++setFoundOk;
        }

        /**
         * Increments the counter of partially fixable found sets.
         */
        public void incSetFoundFixPartial() {
            ++setFoundFixPartial;
        }

        /**
         * Increments the counter of fully fixable found sets.
         */
        public void incSetFoundFixComplete() {
            ++setFoundFixComplete;
        }

        /**
         * Increments the counter of sets to create.
         */
        public void incSetCreate() {
            ++setCreate;
        }

        /**
         * Increments the counter of partially constructible sets.
         */
        public void incSetCreatePartial() {
            ++setCreatePartial;
        }

        /**
         * Increments the counter of fully constructible sets.
         */
        public void incSetCreateComplete() {
            ++setCreateComplete;
        }

        /**
         * Resets all accumulated validation statistics and counters back to zero.
         */
        public void clear() {
            missingSetCnt = 0;
            missingRomsCnt = 0;
            missingDisksCnt = 0;
            missingSamplesCnt = 0;

            fixableRomsCnt = 0;
            fixableDisksCnt = 0;

            setUnneeded = 0;
            setMissing = 0;
            setFound = 0;
            setFoundOk = 0;
            setFoundFixPartial = 0;
            setFoundFixComplete = 0;
            setCreate = 0;
            setCreatePartial = 0;
            setCreateComplete = 0;
        }

        /**
         * Formats a localized text description summarizing all accumulated statistics.
         *
         * @return the status description string
         */
        public String getStatus() {
            return String.format(Messages.getString("Report.Status"), setFound, setFoundOk, setFoundFixPartial, setFoundFixComplete, setCreate, setCreatePartial, setCreateComplete, //$NON-NLS-1$
                    setMissing, setUnneeded, setFound + setCreate, setFound + setCreate + setMissing);
        }
    }

    /**
     * Default constructor initializing an empty Report instance.
     */
    public Report() {
        subjects = Collections.synchronizedList(new ArrayList<>());
        subjectHash = Collections.synchronizedMap(new HashMap<>());
        stats = new Stats();
        handler = new ReportTreeDefaultHandler(this);
    }

    /**
     * Internal predicate implementation verifying if a Subject passes active filtering options.
     */
    class FilterPredicate implements Predicate<Subject> {
        /**
         * Active filtering options applied by this predicate.
         */
        Set<FilterOptions> filterOptions;

        /**
         * Constructs a new FilterPredicate with the specified options.
         *
         * @param filterOptions the filtering options to apply
         */
        public FilterPredicate(final Set<FilterOptions> filterOptions) {
            this.filterOptions = filterOptions;
        }

        /**
         * Evaluates the predicate on the given subject.
         *
         * @param t the input subject to test
         * 
         * @return {@code true} if the subject should remain visible, {@code false} otherwise
         */
        @Override
        public boolean test(final Subject t) {
            if (!filterOptions.contains(FilterOptions.SHOWOK) && t instanceof SubjectSet ss && ss.isOK())
                return false;
            if (filterOptions.contains(FilterOptions.HIDEMISSING) && t instanceof SubjectSet ss && ss.isMissing()) // NOSONAR
                return false;
            return true;
        }

    }

    /**
     * The active visibility filter predicate applied to subjects.
     */
    private transient FilterPredicate filterPredicate = new FilterPredicate(new HashSet<>());

    /**
     * Clones an existing report, applying the specified filtering configuration during structural extraction.
     *
     * @param report the source Report instance to copy
     * @param filterOptions the filtering options configured for the clone
     */
    private Report(final Report report, final Set<FilterOptions> filterOptions) {
        filterPredicate = new FilterPredicate(filterOptions);
        idCnt = new AtomicInteger();
        all = new HashMap<>();
        id = idCnt.getAndIncrement();
        all.put(id, this);
        handler = report.handler;
        profile = report.profile;
        subjects = report.filter(filterOptions);
        for (Subject s : subjects) {
            s.id = idCnt.getAndIncrement();
            all.put(s.id, s);
            for (Note n : s) {
                n.id = idCnt.getAndIncrement();
                all.put(n.id, n);
            }
        }
        subjectHash = subjects.stream().collect(Collectors.toMap(Subject::getWareName, Function.identity(), (_, _) -> null));
        stats = report.stats;
        file = report.file;
        reportFile = report.file;
        fileModified = report.fileModified;
    }

    /**
     * Clones this report under the designated filtering conditions.
     *
     * @param filterOptions the active filtering options
     * 
     * @return the cloned Report instance
     */
    @Override
    public Report clone(final Set<FilterOptions> filterOptions) {
        return new Report(this, filterOptions);
    }

    /**
     * Filters and copies subjects into a sorted list using the current filtering options.
     *
     * @param filterOptions the filtering options to apply
     * 
     * @return a mutable, sorted, and filtered list of Subject instances
     */
    public List<Subject> filter(final Set<FilterOptions> filterOptions) {
        filterPredicate = new FilterPredicate(filterOptions);
        return stream(filterOptions).map(s -> s.clone(filterOptions)).sorted(Subject.getComparator()).collect(Collectors.toList()); // NOSONAR
                                                                                                                                    // list
                                                                                                                                    // must
                                                                                                                                    // be
                                                                                                                                    // mutable
    }

    /**
     * Streams subjects passing active filtering constraints sorted alphabetically by associated ware name.
     *
     * @param filterOptions the active filtering options to evaluate
     * 
     * @return a sorted stream of filtered Subject instances
     */
    public Stream<Subject> stream(final Set<FilterOptions> filterOptions) {
        return subjects.stream().sorted(Subject.getComparator()).filter(new FilterPredicate(filterOptions));
    }

    /**
     * Links a scanning profile configuration to this report, clearing any existing state.
     *
     * @param profile the Profile catalog to assign
     */
    public void setProfile(final Profile profile) {
        this.profile = profile;
        reset();
    }

    /**
     * Resets this report to its initial state, clearing all compiled statistics, subjects, and caches.
     */
    public void reset() {
        subjectHash.clear();
        subjects.clear();
        insertObjectCache.clear();
        stats.clear();
        if (handler != null)
            handler.filter(filterPredicate.filterOptions);
        flush();
    }

    /**
     * The linked progress and status updater hook.
     */
    private StatusHandler statusHandler = null;

    /**
     * Registers a status updater hook to output live scanner updates.
     *
     * @param handler the StatusHandler implementation to bind
     */
    public void setStatusHandler(final StatusHandler handler) {
        statusHandler = handler;
    }

    /**
     * Retrieves the active report tree model synchronization handler.
     *
     * @return the registered tree handler instance
     */
    public ReportTreeHandler<Report> getHandler() {
        return handler;
    }

    /**
     * Binds a report tree model handler to sync status changes with UI tree controls.
     *
     * @param handler the active UI tree handler
     */
    public void setHandler(ReportTreeHandler<Report> handler) {
        this.handler = handler;
    }

    /**
     * Locates a subject instance within the flat registry map by its unique integer identifier.
     *
     * @param id the unique subject ID
     * 
     * @return the matching Subject, or {@code null} if not found or the ID refers to a Note
     */
    public Subject findSubject(final Integer id) {
        Object obj = all.get(id);
        if (obj instanceof Subject s)
            return s;
        return null; // NOSONAR
    }

    /**
     * Locates a subject using its associated gaming system model.
     *
     * @param ware the target Anyware retro-gaming machine metadata
     * 
     * @return the matching Subject, or {@code null} if none is found
     */
    public Subject findSubject(final Anyware ware) {
        return ware != null ? subjectHash.get(ware.getFullName()) : null;
    }

    /**
     * Resolves a subject from the index by its metadata model, registering a default fallback if none is mapped.
     *
     * @param ware the target Anyware machine definition
     * @param def the default fallback Subject to register if none is currently indexed
     * 
     * @return the existing Subject matching the ware, or the newly registered default instance
     */
    public Subject findSubject(final Anyware ware, final Subject def) {
        if (ware != null) {
            if (subjectHash.containsKey(ware.getFullName()))
                return subjectHash.get(ware.getFullName());
            add(def);
            return def;
        }
        return null; // NOSONAR
    }

    /**
     * Thread-safe insertion event cache to throttle and batch UI update events.
     */
    private final transient Map<Integer, Subject> insertObjectCache = Collections.synchronizedMap(LinkedHashMap.newLinkedHashMap(250));

    /**
     * Appends a report subject logically, synchronizing statistics and propagating batch updates to listeners.
     *
     * @param subject the Subject to add
     * 
     * @return {@code true} if successful, {@code false} otherwise
     */
    @Override
    public synchronized boolean add(final Subject subject) {
        subject.parent = this; // initialize subject.parent
        if (all != null) {
            subject.id = idCnt.getAndIncrement();
            all.put(subject.id, subject);
            for (Note n : subject)
                n.id = idCnt.getAndIncrement();
        }
        if (subject.ware != null) // add to subject_hash if there is a subject.ware
            subjectHash.put(subject.ware.getFullName(), subject);
        final boolean result = subjects.add(subject); // add to subjects list and keep result
        final Report clone = handler.getFilteredReport(); // get model Report clone (filtered one)
        if (this != clone) // if this report is not already the clone itself then update clone
        {
            subject.updateStats();
            if (filterPredicate.test(subject)) // manually test predicate
            {
                final Subject clonedSubject = subject.clone(filterPredicate.filterOptions); // clone the subject according
                                                                                            // filterPredicate
                clone.add(clonedSubject); // then call this method on clone object
                insertObjectCache.put(clone.subjects.size() - 1, clonedSubject); // insert cloned subject into insert event cache
                if (insertObjectCache.size() >= 250) // and call flush only if the event cache is at least 250 objects
                    flush();
            }
        }
        return result;
    }

    /**
     * Flushes the insertion batch cache, triggering a single structural insertion event to all UI tree listeners.
     */
    public synchronized void flush() {
        if (statusHandler != null)
            statusHandler.setStatus(stats.getStatus());
        if (insertObjectCache.size() > 0) {
            if (handler!=null && handler.hasListeners())
                handler.notifyInsertion(IntStreamEx.of(insertObjectCache.keySet()).toArray(), insertObjectCache.values().toArray());
            insertObjectCache.clear();
        }
    }

    /**
     * Enumerates active output components and diagnostic groupings supported during text report exporting.
     */
    enum ReportMode {
        /** Include active configuration parameters. */
        SETTINGS,
        /** Output general audit and validation summary statistics. */
        STATS,
        /** Include perfectly matching items. */
        OK,
        /** Include items containing fixable structural issues. */
        FIXABLE,
        /** Include elements that are completely missing. */
        MISSING,
        /** Include non-standard categories (e.g., unknown files). */
        OTHERS,
        /** Render using a tab-delimited flat line presentation. */
        COMPACT,
        /** Restrict subjects listing, omitting child entry notes. */
        NO_ENTRIES,
        /** Group exported diagnostics by status codes. */
        GROUP_BY_TYPE_AND_STATUS
    }

    /**
     * Formats and writes diagnostic validation logs to the user's workspace reports folder.
     *
     * @param session the user session containing workspace context and active profile settings
     */
    public void write(final Session session) {
        final var modes = parseReportModes(session);
        final File reportdir = createReportDirectory(session);
        reportFile = createReportFile(reportdir);

        try (PrintWriter reportWriter = new PrintWriter(reportFile)) {
            writeReportHeader(reportWriter);
            writeReportSettings(reportWriter, modes);
            writeReportStatistics(reportWriter, modes);
            writeReportBody(reportWriter, modes);
            reportWriter.println();
        } catch (final IOException e) {
            Log.err(e.getMessage(), e);
        }
    }

    private EnumSet<ReportMode> parseReportModes(final Session session) {
        final var jsondata = session.getUser().getSettings().getProperty(SettingsEnum.report_settings);
        final var jsonarray = Json.parse(jsondata);
        final var modes = EnumSet.noneOf(ReportMode.class);
        if (jsonarray.isArray())
            for (final var jsonvalue : jsonarray.asArray())
                if (jsonvalue.isString())
                    modes.add(ReportMode.valueOf(jsonvalue.asString()));
        return modes;
    }

    private File createReportDirectory(final Session session) {
        final File workdir = session.getUser().getSettings().getWorkPath().toFile();
        final File reportdir = new File(workdir, "reports");
        reportdir.mkdirs();
        return reportdir;
    }

    private File createReportFile(final File reportdir) {
        return new File(reportdir, "report-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now(ZoneId.systemDefault())) + ".log");
    }

    private void writeReportHeader(PrintWriter reportWriter) {
        reportWriter.println("=== Scanned Profile ===");
        reportWriter.println(profile.getNfo().getFile());
        reportWriter.println();
    }

    private void writeReportSettings(PrintWriter reportWriter, EnumSet<ReportMode> modes) throws IOException {
        if (!modes.contains(ReportMode.SETTINGS))
            return;
        reportWriter.println("=== Used Profile Properties ===");
        profile.getSettings().getProperties().store(reportWriter, null);
        reportWriter.println();
    }

    private void writeReportStatistics(PrintWriter reportWriter, EnumSet<ReportMode> modes) {
        if (!modes.contains(ReportMode.STATS))
            return;
        reportWriter.println("=== Statistics ===");
        reportWriter.println(String.format(Messages.getString("Report.MissingRoms"),
                stats.missingRomsCnt, stats.missingRomsCnt - stats.fixableRomsCnt, profile.getRomsCnt()));
        reportWriter.println(String.format(Messages.getString("Report.MissingDisks"),
                stats.missingDisksCnt, stats.missingDisksCnt - stats.fixableDisksCnt, profile.getDisksCnt()));
        reportWriter.println(String.format(Messages.getString("Report.MissingSets"),
                profile.getMachinesCnt() - stats.setFoundOk,
                profile.getMachinesCnt() - stats.setFoundOk - stats.setFoundFixComplete,
                profile.getMachinesCnt()));
        reportWriter.println();
    }

    private void writeReportBody(PrintWriter reportWriter, EnumSet<ReportMode> modes) {
        reportWriter.println("=== Scanner Report ===");
        if (modes.contains(ReportMode.GROUP_BY_TYPE_AND_STATUS))
            writeGroupedReport(reportWriter, modes);
        else
            subjects.stream().filter(new ReportSubjectFilter(modes)).sorted(Subject.getComparator())
                    .forEachOrdered(subject -> writeReport(reportWriter, subject, modes));
    }

    private void writeGroupedReport(PrintWriter reportWriter, EnumSet<ReportMode> modes) {
        if (modes.contains(ReportMode.NO_ENTRIES))
            writeGroupedSubjects(reportWriter, modes);
        else
            writeGroupedNotes(reportWriter, modes);
    }

    private void writeGroupedSubjects(PrintWriter reportWriter, EnumSet<ReportMode> modes) {
        final Map<ReportMode, List<Subject>> grouped = subjects.stream()
                .collect(Collectors.groupingBy(this::classifySubject));
        grouped.forEach((mode, list) -> {
            reportWriter.println();
            reportWriter.println("== %s ==".formatted(mode));
            list.stream().sorted(Subject.getComparator()).forEachOrdered(n -> writeReport(reportWriter, n, modes));
            reportWriter.println();
        });
    }

    private ReportMode classifySubject(Subject s) {
        if (s instanceof SubjectSet ss) {
            if (ss.isFixable())
                return ReportMode.FIXABLE;
            if (ss.isMissing())
                return ReportMode.MISSING;
            return ReportMode.OK;
        }
        return ReportMode.OTHERS;
    }

    private void writeGroupedNotes(PrintWriter reportWriter, EnumSet<ReportMode> modes) {
        final Map<String, List<Note>> grouped = subjects.stream().flatMap(s -> s.stream())
                .collect(Collectors.groupingBy(Note::getAbbrv));
        grouped.forEach((abbrv, list) -> {
            reportWriter.println();
            reportWriter.println("== %s ==".formatted(abbrv));
            list.stream().sorted(this::compareNotes).forEachOrdered(n -> writeReport(reportWriter, n, modes));
            reportWriter.println();
        });
    }

    private int compareNotes(Note n1, Note n2) {
        int ret = n1.parent != null && n2.parent != null ? Subject.getComparator().compare(n1.parent, n2.parent) : 0;
        if (ret == 0)
            return n1.getName().compareToIgnoreCase(n2.getName());
        return ret;
    }

    /**
     * Filters subjects matching selected export modes during file writing.
     */
    class ReportSubjectFilter implements Predicate<Subject> {
        private final Set<ReportMode> modes;

        /**
         * Constructs a filter with active export modes.
         *
         * @param modes the configuration modes
         */
        public ReportSubjectFilter(Set<ReportMode> modes) {
            this.modes = modes;
        }

        @Override
        public boolean test(Subject subject) {
            if (subject instanceof SubjectSet ss) {
                if (ss.isOK() && modes.contains(ReportMode.OK))
                    return true;
                if (ss.isFixable() && modes.contains(ReportMode.FIXABLE))
                    return true;
                // Mapped by default
                if (ss.isMissing()) //NOSONAR
                    return true;
                return false;
            } else if (modes.contains(ReportMode.OTHERS))
                return true;
            return false;
        }

    }

    /**
     * Formats and outputs structural subject notes to a text print writer.
     *
     * @param reportWriter the target writer
     * @param subject the subject context
     * @param modes the active configuration options
     */
    private void writeReport(PrintWriter reportWriter, Subject subject, final EnumSet<ReportMode> modes) {
        if (modes.contains(ReportMode.NO_ENTRIES))
            reportWriter.println(subject);
        else {
            if (!modes.contains(ReportMode.COMPACT))
                reportWriter.println(subject);
            subject.notes.stream().filter(new ReportNoteFilter(modes)).forEach(note -> writeReport(reportWriter, note, modes));
        }
    }

    /**
     * Filters individual notes matching chosen report configuration modes.
     */
    class ReportNoteFilter implements Predicate<Note> {
        private final Set<ReportMode> modes;

        /**
         * Constructs a note filter using the provided modes.
         *
         * @param modes the configuration modes
         */
        public ReportNoteFilter(Set<ReportMode> modes) {
            this.modes = modes;
        }

        @Override
        public boolean test(Note note) {
            if (modes.contains(ReportMode.OK) && note instanceof EntryOK)
                return true;
            if (modes.contains(ReportMode.FIXABLE)
                    && (note instanceof EntryAdd || note instanceof EntryMissingDuplicate || note instanceof EntryUnneeded || note instanceof EntryWrongName))
                return true;
            // Mapped by default
            if (note instanceof EntryWrongHash || note instanceof EntryMissing) //NOSONAR
                return true;
            return false;
        }

    }

    /**
     * Formats and writes a single status note to the diagnostic output stream.
     *
     * @param reportWriter the destination writer
     * @param note the note to output
     * @param modes the configured export formats
     */
    private void writeReport(PrintWriter reportWriter, Note note, final EnumSet<ReportMode> modes) {
        if (modes.contains(ReportMode.COMPACT)) {
            if (note.parent != null) {
                if (modes.contains(ReportMode.GROUP_BY_TYPE_AND_STATUS))
                    reportWriter.println("[" + note.parent.getWare().getBaseName() + "]\t" + note.getName() + "\t(" + note.getHash() + ")");
                else
                    reportWriter.println(note.getAbbrv() + " :\t[" + note.parent.getWare().getBaseName() + "]\t" + note.getName() + "\t(" + note.getHash() + ")");
            } else
                reportWriter.println(note.getName() + " (" + note.getHash() + ")");
        } else
            reportWriter.println("\t" + note);
    }

    /**
     * Resolves the configuration profile catalog file location associated with this report.
     *
     * @return the catalog file, or {@code null} if uninitialized
     */
    @Override
    public File getFile() {
        return this.profile != null ? this.profile.getNfo().getFile() : this.file;
    }

    /**
     * Gets the modification timestamp of the associated configuration profile catalog.
     *
     * @return the timestamp in milliseconds
     */
    @Override
    public long getFileModified() {
        return fileModified;
    }

    /**
     * Serializes the current report status and catalog structure to the default work directory path.
     *
     * @param session the target user execution session context
     */
    public void save(final Session session) {
        save(getReportFile(session));
    }

    /**
     * Serializes the current report state data to a specific file destination.
     *
     * @param file the destination File path
     */
    public void save(File file) {
        try (final ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            oos.writeObject(Report.this);
        } catch (final Exception _) {
            // Silently fail to maintain stability on faulty file systems
        }
    }

    /**
     * Restores a serialized report database instance from file storage.
     * <p>
     * This method applies strict deserialization filtering to prevent arbitrary code execution
     * via malicious serialized objects. Only classes explicitly allowlisted for report
     * serialization are permitted during deserialization.
     * </p>
     *
     * @param session the user execution context
     * @param file the original catalog metadata target path
     * 
     * @return the restored Report instance, or {@code null} if restoration fails
     */
    public static Report load(final Session session, final File file) {
        try (final ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(ReportIntf.getReportFile(session, file))))) {
            // Apply deserialization filter to prevent arbitrary code execution
            ois.setObjectInputFilter(ReportDeserializationFilter.createFilter());
            Report report = (Report) ois.readObject();
            report.file = file;
            report.fileModified = ReportIntf.getReportFile(session, file).lastModified();
            report.handler = new ReportTreeDefaultHandler(report);
            return report;
        } catch (final Exception _) {
            // Returns null when serialized compatibility fails after structural codebase
            // updates or when deserialization filter rejects malicious content
        }
        return null; // NOSONAR
    }

    /**
     * Retrieves the runtime identifier assigned to this report.
     *
     * @return the unique integer ID
     */
    public int getId() {
        return id;
    }

    /**
     * Gets the report subject located at the designated index.
     *
     * @param index the target position index
     * 
     * @return the Subject instance
     */
    @Override
    public Subject get(int index) {
        return subjects.get(index);
    }

    /**
     * Returns the total number of subjects tracked by this report.
     *
     * @return the subject list count
     */
    @Override
    public int size() {
        return subjects.size();
    }

    /**
     * Gets a standardized representation string for this report.
     *
     * @return the string "Report"
     */
    @Override
    public String toString() {
        return "Report";
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
