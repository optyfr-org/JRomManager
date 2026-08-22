package jrm.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import jrm.misc.BreakException;
import jrm.misc.EnumWithDefault;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.Profile;
import jrm.profile.fix.Fix;
import jrm.profile.scan.Scan;
import jrm.profile.scan.ScanException;

/**
 * Handles profile-related commands: load, settings (profile), scan, scanresult, fix.
 */
public class ProfileCLI {

    private final JRomManagerCLI cli;

    public ProfileCLI(final JRomManagerCLI cli) {
        this.cli = cli;
    }

    // load

    int load(final String... args) {
        if (args.length == 2)
            return load(args[1]);
        return cli.error(CLIMessages.getString(JRomManagerCLI.CLI_ERR_WRONG_ARGS));
    }

    int load(final String profile) {
        final Path candidate = cli.cwdir.resolve(profile);
        if (Files.isRegularFile(candidate))
            JRomManagerCLI.session.setCurrProfile(Profile.load(JRomManagerCLI.session, candidate.toFile(), cli.handler));
        else
            cli.printError(String.format(CLIMessages.getString("CLI_ERR_ProfileNotExist"), profile));
        return 0;
    }

    // settings (profile settings)

    int settings(final String... args) {
        if (JRomManagerCLI.session.getCurrProfile() == null)
            return cli.error(CLIMessages.getString("CLI_ERR_NoProfileLoaded"));
        if (args.length == 1)
            return settings();
        if (args.length == 2)
            return settings(jrm.misc.SettingsEnum.from(args[1]));
        if (args.length == 3)
            return settings(jrm.misc.SettingsEnum.from(args[1]), args[2]);
        return cli.error(CLIMessages.getString(JRomManagerCLI.CLI_ERR_WRONG_ARGS));
    }

    private int settings() {
        for (final var e : ProfileSettingsEnum.values())
            cli.printKeyValue(e.toString(), JRomManagerCLI.session.getCurrProfile().getSettings().getProperty(e));
        return 0;
    }

    int settings(final Enum<?> name) {
        if (!JRomManagerCLI.session.getCurrProfile().getSettings().hasProperty(name))
            cli.printWarning(String.format(CLIMessages.getString("CLI_MSG_PropIsNotSet"), name));
        else if (name instanceof final EnumWithDefault n)
            cli.printKeyValue(name.toString(), JRomManagerCLI.session.getCurrProfile().getSettings().getProperty(n));
        return 0;
    }

    int settings(final Enum<?> name, final String value) {
        JRomManagerCLI.session.getCurrProfile().getSettings().setProperty(name, value);
        JRomManagerCLI.session.getCurrProfile().saveSettings();
        return 0;
    }

    // scan / scanresult / fix

    int scan() throws ScanException, BreakException {
        if (JRomManagerCLI.session.getCurrProfile() == null)
            return cli.error(CLIMessages.getString("CLI_ERR_NoProfileLoaded"));
        JRomManagerCLI.session.setCurrScan(new Scan(JRomManagerCLI.session.getCurrProfile(), cli.handler));
        return JRomManagerCLI.session.getCurrScan().actions.stream().mapToInt(Collection::size).sum();
    }

    int scanResult() {
        if (JRomManagerCLI.session.getCurrScan() == null)
            return cli.error(CLIMessages.getString("CLI_ERR_ShouldScanFirst"));
        if (JRomManagerCLI.session.getCurrProfile().hasPropsChanged())
            return cli.error(CLIMessages.getString("CLI_ERR_PropsChanged"));
        if (JRomManagerCLI.session.getReport() == null)
            return cli.error(CLIMessages.getString("CLI_ERR_NoReport"));
        cli.printInfo(JRomManagerCLI.session.getReport().getStats().getStatus());
        return 0;
    }

    int fix() {
        if (JRomManagerCLI.session.getCurrScan() == null)
            return cli.error(CLIMessages.getString("CLI_ERR_ShouldScanFirst"));
        if (JRomManagerCLI.session.getCurrProfile().hasPropsChanged())
            return cli.error(CLIMessages.getString("CLI_ERR_PropsChanged"));
        if (JRomManagerCLI.session.getCurrScan().actions.stream().mapToInt(Collection::size).sum() == 0)
            return cli.error(CLIMessages.getString("CLI_ERR_NothingToFix"));
        final var fix = new Fix(JRomManagerCLI.session.getCurrProfile(), JRomManagerCLI.session.getCurrScan(), cli.handler);
        cli.printInfo(String.format(CLIMessages.getString("CLI_MSG_ActionRemaining"), fix.getActionsRemain()));
        return fix.getActionsRemain();
    }
}
