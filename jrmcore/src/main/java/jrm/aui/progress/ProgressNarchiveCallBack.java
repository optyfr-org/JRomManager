package jrm.aui.progress;

import jrm.aui.status.StatusRendererFactory;
import net.sf.sevenzipjbinding.IProgress;
import net.sf.sevenzipjbinding.SevenZipException;

/**
 * ProgressNarchiveCallBack is a class that implements the IProgress interface to provide progress updates for a narchive operation.
 * It uses a ProgressHandler to report the progress and implements the StatusRendererFactory interface to create status renderers
 * for displaying the progress.
 */
public final class ProgressNarchiveCallBack implements IProgress, StatusRendererFactory {
    /** The ProgressHandler used to report progress updates. */
    ProgressHandler progressHandler;
    /** The total amount of work to be completed, used for calculating progress. */
    long total;

    /**
     * Constructs a new ProgressNarchiveCallBack with the specified ProgressHandler. The constructor initializes the ProgressHandler
     * and sets the total to 0.
     *
     * @param progressHandler the ProgressHandler used to report progress updates
     */
    public ProgressNarchiveCallBack(ProgressHandler progressHandler) {
        this.progressHandler = progressHandler;
    }

    /**
     * Sets the total amount of work to be completed for the narchive operation. This method is called to initialize the total
     * value, which is used for calculating progress updates. The total value is stored in the total field of the class.
     *
     * @param total the total amount of work to be completed
     * 
     * @throws SevenZipException if an error occurs while setting the total
     */
    @Override
    public void setTotal(long total) throws SevenZipException {
        this.total = total;

    }

    /**
     * Sets the completed amount of work for the narchive operation. This method is called to update the progress based on the
     * amount of work completed. If the total value has been set, it calculates the progress percentage and updates the
     * ProgressHandler with the new progress value.
     *
     * @param complete the amount of work that has been completed
     * 
     * @throws SevenZipException if an error occurs while setting the completed amount
     */
    @Override
    public void setCompleted(long complete) throws SevenZipException {
        if (hasProgress())
            progressHandler.setProgress(null, null, null, progress(200, (int) complete, (int) total, null)); // $NON-NLS-1$
    }

}
