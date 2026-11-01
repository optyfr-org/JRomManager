package jrm.compressors;

import jrm.locale.Messages;

/**
 * Enum representing different levels of compression for ZIP files. Each level is associated with a description and a corresponding
 * integer value that indicates the degree of compression. The levels range from "DEFAULT" to "ULTRA", with "STORE" representing no
 * compression and "ULTRA" representing the highest level of compression. This enum can be used to specify the desired compression
 * level when creating or modifying ZIP files.
 */
public enum ZipLevel {
    /** Default compression option, which uses the default compression level of the underlying ZIP implementation. */
    DEFAULT(Messages.getString("ZipOptions.DEFAULT"), -1), //$NON-NLS-1$
    /**
     * Store compression option, which indicates that no compression should be applied to the files in the ZIP archive. This option
     * is typically used when the files are already compressed or when compression is not desired for certain files.
     */
    STORE(Messages.getString("ZipOptions.STORE"), 0), //$NON-NLS-1$
    /**
     * Fastest compression option, which applies the least amount of compression to the files in the ZIP archive. This option is
     * typically used when speed is a priority and a smaller file size is not required.
     */
    FASTEST(Messages.getString("ZipOptions.FASTEST"), 1), //$NON-NLS-1$
    /**
     * Fast compression option, which applies a moderate level of compression to the files in the ZIP archive. This option is
     * typically used when a balance between speed and file size is desired.
     */
    FAST(Messages.getString("ZipOptions.FAST"), 3), //$NON-NLS-1$
    /**
     * Normal compression option, which applies a standard level of compression to the files in the ZIP archive. This option is
     * typically used when a good balance between speed and file size is desired for general use cases.
     */
    NORMAL(Messages.getString("ZipOptions.NORMAL"), 5), //$NON-NLS-1$
    /**
     * Maximum compression option, which applies a higher level of compression to the files in the ZIP archive. This option is
     * typically used when minimizing file size is a priority, even if it results in slower compression times.
     */
    MAXIMUM(Messages.getString("ZipOptions.MAXIMUM"), 7), //$NON-NLS-1$
    /**
     * Ultra compression option, which applies the highest level of compression to the files in the ZIP archive. This option is
     * typically used when achieving the smallest possible file size is a priority, regardless of the time it takes to compress the
     * files.
     */
    ULTRA(Messages.getString("ZipOptions.ULTRA"), 9); //$NON-NLS-1$

    /**
     * Description of the compression level, used for display purposes in user interfaces or logs to indicate the selected
     * compression level.
     */
    private final String desc;
    /**
     * Compression level associated with the option, used for configuring the compression algorithm during archive operations. The
     * integer value represents the degree of compression, with higher values indicating more aggressive compression.
     */
    private final int level;

    /**
     * Constructs a new ZipLevel instance with the specified description and compression level. This constructor initializes the
     * enum constant with the provided description for display purposes and the corresponding compression level for configuring the
     * compression algorithm during archive operations.
     * 
     * @param desc the description of the compression level, used for display purposes
     * @param level the compression level associated with the option, used for configuring the compression algorithm
     */
    private ZipLevel(final String desc, final int level) {
        this.desc = desc;
        this.level = level;
    }

    /**
     * Retrieves the description of the compression level. This method returns the description associated with the enum constant,
     * which can be used for display purposes in user interfaces or logs to indicate the selected compression level.
     * 
     * @return the description of the compression level, used for display purposes
     */
    public String getName() {
        return desc;
    }

    /**
     * Retrieves the compression level associated with the option. This method returns the integer value representing the
     * compression level for the enum constant, which can be used to configure the compression algorithm during archive operations
     * to achieve the desired level of compression.
     * 
     * @return the compression level associated with the option, used for configuring the compression algorithm
     */
    public int getLevel() {
        return level;
    }
}