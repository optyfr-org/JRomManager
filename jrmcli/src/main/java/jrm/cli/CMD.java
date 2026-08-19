package jrm.cli;

import java.util.LinkedHashSet;
import java.util.stream.Stream;

/**
 * Enum representing various command-line commands for the JRomManagerCLI application.
 */
public enum CMD {
    CD("cd"),
    PWD("pwd"),
    SET("set"),
    LS("ls", "list", "dir"),
    RM("rm", "del"),
    MD("md", "mkdir"),
    QUIET("quiet"),
    VERBOSE("verbose"),
    PREFS("prefs", "env"),
    LOAD("load"),
    SETTINGS("settings"),
    SCAN("scan"),
    SCANRESULT("scanresult", "scanresults"),
    FIX("fix"),
    DIRUPD8R("dirupdater", "dirupd8r"),
    TRNTCHK("torrentchecker", "trntchk"),
    COMPRESSOR("compressor", "compress"),
    EXIT("exit", "quit", "bye"),
    HELP("help", "?"),
    EMPTY(""),
    UNKNOWN();

    /**
     * A set of names associated with the command, allowing for multiple aliases.
     */
    private LinkedHashSet<String> names = new LinkedHashSet<>();

    /**
     * Constructs a CMD enum instance with the specified names.
     *
     * @param names The names associated with the command.
     */
    private CMD(String... names) {
        for (final String name : names)
            this.names.add(name.toLowerCase());
    }

    /**
     * Retrieves the CMD enum instance corresponding to the specified name.
     *
     * @param name The name of the command to retrieve.
     * @return The CMD enum instance matching the name, or UNKNOWN if no match is found.
     */
    public static CMD of(String name) {
        for (CMD value : CMD.values())
            if (value.names.contains(name.toLowerCase()))
                return value;
        return UNKNOWN;
    }

    /**
     * Returns the string representation of the command, which is the first name in the set of names.
     *
     * @return The string representation of the command.
     */
    @Override
    public String toString() {
        return names.stream().findFirst().orElse(super.toString());
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
