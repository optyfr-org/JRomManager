package jrm.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;

import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;



import jrm.aui.status.PlainTextRenderer;
import jrm.aui.status.StatusRendererFactory;
import jrm.batch.Compressor;
import jrm.batch.Compressor.FileResult;
import jrm.batch.CompressorFormat;

import jrm.misc.BreakException;
import jrm.misc.EnumWithDefault;
import jrm.misc.Log;

import jrm.misc.ProfileSettingsEnum;
import jrm.misc.SettingsEnum;
import jrm.profile.Profile;
import jrm.profile.fix.Fix;
import jrm.profile.manager.ProfileNFO;
import jrm.profile.scan.Scan;
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
    /**
     * Red bold style for error messages.
     */
    static final AttributedStyle STYLE_RED_BOLD = AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold();
    /**
     * Yellow bold style for warning messages.
     */
    static final AttributedStyle STYLE_YELLOW_BOLD = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold();
    /**
     * Green bold style for success messages.
     */
    static final AttributedStyle STYLE_GREEN_BOLD = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN).bold();
    /**
     * Cyan bold style for key=value pairs.
     */
    static final AttributedStyle STYLE_CYAN_BOLD = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold();
    /**
     * Dim style for less important text, using bright color and italic.
     */
    @SuppressWarnings("unused")
    private static final AttributedStyle STYLE_DIM = AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT).italic();

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

    /**
     * Command line arguments for the JRomManagerCLI.
     */
    @Parameters(separators = " =")
    private static class Args {
        /**
         * Flag to indicate if help message should be displayed.
         */
        @Parameter(names = { "--help", "-h" }, help = true)
        private boolean help = false;

        /**
         * Flag to indicate if the interactive shell should be started.
         */
        @Parameter(names = { "--interactive", "-i" }, description = "Interactive shell")
        private boolean interactive = false;

        /**
         * Flag to indicate if the debug mode should be enabled.
         */
        @Parameter(names = { "--debug", "-d" }, description = "Debug mode")
        private boolean debug = false;

        /**
         * Input file for reading commands. If not provided, commands will be read from standard input.
         */
        @Parameter(names = { "--file", "-f" }, description = "Input file", arity = 1)
        private String file = null;
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

        if (cmd.interactive) {
            /* Start terminal that support interactive mode */
            interactive(cmd);
        } else {
            stream(cmd);
        }
    }

    /**
     * Print error message in red bold
     * 
     * @param msg The error message to be printed
     */
    void printError(final String msg) {
        out.println(new AttributedString(msg, STYLE_RED_BOLD).toAnsi());
    }

    /**
     * Print warning message in yellow bold
     * 
     * @param msg The warning message to be printed
     */
    void printWarning(final String msg) {
        out.println(new AttributedString(msg, STYLE_YELLOW_BOLD).toAnsi());
    }

    /**
     * Print info message in green bold
     * 
     * @param msg The info message to be printed
     */
    void printInfo(final String msg) {
        out.println(new AttributedString(msg, STYLE_GREEN_BOLD).toAnsi());
    }

    /**
     * Print key=value pair with cyan key and default value
     * 
     * @param key The key to be printed
     * @param value The value to be printed
     */
    void printKeyValue(final String key, final String value) {
        final AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(STYLE_CYAN_BOLD);
        sb.append(key);
        sb.style(AttributedStyle.DEFAULT);
        sb.append("="); //$NON-NLS-1$
        sb.append(value);
        out.println(sb.toAnsi());
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
    private void stream(final Args cmd) throws IOException {
        /* Start terminal that support non-interactive mode */
        terminal = TerminalBuilder.builder().dumb(true).build();
        /* Create a PrintWriter for outputting messages to the terminal */
        out = terminal.writer();
        /* Start processing commands from the input file or standard input */
        final Reader reader = cmd.file != null ? new FileReader(cmd.file) : new InputStreamReader(System.in);
        try (final var in = new BufferedReader(reader);) {
            String line;
            while (null != (line = in.readLine())) {
                if (line.startsWith("#")) //$NON-NLS-1$
                    continue;
                analyze(splitLine(line));
            }
        } catch (final IOException e) {
            Log.err(e.getMessage());
        }
    }

    /**
     * Creates a JLine completer that provides tab completion for all commands. Uses ArgumentCompleter for positional completion
     * (command + subcommand).
     * 
     * @return A JLine Completer that provides tab completion for all commands.
     */
    private Completer createCompleter() {
        // Collect all command aliases from CMD enum
        final List<String> commandNames = new ArrayList<>();
        for (final CMD cmd : CMD.values()) {
            if (cmd != CMD.EMPTY && cmd != CMD.UNKNOWN) {
                cmd.allStrings().forEach(commandNames::add);
            }
        }
        final StringsCompleter cmdCompleter = new StringsCompleter(commandNames);

        // Collect DIRUPD8R subcommand aliases
        final List<String> dirupd8rNames = new ArrayList<>();
        for (final CMD_DIRUPD8R cmd : CMD_DIRUPD8R.values()) {
            if (cmd != CMD_DIRUPD8R.EMPTY && cmd != CMD_DIRUPD8R.UNKNOWN) {
                cmd.allStrings().forEach(dirupd8rNames::add);
            }
        }
        final StringsCompleter dirupd8rCompleter = new StringsCompleter(dirupd8rNames);

        // Collect TRNTCHK subcommand aliases
        final List<String> trntchkNames = new ArrayList<>();
        for (final CMD_TRNTCHK cmd : CMD_TRNTCHK.values()) {
            if (cmd != CMD_TRNTCHK.EMPTY && cmd != CMD_TRNTCHK.UNKNOWN) {
                cmd.allStrings().forEach(trntchkNames::add);
            }
        }
        final StringsCompleter trntchkCompleter = new StringsCompleter(trntchkNames);

        // Build completers: main commands, dirupd8r subcommands, trntchk subcommands
        return new AggregateCompleter(
                new ArgumentCompleter(new StringsCompleter("dirupd8r", "dirupdater"), dirupd8rCompleter, NullCompleter.INSTANCE), //$NON-NLS-1$ //$NON-NLS-2$
                new ArgumentCompleter(new StringsCompleter("trntchk", "torrentchecker"), trntchkCompleter, NullCompleter.INSTANCE), //$NON-NLS-1$ //$NON-NLS-2$
                new ArgumentCompleter(cmdCompleter, NullCompleter.INSTANCE));
    }

    /**
     * Builds a colored prompt string using JLine's AttributedStringBuilder. Shows "jrm [profile]> " with green "jrm" and yellow
     * profile name.
     * 
     * @return A string representing the prompt to be displayed in the interactive shell.
     */
    private String buildPrompt() {
        final AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN).bold()).append("jrm");
        if (session.getCurrProfile() != null) {
            sb.style(AttributedStyle.DEFAULT).append(" [")
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold())
                    .append(session.getCurrProfile().getNfo().getFile().getName())
                    .style(AttributedStyle.DEFAULT).append("]");
        }
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN)).append("> ");
        return sb.toAnsi();
    }

    /**
     * Initializes the interactive shell and starts the command processing loop.
     * 
     * @throws IOException If an I/O error occurs during initialization.
     */
    private void interactive(Args cmd) throws IOException {
        terminal = TerminalBuilder.builder().system(true).build();
        final LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(createCompleter())
                .option(LineReader.Option.AUTO_FRESH_LINE, true)
                .build();
        out = terminal.writer();
        do {
            boolean doBreak = false;
            String line = null;
            try {
                line = reader.readLine(buildPrompt());
            } catch (UserInterruptException | EndOfFileException _) {
                // Ctrl+C (INT), or Ctrl+D (EOF) pressed - break the loop and exit
                doBreak = true;
            }
            if (doBreak)
                break;
            try {
                if (line != null && !line.trim().isEmpty())
                    analyze(splitLine(line));
            } catch(Exception e) {
                out.println(e.getMessage());
                if(cmd.debug)
                    Log.err(e.getMessage(), e);
            }
        } while (true);
    }

    /**
     * Pattern to split a command line into arguments, considering quoted strings and whitespace.
     */
    private final Pattern splitLinePattern = Pattern.compile("\"([^\"]*)\"|(\\S+)"); //$NON-NLS-1$
    /**
     * Pattern to match environment variable references in the form of $VAR or ${VAR}.
     */
    private final Pattern envPattern = Pattern.compile("\\$(?:([\\w\\.]+)|\\{([\\w\\.]+)\\})"); //$NON-NLS-1$

    /**
     * Retrieves the value of an environment variable or system property by name.
     * 
     * @param name The name of the environment variable or system property.
     * 
     * @return An Optional containing the value if present, or an empty Optional if not found.
     */
    private Optional<String> getEnv(final String name) {
        Optional<String> ret = Optional.ofNullable(System.getProperty(name));
        if (!ret.isPresent())
            ret = Optional.ofNullable(System.getenv(name));
        return ret;
    }

    /**
     * Splits a command line string into an array of arguments, handling quoted strings and environment variable substitution.
     * 
     * @param line The command line string to be split.
     * 
     * @return An array of strings representing the individual arguments.
     */
    private String[] splitLine(final String line) {
        final List<String> list = new ArrayList<>();
        final var m = splitLinePattern.matcher(line);
        while (m.find()) {
            final var im = envPattern.matcher(m.group(m.group(1) != null ? 1 : 2));
            final var sb = new StringBuilder();
            while (im.find())
                im.appendReplacement(sb, getEnv(im.group(im.group(1) != null ? 1 : 2)).map(Matcher::quoteReplacement).orElse("")); //$NON-NLS-1$
            im.appendTail(sb);
            list.add(sb.toString());
        }
        return list.stream().toArray(String[]::new);
    }

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
        return compressor(Arrays.copyOfRange(args, 1, args.length));
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
        if (args.length == 1)
            return pwd();
        if (args.length == 2)
            return cd(args[1]);
        return error(CLIMessages.getString(CLI_ERR_WRONG_ARGS)); // $NON-NLS-1$
    }

    /**
     * Displays or modifies the application preferences based on the provided arguments.
     * 
     * @param args The command line arguments for the "prefs" command.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int prefs(final String... args) {
        if (args.length == 1)
            return prefs();
        if (args.length == 2)
            return prefs(jrm.misc.SettingsEnum.from(args[1]));
        if (args.length == 3)
            return prefs(jrm.misc.SettingsEnum.from(args[1]), args[2]);
        return error(CLIMessages.getString(CLI_ERR_WRONG_ARGS)); // $NON-NLS-1$
    }

    /**
     * Loads a profile or configuration based on the provided arguments.
     * 
     * @param args The command line arguments for the "load" command.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int load(final String... args) {
        if (args.length == 2)
            return load(args[1]);
        return error(CLIMessages.getString(CLI_ERR_WRONG_ARGS)); // $NON-NLS-1$
    }

    /**
     * Displays or modifies the application settings based on the provided arguments.
     * 
     * @param args The command line arguments for the "settings" command.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int settings(final String... args) {
        if (session.getCurrProfile() == null)
            return error(CLIMessages.getString("CLI_ERR_NoProfileLoaded")); //$NON-NLS-1$
        if (args.length == 1)
            return settings();
        if (args.length == 2)
            return settings(jrm.misc.SettingsEnum.from(args[1]));
        if (args.length == 3)
            return settings(jrm.misc.SettingsEnum.from(args[1]), args[2]);
        return error(CLIMessages.getString(CLI_ERR_WRONG_ARGS)); // $NON-NLS-1$
    }

    /**
     * Command line arguments for the "rm" command, supporting recursive deletion and file list.
     */
    @Parameters(separators = " =")
    private static class RmArgs {
        /**
         * Flag to indicate if recursive deletion should be performed.
         */
        @Parameter(names = { "--recursive", "-r" }, description = "Recursive delete")
        private boolean recurisve;

        /**
         * List of files or directories to be deleted.
         */
        @Parameter(description = "Files")
        private List<String> files = new ArrayList<>();
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
        final var jArgs = new RmArgs();
        JCommander.newBuilder().addObject(jArgs).build().parse(Arrays.copyOfRange(args, 1, args.length));
        for (final String arg : jArgs.files)
            recursiveDelete(Paths.get(arg), jArgs.recurisve);
        return 0;
    }

    /**
     * Fixes issues in the current scan based on the loaded profile.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int fix() {
        if (session.getCurrScan() == null)
            return error(CLIMessages.getString("CLI_ERR_ShouldScanFirst")); //$NON-NLS-1$
        if (session.getCurrProfile().hasPropsChanged())
            return error(CLIMessages.getString("CLI_ERR_PropsChanged")); //$NON-NLS-1$
        if (session.getCurrScan().actions.stream().mapToInt(Collection::size).sum() == 0)
            return error(CLIMessages.getString("CLI_ERR_NothingToFix")); //$NON-NLS-1$
        final var fix = new Fix(session.getCurrProfile(), session.getCurrScan(), handler);
        printInfo(String.format(CLIMessages.getString("CLI_MSG_ActionRemaining"), fix.getActionsRemain())); //$NON-NLS-1$
        return fix.getActionsRemain();
    }

    /**
     * Displays the results of the current scan.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int scanResult() {
        if (session.getCurrScan() == null)
            return error(CLIMessages.getString("CLI_ERR_ShouldScanFirst")); //$NON-NLS-1$
        if (session.getCurrProfile().hasPropsChanged())
            return error(CLIMessages.getString("CLI_ERR_PropsChanged")); //$NON-NLS-1$
        if (session.getReport() == null)
            return error(CLIMessages.getString("CLI_ERR_NoReport")); //$NON-NLS-1$
        printInfo(session.getReport().getStats().getStatus());
        return 0;
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
        if (session.getCurrProfile() == null)
            return error(CLIMessages.getString("CLI_ERR_NoProfileLoaded")); //$NON-NLS-1$
        session.setCurrScan(new Scan(session.getCurrProfile(), handler));
        return session.getCurrScan().actions.stream().mapToInt(Collection::size).sum();
    }

    /**
     * Command line arguments for the "md" command, supporting parent directory creation and file list.
     */
    @Parameters(separators = " =")
    private static class MdArgs {
        /**
         * Flag to indicate if parent directories should be created up to the specified directory.
         */
        @Parameter(names = { "--parents", "-p" }, description = "Create parents up to this directory")
        private boolean parents;

        /**
         * List of directories to be created.
         */
        @Parameter(description = "Files")
        private List<String> files = new ArrayList<>();
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
        final var jArgs = new MdArgs();
        JCommander.newBuilder().addObject(jArgs).build().parse(Arrays.copyOfRange(args, 1, args.length));
        for (final String arg : jArgs.files) {
            final var path = Paths.get(arg);
            if (!Files.exists(path)) {
                if (jArgs.parents)
                    Files.createDirectories(path);
                else
                    Files.createDirectory(path);
            }
        }
        return 0;
    }

    /**
     * Sets system properties or environment variables based on the provided arguments.
     * 
     * @param args The command line arguments for the "set" command.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int set(final String... args) {
        if (args.length == 1) {
            System.getProperties().forEach((k, v) -> printKeyValue(String.valueOf(k), String.valueOf(v)));
            System.getenv().forEach(this::printKeyValue);
            return 0;
        }
        if (args.length == 2) {
            getEnv(args[1]).ifPresent(out::println);
            return 0;
        }
        if (args.length == 3) {
            if (args[2].isEmpty())
                System.clearProperty(args[1]);
            else
                System.setProperty(args[1], args[2]);
            return 0;
        }
        return error(CLIMessages.getString(CLI_ERR_WRONG_ARGS)); // $NON-NLS-1$
    }

    /**
     * Recursively deletes a directory and its contents.
     * 
     * @param path The path of the directory to delete.
     * @param recurse Flag indicating whether to delete contents recursively.
     * 
     * @throws IOException If there is an error accessing the file system.
     */
    private void recursiveDelete(final Path path, final boolean recurse) throws IOException {
        if (!Files.exists(path))
            return;
        if (!Files.isDirectory(path)) {
            Files.delete(path);
            return;
        }
        try {
            Files.delete(path);
        } catch (final DirectoryNotEmptyException _) {
            if (recurse) // recursively delete from bottom to top
                try (final var stream = Files.walk(path)) {
                    stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
        }
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
     * Command line arguments for the "trntchk" command, supporting check mode and various options.
     */
    @Parameters(separators = " =")
    private static class CompressorArgs {
        /**
         * The compression format to be used for the compression operation.
         */
        @Parameter(names = { "--compressor", "-c" }, arity = 1, required = true, description = "Compression format")
        private String compressor;

        /**
         * Flag to indicate if the compression operation should be forced, even if the target file already exists.
         */
        @Parameter(names = { "--force", "-f" }, description = "Force compression")
        private boolean force;

        /**
         * List of files to be compressed.
         */
        @Parameter(description = "Files")
        private List<String> files = new ArrayList<>();
    }

    /**
     * Compresses files based on the provided command line arguments.
     * 
     * @param args The command line arguments for the compression operation.
     * 
     * @return An integer status code indicating the result of the operation.
     * 
     * @throws IOException If there is an error accessing the file system.
     * @throws ParameterException If there is an error parsing the command line arguments.
     */
    private int compressor(final String... args) throws IOException, ParameterException {
        final var jArgs = new CompressorArgs();
        final var cmd = JCommander.newBuilder().addObject(jArgs).build();
        try {
            cmd.parse(args);
            final CompressorFormat format = jArgs.compressor != null ? CompressorFormat.valueOf(jArgs.compressor) : CompressorFormat.TZIP;
            for (final var arg : jArgs.files) {
                final var path = Paths.get(arg);
                final List<FileResult> frl;
                if (Files.isDirectory(path)) {
                    try (final var stream = Files.walk(path)) {
                        frl = stream.filter(p -> Files.isRegularFile(p) && FilenameUtils.isExtension(p.getFileName().toString(), Compressor.getExtensions())).map(FileResult::new)
                                .toList();
                    }
                } else
                    frl = Arrays.asList(new FileResult(path));
                final var cnt = new AtomicInteger();
                final var compressor = new Compressor(session, cnt, frl.size(), handler);
                frl.parallelStream().forEach(fr -> {
                    final Path file = fr.getFile();
                    cnt.incrementAndGet();
                    final Compressor.UpdResultCallBack cb = fr::setResult;
                    final Compressor.UpdSrcCallBack scb = src -> fr.setFile(src.toPath());
                    compressor.compress(format, file.toFile(), jArgs.force, cb, scb);
                });
            }
        } catch (final ParameterException e) {
            Log.err(e.getMessage(), e);
            cmd.usage();
            throw e;
        }
        return 0;
    }

    /**
     * Displays or modifies the application preferences based on the provided arguments.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int prefs() {
        for (final var e : SettingsEnum.values())
            printKeyValue(e.toString(), session.getUser().getSettings().getProperty(e));
        return 0;
    }

    /**
     * Displays or modifies a specific application preference based on the provided name.
     * 
     * @param name The name of the preference to display or modify.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    int prefs(final Enum<?> name) {
        if (!session.getUser().getSettings().hasProperty(name))
            printWarning(String.format(CLIMessages.getString("CLI_MSG_PropIsNotSet"), name));
        else if (name instanceof final EnumWithDefault n)
            printKeyValue(name.toString(), session.getUser().getSettings().getProperty(n));
        return 0;
    }

    /**
     * Modifies a specific application preference based on the provided name and value.
     * 
     * @param name The name of the preference to modify.
     * @param value The new value for the preference.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    int prefs(final Enum<?> name, final String value) {
        session.getUser().getSettings().setProperty(name, value);
        session.getUser().getSettings().saveSettings();
        return 0;
    }

    /**
     * Displays all profile settings.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int settings() {
        for (final var e : ProfileSettingsEnum.values())
            printKeyValue(e.toString(), session.getCurrProfile().getSettings().getProperty(e));
        return 0;
    }

    /**
     * Displays or modifies a specific profile setting based on the provided name.
     * 
     * @param name The name of the profile setting to display or modify.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int settings(final Enum<?> name) {
        if (!session.getCurrProfile().getSettings().hasProperty(name))
            printWarning(String.format(CLIMessages.getString("CLI_MSG_PropIsNotSet"), name));
        else if (name instanceof final EnumWithDefault n)
            printKeyValue(name.toString(), session.getCurrProfile().getSettings().getProperty(n));
        return 0;
    }

    /**
     * Modifies a specific profile setting based on the provided name and value.
     * 
     * @param name The name of the profile setting to modify.
     * @param value The new value for the profile setting.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int settings(final Enum<?> name, final String value) {
        session.getCurrProfile().getSettings().setProperty(name, value);
        session.getCurrProfile().saveSettings();
        return 0;
    }

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
        final Path candidate = cwdir.resolve(profile);
        if (Files.isRegularFile(candidate))
            session.setCurrProfile(Profile.load(session, candidate.toFile(), handler));
        else
            printError(String.format(CLIMessages.getString("CLI_ERR_ProfileNotExist"), profile)); //$NON-NLS-1$
        return 0;
    }

    /**
     * Changes the current working directory to the specified directory.
     * 
     * @param dir The directory to change to.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int cd(final String dir) {
        if (dir.equals(File.separator)) {
            cwdir = rootdir;
        } else {
            final var resolvedDir = dir.startsWith("~") ? dir.replace("~", rootdir.toString()) : dir;
            final var candidate = cwdir.resolve(resolvedDir).normalize();
            if (rootdir.startsWith(candidate) && !rootdir.equals(candidate)) {
                cwdir = rootdir;
                printError(String.format(CLIMessages.getString("CLI_ERR_CantGoUpDir"), resolvedDir));
            } else if (Files.isDirectory(candidate)) {
                if (candidate.startsWith(rootdir)) {
                    cwdir = candidate;
                } else {
                    printError(String.format(CLIMessages.getString("CLI_ERR_CantChangeDir"), resolvedDir));
                }
            } else {
                printError(String.format(CLIMessages.getString("CLI_ERR_UnknownDir"), resolvedDir));
            }
        }
        return 0;
    }

    /**
     * Displays the current working directory relative to the root directory.
     * 
     * @return An integer status code indicating the result of the operation.
     */
    private int pwd() {
        printInfo("~/" + rootdir.relativize(cwdir)); //$NON-NLS-1$
        return 0;
    }

    /**
     * Lists the directories and data files in the current working directory.
     * 
     * @return An integer status code indicating the result of the operation.
     * 
     * @throws IOException If there is an error accessing the file system.
     */
    private int list() throws IOException {
        try (final var stream = Files.walk(cwdir, 1)) {
            stream.filter(p -> Files.isDirectory(p) && !p.equals(cwdir)).sorted(Path::compareTo).map(cwdir::relativize)
                    .forEachOrdered(p -> {
                        final AttributedStringBuilder sb = new AttributedStringBuilder();
                        sb.style(STYLE_GREEN_BOLD).append("<DIR>").append("\t");
                        sb.style(AttributedStyle.DEFAULT).append(p.toString());
                        out.println(sb.toAnsi());
                    });
        }
        for (val row : ProfileNFO.list(session, cwdir.toFile())) {
            final AttributedStringBuilder sb = new AttributedStringBuilder();
            sb.style(STYLE_CYAN_BOLD).append("<DAT>").append("\t"); // NOSONAR
            sb.style(AttributedStyle.DEFAULT).append(row.getName()); // NOSONAR
            out.println(sb.toAnsi());
        }
        return 0;
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
