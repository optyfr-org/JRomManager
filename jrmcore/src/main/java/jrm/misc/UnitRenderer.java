package jrm.misc;

/**
 * Interface that provides formatting methods for rendering sizes and byte counts into human-readable representations.
 * 
 * @author optyfr
 */
public interface UnitRenderer {
    /**
     * Formats a given number of bytes into a human-readable string representation, optionally using SI units ($1000$ base) or
     * binary units ($1024$ base).
     * <p>
     * Formula for inline display: $bytes = value \times unit^{exp}$
     * </p>
     * 
     * @param bytes the quantity in bytes to render
     * @param si if {@code true}, uses SI unit base ($1000$); if {@code false}, uses binary unit base ($1024$)
     * 
     * @return a formatted, human-readable string representation of the byte size (e.g. {@code "1.5 MiB"} or {@code "1.6 MB"})
     */
    public default String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit)
            return bytes + " B"; //$NON-NLS-1$
        int exp = (int) (Math.log((double)bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre); //$NON-NLS-1$
    }
}
