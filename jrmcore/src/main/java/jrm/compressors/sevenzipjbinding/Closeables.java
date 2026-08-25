package jrm.compressors.sevenzipjbinding;

import java.io.Closeable;
import java.util.List;

/**
 * Interface representing a collection of closeable resources. It provides methods to retrieve the list of closeables and to add new
 * closeable resources to the collection. This interface is intended to be implemented by classes that manage multiple closeable
 * resources, allowing for centralized management and cleanup of those resources when necessary.
 */
public interface Closeables {
    /**
     * Retrieves the list of closeable resources managed by this collection. This method returns a list of Closeable instances that
     * are currently being tracked and managed by the implementing class. The returned list can be used to access and manage the
     * individual closeable resources as needed.
     * 
     * @return a list of Closeable instances representing the closeable resources managed by this collection
     */
    List<Closeable> getCloseables();

    /**
     * Adds a new closeable resource to the collection. This method allows for adding a Closeable instance to the list of resources
     * being managed by the implementing class. The added closeable will be included in the management and cleanup processes when
     * necessary.
     * 
     * @param closeable the Closeable instance to be added to the collection
     */
    public void addCloseables(Closeable closeable);
}
