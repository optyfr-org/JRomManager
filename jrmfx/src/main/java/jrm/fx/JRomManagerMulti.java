package jrm.fx;

/**
 * Convenience launcher that starts JRomManager in multi-user, no-update-check mode.
 *
 * @since 2.5
 */
public class JRomManagerMulti {
    /**
     * Entry point that delegates to {@link JRomManager#main(String[])} with
     * {@code --multiuser --noupdate}.
     *
     * @param args command-line arguments (ignored; the multi-user flags are always appended)
     */
    public static void main(String[] args /* NOSONAR */) {
        JRomManager.main(new String[] { "--multiuser", "--noupdate" });
    }

}
