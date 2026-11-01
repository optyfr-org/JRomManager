package jrm.digest;

import java.util.zip.CRC32;

/**
 * An implementation of {@link MDigest} for calculating CRC32 checksums. This class wraps {@link CRC32} to provide standard CRC32
 * digest operations.
 * 
 * @author optyfr
 */
class CRCDigest extends MDigest {
    /**
     * The underlying CRC32 engine used to calculate the checksum.
     */
    private final CRC32 crc = new CRC32();

    /**
     * Constructs a new {@code CRCDigest} instance.
     */
    CRCDigest() {
        super();
    }

    /**
     * Updates the CRC32 checksum with the specified array of bytes.
     * 
     * @param input the byte array to update the checksum with
     * @param offset the start offset in the byte array
     * @param len the number of bytes to use for the update
     */
    @Override
    public void update(byte[] input, int offset, int len) {
        crc.update(input, offset, len);
    }

    /**
     * Returns the accumulated CRC32 checksum as a formatted 8-character lower-case hexadecimal string.
     * 
     * @return the CRC32 value formatted as an 8-character hex string
     */
    @Override
    public String toString() {
        return String.format("%08x", crc.getValue()); //$NON-NLS-1$
    }

    /**
     * Gets the algorithm representation for this digest.
     * 
     * @return the {@link Algo#CRC32} algorithm type
     */
    @Override
    public Algo getAlgorithm() {
        return Algo.CRC32; // $NON-NLS-1$
    }

    /**
     * Resets the CRC32 engine to its initial state for reuse.
     */
    @Override
    public void reset() {
        crc.reset();
    }

}
