package jrm.digest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Hex;

/**
 * An implementation of {@link MDigest} wrapping standard JDK {@link MessageDigest} algorithms. Used for cryptographic hashing
 * algorithms such as MD5 and SHA-1.
 * <p>
 * Instances are stateful and <b>not thread-safe</b>: the wrapped engine accumulates state across {@link #update(byte[], int, int)}
 * calls until {@link #toString()} finalizes it. Each instance must therefore be confined to a single thread; obtain a fresh
 * instance per task via {@link MDigest#getAlgorithm(Algo)}.
 *
 * @author optyfr
 */
class MsgDigest extends MDigest {
    /**
     * The underlying cryptographic {@link MessageDigest} engine. Kept as a field (rather than a local) because the wrapper is an
     * incremental accumulator whose state must survive across {@code update}/{@code toString}/{@code reset} calls; thread safety
     * is ensured by thread-confinement, not by sharing.
     */
    private final MessageDigest digest; // NOSONAR - stateful incremental wrapper, instances are thread-confined by design (see class javadoc)

    /**
     * Constructs a new {@code MsgDigest} for the specified algorithm.
     * 
     * @param algorithm the hashing algorithm to instantiate
     * 
     * @throws NoSuchAlgorithmException if the underlying cryptographic provider does not support the algorithm
     * @throws NullPointerException if the specified {@code algorithm} parameter is {@code null}
     */
    MsgDigest(Algo algorithm) throws NoSuchAlgorithmException {
        digest = MessageDigest.getInstance(algorithm.getName());
    }

    /**
     * Updates the digest with the specified byte array input.
     * 
     * @param input the byte array input to update the digest with
     * @param offset the start offset in the byte array
     * @param len the length of bytes to read from the input, starting from the offset
     */
    @Override
    public void update(byte[] input, int offset, int len) {
        digest.update(input, offset, len);
    }

    /**
     * Completes the hash computation and returns the final hash formatted as a lower-case hexadecimal string.
     * <p>
     * This call is destructive: like the underlying {@link MessageDigest#digest()}, it finalizes the current hash and resets the
     * engine, so a second consecutive call would hash empty input. Callers must {@link #update(byte[], int, int)} new data (or
     * {@link #reset()}) before hashing again.
     *
     * @return the lower-case hexadecimal representation of the calculated hash
     */
    @Override
    public String toString() {
        return Hex.encodeHexString(digest.digest());
    }

    /**
     * Gets the matching {@link Algo} representation of the current message digest algorithm.
     * 
     * @return the matching {@link Algo} enum instance
     */
    @Override
    public Algo getAlgorithm() {
        return Algo.fromName(digest.getAlgorithm());
    }

    /**
     * Resets the message digest engine to its initial state for reuse.
     */
    @Override
    public void reset() {
        digest.reset();
    }
}
