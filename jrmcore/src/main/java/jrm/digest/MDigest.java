package jrm.digest;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

/**
 * A federating class that bridges {@link MessageDigest} and {@link CRC32} structures. Concrete subclasses must implement
 * {@link #toString()} to return the hash represented as a lower-case hexadecimal string.
 * 
 * @author optyfr
 */
public abstract class MDigest {
    /**
     * Supported hashing algorithms.
     */
    public enum Algo {
        /**
         * CRC32 cyclic redundancy check.
         */
        CRC32("CRC"),

        /**
         * Message Digest 5 (128-bit hash).
         */
        MD5("MD5"),

        /**
         * Secure Hash Algorithm 1 (160-bit hash).
         */
        SHA1("SHA-1");

        /**
         * The standard string identifier of the algorithm.
         */
        private final String name;

        /**
         * Constructs an instance of the Algo enumeration.
         * 
         * @param name the standard algorithm identifier
         */
        private Algo(String name) {
            this.name = name;
        }

        /**
         * Gets the standard string identifier of this hashing algorithm.
         * 
         * @return the standard name of the algorithm
         */
        public String getName() {
            return name;
        }

        /**
         * Finds the {@link Algo} enum instance corresponding to the specified standard string name (case-insensitive).
         * 
         * @param name the standard name of the algorithm to find
         * 
         * @return the matching {@link Algo} instance, or {@code null} if no match is found
         */
        public static Algo fromName(String name) {
            for (var algo : Algo.values())
                if (algo.name.equalsIgnoreCase(name))
                    return algo;
            return null;
        }
    }

    /**
     * Protected constructor for the abstract {@code MDigest} base class.
     */
    protected MDigest() {
        super();
    }

    /**
     * Updates the digest with the specified byte input.
     * 
     * @param input the byte array input to update the digest with
     * @param offset the start offset in the byte array (typically 0)
     * @param len the length of bytes to read from the input, starting from the offset
     */
    public abstract void update(byte[] input, int offset, int len);

    /**
     * Updates the digest with the entire byte array input. This convenience method is equivalent to calling
     * {@code update(input, 0, input.length)}.
     * 
     * @param input the byte array input to update the digest with
     */
    public void update(byte[] input) {
        update(input, 0, input.length);
    }

    /**
     * Gets the current named algorithm.
     * 
     * @return the {@link Algo} type of this digest instance
     */
    public abstract Algo getAlgorithm();

    /**
     * Resets the digest state to zero, allowing the instance to be re-used for subsequent operations.
     */
    public abstract void reset();

    /**
     * Returns the calculated digest hash formatted as a lower-case hexadecimal string.
     * 
     * @return the lowercase hexadecimal representation of the calculated hash
     */
    @Override
    public abstract String toString();

    /**
     * Factory method to obtain an appropriate {@code MDigest} instance for the specified algorithm.
     * 
     * @param algorithm the target {@link Algo} algorithm
     * 
     * @return an instance of {@link MDigest} (either {@link CRCDigest} or {@link MsgDigest})
     * 
     * @throws NoSuchAlgorithmException if the underlying cryptographic provider does not support the algorithm
     * @throws NullPointerException if the specified {@code algorithm} parameter is {@code null}
     */
    public static MDigest getAlgorithm(Algo algorithm) throws NoSuchAlgorithmException {
        if (algorithm == Algo.CRC32) // $NON-NLS-1$
            return new CRCDigest();
        return new MsgDigest(algorithm);
    }

    /**
     * Computes hashes in parallel for an array of {@link MDigest} instances from an {@link InputStream}. The stream is read into a
     * buffer, updating all specified digest engines.
     * 
     * @param in the input stream to read from
     * @param md an array of {@code MDigest} instances to update
     * 
     * @return the updated array of {@code MDigest} instances
     * 
     * @throws IOException if an I/O error occurs while reading from the stream
     * @throws NullPointerException if the specified {@code in} stream or {@code md} array is {@code null}
     */
    public static MDigest[] computeHash(final InputStream in, final MDigest[] md) throws IOException {
        try (final InputStream is = new BufferedInputStream(in, 1024 * 1024)) {
            final var buffer = new byte[8192];
            int len = is.read(buffer);
            while (len != -1) {
                for (MDigest m : md)
                    m.update(buffer, 0, len);
                len = is.read(buffer);
            }
        }
        return md;
    }
}
