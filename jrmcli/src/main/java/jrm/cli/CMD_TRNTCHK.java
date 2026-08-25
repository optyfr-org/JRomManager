package jrm.cli;

import java.util.LinkedHashSet;
import java.util.stream.Stream;

/**
 * Enum representing various command-line commands for the JRomManagerCLI application.
 */
public enum CMD_TRNTCHK implements CommandNames {
    LSSDR("lssdr"),
    CLEARSDR("clearsdr"),
    ADDSDR("addsdr"),
    START("start"),
    HELP("help", "?"),
    EMPTY(""),
    UNKNOWN();

    /**
     * A set of names associated with the command, allowing for multiple aliases.
     */
    private LinkedHashSet<String> names = new LinkedHashSet<>();

    /**
     * Constructs a CMD_TRNTCHK enum instance with the specified names.
     *
     * @param names The names associated with the command.
     */
    private CMD_TRNTCHK(String... names) {
        for (final String name : names)
            this.names.add(name.toLowerCase());
    }

    /**
     * Retrieves the CMD_TRNTCHK enum instance corresponding to the specified name.
     *
     * @param name The name of the command to retrieve.
     * @return The CMD_TRNTCHK enum instance matching the name, or UNKNOWN if no match is found.
     */
    public static CMD_TRNTCHK of(String name) {
        for (CMD_TRNTCHK value : CMD_TRNTCHK.values())
            if (value.names.contains(name.toLowerCase()))
                return value;
        return UNKNOWN;
    }

    /**
     * Returns the first name associated with the command, or the default toString() if no names are present.
     *
     * @return The first name of the command or the default string representation.
     */
    @Override
    public String toString() {
        return names.isEmpty() ? super.toString() : names.getFirst();
    }

    /**
     * Returns a stream of all names associated with the command.
     *
     * @return A stream of all names associated with the command.
     */
    public Stream<String> allStrings() {
        return names.stream();
    }

}
