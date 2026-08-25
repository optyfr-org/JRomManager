package jrm.misc;

/**
 * Interface that provides offset index information for UI progress reporting in multithreaded tasks. Allows threads to locate their
 * unique logical display position and recycle unused positions.
 * 
 * @author optyfr
 */
public interface OffsetProvider {
    /**
     * Retrieves the logical progress reporting offset index for the calling thread.
     * 
     * @return the unique 0-based offset index, or {@code -1} if no offset is allocated
     */
    int getOffset();

    /**
     * Returns an array containing all currently unassigned and recycled logical offset indexes.
     * 
     * @return an array of available offset indexes
     */
    int[] freeOffsets();
}
