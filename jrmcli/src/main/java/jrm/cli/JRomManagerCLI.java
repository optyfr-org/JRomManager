package jrm.cli;

import java.io.IOException;
import java.io.PrintWriter;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;



import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;



import jrm.aui.status.PlainTextRenderer;
import jrm.aui.status.StatusRendererFactory;


import jrm.misc.BreakException;
import jrm.misc.EnumWithDefault;
import jrm.misc.Log;


import jrm.misc.SettingsEnum;
import jrm.profile.scan.ScanException;

import jrm.security.Session;
import jrm.security.Sessions;
import lombok.Setter;
import lombok.val;

/**
 * Command line interface for JRomManager.
 */
public class JRomManagerCLI {
    static final String CLI_ERR_UNKNOWN_COMMAND = "CLI_ERR_UnknownCommand";
    static final String CLI_ERR_WRONG_ARGS = "CLI_ERR_WrongArgs";

    // Styles are defined in CLIPrinter
    static final AttributedStyle STYLE_RED_BOLD = CLIPrinter.STYLE_RED_BOLD;
    static final AttributedStyle STYLE_YELLOW_BOLD = CLIPrinter.STYLE_YELLOW_BOLD;
    static final AttributedStyle STYLE_GREEN_BOLD = CLIPrinter.STYLE_GREEN_BOLD;
    static final AttributedStyle STYLE_CYAN_BOLD = CLIPrinter.STYLE_CYAN_BOLD;

    /**
     * Session object for managing user sessions and profiles.
     * 
     * @param session The session object to be set.
     */
    @Setter
    static Session session;

    /**
     * Current working directory and root directory for file operations.
     */
    Path cwdir = null;
    /**
     * Root directory for file operations.
     */
    Path rootdir = null;

    /**
     * Progress handler for displaying progress of operations.
     */
    Progress handler = null;
    /**
     * Terminal object for interacting with the command line interface.
     */
    Terminal terminal;
    /**
     * PrintWriter for outputting messages to the terminal.
     */
    PrintWriter out;

    private final DirUpd8rCLI dirUpd8rCLI;
    private final TrntChkCLI trntChkCLI;
    private final CompressorCLI compressorCLI;
    private final FileSystemCLI fsCLI;
    private final ProfileCLI profileCLI;
    private final PrefsCLI prefsCLI;

    CLIPrinter printer;

    private final CLIRunner runner;

    final CommandLineParser parser = new CommandLineParser(this);

    Optional<String> getEnv(final String name) {
        return parser.getEnv(name);
    }

    String[] splitLine(final String line) {
        return parser.splitLine(line);
    }

    /**
     * Command line arguments for the JRomManagerCLI.
     */
    @Parameters(separators = " =")
    static class Args {
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

    /**
     * Constructs a new JRomManagerCLI instance with the provided command line arguments.
     *
     * @param cmd The command line arguments.
     * 
     * @throws IOException If an I/O error occurs during initialization.
     */
    public JRomManagerCLI(final Args cmd) throws IOException {

        /* Set the session object */
        setSession(Sessions.getSession(true, false));

        /* Set the current working directory and root directory */
        rootdir = cwdir = session.getUser().getSettings().getWorkPath().resolve("xmlfiles").toAbsolutePath().normalize(); //$NON-NLS-1$

        /* Set the status renderer to plain text */
        StatusRendererFactory.Factory.setInstance(new PlainTextRenderer());

        /* Initialize logging system */
        Log.init(session.getUser().getSettings().getLogPath() + "/JRM.%g.log", cmd.debug, 1024 * 1024, 5); //$NON-NLS-1$

        /* Set the progress handler */
        handler = new Progress();

        dirUpd8rCLI = new DirUpd8rCLI(this);
        trntChkCLI = new TrntChkCLI(this);
        compressorCLI = new CompressorCLI(this);
        fsCLI = new FileSystemCLI(this);
        profileCLI = new ProfileCLI(this);
        prefsCLI = new PrefsCLI(this);
        runner = new CLIRunner(this);

        if (cmd.interactive) {
            /* Start terminal that support interactive mode */
            runner.interactive(cmd);
        } else {
            runner.stream(cmd);
        }
    }

    /**
     * Print error message in red bold
     * 
     * @param msg The error message to be printed
     */
    void printError(final String msg) {
        printer.printError(msg);
    }

    /**
     * Print warning message in yellow bold
     * 
     * @param msg The warning message to be printed
     */
    void printWarning(final String msg) {
        printer.printWarning(msg);
    }

    /**
     * Print info message in green bold
     * 
     * @param msg The info message to be printed
     */
    void printInfo(final String msg) {
        printer.printInfo(msg);
    }

    /**
     * Print key=value pair with cyan key and default value
     * 
     * @param key The key to be printed
     * @param value The value to be printed
     */
    void printKeyValue(final String key, final String value) {
        printer.printKeyValue(key, value);
    }

    /**
     * Print label:value with colored label
     * 
     * @param label The label to be printed
     * @param value The value to be printed
     * @param labelStyle The style for the label
     */
    @SuppressWarnings("unused")
    private void printLabel(final String label, final String value, final AttributedStyle labelStyle) {
        final AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(labelStyle);
        sb.append(label);
        sb.style(AttributedStyle.DEFAULT);
        sb.append(value);
        out.println(sb.toAnsi());
    }

    /**
     * Reads commands from a file or standard input and analyzes them.
     *
     * @param cmd The command line arguments containing the input file or standard input.
     * 
     * @throws IOException If an I/O error occurs while reading the input file or standard input.
     */


    /**
     * Analyzes the provided command line arguments and executes the corresponding command.
     * 
     * @param args The command line arguments to be analyzed.
     * 
     * @return An integer status code indicating the result of the command execution.
     */
    protected int analyze(final String... args) {
        if (args.length == 0)
            return 0;
        try {
            return switch (CMD.of(args[0])) {
                case LS -> list();
                case PWD -> pwd();
                case QUIET -> setQuietMode(true);
                case VERBOSE -> setQuietMode(false);
                case SET -> set(args);
                case CD -> cd(args);
                case RM -> rm(args);
                case MD -> md(args);
                case PREFS -> prefs(args);
                case LOAD -> load(args);
                case SETTINGS -> settings(args);
                case SCAN -> scan();
                case SCANRESULT -> scanResult();
                case FIX -> fix();
                case DIRUPD8R -> processDirectoryUpdater(args);
                case TRNTCHK -> processTorrentCheck(args);
                case COMPRESSOR -> processCompressor(args);
                case HELP -> help();
                case EXIT -> exit(0);
                case EMPTY -> 0;
                case UNKNOWN -> unknownCmd(args[0], Arrays.copyOfRange(args, 1, args.length));
            };
        } catch (final IOException e) {
            Log.err(e.getMessage(), e);
        } catch (ScanException | ParameterException e) {
            out.println(e.getMessage()); // NOSONAR
            Log.err(e.getMessage(), e);
        }
        return -1;
    }

    /**
     * Processes the "compressor" command with the provided arguments.
     * 
     * @param args The command line arguments for the "compressor" command.
     * 
     * @return An integer status code indicating the result of the command execution.
     * 
     * @throws IOException If an I/O error occurs during processing.
     */
    private int processCompressor(final String... args) throws IOException {
        if (args.length < 3)
            return error(CLIMessages.getString(CLI_ERR_WRONG_ARGS));
        return compressorCLI.compressor(Arrays.copyOfRange(args, 1, args.length));
    }

    /**
     * Processes the "torrentcheck" command with the provided arguments.
     * 
     * @param args The command line arguments for the "torrentcheck" command.
     * 
     * @return An integer status code indicating the result of the command execution.
     */
    private int processTorrentCheck(final String... args) {
        if (args.length == 1)
            return error(CLIMessages.getString("CLI_ERR_TRNTCHK_SubCmdMissing"));
        return trntchk(args[1], Arrays.copyOfRange(args, 2, args.length));
    }

    /**
     * Processes the "dirupd8r" command with the provided arguments.
     * 
     * @param args The command line arguments for the "dirupd8r" command.
     * 
     * @return An integer status code indicating the result of the command execution.
     */
    private int processDirectoryUpdater(final String... args) {
        if (args.length == 1)
            return error(CLIMessages.getString("CLI_ERR_DIRUPD8R_SubCmdMissing"));
        return dirupd8r(args[1], Arrays.copyOfRange(args, 2, args.length));
    }

    /**
     * Sets the quiet mode for the CLI, controlling the verbosity of output.
     * 
     * @param quiet A boolean indicating whether to enable (true) or disable (false) quiet mode.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int setQuietMode(final boolean quiet) {
        handler.quiet(quiet);
        return 0;
    }

    /**
     * Displays help information for the CLI commands.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int help() {
        for (val cmd : CMD.values()) {
            if (cmd != CMD.EMPTY && cmd != CMD.UNKNOWN) {
                final var sb = new AttributedStringBuilder();
                sb.style(STYLE_YELLOW_BOLD).append(cmd.allStrings().collect(Collectors.joining(", "))); // NOSONAR
                sb.style(AttributedStyle.DEFAULT).append(": ").append(CLIMessages.getString("CLI_HELP_" + cmd.name())); // NOSONAR
                out.println(sb.toAnsi());
            }
        }
        return 0;
    }

    /**
     * Changes the current working directory based on the provided arguments.
     * 
     * @param args The command line arguments for the "cd" command.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int cd(final String... args) {
        return fsCLI.cd(args);
    }

    /**
     * Displays or modifies the application preferences based on the provided arguments.
     * 
     * @param args The command line arguments for the "prefs" command.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int prefs(final String... args) {
        return prefsCLI.prefs(args);
    }

    /**
     * Loads a profile or configuration based on the provided arguments.
     * 
     * @param args The command line arguments for the "load" command.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int load(final String... args) {
        return profileCLI.load(args);
    }

    /**
     * Displays or modifies the application settings based on the provided arguments.
     * 
     * @param args The command line arguments for the "settings" command.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int settings(final String... args) {
        return profileCLI.settings(args);
    }

    /**
     * Deletes files or directories based on the provided arguments.
     * 
     * @param args The command line arguments for the "rm" command.
     * 
     * @return An integer status code indicating the result of the operation.
     * 
     * @throws ParseException If there is an error parsing the command line arguments.
     * @throws IOException If there is an error accessing the file system.
     */
    private int rm(final String... args) throws ParameterException, IOException {
        return fsCLI.rm(args);
    }

    /**
     * Fixes issues in the current scan based on the loaded profile.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int fix() {
        return profileCLI.fix();
    }

    /**
     * Displays the results of the current scan.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int scanResult() {
        return profileCLI.scanResult();
    }

    /**
     * Scans the specified directories and files.
     * 
     * @return An integer status code indicating the result of the operation.
     * 
     * @throws BreakException If the scan is interrupted.
     * @throws ScanException If there is an error during the scan.
     */
    private int scan() throws BreakException, ScanException {
        return profileCLI.scan();
    }

    /**
     * Creates directories based on the provided arguments.
     * 
     * @param args The command line arguments for the "md" command.
     * 
     * @return An integer status code indicating the result of the operation.
     * 
     * @throws ParseException If there is an error parsing the command line arguments.
     * @throws IOException If there is an error accessing the file system.
     */
    private int md(final String... args) throws ParameterException, IOException {
        return fsCLI.md(args);
    }

    /**
     * Sets system properties or environment variables based on the provided arguments.
     * 
     * @param args The command line arguments for the "set" command.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int set(final String... args) {
        return fsCLI.set(args);
    }

    /**
     * Processes the "dirupd8r" command with the provided arguments.
     * 
     * @param cmd The subcommand for the "dirupd8r" command.
     * @param args The command line arguments for the "dirupd8r" command.
     * 
     * @return An integer status code indicating the result of the command execution.
     * 
     * @throws ParameterException If there is an error parsing the command line arguments.
     */
    private int dirupd8r(final String cmd, final String... args) throws ParameterException {
        return dirUpd8rCLI.dirupd8r(cmd, args);
    }

    /**
     * Handles unknown commands by displaying an error message.
     * 
     * @param cmd The unknown command.
     * @param args The command line arguments associated with the unknown command.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    int unknownCmd(final String cmd, final String... args) {
        return error(() -> CLIMessages.getString(CLI_ERR_UNKNOWN_COMMAND) + cmd + " "
                + Stream.of(args).map(s -> s.contains(" ") ? ('"' + s + '"') : s).collect(Collectors.joining(" ")));
    }

    /**
     * Processes the "trntchk" command with the provided arguments.
     * 
     * @param cmd The subcommand for the "trntchk" command.
     * @param args The command line arguments for the "trntchk" command.
     * 
     * @return An integer status code indicating the result of the command execution.
     * 
     * @throws ParameterException If there is an error parsing the command line arguments.
     */
    private int trntchk(final String cmd, final String... args) throws ParameterException {
        return trntChkCLI.trntchk(cmd, args);
    }

    /**
     * Displays or modifies the application preferences based on the provided arguments.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    int prefs(final Enum<?> name) {
        return prefsCLI.prefs(name);
    }

    int prefs(final Enum<?> name, final String value) {
        return prefsCLI.prefs(name, value);
    }

    /**
     * Displays all profile settings.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    /**
     * Exits the application with the specified status code.
     * 
     * @param status The exit status code.
     * 
     * @return The exit status code.
     */
    private int exit(final int status) {
        System.exit(status);
        return status;
    }

    /**
     * Displays an error message and returns a status code indicating an error.
     * 
     * @param msg The error message to display.
     * 
     * @return An integer status code indicating an error.
     */
    int error(final String msg) {
        printError(msg);
        return -1;
    }

    /**
     * Displays an error message generated by the provided supplier and returns a status code indicating an error.
     * 
     * @param supplier A supplier that generates the error message to display.
     * 
     * @return An integer status code indicating an error.
     */
    int error(final Supplier<String> supplier) {
        printError(supplier.get());
        return -1;
    }

    /**
     * Loads a profile from the specified file path.
     * 
     * @param profile The file path of the profile to load.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int load(final String profile) {
        return profileCLI.load(profile);
    }

    /**
     * Changes the current working directory to the specified directory.
     * 
     * @param dir The directory to change to.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int cd(final String dir) {
        return fsCLI.cd(dir);
    }

    /**
     * Displays the current working directory relative to the root directory.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int pwd() {
        return fsCLI.pwd();
    }

    /**
     * Lists the directories and data files in the current working directory.
     * 
     * @return An integer status code indicating the result of the operation.
     * 
     * @throws IOException If there is an error accessing the file system.
     */
    private int list() throws IOException {
        return fsCLI.list();
    }

    /**
     * The main entry point of the JRomManagerCLI application.
     * 
     * @param args The command line arguments passed to the application.
     */
    public static void main(final String[] args) {
        final var jArgs = new Args();
        final var cmd = JCommander.newBuilder().addObject(jArgs).build();
        try {
            cmd.parse(args);
            if (jArgs.help)
                cmd.usage();
            else
                new JRomManagerCLI(jArgs);
        } catch (final ParameterException e) {
            Log.err(e.getMessage(), e);
            cmd.usage();
            System.exit(1);
        } catch (final IOException e) {
            Log.err(e.getMessage());
        }
    }

}
