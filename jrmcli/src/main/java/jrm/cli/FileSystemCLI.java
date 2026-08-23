package jrm.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

import jrm.profile.manager.ProfileNFO;

/**
 * Handles basic filesystem / shell navigation and file commands:
 * cd, pwd, ls, rm, md, set.
 */
public class FileSystemCLI {

    private final JRomManagerCLI cli;

    public FileSystemCLI(final JRomManagerCLI cli) {
        this.cli = cli;
    }

    // --- cd / pwd / list ---

    int cd(final String... args) {
        if (args.length == 1)
            return pwd();
        if (args.length == 2)
            return cd(args[1]);
        return cli.error(CLIMessages.getString(JRomManagerCLI.CLI_ERR_WRONG_ARGS));
    }

    int cd(final String dir) {
        if (dir.equals(File.separator)) {
            cli.cwdir = cli.rootdir;
        } else {
            final var resolvedDir = dir.startsWith("~") ? dir.replace("~", cli.rootdir.toString()) : dir;
            final var candidate = cli.cwdir.resolve(resolvedDir).normalize();
            if (cli.rootdir.startsWith(candidate) && !cli.rootdir.equals(candidate)) {
                cli.cwdir = cli.rootdir;
                cli.printError(String.format(CLIMessages.getString("CLI_ERR_CantGoUpDir"), resolvedDir));
            } else if (Files.isDirectory(candidate)) {
                if (candidate.startsWith(cli.rootdir)) {
                    cli.cwdir = candidate;
                } else {
                    cli.printError(String.format(CLIMessages.getString("CLI_ERR_CantChangeDir"), resolvedDir));
                }
            } else {
                cli.printError(String.format(CLIMessages.getString("CLI_ERR_UnknownDir"), resolvedDir));
            }
        }
        return 0;
    }

    int pwd() {
        cli.printInfo("~/" + cli.rootdir.relativize(cli.cwdir));
        return 0;
    }

    int list() throws IOException {
        try (final var stream = Files.walk(cli.cwdir, 1)) {
            stream.filter(p -> Files.isDirectory(p) && !p.equals(cli.cwdir)).sorted(Path::compareTo).map(cli.cwdir::relativize)
                    .forEachOrdered(p -> {
                        final AttributedStringBuilder sb = new AttributedStringBuilder();
                        sb.style(JRomManagerCLI.STYLE_GREEN_BOLD).append("<DIR>").append("\t");
                        sb.style(AttributedStyle.DEFAULT).append(p.toString());
                        cli.out.println(sb.toAnsi());
                    });
        }
        for (final var row : ProfileNFO.list(JRomManagerCLI.session, cli.cwdir.toFile())) {
            final AttributedStringBuilder sb = new AttributedStringBuilder();
            sb.style(JRomManagerCLI.STYLE_CYAN_BOLD).append("<DAT>").append("\t");
            sb.style(AttributedStyle.DEFAULT).append(row.getName());
            cli.out.println(sb.toAnsi());
        }
        return 0;
    }

    // --- rm ---

    /**
     * Command line arguments for the "rm" command.
     */
    @Parameters(separators = " =")
    static class RmArgs {
        @Parameter(names = { "--recursive", "-r" }, description = "Recursive delete")
        boolean recurisve;

        @Parameter(description = "Files")
        List<String> files = new ArrayList<>();
    }

    int rm(final String... args) throws ParameterException, IOException {
        final var jArgs = new RmArgs();
        JCommander.newBuilder().addObject(jArgs).build().parse(Arrays.copyOfRange(args, 1, args.length));
        for (final String arg : jArgs.files)
            recursiveDelete(Paths.get(arg), jArgs.recurisve);
        return 0;
    }

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
            if (recurse)
                try (final var stream = Files.walk(path)) {
                    stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
        }
    }

    // --- md ---

    /**
     * Command line arguments for the "md" command.
     */
    @Parameters(separators = " =")
    static class MdArgs {
        @Parameter(names = { "--parents", "-p" }, description = "Create parents up to this directory")
        boolean parents;

        @Parameter(description = "Files")
        List<String> files = new ArrayList<>();
    }

    int md(final String... args) throws ParameterException, IOException {
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

    // --- set (env / system props) ---

    int set(final String... args) {
        if (args.length == 1) {
            System.getProperties().forEach((k, v) -> cli.printKeyValue(String.valueOf(k), String.valueOf(v)));
            System.getenv().forEach(cli::printKeyValue);
            return 0;
        }
        if (args.length == 2) {
            cli.getEnv(args[1]).ifPresent(cli.out::println);
            return 0;
        }
        if (args.length == 3) {
            if (args[2].isEmpty())
                System.clearProperty(args[1]);
            else
                System.setProperty(args[1], args[2]);
            return 0;
        }
        return cli.error(CLIMessages.getString(JRomManagerCLI.CLI_ERR_WRONG_ARGS));
    }
}
