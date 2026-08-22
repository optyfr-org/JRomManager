package jrm.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles splitting command lines (with quotes and env var substitution).
 */
public class CommandLineParser {

    private final Pattern splitLinePattern = Pattern.compile("\"([^\"]*)\"|(\\S+)"); //$NON-NLS-1$
    private final Pattern envPattern = Pattern.compile("\\$(?:([\\w\\.]+)|\\{([\\w\\.]+)\\})"); //$NON-NLS-1$

    CommandLineParser() {
    }

    String[] splitLine(final String line) {
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

    Optional<String> getEnv(final String name) {
        Optional<String> ret = Optional.ofNullable(System.getProperty(name));
        if (ret.isEmpty())
            ret = Optional.ofNullable(System.getenv(name));
        return ret;
    }
}
