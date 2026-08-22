package jrm.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.eclipsesource.json.Json;

import lombok.val;

import jrm.aui.basic.AbstractSrcDstResult;
import jrm.aui.basic.ResultColUpdater;
import jrm.aui.basic.SrcDstResult;
import jrm.batch.DirUpdater;
import jrm.misc.ProfileSettings;
import jrm.profile.Profile;
import jrm.security.PathAbstractor;

/**
 * Handles the "dirupd8r" / "dirupdater" command family and its subcommands.
 */
public class DirUpd8rCLI {

    private final JRomManagerCLI cli;

    public DirUpd8rCLI(final JRomManagerCLI cli) {
        this.cli = cli;
    }

    int dirupd8r(final String cmd, final String... args) throws ParameterException {
        return switch (CMD_DIRUPD8R.of(cmd)) {
            case LSSRC -> listSourceDirectories();
            case LSSDR -> listSourceDestinationResults();
            case CLEARSRC -> clearSourceDirectories();
            case CLEARSDR -> clearSourceDestinationResults();
            case PRESETS -> dirupd8rPresets(args);
            case SETTINGS -> dirupd8rSettings(args);
            case ADDSRC -> addSourceDirectory(args);
            case ADDSDR -> addSourceDestinationResult(args);
            case START -> dirupd8rStart(args);
            case HELP -> dirupd8rHelp();
            case EMPTY -> 0;
            case UNKNOWN -> cli.unknownCmd(cmd, args);
        };
    }

    private int addSourceDestinationResult(final String... args) {
        val list = SrcDstResult.fromJSON(JRomManagerCLI.session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dat2dir_sdr));
        list.add(new SrcDstResult(args[0], args[1]));
        cli.prefs(jrm.misc.SettingsEnum.dat2dir_sdr, AbstractSrcDstResult.toJSON(list));
        return 0;
    }

    private int addSourceDirectory(final String... args) {
        val list = Stream.of(StringUtils.split(JRomManagerCLI.session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dat2dir_srcdirs), '|'))
                .collect(Collectors.toCollection(ArrayList::new));
        list.add(args[0]);
        cli.prefs(jrm.misc.SettingsEnum.dat2dir_srcdirs, list.stream().collect(Collectors.joining("|")));
        return 0;
    }

    private int clearSourceDestinationResults() {
        cli.prefs(jrm.misc.SettingsEnum.dat2dir_sdr, "[]");
        return 0;
    }

    private int clearSourceDirectories() {
        cli.prefs(jrm.misc.SettingsEnum.dat2dir_srcdirs, "");
        return 0;
    }

    private int listSourceDestinationResults() {
        cli.out.append("sdr = [\n").append(SrcDstResult.fromJSON(JRomManagerCLI.session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dat2dir_sdr)).stream()
                .map(sdr -> "\t" + sdr.toJSONObject().toString()).collect(Collectors.joining(",\n"))).append("\n];\n"); // NOSONAR
        return 0;
    }

    private int listSourceDirectories() {
        cli.out.append("srcdirs = [\n").append(Stream.of(StringUtils.split(JRomManagerCLI.session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dat2dir_srcdirs), '|'))
                .map(s -> "\t" + Json.value(s).toString()).collect(Collectors.joining(",\n"))).append("\n];\n"); // //NOSONAR
        return 0;
    }

    private int dirupd8rHelp() {
        for (val ducmd : CMD_DIRUPD8R.values()) {
            if (ducmd != CMD_DIRUPD8R.EMPTY && ducmd != CMD_DIRUPD8R.UNKNOWN) {
                cli.out.append(new AttributedString(ducmd.allStrings().collect(Collectors.joining(", ")), JRomManagerCLI.STYLE_YELLOW_BOLD).toAnsi()); //$NON-NLS-1$
                cli.out.append(new AttributedString(": " + CLIMessages.getString("CLI_HELP_DIRUPD8R_" + ducmd.name()), AttributedStyle.DEFAULT).toAnsi()); //$NON-NLS-1$ //$NON-NLS-2$
                cli.out.append("\n"); //$NON-NLS-1$
            }
        }
        return 0;
    }

    /**
     * Command line arguments for the "dirupd8r" command, supporting dry run mode.
     */
    @Parameters(separators = " =")
    static class DirUpdaterArgs {
        /**
         * Flag to indicate if the directory update should be performed in dry run mode.
         */
        @Parameter(names = { "--dryrun", "-d" }, description = "Dry run")
        boolean dryrun;
    }

    private int dirupd8rStart(final String... args) throws ParameterException {
        final var sdrl = SrcDstResult.fromJSON(JRomManagerCLI.session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dat2dir_sdr));
        final List<File> srcdirs = Stream.of(StringUtils.split(JRomManagerCLI.session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dat2dir_srcdirs), '|')).map(File::new)
                .collect(Collectors.toCollection(ArrayList::new));
        final var results = new String[sdrl.size()];
        final var resulthandler = new ResultColUpdater() {
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
        final var jArgs = new DirUpdaterArgs();
        JCommander.newBuilder().addObject(jArgs).build().parse(args);
        new DirUpdater(JRomManagerCLI.session, sdrl, cli.handler, srcdirs, resulthandler, jArgs.dryrun);
        for (var i = 0; i < results.length; i++)
            cli.printKeyValue(String.valueOf(i), results[i]);
        return 0;
    }

    private int dirupd8rSettings(final String... args) throws NumberFormatException, SecurityException {
        if (args.length <= 0)
            return cli.error(CLIMessages.getString(JRomManagerCLI.CLI_ERR_WRONG_ARGS));

        val list = SrcDstResult.fromJSON(JRomManagerCLI.session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dat2dir_sdr));
        final var index = Integer.parseInt(args[0]);
        if (index < list.size()) {
            final ProfileSettings settings = JRomManagerCLI.session.getUser().getSettings().loadProfileSettings(PathAbstractor.getAbsolutePath(JRomManagerCLI.session, list.get(index).getSrc()).toFile(), null);
            if (args.length == 3) {
                settings.setProperty(jrm.misc.SettingsEnum.from(args[1]), args[2]);
                JRomManagerCLI.session.getUser().getSettings().saveProfileSettings(PathAbstractor.getAbsolutePath(JRomManagerCLI.session, list.get(index).getSrc()).toFile(), settings);
            } else if (args.length == 2)
                cli.out.format("%s%n", settings.getProperty(jrm.misc.SettingsEnum.from(args[1])));
            else
                for (final Map.Entry<Object, Object> entry : settings.getProperties().entrySet())
                    cli.printKeyValue(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return 0;
    }

    private int dirupd8rPresets(final String... args) throws NumberFormatException, SecurityException {
        return switch (args.length) {
            case 0 -> {
                cli.printInfo("TZIP");
                cli.printInfo("DIR");
                yield 0;
            }
            case 2 -> {
                val list = SrcDstResult.fromJSON(JRomManagerCLI.session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dat2dir_sdr));
                val index = Integer.parseInt(args[0]);
                if (index >= list.size())
                    yield cli.error(CLIMessages.getString(JRomManagerCLI.CLI_ERR_WRONG_ARGS));
                switch (args[1]) {
                    case "TZIP" -> ProfileSettings.TZIP(JRomManagerCLI.session, PathAbstractor.getAbsolutePath(JRomManagerCLI.session, list.get(index).getSrc()).toFile());
                    case "DIR" -> ProfileSettings.DIR(JRomManagerCLI.session, PathAbstractor.getAbsolutePath(JRomManagerCLI.session, list.get(index).getSrc()).toFile());
                    default -> {
                        /* unknown preset — no-op */ }
                }
                yield 0;
            }
            default -> cli.error(CLIMessages.getString(JRomManagerCLI.CLI_ERR_WRONG_ARGS));
        };
    }

    org.jline.reader.impl.completer.StringsCompleter getSubCompleter() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (final CMD_DIRUPD8R cmd : CMD_DIRUPD8R.values()) {
            if (cmd != CMD_DIRUPD8R.EMPTY && cmd != CMD_DIRUPD8R.UNKNOWN) {
                cmd.allStrings().forEach(names::add);
            }
        }
        return new org.jline.reader.impl.completer.StringsCompleter(names);
    }
}
