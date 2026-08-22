package jrm.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Command line arguments for the JRomManagerCLI.
 */
@Parameters(separators = " =")
public class CLIArgs {
    /**
     * Flag to indicate if help message should be displayed.
     */
    @Parameter(names = { "--help", "-h" }, help = true)
    boolean help = false;

    /**
     * Flag to indicate if the interactive shell should be started.
     */
    @Parameter(names = { "--interactive", "-i" }, description = "Interactive shell")
    boolean interactive = false;

    /**
     * Flag to indicate if the debug mode should be enabled.
     */
    @Parameter(names = { "--debug", "-d" }, description = "Debug mode")
    boolean debug = false;

    /**
     * Input file for reading commands. If not provided, commands will be read from standard input.
     */
    @Parameter(names = { "--file", "-f" }, description = "Input file", arity = 1)
    String file = null;
}
