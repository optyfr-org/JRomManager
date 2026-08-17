package jrm.profile.report;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jrm.locale.Messages;
import jrm.profile.data.AnywareBase;

/**
 * Represents a standard retro-gaming system romset validation subject within a profile scan report.
 * <p>
 * This subject manages an active set status (such as missing, found, or repairable) and compiles associated notes detailing
 * file-level discrepancies.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
public class SubjectSet extends Subject {
    /**
     * Serial version identifier for object serialization compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The current validation status of the target romset.
     */
    private Status status = Status.UNKNOWN;

    /**
     * Enumerates all possible romset validation status levels.
     */
    public enum Status {
        /**
         * Unknown validation status.
         */
        UNKNOWN,
        /**
         * The romset is present on disk.
         */
        FOUND,
        /**
         * The romset is missing but can be partially assembled or created from local resources.
         */
        CREATE,
        /**
         * The romset is missing but can be fully assembled or created from local resources.
         */
        CREATEFULL,
        /**
         * The romset is present but is not needed by the active profile database.
         */
        UNNEEDED,
        /**
         * The romset is completely missing and cannot be built from local resources.
         */
        MISSING;
    }

    /**
     * Constructs a new SubjectSet for the given retro-gaming machine.
     *
     * @param machine the target retro-gaming system or romset model
     */
    public SubjectSet(final AnywareBase machine) {
        super(machine);
    }

    /**
     * Clones a SubjectSet from an originating instance, applying a filtered list of notes.
     *
     * @param org the originating SubjectSet to replicate
     * @param notes the list of filtered Note instances
     */
    private SubjectSet(final SubjectSet org, final List<Note> notes) {
        super(org, notes);
    }

    /**
     * Clones this SubjectSet according to the active filtering options.
     *
     * @param filterOptions the active filtering options
     * 
     * @return the cloned, filtered SubjectSet instance
     */
    @Override
    public Subject clone(final Set<FilterOptions> filterOptions) {
        SubjectSet clone;
        clone = new SubjectSet(this, filter(filterOptions));
        clone.status = status;
        return clone;
    }

    /**
     * Filters and sorts notes belonging to this subject based on the active filtering options.
     *
     * @param filterOptions the active filtering options to apply
     * 
     * @return a sorted, filtered list of Note instances
     */
    public List<Note> filter(final Set<FilterOptions> filterOptions) {
        return stream(filterOptions).sorted(Note.getComparator())
                .collect(Collectors.toList()); // NOSONAR
    }

    /**
     * Streams notes belonging to this subject, filtering out perfect matches unless showing OK items is enabled.
     *
     * @param filterOptions the active filtering options
     * 
     * @return a filtered stream of Note instances
     */
    @Override
    public Stream<Note> stream(Set<FilterOptions> filterOptions) {
        return notes.stream().sorted(Note.getComparator()).filter(n -> !(!filterOptions.contains(FilterOptions.SHOWOK) && n instanceof EntryOK));
    }

    /**
     * Configures this subject set's status to {@link Status#MISSING}.
     */
    public void setMissing() {
        status = Status.MISSING;
    }

    /**
     * Configures this subject set's status to {@link Status#FOUND}.
     */
    public void setFound() {
        status = Status.FOUND;
    }

    /**
     * Configures this subject set's status to {@link Status#UNNEEDED}.
     */
    public void setUnneeded() {
        status = Status.UNNEEDED;
    }

    /**
     * Configures this subject set's status to {@link Status#CREATE}.
     */
    public void setCreate() {
        status = Status.CREATE;
    }

    /**
     * Configures this subject set's status to {@link Status#CREATEFULL}.
     */
    public void setCreateFull() {
        status = Status.CREATEFULL;
    }

    /**
     * Gets the current validation status of this subject set.
     *
     * @return the active Status enum value
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Checks if this subject set contains any status notes other than standard successful matches.
     *
     * @return {@code true} if there is at least one non-OK validation note; {@code false} otherwise
     */
    public boolean hasNotes() {
        return notes.stream().filter(n -> !(n instanceof EntryOK)).count() > 0;
    }

    /**
     * Checks if all issues within this subject set can be fully repaired.
     * <p>
     * A set is fully repairable if it does not contain any completely missing or mismatched hash files.
     *
     * @return {@code true} if the romset is fully repairable; {@code false} otherwise
     */
    public boolean isFixable() {
        return notes.stream().filter(n -> (n instanceof EntryMissing || n instanceof EntryWrongHash)).count() == 0;
    }

    /**
     * Checks if this subject set contains at least one repairable issue.
     *
     * @return {@code true} if there are repairable discrepancies; {@code false} otherwise
     */
    public boolean hasFix() {
        return notes.stream().filter(n -> !(n instanceof EntryOK || n instanceof EntryMissing || n instanceof EntryWrongHash)).count() > 0;
    }

    /**
     * Checks if the validation status is currently configured to {@link Status#FOUND}.
     *
     * @return {@code true} if found; {@code false} otherwise
     */
    public boolean isFound() {
        return status == Status.FOUND;
    }

    /**
     * Checks if the validation status is currently configured to {@link Status#MISSING}.
     *
     * @return {@code true} if missing; {@code false} otherwise
     */
    public boolean isMissing() {
        return status == Status.MISSING;
    }

    /**
     * Checks if the validation status is currently configured to {@link Status#UNNEEDED}.
     *
     * @return {@code true} if unneeded; {@code false} otherwise
     */
    public boolean isUnneeded() {
        return status == Status.UNNEEDED;
    }

    /**
     * Checks if the romset is perfectly OK on disk with no validation discrepancies.
     *
     * @return {@code true} if found and free of issues; {@code false} otherwise
     */
    public boolean isOK() {
        return isFound() && !hasNotes();
    }

    /**
     * Formats a localized text summary of the status of this subject set.
     *
     * @return the localized string summary
     */
    @Override
    public String toString() {
        return switch (status) {
            case MISSING -> String.format(Messages.getString("SubjectSet.Missing"), ware.getFullName(), ware.getDescription()); //$NON-NLS-1$
            case UNNEEDED -> String.format(Messages.getString("SubjectSet.Unneeded"), ware.getFullName(), ware.getDescription()); //$NON-NLS-1$
            case FOUND -> {
                if (hasNotes()) {
                    if (isFixable())
                        yield String.format(Messages.getString("SubjectSet.FoundNeedFixes"), ware.getFullName(), ware.getDescription()); //$NON-NLS-1$
                    yield String.format(Messages.getString("SubjectSet.FoundIncomplete"), ware.getFullName(), ware.getDescription()); //$NON-NLS-1$
                }
                yield String.format(Messages.getString("SubjectSet.Found"), ware.getFullName(), ware.getDescription()); //$NON-NLS-1$
            }
            case CREATE, CREATEFULL -> {
                if (isFixable())
                    yield String.format(Messages.getString("SubjectSet.MissingTotallyCreated"), ware.getFullName(), ware.getDescription()); //$NON-NLS-1$
                yield String.format(Messages.getString("SubjectSet.MissingPartiallyCreated"), ware.getFullName(), ware.getDescription()); //$NON-NLS-1$
            }
            default -> String.format(Messages.getString("SubjectSet.Unknown"), ware.getFullName(), ware.getDescription()); //$NON-NLS-1$
        };
    }

    /**
     * Renders an HTML-styled diagnostic document summarizing this subject set.
     *
     * @return the styled HTML diagnostic document string
     */
    @Override
    public String getDocument() {
        final String machine_name = toBlue(ware.getFullName());
        final String machine_description = toPurple(ware.getDescription());
        return switch (status) {
            case MISSING -> toDocument(String.format(escape(Messages.getString("SubjectSet.Missing")), machine_name, machine_description)); //$NON-NLS-1$
            case UNNEEDED -> toDocument(String.format(escape(Messages.getString("SubjectSet.Unneeded")), machine_name, machine_description)); //$NON-NLS-1$
            case FOUND -> {
                if (hasNotes()) {
                    if (isFixable())
                        yield toDocument(String.format(escape(Messages.getString("SubjectSet.FoundNeedFixes")), machine_name, machine_description)); //$NON-NLS-1$
                    yield toDocument(String.format(escape(Messages.getString("SubjectSet.FoundIncomplete")), machine_name, machine_description)); //$NON-NLS-1$
                }
                yield toDocument(String.format(escape(Messages.getString("SubjectSet.Found")), machine_name, machine_description)); //$NON-NLS-1$
            }
            case CREATE, CREATEFULL -> {
                if (isFixable())
                    yield toDocument(String.format(escape(Messages.getString("SubjectSet.MissingTotallyCreated")), machine_name, machine_description)); //$NON-NLS-1$
                yield toDocument(String.format(escape(Messages.getString("SubjectSet.MissingPartiallyCreated")), machine_name, machine_description)); //$NON-NLS-1$
            }
            default -> toDocument(String.format(escape(Messages.getString("SubjectSet.Unknown")), machine_name, machine_description)); //$NON-NLS-1$
        };
    }

    /**
     * Increments the appropriate summary validation metrics in the parent report depending on the active status.
     */
    @Override
    public void updateStats() {
        switch (status) {
            case MISSING -> parent.getStats().incSetMissing();
            case UNNEEDED -> parent.getStats().incSetUnneeded();
            case FOUND -> {
                parent.getStats().incSetFound();
                if (hasNotes()) {
                    if (isFixable())
                        parent.getStats().incSetFoundFixComplete();
                    else
                        parent.getStats().incSetFoundFixPartial();
                } else
                    parent.getStats().incSetFoundOk();
            }
            case CREATE, CREATEFULL -> {
                parent.getStats().incSetCreate();
                if (isFixable())
                    parent.getStats().incSetCreateComplete();
                else
                    parent.getStats().incSetCreatePartial();
            }
            default -> {
                /* no stats update for UNKNOWN */ }
        }
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
