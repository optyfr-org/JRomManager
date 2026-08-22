package jrm.compressors;

import jrm.locale.Messages;

/**
 * Enum representing different thresholds for using temporary files during ZIP compression. Each enum constant corresponds to a
 * specific threshold value, which determines when temporary files should be used based on the size of the data being compressed.
 * The enum provides a description and a threshold value for each option, allowing users to choose the appropriate threshold for
 * their compression needs.
 */
public enum ZipTempThreshold {
    /**
     * Option indicating that temporary files should never be used during ZIP compression, regardless of the size of the data being
     * compressed. This option is represented by a threshold value of -1, which signifies that temporary files will not be utilized
     * under any circumstances.
     */
    _NEVER(Messages.getString("ZipTempThreshold.Never"), -1L), //$NON-NLS-1$ // NOSONAR
    /**
     * Option indicating that temporary files should be used during ZIP compression when the size of the data being compressed
     * exceeds 1 megabyte (MB). This option is represented by a threshold value of 1,000,000 bytes, which signifies that temporary
     * files will be utilized for compression operations involving data sizes greater than 1 MB.
     */
    _1MB(Messages.getString("ZipTempThreshold.1MB"), 1_000_000L), //$NON-NLS-1$ // NOSONAR
    /**
     * Option indicating that temporary files should be used during ZIP compression when the size of the data being compressed
     * exceeds 2 megabytes (MB). This option is represented by a threshold value of 2,000,000 bytes, which signifies that temporary
     * files will be utilized for compression operations involving data sizes greater than 2 MB.
     */
    _2MB(Messages.getString("ZipTempThreshold.2MB"), 2_000_000L), //$NON-NLS-1$ // NOSONAR
    /**
     * Option indicating that temporary files should be used during ZIP compression when the size of the data being compressed
     * exceeds 5 megabytes (MB). This option is represented by a threshold value of 5,000,000 bytes, which signifies that temporary
     * files will be utilized for compression operations involving data sizes greater than 5 MB.
     */
    _5MB(Messages.getString("ZipTempThreshold.5MB"), 5_000_000L), //$NON-NLS-1$ // NOSONAR
    /**
     * Option indicating that temporary files should be used during ZIP compression when the size of the data being compressed
     * exceeds 10 megabytes (MB). This option is represented by a threshold value of 10,000,000 bytes, which signifies that
     * temporary files will be utilized for compression operations involving data sizes greater than 10 MB.
     */
    _10MB(Messages.getString("ZipTempThreshold.10MB"), 10_000_000L), //$NON-NLS-1$ // NOSONAR
    /**
     * Option indicating that temporary files should be used during ZIP compression when the size of the data being compressed
     * exceeds 25 megabytes (MB). This option is represented by a threshold value of 25,000,000 bytes, which signifies that
     * temporary files will be utilized for compression operations involving data sizes greater than 25 MB.
     */
    _25MB(Messages.getString("ZipTempThreshold.25MB"), 25_000_000L), //$NON-NLS-1$ // NOSONAR
    /**
     * Option indicating that temporary files should be used during ZIP compression when the size of the data being compressed
     * exceeds 50 megabytes (MB). This option is represented by a threshold value of 50,000,000 bytes, which signifies that
     * temporary files will be utilized for compression operations involving data sizes greater than 50 MB.
     */
    _50MB(Messages.getString("ZipTempThreshold.50MB"), 50_000_000L), //$NON-NLS-1$ // NOSONAR
    /**
     * Option indicating that temporary files should be used during ZIP compression when the size of the data being compressed
     * exceeds 100 megabytes (MB). This option is represented by a threshold value of 100,000,000 bytes, which signifies that
     * temporary files will be utilized for compression operations involving data sizes greater than 100 MB.
     */
    _100MB(Messages.getString("ZipTempThreshold.100MB"), 100_000_000L), //$NON-NLS-1$ // NOSONAR
    /**
     * Option indicating that temporary files should be used during ZIP compression when the size of the data being compressed
     * exceeds 250 megabytes (MB). This option is represented by a threshold value of 250,000,000 bytes, which signifies that
     * temporary files will be utilized for compression operations involving data sizes greater than 250 MB.
     */
    _250MB(Messages.getString("ZipTempThreshold.250MB"), 250_000_000L), //$NON-NLS-1$ // NOSONAR
    /**
     * Option indicating that temporary files should be used during ZIP compression when the size of the data being compressed
     * exceeds 500 megabytes (MB). This option is represented by a threshold value of 500,000,000 bytes, which signifies that
     * temporary files will be utilized for compression operations involving data sizes greater than 500 MB.
     */
    _500MB(Messages.getString("ZipTempThreshold.500MB"), 500_000_000L); //$NON-NLS-1$ // NOSONAR

    /**
     * Description of the threshold option, used for display purposes. This field holds a string that provides a human-readable
     * description of the threshold option, which can be used in user interfaces or logs to indicate the selected threshold for
     * using temporary files during ZIP compression.
     */
    String desc;
    /**
     * Threshold value in bytes for using temporary files during ZIP compression. This field holds a long integer that represents
     * the size threshold in bytes, which determines when temporary files should be utilized based on the size of the data being
     * compressed. The threshold value is used to configure the compression process to optimize performance and resource usage when
     * handling large data sizes.
     */
    long threshold;

    /**
     * Constructs a new ZipTempThreshold instance with the specified description and threshold value. This constructor initializes
     * the enum constant with the provided description for display purposes and the corresponding threshold value in bytes for
     * determining when to use temporary files during ZIP compression.
     * 
     * @param name the description of the threshold option, used for display purposes
     * @param threshold the threshold value in bytes for using temporary files during ZIP compression
     */
    private ZipTempThreshold(String name, long threshold) {
        this.desc = name;
        this.threshold = threshold;
    }

    /**
     * Retrieves the description of the threshold option. This method returns the description associated with the enum constant,
     * which can be used for display purposes in user interfaces or logs to indicate the selected threshold for using temporary
     * files during ZIP compression.
     * 
     * @return the description of the threshold option, used for display purposes
     */
    public String getDesc() {
        return desc;
    }

    /**
     * Retrieves the threshold value in bytes for using temporary files during ZIP compression. This method returns the long integer
     * value representing the size threshold in bytes, which determines when temporary files should be utilized based on the size of
     * the data being compressed. The threshold value can be used to configure the compression process to optimize performance and
     * resource usage when handling large data sizes.
     * 
     * @return the threshold value in bytes for using temporary files during ZIP compression
     */
    public long getThreshold() {
        return threshold;
    }
}
