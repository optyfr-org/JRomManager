package jrm.ui.progress;

import java.awt.HeadlessException;
import java.awt.Window;
import java.beans.PropertyChangeEvent;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import jrm.aui.progress.ProgressHandler;
import jrm.aui.progress.ProgressInputStream;
import jrm.misc.OffsetProvider;
import lombok.RequiredArgsConstructor;

/**
 * Abstract base class for SwingWorker-based progress tracking.
 * <p>
 * Integrates a {@link Progress} dialog with SwingWorker to provide
 * real-time progress updates during background operations. Handles
 * property change events to update progress bars, labels, and time estimates.
 *
 * @param <T> the result type of the background computation
 * @param <V> the type of intermediate results published during computation
 */
public abstract class SwingWorkerProgress<T, V> extends SwingWorker<T, V> implements ProgressHandler {
    /** Property name for tertiary progress updates. */
    private static final String SET_PROGRESS_3 = "setProgress3";

    /** Property name for secondary progress updates. */
    private static final String SET_PROGRESS_2 = "setProgress2";

    /** Property name for primary progress updates. */
    private static final String SET_PROGRESS = "setProgress";

    /** The progress dialog displaying operation status. */
    private final Progress progress;

    /** List of error messages collected during execution. Thread-safe because errors are added from background threads and read on the EDT when closing. */
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());

    /** Number of threads used for parallel operations. */
    private int threadCnt;

    /**
     * Constructs a new SwingWorker progress tracker.
     *
     * @param owner the parent window for the progress dialog
     */
    protected SwingWorkerProgress(final Window owner) {
        super();
        progress = new Progress(owner);
        addPropertyChangeListener(e -> propertyChange(owner, e));
        progress.setVisible(true);
    }

    /**
     * Handles property change events to update the progress dialog.
     *
     * @param owner the parent window for displaying error dialogs
     * @param e the property change event containing progress update information
     * 
     * @throws HeadlessException if the operation requires a display that is not available
     */
    private void propertyChange(final Window owner, PropertyChangeEvent e) throws HeadlessException {
        switch (e.getPropertyName()) {
            case SET_PROGRESS:
                if (e.getNewValue() instanceof SetProgress props)
                    progress.setProgress(props.offset, props.msg, props.val, props.max, props.submsg);
                break;
            case SET_PROGRESS_2:
                if (e.getNewValue() instanceof SetProgress2 props)
                    progress.setProgress2(props.msg, props.val, props.max);
                break;
            case SET_PROGRESS_3:
                if (e.getNewValue() instanceof SetProgress3 props)
                    progress.setProgress3(props.msg, props.val, props.max);
                break;
            case "setInfos":
                if (e.getNewValue() instanceof SetInfos props)
                    progress.setInfos(props.threadCnt, props.multipleSubInfos);
                break;
            case "extendInfos":
                if (e.getNewValue() instanceof ExtendInfos props)
                    progress.extendInfos(props.threadCnt, props.multipleSubInfos);
                break;
            case "clearInfos":
                progress.clearInfos();
                break;
            case "canCancel":
                progress.canCancel((Boolean) e.getNewValue());
                break;
            case "cancel":
                progress.cancel();
                break;
            case "close":
                progress.close();
                final String errorMessage;
                synchronized (errors) {
                    errorMessage = errors.isEmpty() ? null : errors.stream().collect(Collectors.joining("\n"));
                }
                if (errorMessage != null)
                    JOptionPane.showMessageDialog(owner, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
                break;
            default:
                break;
        }
    }

    /**
     * Data class for setting info display configuration.
     */
    @RequiredArgsConstructor
    private static class SetInfos {
        /** Number of threads to display. */
        private final int threadCnt;
        /** Whether to show multiple sub-info lines. */
        private final Boolean multipleSubInfos;
    }

    /**
     * Data class for extending info display configuration.
     */
    @RequiredArgsConstructor
    private static class ExtendInfos {
        /** Additional number of threads to display. */
        private final int threadCnt;
        /** Whether to show multiple sub-info lines. */
        private final Boolean multipleSubInfos;
    }

    /** Flag indicating whether multiple sub-info lines should be displayed. */
    private Boolean multipleSubInfos = null;

    /**
     * Configures the info display with the specified number of threads.
     *
     * @param threadCnt the number of threads to display; if {@code <= 0}, uses available processors
     * @param multipleSubInfos whether to show multiple sub-info lines
     */
    @Override
    public void setInfos(int threadCnt, Boolean multipleSubInfos) {
        this.threadCnt = threadCnt <= 0 ? Runtime.getRuntime().availableProcessors() : threadCnt;
        this.multipleSubInfos = multipleSubInfos;
        firePropertyChange("setInfos", null, new SetInfos(this.threadCnt, this.multipleSubInfos));
    }

    /**
     * Extends the info display to accommodate additional threads.
     *
     * @param threadCnt the new total number of threads
     */
    public void extendInfos(int threadCnt) {
        this.threadCnt = threadCnt;
        firePropertyChange("extendInfos", null, new ExtendInfos(this.threadCnt, this.multipleSubInfos));
    }

    /**
     * Clears all info and sub-info labels in the progress dialog.
     */
    @Override
    public void clearInfos() {
        firePropertyChange("clearInfos", false, true);
    }

    /**
     * Data class holding primary progress update information.
     */
    @RequiredArgsConstructor
    private static class SetProgress {
        /** Thread offset for multi-threaded display. */
        private final int offset;
        /** Status message. */
        private final String msg;
        /** Current progress value. */
        private final Integer val;
        /** Maximum progress value. */
        private final Integer max;
        /** Sub-status message. */
        private final String submsg;
    }

    /**
     * Resets progress display for all free offsets from the offset provider.
     */
    private void cleanup() {
        if (offsetProvider != null) {
            for (final var offset : offsetProvider.freeOffsets()) {
                if (offset < threadCnt) {
                    firePropertyChange(SET_PROGRESS, null, new SetProgress(offset, "", null, null, this.multipleSubInfos != null && this.multipleSubInfos ? "" : null));
                }
            }
        }
    }

    /**
     * Updates the primary progress bar with the specified values.
     *
     * @param msg the status message
     * @param val the current progress value
     * @param max the maximum progress value
     * @param submsg the sub-status message
     */
    @Override
    public void setProgress(String msg, Integer val, Integer max, String submsg) {
        final int offset = getOffset();
        cleanup();
        firePropertyChange(SET_PROGRESS, null, new SetProgress(offset, msg, val, max, submsg));
    }

    /**
     * Gets the current thread offset from the offset provider.
     *
     * @return the current offset, or {@code 0} if no provider is set
     */
    private int getOffset() {
        if (offsetProvider != null) {
            int offset = offsetProvider.getOffset();
            if (offset < 0)
                return 0;
            if (offset >= threadCnt)
                extendInfos(offset + 1);
            return offset;
        }
        return 0;

    }

    /**
     * Data class holding secondary progress update information.
     */
    @RequiredArgsConstructor
    private static class SetProgress2 {
        /** Status message. */
        private final String msg;
        /** Current progress value. */
        private final Integer val;
        /** Maximum progress value. */
        private final Integer max;
    }

    /**
     * Updates the secondary progress bar with the specified values.
     *
     * @param msg the status message
     * @param val the current progress value
     * @param max the maximum progress value
     */
    @Override
    public void setProgress2(String msg, Integer val, Integer max) {
        firePropertyChange(SET_PROGRESS_2, null, new SetProgress2(msg, val, max));
    }

    /**
     * Data class holding tertiary progress update information.
     */
    @RequiredArgsConstructor
    private static class SetProgress3 {
        /** Status message. */
        private final String msg;
        /** Current progress value. */
        private final Integer val;
        /** Maximum progress value. */
        private final Integer max;
    }

    /**
     * Updates the tertiary progress bar with the specified values.
     *
     * @param msg the status message
     * @param val the current progress value
     * @param max the maximum progress value
     */
    @Override
    public void setProgress3(String msg, Integer val, Integer max) {
        firePropertyChange(SET_PROGRESS_3, null, new SetProgress3(msg, val, max));
    }

    /**
     * Gets the current value of the primary progress bar.
     *
     * @return the current progress value
     */
    @Override
    public int getCurrent() {
        return progress.getValue();
    }

    /**
     * Gets the current value of the secondary progress bar.
     *
     * @return the current progress value
     */
    @Override
    public int getCurrent2() {
        return progress.getValue2();
    }

    /**
     * Gets the current value of the tertiary progress bar.
     *
     * @return the current progress value
     */
    @Override
    public int getCurrent3() {
        return progress.getValue3();
    }

    /**
     * Checks whether the operation has been cancelled.
     *
     * @return {@code true} if the operation was cancelled
     */
    @Override
    public boolean isCancel() {
        return progress.isCancel();
    }

    /**
     * Triggers cancellation of the current operation.
     */
    @Override
    public void doCancel() {
        firePropertyChange("cancel", false, true);
    }

    /**
     * Enables or disables the cancel button.
     *
     * @param canCancel {@code true} to enable cancellation
     */
    @Override
    public void canCancel(boolean canCancel) {
        firePropertyChange("canCancel", canCancel(), canCancel);
    }

    /**
     * Checks whether cancellation is currently allowed.
     *
     * @return {@code true} if the operation can be cancelled
     */
    @Override
    public boolean canCancel() {
        return progress.canCancel();
    }

    /**
     * Wraps the input stream with progress tracking.
     *
     * @param in the input stream to wrap
     * @param len the expected length of the stream
     * @return a progress-tracking input stream
     */
    @Override
    public InputStream getInputStream(InputStream in, Integer len) {
        return new ProgressInputStream(in, len, this);
    }

    /**
     * Closes the progress dialog and displays any accumulated errors.
     */
    @Override
    public void close() {
        firePropertyChange("close", false, true);
    }

    /**
     * Adds an error message to the list of errors to display on close.
     *
     * @param error the error message to add
     */
    @Override
    public void addError(String error) {
        errors.add(error);
    }

    /**
     * Sets options for the progress handler. Currently a no-op.
     *
     * @param first the first option
     * @param rest additional options
     */
    @Override
    public void setOptions(Option first, Option... rest) {
    }

    /** Provider for thread offset allocation. */
    private OffsetProvider offsetProvider = null;

    /**
     * Sets the offset provider for thread offset allocation.
     *
     * @param offsetProvider the offset provider to use
     */
    @Override
    public void setOffsetProvider(OffsetProvider offsetProvider) {
        this.offsetProvider = offsetProvider;
    }
}
