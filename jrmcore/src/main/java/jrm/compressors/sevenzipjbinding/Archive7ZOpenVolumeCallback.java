package jrm.compressors.sevenzipjbinding;

import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

import net.sf.sevenzipjbinding.IArchiveOpenVolumeCallback;
import net.sf.sevenzipjbinding.IInStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;

/**
 * Callback implementation for opening 7-Zip archive volumes using SevenZipJBinding. This class manages the opening of archive
 * volumes by providing input streams for specified filenames. It maintains a mapping of opened RandomAccessFile instances to ensure
 * that they are properly managed and closed when necessary. The callback is designed to work with 7-Zip archives that may consist
 * of multiple volumes, allowing for seamless access to each volume as needed during archive operations.
 */
public class Archive7ZOpenVolumeCallback implements IArchiveOpenVolumeCallback {
    /**
     * A reference to the Closeables instance used for managing closeable resources. This allows the callback to add opened
     * RandomAccessFile instances to the collection of closeables, ensuring that they are properly closed when the archive
     * operations are completed.
     */
    private final Closeables closeables;

    /**
     * Constructs a new Archive7ZOpenVolumeCallback instance with the specified Closeables instance for managing closeable
     * resources.
     * 
     * @param closeables the Closeables instance used for managing closeable resources, allowing the callback to add opened
     *        RandomAccessFile instances to the collection of closeables for proper resource management
     */
    public Archive7ZOpenVolumeCallback(Closeables closeables) {
        this.closeables = closeables;
    }

    /**
     * A mapping of filenames to their corresponding opened RandomAccessFile instances. This map is used to keep track of the opened
     * files for each volume, allowing the callback to manage and reuse the RandomAccessFile instances as needed when opening
     * multiple volumes of a 7Z archive. The map ensures that each filename is associated with its respective RandomAccessFile
     * instance, enabling efficient access to the archive volumes during operations.
     */
    private Map<String, RandomAccessFile> openedRandomAccessFileList = new HashMap<>();

    /**
     * Retrieves the property value associated with the specified property ID. In this implementation, it always returns null,
     * indicating that no specific properties are provided by this callback. This method can be overridden in subclasses if there is
     * a need to provide specific properties related to the archive volume during operations.
     * 
     * @param propID the property ID for which to retrieve the value (e.g., PropID.NAME)
     * 
     * @return null, indicating that no specific properties are provided by this callback
     * 
     * @throws SevenZipException if an error occurs while retrieving the property value (not applicable in this implementation)
     */
    public Object getProperty(PropID propID) throws SevenZipException {
        return null;
    }

    /**
     * Retrieves an input stream for the specified filename. This method checks if a RandomAccessFile instance for the given
     * filename already exists in the openedRandomAccessFileList map. If it does, it seeks to the beginning of the file and returns
     * a new RandomAccessFileInStream based on that instance. If it does not exist, it creates a new RandomAccessFile instance for
     * the filename, adds it to the closeables collection for proper resource management, and stores it in the
     * openedRandomAccessFileList map before returning a new RandomAccessFileInStream based on the newly created instance. If a
     * FileNotFoundException occurs during this process, it returns null, indicating that the specified file could not be found. If
     * any other exception occurs, it wraps it in an Archive7ZVolCBException and throws it.
     * 
     * @param filename the name of the file for which to retrieve the input stream
     * 
     * @return an IInStream instance representing the input stream for the specified filename, or null if the file could not be
     *         found
     * 
     * @throws SevenZipException if an error occurs while retrieving the input stream (e.g., if an unexpected exception occurs
     *         during file access)
     */
    public IInStream getStream(String filename) throws SevenZipException {
        try {
            RandomAccessFile randomAccessFile = openedRandomAccessFileList.get(filename);
            if (randomAccessFile != null)
                randomAccessFile.seek(0);
            else {
                randomAccessFile = new RandomAccessFile(filename, "r");
                closeables.addCloseables(randomAccessFile);
                openedRandomAccessFileList.put(filename, randomAccessFile);
            }
            return new RandomAccessFileInStream(randomAccessFile);
        } catch (FileNotFoundException _) {
            return null; // We return always null in this case
        } catch (Exception e) {
            throw new Archive7ZVolCBException(e);
        }
    }

    /**
     * A custom runtime exception class for handling errors related to 7-Zip volume callback operations. This exception is used to
     * wrap any unexpected exceptions that may occur during the retrieval of input streams or other operations within the
     * Archive7ZOpenVolumeCallback. By using a custom exception, it allows for more specific error handling and messaging related to
     * 7-Zip archive volume processing, while still providing the underlying cause of the error through the wrapped exception.
     */
    class Archive7ZVolCBException extends RuntimeException {
        /**
         * Constructs a new Archive7ZVolCBException instance with the specified cause. This constructor allows for wrapping an
         * underlying exception that may have occurred during the operations of the Archive7ZOpenVolumeCallback, providing a more
         * specific context for the error while still retaining the original exception information for debugging and error handling
         * purposes.
         * 
         * @param e the underlying exception that caused this Archive7ZVolCBException to be thrown
         */
        public Archive7ZVolCBException(Throwable e) {
            super(e);
        }
    }
}