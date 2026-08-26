/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.report;

import java.io.File;
import java.io.Serializable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
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

import jrm.aui.profile.report.ReportTreeDefaultHandler;
import jrm.aui.profile.report.ReportTreeHandler;
import jrm.aui.progress.StatusHandler;
import jrm.aui.status.StatusRendererFactory;
import jrm.locale.Messages;
import jrm.profile.Profile;
import jrm.profile.data.Anyware;
import jrm.security.Session;
import jrm.security.SignedObjectStore;
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
     * Serial version identifier for object serialization compatibility.
     */
    private static final long serialVersionUID = 3L;

    /**
     * The related profile associated with this report.
     *
     * @return the Profile instance
     */
    @Getter
    transient Profile profile = null;

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
    File reportFile = null;

    /**
     * The logical collection of scanned report subjects managed by this report.
     *
     * @return the list of Subject instances
     */
    @Getter List<Subject> subjects;

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
    @Getter Stats stats;

    /**
     * The linked UI presentation tree handler responsible for notifying components of structural changes.
     */
    private transient ReportTreeHandler<Report> handler = null;

    /**
     * Rewires transient parent links and filters after Fory deserialization.
     */
    public void afterLoad() {
        if (subjects == null)
            subjects = Collections.synchronizedList(new ArrayList<>());
        if (stats == null)
            stats = new Stats();
        subjects.forEach(s -> {
            s.parent = this;
            if (s.getNotes() != null)
                s.getNotes().forEach(n -> n.parent = s);
            if (s.ware instanceof Anyware ware)
                ware.initTransient();
        });
        subjectHash = subjects.stream().collect(Collectors.toMap(Subject::getWareName, Function.identity(), (_, _) -> null));
        filterPredicate = new FilterPredicate(new HashSet<>());
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
        public synchronized void incMissingSetCnt() {
            ++missingSetCnt;
        }

        /**
         * Increments the counter of missing ROMs.
         */
        public synchronized void incMissingRomsCnt() {
            ++missingRomsCnt;
        }

        /**
         * Increments the counter of missing CHD disks.
         */
        public synchronized void incMissingDisksCnt() {
            ++missingDisksCnt;
        }

        /**
         * Increments the counter of missing audio samples.
         */
        public synchronized void incMissingSamplesCnt() {
            ++missingSamplesCnt;
        }

        /**
         * Increments the counter of fixable ROMs.
         */
        public synchronized void incFixableRomsCnt() {
            ++fixableRomsCnt;
        }

        /**
         * Increments the counter of fixable CHD disks.
         */
        public synchronized void incFixableDisksCnt() {
            ++fixableDisksCnt;
        }

        /**
         * Increments the counter of unneeded sets.
         */
        public synchronized void incSetUnneeded() {
            ++setUnneeded;
        }

        /**
         * Increments the counter of missing sets.
         */
        public synchronized void incSetMissing() {
            ++setMissing;
        }

        /**
         * Increments the counter of sets found.
         */
        public synchronized void incSetFound() {
            ++setFound;
        }

        /**
         * Increments the counter of perfectly matching sets.
         */
        public synchronized void incSetFoundOk() {
            ++setFoundOk;
        }

        /**
         * Increments the counter of partially fixable found sets.
         */
        public synchronized void incSetFoundFixPartial() {
            ++setFoundFixPartial;
        }

        /**
         * Increments the counter of fully fixable found sets.
         */
        public synchronized void incSetFoundFixComplete() {
            ++setFoundFixComplete;
        }

        /**
         * Increments the counter of sets to create.
         */
        public synchronized void incSetCreate() {
            ++setCreate;
        }

        /**
         * Increments the counter of partially constructible sets.
         */
        public synchronized void incSetCreatePartial() {
            ++setCreatePartial;
        }

        /**
         * Increments the counter of fully constructible sets.
         */
        public synchronized void incSetCreateComplete() {
            ++setCreateComplete;
        }

        /**
         * Resets all accumulated validation statistics and counters back to zero.
         */
        public synchronized void clear() {
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
    private transient StatusHandler statusHandler = null;

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
    public void write(final Session session) {
        new ReportLogWriter(this).write(session);
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
        save(session, getReportFile(session));
    }

    /**
     * Serializes the current report state data to a specific file destination with an HMAC integrity envelope.
     *
     * @param session the user execution context (HMAC key is a per-user random secret)
     * @param file the destination File path
     */
    public void save(final Session session, final File file) {
        try {
            SignedObjectStore.write(session, file, Report.this, SignedObjectStore.Codec.REPORT);
        } catch (final Exception _) {
            // Silently fail to maintain stability on faulty file systems
        }
    }

    /**
     * Restores a serialized report database instance from file storage.
     * <p>
     * Prefer signed payloads written by {@link #save(Session, File)}. Unsigned and legacy work-path HMAC
     * streams are rejected; the scan is rerun and the report is rewritten in the signed format.
     * </p>
     *
     * @param session the user execution context
     * @param file the original catalog metadata target path
     * 
     * @return the restored Report instance, or {@code null} if restoration fails
     */
    public static Report load(final Session session, final File file) {
        try {
            final var reportFile = ReportIntf.getReportFile(session, file);
            Report report = (Report) SignedObjectStore.read(session, reportFile, SignedObjectStore.Codec.REPORT);
            report.file = file;
            report.fileModified = reportFile.lastModified();
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
     * Lists missing or partial romset titles from this report.
     * <p>
     * Includes completely missing sets, found-but-incomplete sets, and
     * partially creatable sets. Fully OK and fully fixable/creatable sets
     * are omitted.
     *
     * @return missing or partial {@link SubjectSet}s, sorted by ware name
     */
    public List<SubjectSet> listIncompleteTitles() {
        return subjects.stream()
                .filter(SubjectSet.class::isInstance)
                .map(SubjectSet.class::cast)
                .filter(Report::isIncompleteTitle)
                .sorted(Subject.getComparator())
                .toList();
    }

    /**
     * Formats the status-bar summary followed by the missing or partial title list.
     *
     * @return copyable summary text
     */
    public String getSummaryText() {
        final var sb = new StringBuilder();
        sb.append(stats.getStatus());
        final List<SubjectSet> titles = listIncompleteTitles();
        sb.append(System.lineSeparator()).append(System.lineSeparator());
        sb.append(Messages.getString("Report.MissingOrPartialTitles"));
        sb.append(System.lineSeparator());
        if (titles.isEmpty()) {
            sb.append(Messages.getString("Report.NoIncompleteTitles"));
            sb.append(System.lineSeparator());
        } else {
            for (final SubjectSet ss : titles) {
                sb.append(incompleteTitleLabel(ss));
                sb.append('\t');
                sb.append(incompleteTitleName(ss));
                sb.append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    /**
     * Formats a copyable text report: summary, missing or partial titles, then every subject and note.
     *
     * @return the full copyable report text
     */
    public String toCopyableText() {
        final var sb = new StringBuilder(getSummaryText());
        sb.append(System.lineSeparator());
        sb.append(Messages.getString("Report.FullReport"));
        sb.append(System.lineSeparator());
        for (final Subject subject : subjects) {
            sb.append(subject);
            sb.append(System.lineSeparator());
            for (final Note note : subject) {
                sb.append('\t');
                sb.append(note);
                sb.append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    /**
     * Resolves the DAT export type for a fixDAT of the given profile.
     * Software-list-only profiles use {@link jrm.profile.manager.Export.ExportType#SOFTWARELIST};
     * otherwise Logiqx {@link jrm.profile.manager.Export.ExportType#DATAFILE}.
     *
     * @param profile the scanned profile, may be {@code null}
     * @return the export type to use for a fixDAT
     */
    public static jrm.profile.manager.Export.ExportType resolveFixDatType(final Profile profile) {
        if (profile == null)
            return jrm.profile.manager.Export.ExportType.DATAFILE;
        final boolean hasMachines = !profile.getMachineListList().isEmpty() && profile.getMachineListList().get(0).size() > 0;
        final boolean hasSoftware = !profile.getMachineListList().getSoftwareListList().isEmpty();
        if (!hasMachines && hasSoftware)
            return jrm.profile.manager.Export.ExportType.SOFTWARELIST;
        return jrm.profile.manager.Export.ExportType.DATAFILE;
    }

    static boolean isIncompleteTitle(final SubjectSet ss) {
        return switch (ss.getStatus()) {
            case MISSING -> true;
            case FOUND -> ss.hasNotes() && !ss.isFixable();
            case CREATE, CREATEFULL -> !ss.isFixable();
            default -> false;
        };
    }

    static String incompleteTitleLabel(final SubjectSet ss) {
        return switch (ss.getStatus()) {
            case MISSING -> Messages.getString("Report.TitleMissing");
            case CREATE, CREATEFULL -> Messages.getString("Report.TitlePartialCreate");
            default -> Messages.getString("Report.TitlePartial");
        };
    }

    static String incompleteTitleName(final SubjectSet ss) {
        if (ss.getWare() != null && ss.getWare().getBaseName() != null && !ss.getWare().getBaseName().isEmpty())
            return ss.getWare().getBaseName();
        return ss.getWareName();
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

}
