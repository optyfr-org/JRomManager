package jrm.aui.progress;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * ProgressInputStream is a custom InputStream that extends FilterInputStream to track the progress of reading data. It uses a
 * ProgressHandler to report the progress of reading from the stream, allowing for real-time updates on the amount of data read.
 */
public final class ProgressInputStream extends FilterInputStream {

    /**
     * The current value of bytes read from the input stream. It is updated each time data is read or skipped, and is used to report
     * progress to the ProgressHandler.
     */
    private long value;

    /**
     * The ProgressHandler instance used to report progress updates. It is initialized in the constructor and is called each time
     * data is read or skipped to update the progress based on the current value of bytes read.
     */
    private final ProgressHandler progress;

    /**
     * Constructs a new ProgressInputStream that wraps the specified InputStream and tracks progress using the provided
     * ProgressHandler. The constructor initializes the value to 0 and sets the initial progress based on the length of the input
     * stream.
     *
     * @param in the InputStream to be wrapped and tracked for progress
     * @param len the length of the input stream, used for progress tracking
     * @param progress the ProgressHandler instance used to report progress updates
     */
    public ProgressInputStream(final InputStream in, final Integer len, final ProgressHandler progress) {
        super(in);
        this.progress = progress;
        value = 0L;
        progress.setProgress(null, Math.toIntExact(value), len);
    }

    /**
     * Reads a single byte from the input stream and updates the progress accordingly. If the end of the stream is reached
     * (indicated by a return value of -1), the progress is not updated. Otherwise, the value is incremented by 1 and the progress
     * is updated with the new value.
     *
     * @return the byte read from the input stream, or -1 if the end of the stream is reached
     * 
     * @throws IOException if an I/O error occurs while reading from the input stream
     */
    @Override
    public int read() throws IOException {
        final int ret = super.read();
        if (ret != -1)
            progress.setProgress(null, Math.toIntExact(++value));
        return ret;
    }

    /**
     * Reads bytes from the input stream into the specified byte array and updates the progress accordingly. If the end of the
     * stream is reached (indicated by a return value of -1), the progress is not updated. Otherwise, the value is incremented by
     * the number of bytes read and the progress is updated with the new value.
     *
     * @param b the byte array into which data is read from the input stream
     * 
     * @return the number of bytes read from the input stream, or -1 if the end of the stream is reached
     * 
     * @throws IOException if an I/O error occurs while reading from the input stream
     */
    @Override
    public int read(final byte[] b) throws IOException {
        final int ret = super.read(b);
        if (ret != -1) {
            value += ret;
            progress.setProgress(null, Math.toIntExact(value));
        }
        return ret;
    }

    /**
     * Reads bytes from the input stream into the specified byte array, starting at the specified offset and reading up to the
     * specified length. The progress is updated based on the number of bytes read. If the end of the stream is reached (indicated
     * by a return value of -1), the progress is not updated. Otherwise, the value is incremented by the number of bytes read and
     * the progress is updated with the new value.
     *
     * @param b the byte array into which data is read from the input stream
     * @param off the start offset in the byte array at which data is written
     * @param len the maximum number of bytes to read from the input stream
     * 
     * @return the number of bytes read from the input stream, or -1 if the end of the stream is reached
     * 
     * @throws IOException if an I/O error occurs while reading from the input stream
     */
    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        final int ret = super.read(b, off, len);
        if (ret != -1) {
            value += ret;
            progress.setProgress(null, Math.toIntExact(value));
        }
        return ret;
    }

    /**
     * Skips over and discards n bytes of data from the input stream. The progress is updated based on the number of bytes skipped.
     * If the end of the stream is reached (indicated by a return value of -1), the progress is not updated. Otherwise, the value is
     * incremented by the number of bytes skipped and the progress is updated with the new value.
     *
     * @param n the number of bytes to be skipped
     * 
     * @return the actual number of bytes skipped
     * 
     * @throws IOException if an I/O error occurs while skipping bytes from the input stream
     */
    @Override
    public long skip(final long n) throws IOException {
        final long ret = super.skip(n);
        if (ret != -1) {
            value += ret;
            progress.setProgress(null, Math.toIntExact(value));
        }
        return ret;
    }
}
