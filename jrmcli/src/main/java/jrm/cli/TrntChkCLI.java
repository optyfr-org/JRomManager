package jrm.cli;

import java.util.EnumSet;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

import jrm.aui.basic.ResultColUpdater;
import jrm.aui.basic.SrcDstResult;
import java.util.stream.Collectors;

import lombok.val;

import jrm.aui.basic.AbstractSrcDstResult;
import jrm.batch.TorrentChecker;
import jrm.io.torrent.options.TrntChkMode;

/**
 * Handles the "trntchk" / "torrentchecker" command family and its subcommands.
 */
public class TrntChkCLI {

    private final JRomManagerCLI cli;

    public TrntChkCLI(final JRomManagerCLI cli) {
        this.cli = cli;
    }

    int trntchk(final String cmd, final String... args) throws ParameterException {
        return switch (CMD_TRNTCHK.of(cmd)) {
            case LSSDR -> trntchkLsSDR();
            case CLEARSDR -> cli.prefs(jrm.misc.SettingsEnum.trntchk_sdr);
            case ADDSDR -> trntchkAddSDR(args);
            case START -> trntchkStart(args);
            case HELP -> trntchkHelp();
            case EMPTY -> 0;
            case UNKNOWN -> cli.unknownCmd(cmd, args);
        };
    }

    private int trntchkLsSDR() {
        cli.out.append("sdr = [\n").append(SrcDstResult.fromJSON(JRomManagerCLI.session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.trntchk_sdr)).stream()
                .map(sdr -> "\t" + sdr.toJSONObject().toString()).collect(Collectors.joining(",\n"))).append("\n];\n"); // //NOSONAR
        return 0;
    }

    private int trntchkAddSDR(final String... args) {
        if (args.length == 2) {
            val list = SrcDstResult.fromJSON(JRomManagerCLI.session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.trntchk_sdr));
            list.add(new SrcDstResult(args[0], args[1]));
            cli.prefs(jrm.misc.SettingsEnum.trntchk_sdr, AbstractSrcDstResult.toJSON(list));
        } else
            return cli.error(CLIMessages.getString(JRomManagerCLI.CLI_ERR_WRONG_ARGS));
        return 0;
    }

    private int trntchkHelp() {
        for (val ducmd : CMD_TRNTCHK.values()) {
            if (ducmd != CMD_TRNTCHK.EMPTY && ducmd != CMD_TRNTCHK.UNKNOWN) {
                cli.out.append(new AttributedString(ducmd.allStrings().collect(Collectors.joining(", ")), JRomManagerCLI.STYLE_YELLOW_BOLD).toAnsi());
                cli.out.append(new AttributedString(": " + CLIMessages.getString("CLI_HELP_TRNTCHK_" + ducmd.name()), AttributedStyle.DEFAULT).toAnsi());
                cli.out.append("\n");
            }
        }
        return 0;
    }

    /**
     * Command line arguments for the "trntchk" command, supporting check mode and various options.
     */
    @Parameters(separators = " =")
    static class TrntchkArgs {
        /**
         * The check mode for the torrent check operation.
         */
        @Parameter(names = { "--checkmode", "-m" }, arity = 1, description = "Check mode")
        String checkmode = null;

        /**
         * Flag to indicate if unknown files should be removed during the torrent check.
         */
        @Parameter(names = { "--removeunknown", "-u" }, description = "Remove unknown files")
        boolean removeunknown = false;

        /**
         * Flag to indicate if wrong sized files should be removed during the torrent check.
         */
        @Parameter(names = { "--removewrongsized", "-w" }, description = "Remove wrong sized files")
        boolean removewrongsized = false;

        /**
         * Flag to indicate if archived folders should be detected during the torrent check.
         */
        @Parameter(names = { "--detectarchives", "-a" }, description = "Detect archived folders")
        boolean detectarchives = false;
    }

    private int trntchkStart(final String... args) throws ParameterException {
        final var sdrl = SrcDstResult.fromJSON(JRomManagerCLI.session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.trntchk_sdr));
        final var results = new String[sdrl.size()];
        final ResultColUpdater resulthandler = new ResultColUpdater() {
            @Override
            public void updateResult(final int row, final String result) {
                results[row] = result;
            }

            @Override
            public void clearResults() {
                for (var i = 0; i < results.length; i++)
                    results[i] = "";
            }
        };
        final var jArgs = new TrntchkArgs();
        JCommander.newBuilder().addObject(jArgs).build().parse(args);
        final TrntChkMode mode = jArgs.checkmode != null ? TrntChkMode.valueOf(jArgs.checkmode) : TrntChkMode.FILESIZE;
        final var opts = EnumSet.noneOf(TorrentChecker.Options.class);
        if (jArgs.removeunknown)
            opts.add(TorrentChecker.Options.REMOVEUNKNOWNFILES);
        if (jArgs.removewrongsized)
            opts.add(TorrentChecker.Options.REMOVEWRONGSIZEDFILES);
        if (jArgs.detectarchives)
            opts.add(TorrentChecker.Options.DETECTARCHIVEDFOLDERS);
        new TorrentChecker<SrcDstResult>(JRomManagerCLI.session, cli.handler, sdrl, mode, resulthandler, opts);
        return 0;
    }

    org.jline.reader.impl.completer.StringsCompleter getSubCompleter() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (final CMD_TRNTCHK cmd : CMD_TRNTCHK.values()) {
            if (cmd != CMD_TRNTCHK.EMPTY && cmd != CMD_TRNTCHK.UNKNOWN) {
                cmd.allStrings().forEach(names::add);
            }
        }
        return new org.jline.reader.impl.completer.StringsCompleter(names);
    }
}
