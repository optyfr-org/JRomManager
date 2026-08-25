package jrm.profile.report;

import java.io.Serializable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import jrm.aui.status.StatusRendererFactory;
import jrm.profile.data.AnywareBase;
import lombok.Getter;

/**
 * Represents a logical report subject, typically a container file (such as a zip file) or a retro-gaming system romset.
 * <p>
 * A Subject acts as an intermediate group in the reporting hierarchy, extending {@link AbstractList} of {@link Note}s to manage and
 * filter individual leaf status notes associated with the target metadata.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
public abstract class Subject extends AbstractList<Note> implements StatusRendererFactory, Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The retro-gaming system or romset model associated with this subject.
     *
     * @return the associated AnywareBase ware
     */
    protected @Getter AnywareBase ware;

    /**
     * The underlying collection of leaf notes describing detailed validation issues or statuses.
     *
     * @return the list of Note instances
     */
    private @Getter List<Note> notes;

    /**
     * The root report containing this subject.
     */
    protected transient Report parent;

    /**
     * The transient index identifier assigned to this subject.
     */
    protected transient int id = -1;

    /**
     * Constructs a new Subject associated with the specified gaming system model.
     *
     * @param machine the gaming system or romset model to associate
     */
    protected Subject(final AnywareBase machine) {
        ware = machine;
        notes = new ArrayList<>();
    }

    /**
     * Constructs a cloned Subject from an originating instance, applying a new filtered collection of notes.
     *
     * @param org the originating Subject instance to copy
     * @param notes the list of filtered Note instances to populate in the clone
     */
    protected Subject(Subject org, final List<Note> notes) {
        ware = org.ware;
        id = org.id;
        this.notes = notes;
    }

    /**
     * Clones this subject based on the specified set of active filtering options.
     *
     * @param filterOptions the set of active filtering options to apply
     * 
     * @return the cloned, filtered Subject instance
     */
    public abstract Subject clone(Set<FilterOptions> filterOptions);

    /**
     * Returns a stream of leaf notes belonging to this subject, filtered according to the active options.
     *
     * @param filterOptions the active filtering options to apply
     * 
     * @return a stream of filtered Note instances
     */
    public abstract Stream<Note> stream(Set<FilterOptions> filterOptions);

    /**
     * Appends a status note to this subject's collection and configures its parent reference.
     *
     * @param note the status note to add
     * 
     * @return {@code true} if the note was successfully appended; {@code false} otherwise
     */
    @Override
    public boolean add(final Note note) {
        note.parent = this;
        return notes.add(note);
    }

    /**
     * Gets the full display name of the gaming system model or ware, if available.
     *
     * @return the full name string of the ware, or an empty string if the ware is {@code null}
     */
    public String getWareName() {
        if (ware != null)
            return ware.getFullName();
        return ""; //$NON-NLS-1$
    }

    /**
     * Increments or updates the summary metrics in the parent report based on this subject's state.
     */
    public abstract void updateStats();

    /**
     * Returns a localized text summary of this subject's overall status.
     *
     * @return the localized string summary
     */
    @Override
    public abstract String toString();

    /**
     * Gets the unique identifier assigned to this subject.
     *
     * @return the integer identifier, or {@code -1} if uninitialized
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the total number of notes contained in this subject.
     *
     * @return the note list size
     */
    @Override
    public int size() {
        return notes.size();
    }

    /**
     * Retrieves the note located at the specified index position.
     *
     * @param index the 0-based position index to query
     * 
     * @return the Note instance at the specified index
     * 
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    @Override
    public Note get(int index) {
        return notes.get(index);
    }

    /**
     * Returns the identity-based hash code of this instance.
     *
     * @return the identity hash code
     */
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * Returns a comparator for sorting subjects alphabetically by their associated ware name case-insensitively.
     *
     * @return the subject sorting comparator
     */
    public static Comparator<Subject> getComparator() {
        return (s1, s2) -> {
            if (s1.ware == null) {
                if (s2.ware == null)
                    return 0;
                return -1;
            }
            if (s2.ware == null)
                return 1;
            return s1.ware.getName().compareToIgnoreCase(s2.ware.getName());
        };
    }
}
