package jrm.aui.progress;

import jrm.aui.status.StatusRendererFactory;
import jtrrntzip.LogCallback;

/**
 * ProgressTZipCallBack is a class that implements the LogCallback and StatusRendererFactory interfaces. It is designed to handle
 * progress updates and logging for a TZip operation. The class uses a ProgressHandler to update the progress status based on the
 * percentage of completion.
 */
public final class ProgressTZipCallBack implements LogCallback, StatusRendererFactory {

    /** The ProgressHandler used to report progress updates. */
    ProgressHandler progressHandler;

    /**
     * Constructs a new ProgressTZipCallBack with the specified ProgressHandler. The constructor initializes the ProgressHandler for
     * reporting progress updates during the TZip operation.
     *
     * @param progressHandler the ProgressHandler used to report progress updates
     */
    public ProgressTZipCallBack(ProgressHandler progressHandler) {
        this.progressHandler = progressHandler;
    }

    /**
     * Updates the progress status based on the percentage of completion. This method is called to report the current progress of
     * the TZip operation. If the ProgressHandler is available, it updates the progress status with the new percentage value.
     *
     * @param percent the percentage of completion for the TZip operation
     */
    @Override
    public void statusCallBack(int percent) {
        if (hasProgress())
            progressHandler.setProgress(null, null, null, progress(200, percent, 100, null));
    }

    /**
     * Checks if the ProgressHandler is available for reporting progress updates. This method returns true if the ProgressHandler is
     * not null, indicating that progress updates can be reported.
     *
     * @return true if the ProgressHandler is available, false otherwise
     */
    @Override
    public boolean isVerboseLogging() {
        return false;
    }

    /**
     * Logs a message for the TZip operation. This method is called to log messages related to the TZip operation. In this
     * implementation, it does nothing, as logging is not required for this callback.
     *
     * @param log the message to be logged
     */
    @Override
    public void statusLogCallBack(String log) {
        // do nothing
    }

}