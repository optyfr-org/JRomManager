package jrm.cli;

import java.io.IOException;
import java.io.PrintWriter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import jrm.aui.status.PlainTextRenderer;
import jrm.aui.status.StatusRendererFactory;

import jrm.misc.Log;

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

    /** Session object for managing user sessions and profiles (global for CLI). */
    @Setter
    @SuppressWarnings("squid:S1444")
    static Session session;

    Path cwdir = null;
    Path rootdir = null;

    Progress handler = null;
    Terminal terminal;
    PrintWriter out;

    DirUpd8rCLI dirUpd8rCLI;
    TrntChkCLI trntChkCLI;
    CompressorCLI compressorCLI;
    FileSystemCLI fsCLI;
    ProfileCLI profileCLI;
    PrefsCLI prefsCLI;

    CLIPrinter printer;

    CLIRunner runner;

    CommandLineParser parser = new CommandLineParser();

    Optional<String> getEnv(final String name) {
        return parser.getEnv(name);
    }

    String[] splitLine(final String line) {
        return parser.splitLine(line);
    }

    /**
     * Functional interface for command handlers to enable registry-based dispatch.
     */
    @FunctionalInterface
    private static interface CommandHandler {
        @SuppressWarnings("java:S112")
        int execute(String... args) throws Exception;
    }

    Map<CMD, CommandHandler> commandHandlers = new EnumMap<>(CMD.class);

    void initCommandHandlers() {
        // Simple commands that delegate directly
        commandHandlers.put(CMD.LS, args -> fsCLI.list());
        commandHandlers.put(CMD.PWD, args -> fsCLI.pwd());
        commandHandlers.put(CMD.QUIET, args -> setQuietMode(true));
        commandHandlers.put(CMD.VERBOSE, args -> setQuietMode(false));
        commandHandlers.put(CMD.SET, fsCLI::set);
        commandHandlers.put(CMD.CD, fsCLI::cd);
        commandHandlers.put(CMD.RM, fsCLI::rm);
        commandHandlers.put(CMD.MD, fsCLI::md);
        commandHandlers.put(CMD.PREFS, prefsCLI::prefs);
        commandHandlers.put(CMD.LOAD, profileCLI::load);
        commandHandlers.put(CMD.SETTINGS, profileCLI::settings);
        commandHandlers.put(CMD.SCAN, args -> profileCLI.scan());
        commandHandlers.put(CMD.SCANRESULT, args -> profileCLI.scanResult());
        commandHandlers.put(CMD.FIX, args -> profileCLI.fix());

        // Sub-command processors
        commandHandlers.put(CMD.DIRUPD8R, args -> {
            if (args.length == 1)
                return error(CLIMessages.getString("CLI_ERR_DIRUPD8R_SubCmdMissing"));
            return dirUpd8rCLI.dirupd8r(args[1], Arrays.copyOfRange(args, 2, args.length));
        });
        commandHandlers.put(CMD.TRNTCHK, args -> {
            if (args.length == 1)
                return error(CLIMessages.getString("CLI_ERR_TRNTCHK_SubCmdMissing"));
            return trntChkCLI.trntchk(args[1], Arrays.copyOfRange(args, 2, args.length));
        });
        commandHandlers.put(CMD.COMPRESSOR, args -> {
            if (args.length < 3)
                return error(CLIMessages.getString(CLI_ERR_WRONG_ARGS));
            return compressorCLI.compressor(Arrays.copyOfRange(args, 1, args.length));
        });

        // Built-in
        commandHandlers.put(CMD.HELP, args -> help());
        commandHandlers.put(CMD.EXIT, args -> exit(0));
        // EMPTY and UNKNOWN handled specially in analyze
    }

    /**
     * Creates a StringsCompleter from a command enum's values, excluding EMPTY/UNKNOWN.
     * Used for main commands and sub-command families (deduplicates completer logic).
     */
    static <E extends Enum<E> & CommandNames> StringsCompleter createCommandCompleter(final Class<E> enumClass, final E empty, final E unknown) {
        final List<String> names = new ArrayList<>();
        for (final E cmd : enumClass.getEnumConstants()) {
            if (cmd != empty && cmd != unknown) {
                cmd.allStrings().forEach(names::add);
            }
        }
        return new StringsCompleter(names);
    }

    /**
     * Constructs a new JRomManagerCLI instance with the provided command line arguments.
     *
     * @param cmd The command line arguments.
     * 
     * @throws IOException If an I/O error occurs during initialization.
     */
    public JRomManagerCLI(final CLIArgs cmd) throws IOException {

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

        initCommandHandlers();

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
     * Analyzes the provided command line arguments and executes the corresponding command.
     * 
     * @param args The command line arguments to be analyzed.
     * 
     * @return An integer status code indicating the result of the command execution.
     */
    @SuppressWarnings("java:S112") // generic Exception needed for pluggable CommandHandler dispatch
    protected int analyze(final String... args) {
        if (args.length == 0)
            return 0;
        try {
            CMD cmd = CMD.of(args[0]);
            if (cmd == CMD.EMPTY)
                return 0;
            if (cmd == CMD.UNKNOWN)
                return unknownCmd(args[0], Arrays.copyOfRange(args, 1, args.length));
            CommandHandler cmdHandler = commandHandlers.get(cmd);
            if (cmdHandler == null)
                return unknownCmd(args[0], Arrays.copyOfRange(args, 1, args.length));
            return cmdHandler.execute(args);
        } catch (final IOException e) {
            Log.err(e.getMessage(), e);
        } catch (ScanException | ParameterException e) {
            out.println(e.getMessage()); // NOSONAR
            Log.err(e.getMessage(), e);
        } catch (Exception e) {
            Log.err(e.getMessage(), e);
            return -1;
        }
        return -1;
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
        for (val cmd : commandHandlers.keySet()) {
            final var sb = new AttributedStringBuilder();
            sb.style(STYLE_YELLOW_BOLD).append(cmd.allStrings().collect(Collectors.joining(", "))); // NOSONAR
            sb.style(AttributedStyle.DEFAULT).append(": ").append(CLIMessages.getString("CLI_HELP_" + cmd.name())); // NOSONAR
            out.println(sb.toAnsi());
        }
        return 0;
    }

    int unknownCmd(final String cmd, final String... args) {
        return error(() -> CLIMessages.getString(CLI_ERR_UNKNOWN_COMMAND) + cmd + " "
                + Stream.of(args).map(s -> s.contains(" ") ? ('"' + s + '"') : s).collect(Collectors.joining(" ")));
    }

    int prefs(final Enum<?> name) {
        return prefsCLI.prefs(name);
    }

    int prefs(final Enum<?> name, final String value) {
        return prefsCLI.prefs(name, value);
    }

    private int exit(final int status) {
        System.exit(status);
        return status;
    }

    int error(final String msg) {
        printError(msg);
        return -1;
    }

    int error(final Supplier<String> supplier) {
        printError(supplier.get());
        return -1;
    }

    public static void main(final String[] args) {
        final var jArgs = new CLIArgs();
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
