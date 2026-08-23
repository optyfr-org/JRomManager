package jrm.compressors.sevenzipjbinding;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.IntStream;

import jrm.misc.IOUtils;
import jrm.misc.Log;
import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.IOutCreateCallback;
import net.sf.sevenzipjbinding.IOutItemAllFormats;
import net.sf.sevenzipjbinding.ISequentialInStream;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.OutItemFactory;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.impl.RandomAccessFileOutStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;

/**
 * An abstract callback implementation for creating or modifying archives using SevenZipJBinding. It manages the input streams for
 * existing archive entries, handles new entries to be added, and processes entries to be renamed or duplicated during the archive
 * creation or modification process.
 */
public abstract class CloseCreateCallback implements IOutCreateCallback<IOutItemAllFormats> {
    /**
     * A private inner class implementing the IArchiveExtractCallback interface, used for extracting specific entries from the
     * archive during the process of handling duplicate entries. It manages the output streams for writing extracted data to
     * temporary files, and is used to facilitate the extraction of entries that need to be duplicated in the modified archive.
     */
    private final class ExtractCallback implements IArchiveExtractCallback {
        /**
         * A mapping of archive entry indices to RandomAccessFile instances used for writing extracted data during the extraction
         * process. This allows for managing the output streams associated with each entry being extracted, specifically for
         * handling duplicate entries that need to be extracted and written to temporary files.
         */
        private final HashMap<Integer, RandomAccessFile> rafs2;

        /**
         * Constructs a new ExtractCallback instance with the specified mapping of archive entry indices to RandomAccessFile
         * instances.
         * 
         * @param rafs2 a mapping of archive entry indices to RandomAccessFile instances for writing extracted data
         */
        private ExtractCallback(HashMap<Integer, RandomAccessFile> rafs2) {
            this.rafs2 = rafs2;
        }

        /**
         * Sets the result of the extraction operation. In this implementation, no specific handling is needed for the operation
         * result, so the method is left empty. This method can be overridden by subclasses if any specific actions are required
         * based on the outcome of the extraction operation.
         * 
         * @param extractOperationResult the result of the extraction operation (e.g., OK, CRC_ERROR, etc.)
         * 
         * @throws SevenZipException if an error occurs while setting the operation result (not applicable in this implementation)
         */
        @Override
        public void setTotal(final long total) throws SevenZipException {
            // do nothing
        }

        /**
         * Sets the amount of data that has been completed during the extraction process. In this implementation, no specific
         * handling is needed for the completion status, so the method is left empty. This method can be overridden by subclasses if
         * any specific actions are required based on the progress of the extraction operation.
         * 
         * @param complete the amount of data that has been completed during extraction
         * 
         * @throws SevenZipException if an error occurs while setting the completion status (not applicable in this implementation)
         */
        @Override
        public void setCompleted(final long complete) throws SevenZipException {
            // do nothing
        }

        /**
         * Sets the result of the extraction operation for the current entry. In this implementation, no specific handling is needed
         * for the operation result, so the method is left empty. This method can be overridden by subclasses if any specific
         * actions are required based on the outcome of the extraction operation for each entry.
         * 
         * @param extractOperationResult the result of the extraction operation for the current entry (e.g., OK, CRC_ERROR, etc.)
         * 
         * @throws SevenZipException if an error occurs while setting the operation result for the current entry (not applicable in
         *         this implementation)
         */
        @Override
        public void setOperationResult(final ExtractOperationResult extractOperationResult) throws SevenZipException {
            // do nothing
        }

        /**
         * Prepares the extraction operation for the current entry. In this implementation, no specific preparation is needed, so
         * the method is left empty. This method can be overridden by subclasses if any setup is required before extracting an
         * entry, such as initializing resources or setting flags based on the extraction mode.
         * 
         * @param extractAskMode the mode of extraction being performed (e.g., EXTRACT, TEST, etc.)
         * 
         * @throws SevenZipException if an error occurs during preparation (not applicable in this implementation)
         */
        @Override
        public void prepareOperation(final ExtractAskMode extractAskMode) throws SevenZipException {
            // do nothing
        }

        /**
         * Provides an output stream for writing the extracted data of the current entry. If the extraction mode is EXTRACT, it
         * returns a new RandomAccessFileOutStream associated with the corresponding RandomAccessFile for the entry index. If the
         * extraction mode is not EXTRACT, it returns null, indicating that no output stream is needed for other modes (e.g., TEST).
         * 
         * @param idx the index of the current entry being extracted
         * @param extractAskMode the mode of extraction being performed (e.g., EXTRACT, TEST, etc.)
         * 
         * @return an ISequentialOutStream for writing extracted data if in EXTRACT mode, or null for other modes
         * 
         * @throws SevenZipException if an error occurs while providing the output stream
         */
        @Override
        public ISequentialOutStream getStream(final int idx, final ExtractAskMode extractAskMode) throws SevenZipException {
            if (ExtractAskMode.EXTRACT == extractAskMode)
                return new RandomAccessFileOutStream(rafs2.get(idx));
            return null;
        }
    }

    /**
     * The NArchiveBase instance representing the archive being created or modified. This instance provides access to the archive's
     * properties, such as the list of entries, and manages the operations for adding, deleting, renaming, and copying entries
     * during the archive creation or modification process.
     */
    private final NArchiveBase nArchive;
    /**
     * A mapping of archive entry indices to temporary File instances used for storing data during the archive creation or
     * modification process. This allows for managing the temporary files associated with each entry, especially for new entries
     * being added or existing entries being duplicated.
     */
    private final HashMap<Integer, File> tmpfiles;
    /**
     * A mapping of archive entry indices to RandomAccessFile instances used for reading existing entries from the archive during
     * the creation or modification process. This allows for managing the input streams associated with each entry, especially for
     * handling entries that are being renamed or duplicated.
     */
    private final HashMap<Integer, RandomAccessFile> rafs;
    /**
     * A mapping of archive entry indices to their corresponding file paths that are marked for deletion in the modified archive.
     * This allows for tracking which entries should be removed from the archive during the creation or modification process.
     */
    private HashMap<Integer, String> idxToDelete = new HashMap<>();
    /**
     * A mapping of archive entry indices to their new file paths for entries that are marked for renaming in the modified archive.
     * This allows for tracking which entries should be renamed and what their new paths should be during the creation or
     * modification process.
     */
    private HashMap<Integer, String> idxToRename = new HashMap<>();
    /**
     * A list of objects representing entries that are marked for duplication in the modified archive. Each object array contains
     * the original entry index, the new file path for the duplicated entry, and a placeholder for the new index assigned during
     * processing. This allows for tracking which entries should be duplicated and what their new paths should be during the
     * creation or modification process.
     */
    private ArrayList<Object[]> idxToDuplicate = new ArrayList<>();
    /**
     * The index of the current entry being processed during the creation or modification process. This is used to keep track of
     * which entry is currently being handled, especially when determining how to manage the input streams and properties for each
     * entry based on whether it is being deleted, renamed, duplicated, or added as a new entry.
     */
    private int oldIdx = 0;
    /**
     * The total number of entries in the original archive before any modifications are applied. This is used to determine the range
     * of existing entries that need to be processed and to manage the indices for new entries being added or existing entries being
     * duplicated during the creation or modification process.
     */
    private int oldTot = 0;

    /**
     * Constructs a new CloseCreateCallback instance with the specified parameters. It initializes the mappings for entries to be
     * deleted, renamed, and duplicated based on the properties of the provided NArchiveBase instance. It also prepares the internal
     * state for processing the entries during the archive creation or modification process.
     * 
     * @param nArchive the NArchiveBase instance representing the archive being created or modified
     * @param tmpfiles a mapping of archive entry indices to temporary File instances for managing data during the process
     * @param rafs a mapping of archive entry indices to RandomAccessFile instances for reading existing entries during the process
     * 
     * @throws SevenZipException if an error occurs while initializing the callback (e.g., accessing archive properties)
     */
    protected CloseCreateCallback(NArchiveBase nArchive, HashMap<Integer, File> tmpfiles, HashMap<Integer, RandomAccessFile> rafs) throws SevenZipException {
        this.nArchive = nArchive;
        this.tmpfiles = tmpfiles;
        this.rafs = rafs;
        if (this.nArchive.getIInArchive() == null)
            return;
        oldTot = this.nArchive.getIInArchive().getNumberOfItems();
        final Map<String, List<String>> copiesToKeys = new HashMap<>();
        for (final Entry<String, String> e : this.nArchive.getToCopy().entrySet())
            copiesToKeys.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        for (int i = 0; i < oldTot; i++) {
            final String path = this.nArchive.getIInArchive().getProperty(i, PropID.PATH).toString();
            if (this.nArchive.getToDelete().contains(path))
                idxToDelete.put(i, path);
            if (this.nArchive.getToRename().containsKey(path))
                idxToRename.put(i, this.nArchive.getToRename().get(path));
            final List<String> keys = copiesToKeys.get(path);
            if (keys != null)
                for (final String key : keys)
                    idxToDuplicate.add(new Object[] { i, key, null });
        }
        if (this.nArchive.getToDelete().size() != idxToDelete.size())
            Log.err(() -> "to_delete:" + this.nArchive.getToDelete().size() + "!=" + idxToDelete.size()); //$NON-NLS-1$ //$NON-NLS-2$
        if (this.nArchive.getToRename().size() != idxToRename.size())
            Log.err(() -> "to_rename:" + this.nArchive.getToRename().size() + "!=" + idxToRename.size()); //$NON-NLS-1$ //$NON-NLS-2$
        if (this.nArchive.getToCopy().size() != idxToDuplicate.size())
            Log.err(() -> "to_duplicate:" + this.nArchive.getToCopy().size() + "!=" + idxToDuplicate.size()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public void setOperationResult(final boolean operationResultOk) throws SevenZipException {
        // do nothing
    }

    /**
     * Provides an input stream for reading the data of the entry at the specified index during the archive creation or modification
     * process. It determines whether the entry is a new entry being added, an existing entry being renamed, or an existing entry
     * being duplicated, and returns the appropriate input stream based on the entry's status. If the entry is a new entry being
     * added, it returns a RandomAccessFileInStream for the corresponding temporary file. If the entry is an existing entry being
     * renamed or duplicated, it returns a RandomAccessFileInStream for the corresponding RandomAccessFile. If the index exceeds the
     * range of entries to be processed, it returns null.
     * 
     * @param index the index of the entry for which to provide the input stream
     * 
     * @return an ISequentialInStream for reading the entry's data, or null if the index exceeds the range of entries to be
     *         processed
     * 
     * @throws SevenZipException if an error occurs while providing the input stream (e.g., accessing files or archive properties)
     */
    @Override
    public ISequentialInStream getStream(final int index) throws SevenZipException {
        if (index + idxToDelete.size() - oldTot < this.nArchive.getToAdd().size()) {
            try {
                rafs.put(index, new RandomAccessFile(new File(this.nArchive.getTempDir(), this.nArchive.getToAdd().get(index + idxToDelete.size() - oldTot)), "r")); //$NON-NLS-1$
                return new RandomAccessFileInStream(rafs.get(index));
            } catch (final IOException e) {
                Log.err(e.getMessage(), e);
            }
        }
        if (index + idxToDelete.size() - oldTot - this.nArchive.getToAdd().size() >= this.nArchive.getToCopy().size())
            return null;
        try {
            if (!rafs.containsKey(index)) {
                rebuildRafsMap();
            }
            rafs.get(index).seek(0);
            return new RandomAccessFileInStream(rafs.get(index));
        } catch (final IOException e) {
            Log.err(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Rebuilds the mapping of archive entry indices to RandomAccessFile instances for duplicated entries. This method identifies
     * the original indices of entries that need to be duplicated, extracts their contents to temporary files using
     * {@link ExtractCallback}, and registers new {@link RandomAccessFile} instances in the {@code rafs} map for subsequent reading
     * of the duplicated data.
     * 
     * @throws IOException if an I/O error occurs while creating or accessing temporary files, or during archive extraction
     */
    private void rebuildRafsMap() throws IOException {
        final HashMap<Integer, File> tmpFilesByOldIndex = new HashMap<>();
        final HashMap<Integer, RandomAccessFile> rafs2 = new HashMap<>();
        for (final Object[] o : idxToDuplicate) {
            if (!tmpFilesByOldIndex.containsKey(o[0]))
                tmpFilesByOldIndex.put((Integer) o[0], IOUtils.createTempFile("JRM", null).toFile()); //$NON-NLS-1$
            tmpfiles.put((Integer) o[2], tmpFilesByOldIndex.get(o[0]));

        }
        for (final Entry<Integer, File> entry : tmpFilesByOldIndex.entrySet())
            rafs2.put(entry.getKey(), new RandomAccessFile(entry.getValue(), "rw")); //$NON-NLS-1$

        final int[] indices = idxToDuplicate.stream().flatMapToInt(objs -> IntStream.of((Integer) objs[0])).toArray();

        this.nArchive.getIInArchive().extract(indices, false, new ExtractCallback(rafs2));
        for (final RandomAccessFile raf2 : rafs2.values())
            raf2.close();

        for (final Entry<Integer, File> entry : tmpfiles.entrySet())
            rafs.put(entry.getKey(), new RandomAccessFile(entry.getValue(), "r")); //$NON-NLS-1$
    }

    /**
     * Retrieves the information for the entry at the specified index during the archive creation or modification process. It
     * determines whether the entry is a new entry being added, an existing entry being renamed, or an existing entry being
     * duplicated, and returns the appropriate IOutItemAllFormats instance with the corresponding properties set based on the
     * entry's status. If the entry is a new entry being added, it creates a new IOutItemAllFormats instance with the file path and
     * data size set based on the corresponding temporary file. If the entry is an existing entry being renamed, it creates a new
     * IOutItemAllFormats instance with the new file path set based on the renaming mapping. If the entry is an existing entry being
     * duplicated, it creates a new IOutItemAllFormats instance with the file path and data size set based on the original entry's
     * properties. If the index exceeds the range of entries to be processed, it returns null.
     * 
     * @param index the index of the entry for which to retrieve information
     * @param outItemFactory a factory for creating IOutItemAllFormats instances
     * 
     * @return an IOutItemAllFormats instance with properties set based on the entry's status, or null if the index exceeds the
     *         range of entries to be processed
     * 
     * @throws SevenZipException if an error occurs while retrieving item information (e.g., accessing archive properties)
     */
    @Override
    public IOutItemAllFormats getItemInformation(final int index, final OutItemFactory<IOutItemAllFormats> outItemFactory) throws SevenZipException {
        try {
            while (idxToDelete.containsKey(oldIdx))
                oldIdx++;
            if (idxToRename.containsKey(oldIdx)) {
                final IOutItemAllFormats item = outItemFactory.createOutItemAndCloneProperties(oldIdx);
                item.setPropertyPath(idxToRename.get(oldIdx));
                return item;
            }
            if (oldIdx < oldTot)
                return outItemFactory.createOutItem(oldIdx);
            else {
                if (oldIdx - oldTot < this.nArchive.getToAdd().size()) {
                    final String file = this.nArchive.getToAdd().get(oldIdx - oldTot);
                    final IOutItemAllFormats item = outItemFactory.createOutItem();
                    item.setPropertyPath(file);
                    try {
                        item.setDataSize(new File(this.nArchive.getTempDir(), file).length());
                    } catch (final IOException e) {
                        Log.err(e.getMessage(), e);
                    }
                    item.setUpdateIsNewData(true);
                    item.setUpdateIsNewProperties(true);
                    return item;
                } else {
                    final Object[] objects = idxToDuplicate.get(oldIdx - oldTot - this.nArchive.getToAdd().size());
                    final ISimpleInArchiveItem refItem = this.nArchive.getIInArchive().getSimpleInterface().getArchiveItem((Integer) objects[0]);
                    objects[2] = index;
                    final IOutItemAllFormats item = outItemFactory.createOutItem();
                    item.setPropertyPath((String) objects[1]);
                    item.setDataSize(refItem.getSize());
                    item.setUpdateIsNewData(true);
                    item.setUpdateIsNewProperties(true);
                    return item;
                }
            }
        } finally {
            oldIdx++;
        }
    }
}