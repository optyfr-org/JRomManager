/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.aui.progress;

import java.io.InputStream;

import jrm.misc.OffsetProvider;

/** Progress handler interface. */
public interface ProgressHandler {
    /**
     * Options for the progress handler. These options can be used to configure the behavior of the progress handler, such as
     * enabling lazy updates or other features that may affect how progress is tracked and displayed. The specific options available
     * may depend on the implementation of the ProgressHandler interface.
     */
    enum Option {
        /**
         * Enables lazy updates for the progress handler, which may improve performance by reducing the frequency of progress
         * updates. When this option is enabled, the progress handler may choose to update the progress display less frequently,
         * such as only when a certain threshold of progress has been reached or after a certain amount of time has passed. This can
         * help to reduce overhead and improve responsiveness in situations where frequent updates may be costly or unnecessary.
         */
        LAZY;
    }

    /**
     * Sets the options.
     *
     * @param first the first
     * @param rest the rest
     */
    public void setOptions(Option first, Option... rest);

    /**
     * Sets the infos.
     *
     * @param threadCnt the thread cnt
     * @param multipleSubInfos the multiple sub infos
     */
    public void setInfos(int threadCnt, Boolean multipleSubInfos);

    /**
     * Clear infos.
     */
    public void clearInfos();

    /**
     * Sets the progress.
     *
     * @param msg the new progress
     */
    public default void setProgress(String msg) {
        setProgress(msg, null, null, null);
    }

    /**
     * Sets the progress.
     *
     * @param msg the msg
     * @param val the val
     */
    public default void setProgress(String msg, Integer val) {
        setProgress(msg, val, null, null);
    }

    /**
     * Sets the progress.
     *
     * @param msg the msg
     * @param val the val
     * @param max the max
     */
    public default void setProgress(String msg, Integer val, Integer max) {
        setProgress(msg, val, max, null);
    }

    /**
     * Sets the progress.
     *
     * @param msg the msg
     * @param val the val
     * @param max the max
     * @param submsg the submsg
     */
    public void setProgress(String msg, Integer val, Integer max, String submsg);

    /**
     * Sets the progress 2.
     *
     * @param msg the msg
     * @param val the val
     */
    public default void setProgress2(String msg, Integer val) {
        setProgress2(msg, val, null);
    }

    /**
     * Sets the progress 2.
     *
     * @param msg the msg
     * @param val the val
     * @param max the max
     */
    public void setProgress2(String msg, Integer val, Integer max);

    /**
     * Sets the progress 3.
     *
     * @param msg the msg
     * @param val the val
     */
    public default void setProgress3(String msg, Integer val) {
        setProgress3(msg, val, null);
    }

    /**
     * Sets the progress 3.
     *
     * @param msg the msg
     * @param val the val
     * @param max the max
     */
    public void setProgress3(String msg, Integer val, Integer max);

    /**
     * Gets the current value of the primary progress bar.
     *
     * @return the current progress value of the primary progress bar
     */

    public int getCurrent();

    /**
     * Gets the current value of the second progress bar.
     *
     * @return the current progress value of the second progress bar
     */
    public int getCurrent2();

    /**
     * Gets the current value of the third progress bar.
     *
     * @return the current progress value of the third progress bar
     */
    public int getCurrent3();

    /**
     * Is cancel?
     *
     * @return true if the progress has been cancelled, false otherwise
     */
    public boolean isCancel();

    /**
     * Do cancel.
     */
    public void doCancel();

    /**
     * Sets whether the progress can be cancelled.
     *
     * @param canCancel true if the progress can be cancelled, false otherwise
     */
    public void canCancel(boolean canCancel);

    /**
     * Can cancel?
     *
     * @return true if the progress can be cancelled, false otherwise
     */
    public boolean canCancel();

    /**
     * Gets the input stream.
     *
     * @param in the input stream to wrap with progress tracking
     * @param len the length of the input stream, used for progress tracking
     * 
     * @return an InputStream that tracks progress based on the provided input stream and length
     */
    public InputStream getInputStream(InputStream in, Integer len);

    /**
     * Closes the progress handler and releases any resources associated with it. This method should be called when the progress
     * tracking is complete or when the handler is no longer needed to ensure proper cleanup and resource management.
     */
    public void close();

    /**
     * Adds an error message to the progress handler. This method can be used to report errors that occur during the progress
     * tracking process, allowing the handler to display or log the error messages as needed.
     *
     * @param error the error message to add to the progress handler
     */
    public void addError(String error);

    /**
     * Sets the offset provider for the progress handler. The offset provider is used to track the progress of operations that
     * involve offsets, such as reading from a file or processing a stream. By setting the offset provider, the progress handler can
     * update its progress tracking based on the current offset provided by the offset provider.
     *
     * @param offsetProvider the OffsetProvider instance to be used by the progress handler for tracking offsets
     */
    public void setOffsetProvider(OffsetProvider offsetProvider);
}
