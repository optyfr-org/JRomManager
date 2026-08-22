package jrm.cli;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

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

    void stream(final JRomManagerCLI.Args cmd) throws IOException {
        /* Start terminal that support non-interactive mode */
        cli.terminal = TerminalBuilder.builder().dumb(true).build();
        /* Create a PrintWriter for outputting messages to the terminal */
        cli.out = cli.terminal.writer();
        cli.printer = new CLIPrinter(cli.out);
        /* Start processing commands from the input file or standard input */
        final Reader reader = cmd.file != null ? new FileReader(cmd.file) : new InputStreamReader(System.in);
        try (final var in = new BufferedReader(reader);) {
            String line;
            while (null != (line = in.readLine())) {
                if (line.startsWith("#")) //$NON-NLS-1$
                    continue;
                    cli.analyze(cli.parser.splitLine(line));
            }
        } catch (final IOException e) {
            jrm.misc.Log.err(e.getMessage());
        }
    }

    void interactive(JRomManagerCLI.Args cmd) throws IOException {
        cli.terminal = TerminalBuilder.builder().system(true).build();
        final LineReader reader = LineReaderBuilder.builder()
                .terminal(cli.terminal)
                .completer(createCompleter())
                .option(LineReader.Option.AUTO_FRESH_LINE, true)
                .build();
        cli.out = cli.terminal.writer();
        cli.printer = new CLIPrinter(cli.out);
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
                    jrm.misc.Log.err(e.getMessage(), e);
            }
        } while (true);
    }

    private Completer createCompleter() {
        // Collect all command aliases from CMD enum
        final List<String> commandNames = new ArrayList<>();
        for (final CMD cmd : CMD.values()) {
            if (cmd != CMD.EMPTY && cmd != CMD.UNKNOWN) {
                cmd.allStrings().forEach(commandNames::add);
            }
        }
        final StringsCompleter cmdCompleter = new StringsCompleter(commandNames);

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
