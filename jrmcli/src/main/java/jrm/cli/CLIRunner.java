package jrm.cli;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import jrm.misc.Log;

import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Handles interactive and stream (non-interactive) execution modes for the CLI.
 */
public class CLIRunner {

    private final JRomManagerCLI cli;

    public CLIRunner(JRomManagerCLI cli) {
        this.cli = cli;
    }

    void stream(final CLIArgs cmd) throws IOException {
        /* Plain output without terminal handling: no JLine terminal is created at all, so no
         * "Unable to create a system terminal" warning is emitted when stdin is piped. */
        cli.out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        cli.printer = new CLIPrinter(cli.out);
        /* Start processing commands from the input file or standard input */
        final Reader reader = cmd.file != null ? new FileReader(cmd.file, StandardCharsets.UTF_8) : new InputStreamReader(System.in, StandardCharsets.UTF_8);
        try (final var in = new BufferedReader(reader)) {
            String line;
            while (null != (line = in.readLine())) {
                if (line.startsWith("#")) //$NON-NLS-1$
                    continue;
                cli.analyze(cli.parser.splitLine(line));
            }
        } catch (final IOException e) {
            Log.err(e.getMessage());
        }
    }

    void interactive(CLIArgs cmd) throws IOException {
        preferBundledCapabilities();
        cli.terminal = TerminalBuilder.builder().system(true).build();
        final LineReader reader = LineReaderBuilder.builder()
                .terminal(cli.terminal)
                .completer(createCompleter())
                .option(LineReader.Option.AUTO_FRESH_LINE, true)
                .build();
        cli.out = cli.terminal.writer();
        cli.printer = new CLIPrinter(cli.out);
        bindBackspace(reader);
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
                    cli.analyze(cli.parser.splitLine(line));
            } catch(Exception e) {
                cli.out.println(e.getMessage());
                if(cmd.debug)
                    Log.err(e.getMessage(), e);
            }
        } while (true);
    }

    /**
     * Forces both DEL ({@code \177}) and Ctrl+H ({@code \010}) to delete the previous character in
     * the emacs and vi-insertion keymaps. Some terminals (notably git-bash/MSYS2, see
     * jline/jline3#1445) report a backspace byte that matches neither the terminfo
     * {@code key_backspace} capability nor the pty erase character JLine binds by default, so the key
     * would otherwise self-insert instead of deleting.
     *
     * @param reader the line reader whose keymaps to patch
     */
    private static void bindBackspace(final LineReader reader) {
        final var delete = new Reference(LineReader.BACKWARD_DELETE_CHAR);
        for (final var keyMapName : new String[] { LineReader.EMACS, LineReader.VIINS }) {
            final KeyMap<Binding> keyMap = reader.getKeyMaps().get(keyMapName);
            if (keyMap != null) {
                keyMap.bind(delete, KeyMap.del(), KeyMap.ctrl('H'));
            }
        }
    }

    /**
     * Pre-seeds JLine's terminfo cache with its bundled capabilities for the current terminal type
     * when running under git-bash/MSYS2, and reports whether the override was applied.
     * <p>
     * Root cause of the git-bash backspace bug (upstream jline/jline3#1445): JLine resolves key
     * capabilities by shelling out to {@code infocmp}. The MSYS ncurses database reports
     * {@code kbs=^?}, which JLine's {@code Curses.doTputs} mis-decodes as U+FFFF instead of DEL
     * ({@code '^' - '@' == -1}, missing the standard {@code ^?}-means-DEL special case). Backspace
     * then arrives as U+FFFF, matches no binding and self-inserts (the ￿ tofu). Where no
     * {@code infocmp} exists (cmd.exe) JLine falls back to its bundled caps ({@code kbs=^H},
     * decoded correctly), which is why only git-bash is affected. Seeding the bundled caps first
     * makes JLine skip the external call entirely.
     *
     * @return the terminal type that was overridden, or empty if no override was applied
     */
    static Optional<String> preferBundledCapabilities() {
        if (System.getenv("MSYSTEM") == null)
            return Optional.empty();
        final var term = System.getenv("TERM");
        final var candidates = term != null ? new String[] { term, "xterm-256color", "xterm" } : new String[] { "xterm-256color", "xterm" };
        for (final var name : candidates) {
            if (seedBundledCapabilities(name))
                return Optional.of(name);
        }
        return Optional.empty();
    }

    /**
     * Loads JLine's bundled terminfo capabilities for the given terminal type into its cache so
     * subsequent terminal creation skips the external {@code infocmp} call.
     *
     * @param name the terminal type (without {@code .caps} suffix)
     * @return {@code true} if bundled capabilities were found and cached
     */
    static boolean seedBundledCapabilities(final String name) {
        try (final var in = org.jline.utils.InfoCmp.class.getResourceAsStream(name + ".caps")) {
            if (in == null)
                return false;
            final var caps = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            org.jline.utils.InfoCmp.setLoadedInfoCmp(name, caps);
            return true;
        } catch (final IOException e) {
            Log.debug(() -> "Cannot load bundled terminfo caps for " + name + ": " + e);
            return false;
        }
    }

    private Completer createCompleter() {
        final StringsCompleter cmdCompleter = JRomManagerCLI.createCommandCompleter(CMD.class, CMD.EMPTY, CMD.UNKNOWN);

        // Collect DIRUPD8R subcommand aliases from handler
        final StringsCompleter dirupd8rCompleter = cli.dirUpd8rCLI.getSubCompleter();

        // Collect TRNTCHK subcommand aliases from handler
        final StringsCompleter trntchkCompleter = cli.trntChkCLI.getSubCompleter();

        // Build completers: main commands, dirupd8r subcommands, trntchk subcommands
        return new AggregateCompleter(
                new ArgumentCompleter(new StringsCompleter("dirupd8r", "dirupdater"), dirupd8rCompleter, NullCompleter.INSTANCE), //$NON-NLS-1$ //$NON-NLS-2$
                new ArgumentCompleter(new StringsCompleter("trntchk", "torrentchecker"), trntchkCompleter, NullCompleter.INSTANCE), //$NON-NLS-1$ //$NON-NLS-2$
                new ArgumentCompleter(cmdCompleter, NullCompleter.INSTANCE));
    }

    private String buildPrompt() {
        final AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN).bold()).append("jrm");
        if (JRomManagerCLI.session.getCurrProfile() != null) {
            sb.style(AttributedStyle.DEFAULT).append(" [")
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold())
                    .append(JRomManagerCLI.session.getCurrProfile().getNfo().getFile().getName())
                    .style(AttributedStyle.DEFAULT).append("]");
        }
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN)).append("> ");
        return sb.toAnsi();
    }
}
