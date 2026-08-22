package jrm.cli;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FilenameUtils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

import jrm.batch.Compressor;
import jrm.batch.Compressor.FileResult;
import jrm.batch.CompressorFormat;
import jrm.misc.Log;

/**
 * Handles the "compressor" / "compress" command.
 */
public class CompressorCLI {

    private final JRomManagerCLI cli;

    public CompressorCLI(final JRomManagerCLI cli) {
        this.cli = cli;
    }

    /**
     * Command line arguments for the compressor command.
     */
    @Parameters(separators = " =")
    static class CompressorArgs {
        @Parameter(names = { "--compressor", "-c" }, arity = 1, required = true, description = "Compression format")
        String compressor;

        @Parameter(names = { "--force", "-f" }, description = "Force compression")
        boolean force;

        @Parameter(description = "Files")
        List<String> files = new ArrayList<>();
    }

    int compressor(final String... args) throws java.io.IOException, ParameterException {
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
                final var compressor = new Compressor(JRomManagerCLI.session, cnt, frl.size(), cli.handler);
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
}
