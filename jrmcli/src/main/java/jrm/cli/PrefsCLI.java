package jrm.cli;

import jrm.misc.EnumWithDefault;
import jrm.misc.SettingsEnum;

/**
 * Handles the global "prefs" / "env" command for user settings.
 */
public class PrefsCLI {

    private final JRomManagerCLI cli;

    public PrefsCLI(final JRomManagerCLI cli) {
        this.cli = cli;
    }

    int prefs(final String... args) {
        if (args.length == 1)
            return prefs();
        if (args.length == 2)
            return prefs(SettingsEnum.from(args[1]));
        if (args.length == 3)
            return prefs(SettingsEnum.from(args[1]), args[2]);
        return cli.error(CLIMessages.getString(JRomManagerCLI.CLI_ERR_WRONG_ARGS));
    }

    private int prefs() {
        for (final var e : SettingsEnum.values())
            cli.printKeyValue(e.toString(), JRomManagerCLI.session.getUser().getSettings().getProperty(e));
        return 0;
    }

    int prefs(final Enum<?> name) {
        if (!JRomManagerCLI.session.getUser().getSettings().hasProperty(name))
            cli.printWarning(String.format(CLIMessages.getString("CLI_MSG_PropIsNotSet"), name));
        else if (name instanceof final EnumWithDefault n)
            cli.printKeyValue(name.toString(), JRomManagerCLI.session.getUser().getSettings().getProperty(n));
        return 0;
    }

    int prefs(final Enum<?> name, final String value) {
        JRomManagerCLI.session.getUser().getSettings().setProperty(name, value);
        JRomManagerCLI.session.getUser().getSettings().saveSettings();
        return 0;
    }
}
