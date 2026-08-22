package jrm.cli;

import java.util.stream.Stream;

/**
 * Common interface for command enums to provide their string aliases for completers and help.
 */
public interface CommandNames {
    /**
     * @return stream of all alias strings for this command (lowercased).
     */
    Stream<String> allStrings();
}
