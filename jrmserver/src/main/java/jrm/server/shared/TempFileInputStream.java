package jrm.server.shared;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import jrm.misc.IOUtils;

/**
 * class to create a temporary file and return an input stream to read from it. The file will be deleted when the input stream is
 * closed. This is useful for creating a temporary file to store data that will be sent to the client, and then deleting the file
 * after it has been sent.
 */
public class TempFileInputStream extends FileInputStream {
    /**
     * The temporary file that the input stream reads from. This file will be deleted when the input stream is closed.
     * <p>
     * Note that this field is final, so it cannot be changed after the TempFileInputStream is created. This ensures that the file
     * will always be deleted when the input stream is closed, even if the input stream is used in a try-with-resources block or if
     * it is closed manually. The file is created when the TempFileInputStream is instantiated, and it is deleted in the close()
     * method.
     */
    private final File file;

    /**
     * The length of the temporary file. This is useful for setting the Content-Length header when sending the file to the client.
     * <p>
     * Note that this field is final, so it cannot be changed after the TempFileInputStream is created. This ensures that the length
     * of the file is always accurate and can be used reliably when sending the file to the client. The length is determined when
     * the TempFileInputStream is instantiated, and it is based on the length of the file at that time. If the file is modified
     * after the TempFileInputStream is created, the length may not be accurate, but since the file is intended to be temporary and
     * deleted after use, this should not be an issue in practice.
     */
    private final long length;

    /**
     * Creates a new TempFileInputStream that reads from the given file. The file will be deleted when the input stream is closed.
     * <p>
     * Note that the file must exist when this constructor is called, and it will be deleted when the input stream is closed. This
     * means that the file should be created as a temporary file before calling this constructor, and it should not be used for any
     * other purpose after the TempFileInputStream is created, since it will be deleted when the input stream is closed. The length
     * of the file is determined when the TempFileInputStream is created, and it is based on the length of the file at that time. If
     * the file is modified after the TempFileInputStream is created, the length may not be accurate, but since the file is intended
     * to be temporary and deleted after use, this should not be an issue in practice.
     * <p>
     * The constructor may throw a FileNotFoundException if the file does not exist when the constructor is called. Ensure that the
     * file is created as a temporary file before calling this constructor, and that it exists at the time of instantiation. The
     * file will be deleted when the input stream is closed, so it should not be used for any other purpose after the
     * TempFileInputStream is created.
     * <p>
     * Example usage:
     * 
     * <pre>
     * try (InputStream in = TempFileInputStream.newInstance()) {
     *     // write data to the temporary file using the input stream
     *     // read data from the temporary file using the input stream
     *     // the temporary file will be deleted when the input stream is closed
     * }
     * </pre>
     * 
     * Note: The TempFileInputStream is designed to be used in a try-with-resources block or with manual closing to ensure that the
     * temporary file is deleted properly. If the input stream is not closed, the temporary file may not be deleted, which could
     * lead to resource leaks. Always ensure that the input stream is closed after use to allow for proper cleanup of the temporary
     * file.
     * 
     * @param file the temporary file to read from. This file must exist when the constructor is called, and it will be deleted when
     *        the input stream is closed.
     * 
     * @throws FileNotFoundException if the file does not exist when the constructor is called. Ensure that the file is created as a
     *         temporary file before calling this constructor, and that it exists at the time of instantiation.
     */
    public TempFileInputStream(File file) throws FileNotFoundException {
        super(file);
        this.file = file;
        this.length = file.length();
    }

    /**
     * Closes the input stream and deletes the temporary file. This method is called when the input stream is closed, either
     * manually or through a try-with-resources block. It ensures that the temporary file is deleted properly to avoid resource
     * leaks.
     * <p>
     * Note that this method overrides the close() method of FileInputStream, so it will be called when the input stream is closed.
     * It first calls the superclass's close() method to ensure that the input stream is closed properly, and then it deletes the
     * temporary file using Files.deleteIfExists() to ensure that the file is deleted even if it has already been deleted or if
     * there are any issues with file deletion. Always ensure that the input stream is closed after use to allow for proper cleanup
     * of the temporary file.
     * 
     * @throws IOException if an I/O error occurs while closing the input stream or deleting the temporary file. Ensure that proper
     *         error handling is implemented when using this method to handle any potential issues with closing the input stream or
     *         deleting the temporary file.
     */
    @Override
    public void close() throws IOException {
        super.close();
        Files.deleteIfExists(file.toPath());
    }

    /**
     * create an instance of TempFileInputStream that reads from a new temporary file. The file will be deleted when the input
     * stream is closed.
     * <p>
     * Note that this method creates a new temporary file using IOUtils.createTempFile() and returns a TempFileInputStream that
     * reads from that file. The temporary file will be deleted when the input stream is closed, so it should not be used for any
     * other purpose after the TempFileInputStream is created. The method may throw an IOException if there is an error while
     * creating the temporary file, so ensure that proper error handling is implemented when using this method to handle any
     * potential issues with temporary file creation. The temporary file is created with a prefix of "JRMSRV" and no suffix, but
     * this can be modified as needed by changing the parameters passed to IOUtils.createTempFile(). Always ensure that the input
     * stream is closed after use to allow for proper cleanup of the temporary file.
     * <p>
     * Example usage:
     * 
     * <pre>
     * <code class="language-java">
     * try (InputStream in = TempFileInputStream.newInstance()) {
     *     // write data to the temporary file using the input stream
     *     // read data from the temporary file using the input stream
     *     // the temporary file will be deleted when the input stream is closed
     * }
     * </code>
     * </pre>
     * 
     * Note: The TempFileInputStream is designed to be used in a try-with-resources block or with manual closing to ensure that the
     * temporary file is deleted properly. If the input stream is not closed, the temporary file may not be deleted, which could
     * lead to resource leaks. Always ensure that the input stream is closed after use to allow for proper cleanup of the temporary
     * file.
     * 
     * @return InputStream that reads from a temporary file. The file will be deleted when the input stream is closed.
     * 
     * @throws IOException if an I/O error occurs while creating the temporary file. Ensure that proper error handling is
     *         implemented when using this method to handle any potential issues with temporary file creation.
     */
    public static InputStream newInstance() throws IOException {
        return new TempFileInputStream(IOUtils.createTempFile("JRMSRV", null).toFile());
    }

    /**
     * create an instance of TempFileInputStream that reads from a new temporary file, and writes the contents of the given input
     * stream to the temporary file. The file will be deleted when the input stream is closed.
     * <p>
     * wrapper for newInstance(InputStream in, long len, boolean close) with len set to -1 and close set to false.
     * 
     * @param in the input stream to read from and write to the temporary file. The contents of this input stream will be written to
     *        the temporary file, and the input stream will be closed after writing if the close parameter is true. Ensure that the
     *        input stream is properly closed after use to allow for proper cleanup of the temporary file.
     * 
     * @return InputStream that reads from a temporary file containing the contents of the given input stream. The file will be
     *         deleted when the input stream is closed.
     * 
     * @throws IOException if an I/O error occurs while creating the temporary file or writing to it. Ensure that proper error
     *         handling is implemented when using this method to handle any potential issues with temporary file creation or
     *         writing.
     */
    public static InputStream newInstance(InputStream in) throws IOException {
        return newInstance(in, -1L, false);
    }

    /**
     * Create an instance of TempFileInputStream that reads from a new temporary file, and writes the contents of the given input
     * stream to the temporary file. The file will be deleted when the input stream is closed.
     * <p>
     * wrapper for newInstance(InputStream in, long len, boolean close) with close set to false.
     * 
     * @param in the input stream to read from and write to the temporary file. The contents of this input stream will be written to
     *        the temporary file, and the input stream will be closed after writing if the close parameter is true. Ensure that the
     *        input stream is properly closed after use to allow for proper cleanup of the temporary file.
     * @param len the length of the input stream to read and write to the temporary file. If len is negative, the entire input
     *        stream will be read and written to the temporary file. Ensure that the len parameter is set correctly to avoid issues
     *        with reading and writing the input stream to the temporary file. If len is set to a value that is larger than the
     *        actual length of the input stream, it may cause issues with reading and writing, so ensure that the len parameter is
     *        set appropriately based on the expected length of the input stream.
     * 
     * @return InputStream that reads from a temporary file containing the contents of the given input stream. The file will be
     *         deleted when the input stream is closed.
     * 
     * @throws IOException if an I/O error occurs while creating the temporary file or writing to it. Ensure that proper error
     *         handling is implemented when using this method to handle any potential issues with temporary file creation or
     *         writing.
     */
    public static InputStream newInstance(InputStream in, long len) throws IOException {
        return newInstance(in, len, false);
    }

    /**
     * create an instance of TempFileInputStream that reads from a new temporary file, and writes the contents of the given input
     * stream to the temporary file. The file will be deleted when the input stream is closed.
     * <p>
     * Note that this method creates a new temporary file using IOUtils.createTempFile() and writes the contents of the given input
     * stream to that file. The temporary file will be deleted when the input stream is closed, so it should not be used for any
     * other purpose after the TempFileInputStream is created. The method may throw an IOException if there is an error while
     * creating the temporary file or writing to it, so ensure that proper error handling is implemented when using this method to
     * handle any potential issues with temporary file creation or writing. The temporary file is created with a prefix of "JRMSRV"
     * and no suffix, but this can be modified as needed by changing the parameters passed to IOUtils.createTempFile(). The len
     * parameter specifies the length of the input stream to read and write to the temporary file. If len is negative, the entire
     * input stream will be read and written to the temporary file. The close parameter indicates whether to close the input stream
     * after writing to the temporary file. If close is true, the input stream will be closed after writing, which allows for proper
     * cleanup of the input stream resources. Always ensure that the input stream is closed after use to allow for proper cleanup of
     * the temporary file and input stream resources.
     * <p>
     * Example usage:
     * 
     * <pre>
     * try (InputStream in = TempFileInputStream.newInstance(originalInputStream, length, true)) {
     *     // read data from the temporary file using the input stream
     *     // the temporary file will be deleted when the input stream is closed
     * }
     * </pre>
     * 
     * Note: The TempFileInputStream is designed to be used in a try-with-resources block or with manual closing to ensure that the
     * temporary file is deleted properly. If the input stream is not closed, the temporary file may not be deleted, which could
     * lead to resource leaks. Always ensure that the input stream is closed after use to allow for proper cleanup of the temporary
     * file and input stream resources.
     * 
     * @param in the input stream to read from and write to the temporary file. The contents of this input stream will be written to
     *        the temporary file, and the input stream will be closed after writing if the close parameter is true. Ensure that the
     *        input stream is properly closed after use to allow for proper cleanup of the temporary file and input stream
     *        resources.
     * @param len the length of the input stream to read and write to the temporary file. If len is negative, the entire input
     *        stream will be read and written to the temporary file. Ensure that the len parameter is set correctly to avoid issues
     *        with reading and writing the input stream to the temporary file. If len is set to a value that is larger than the
     *        actual length of the input stream, it may cause issues with reading and writing, so ensure that the len parameter is
     *        set appropriately based on the expected length of the input stream.
     * @param close whether to close the input stream after writing to the temporary file. If close is true, the input stream will
     *        be closed after writing, which allows for proper cleanup of the input stream resources. Ensure that the input stream
     *        is properly closed after use to allow for proper cleanup of the temporary file and input stream resources.
     * 
     * @return InputStream that reads from a temporary file containing the contents of the given input stream. The file will be
     *         deleted when the input stream is closed.
     * 
     * @throws IOException if an I/O error occurs while creating the temporary file or writing to it. Ensure that proper error
     *         handling is implemented when using this method to handle any potential issues with temporary file creation or
     *         writing.
     */
    public static InputStream newInstance(InputStream in, long len, boolean close) throws IOException {
        final var tmpfile = IOUtils.createTempFile("JRMSRV", null);
        try (final var out = new BufferedOutputStream(Files.newOutputStream(tmpfile))) {
            if (len < 0) {
                in.transferTo(out);
            } else {
                final byte[] buf = new byte[8192];
                long remaining = len;
                int r;
                while (remaining > 0 && (r = in.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                    out.write(buf, 0, r);
                    remaining -= r;
                }
            }
            if (close)
                in.close();
        }
        return new TempFileInputStream(tmpfile.toFile());
    }

    /**
     * return the length of the temporary file. This is useful for setting the Content-Length header when sending the file to the
     * client.
     * 
     * @return the length of the temporary file
     */
    public long getLength() {
        return length;
    }

}
