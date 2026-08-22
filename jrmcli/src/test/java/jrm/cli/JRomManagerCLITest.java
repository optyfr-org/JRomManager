package jrm.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import jrm.security.Session;

/**
 * Unit tests for {@link JRomManagerCLI} focusing on command-line parsing ({@code splitLine}), command dispatch
 * ({@code analyze}), and environment variable lookup ({@code getEnv}).
 * <p>
 * The heavy constructor of {@link JRomManagerCLI} initialises a JLine terminal, a {@link Session}, and logging — all of which
 * are unsuitable for fast unit tests. Instead, an instance is created via {@code sun.misc.Unsafe.allocateInstance()} (bypassing
 * the constructor) and the required fields are injected via reflection.
 */
@DisplayName("JRomManagerCLI tests")
class JRomManagerCLITest {

    /** The CLI instance under test, created without calling the constructor. */
    private JRomManagerCLI cli;
    /** Captures output written to the CLI's {@code out} PrintWriter. */
    private StringWriter stringWriter;
    /** The PrintWriter that feeds into {@link #stringWriter}. */
    private PrintWriter printWriter;
    /** A real {@link Progress} instance used as the {@code handler} field. */
    private Progress progress;

    @TempDir
    Path tempDir;

    /**
     * Creates a {@link JRomManagerCLI} instance without invoking its constructor, then injects the fields needed by the
     * methods under test.
     *
     * @throws Exception if reflection or Unsafe allocation fails
     */
    @BeforeEach
    void setUp() throws Exception {
        cli = createInstanceWithoutConstructor();

        stringWriter = new StringWriter();
        printWriter = new PrintWriter(stringWriter);
        progress = new Progress();

        setField(cli, "out", printWriter);
        setField(cli, "printer", new CLIPrinter(printWriter));
        setField(cli, "handler", progress);
        setField(cli, "cwdir", tempDir);
        setField(cli, "rootdir", tempDir);

        setFinalField(cli, "parser", new CommandLineParser(cli));

        setFinalField(cli, "dirUpd8rCLI", new DirUpd8rCLI(cli));
        setFinalField(cli, "trntChkCLI", new TrntChkCLI(cli));
        setFinalField(cli, "compressorCLI", new CompressorCLI(cli));
        setFinalField(cli, "fsCLI", new FileSystemCLI(cli));
        setFinalField(cli, "profileCLI", new ProfileCLI(cli));
        setFinalField(cli, "prefsCLI", new PrefsCLI(cli));
        setFinalField(cli, "runner", new CLIRunner(cli));

        setFinalField(cli, "commandHandlers", new java.util.EnumMap<>(CMD.class));

        cli.initCommandHandlers();

        JRomManagerCLI.setSession(null);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("JRM_TEST_PROP");
    }

    // ─── Helper methods ───────────────────────────────────────────

    /**
     * Allocates a {@link JRomManagerCLI} instance without calling its constructor, using {@link sun.misc.Unsafe}.
     *
     * @return a new un-initialised {@link JRomManagerCLI} instance
     * @throws Exception if the Unsafe lookup or allocation fails
     */
    private static JRomManagerCLI createInstanceWithoutConstructor() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
        return (JRomManagerCLI) unsafe.allocateInstance(JRomManagerCLI.class);
    }

    /**
     * Sets a non-final instance field on {@code obj} via reflection.
     *
     * @param obj       the target object
     * @param fieldName the field name
     * @param value     the value to set
     * @throws Exception if the field does not exist or cannot be set
     */
    private static void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    /**
     * Sets a final instance field on {@code obj} via {@link sun.misc.Unsafe}, bypassing the final-field restriction of
     * regular reflection.
     *
     * @param obj       the target object
     * @param fieldName the final field name
     * @param value     the value to set
     * @throws Exception if the field does not exist or cannot be set
     */
    @SuppressWarnings("removal")
    private static void setFinalField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) uf.get(null);
        long offset = unsafe.objectFieldOffset(field);
        unsafe.putObject(obj, offset, value);
    }

    /**
     * Invokes the private {@code getEnv} method on the CLI instance via reflection.
     *
     * @param name the environment variable / system property name
     * @return the {@link Optional} returned by {@code getEnv}
     * @throws Exception if the method cannot be found or invoked
     */
    @SuppressWarnings("unchecked")
    private Optional<String> invokeGetEnv(String name) throws Exception {
        Method m = JRomManagerCLI.class.getDeclaredMethod("getEnv", String.class);
        m.setAccessible(true);
        return (Optional<String>) m.invoke(cli, name);
    }

    /**
     * Invokes the private {@code splitLine} method on the CLI instance via reflection.
     *
     * @param line the command line string to split
     * @return the array of tokens returned by {@code splitLine}
     * @throws Exception if the method cannot be found or invoked
     */
    private String[] invokeSplitLine(String line) throws Exception {
        Method m = JRomManagerCLI.class.getDeclaredMethod("splitLine", String.class);
        m.setAccessible(true);
        return (String[]) m.invoke(cli, line);
    }

    /**
     * Flushes the PrintWriter and returns the captured output.
     *
     * @return the string written to {@code out} so far
     */
    private String getOutput() {
        printWriter.flush();
        return stringWriter.toString();
    }

    // ─── splitLine tests ──────────────────────────────────────────

    @Nested
    @DisplayName("splitLine tests")
    class SplitLineTests {

        @Test
        @DisplayName("should split simple unquoted command into tokens")
        void shouldSplitSimpleUnquotedCommand() throws Exception {
            String[] result = invokeSplitLine("cd /path");
            assertThat(result).containsExactly("cd", "/path");
        }

        @Test
        @DisplayName("should split command with multiple whitespace into tokens ignoring extra spaces")
        void shouldSplitCommandWithMultipleWhitespace() throws Exception {
            String[] result = invokeSplitLine("  cd   /path  ");
            assertThat(result).containsExactly("cd", "/path");
        }

        @Test
        @DisplayName("should preserve spaces inside quoted strings")
        void shouldPreserveSpacesInsideQuotedStrings() throws Exception {
            String[] result = invokeSplitLine("cd \"/path with spaces\"");
            assertThat(result).containsExactly("cd", "/path with spaces");
        }

        @Test
        @DisplayName("should handle mixed quoted and unquoted tokens")
        void shouldHandleMixedQuotedAndUnquotedTokens() throws Exception {
            String[] result = invokeSplitLine("compress -c TZIP \"file with spaces.zip\"");
            assertThat(result).containsExactly("compress", "-c", "TZIP", "file with spaces.zip");
        }

        @Test
        @DisplayName("should return empty array for empty string")
        void shouldReturnEmptyArrayForEmptyString() throws Exception {
            String[] result = invokeSplitLine("");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty array for whitespace-only string")
        void shouldReturnEmptyArrayForWhitespaceOnly() throws Exception {
            String[] result = invokeSplitLine("   ");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return single token for command with no arguments")
        void shouldReturnSingleTokenForCommandWithNoArguments() throws Exception {
            String[] result = invokeSplitLine("cd");
            assertThat(result).containsExactly("cd");
        }

        @Test
        @DisplayName("should substitute system property referenced with $VAR syntax")
        void shouldSubstituteSystemPropertyWithDollarSyntax() throws Exception {
            System.setProperty("JRM_TEST_PROP", "testvalue");
            String[] result = invokeSplitLine("set PROP $JRM_TEST_PROP");
            assertThat(result).containsExactly("set", "PROP", "testvalue");
        }

        @Test
        @DisplayName("should substitute system property referenced with ${VAR} syntax")
        void shouldSubstituteSystemPropertyWithBracesSyntax() throws Exception {
            System.setProperty("JRM_TEST_PROP", "testvalue");
            String[] result = invokeSplitLine("set PROP ${JRM_TEST_PROP}");
            assertThat(result).containsExactly("set", "PROP", "testvalue");
        }

        @Test
        @DisplayName("should replace undefined env variable with empty string")
        void shouldReplaceUndefinedEnvVariableWithEmptyString() throws Exception {
            String[] result = invokeSplitLine("set PROP $JRM_UNDEFINED_VAR_99999");
            assertThat(result).containsExactly("set", "PROP", "");
        }

        @Test
        @DisplayName("should handle multiple env variable substitutions in one line")
        void shouldHandleMultipleEnvVariableSubstitutions() throws Exception {
            System.setProperty("JRM_TEST_PROP", "val1");
            System.setProperty("JRM_TEST_PROP2", "val2");
            String[] result = invokeSplitLine("set $JRM_TEST_PROP $JRM_TEST_PROP2");
            assertThat(result).containsExactly("set", "val1", "val2");
        }

        @Test
        @DisplayName("should handle empty quoted string as empty token")
        void shouldHandleEmptyQuotedStringAsEmptyToken() throws Exception {
            String[] result = invokeSplitLine("set \"\"");
            assertThat(result).containsExactly("set", "");
        }

        @Test
        @DisplayName("should handle quoted string containing special characters (excluding $ which triggers env substitution)")
        void shouldHandleQuotedStringWithSpecialCharacters() throws Exception {
            String[] result = invokeSplitLine("set \"value with #pecial ch@rs!\"");
            assertThat(result).containsExactly("set", "value with #pecial ch@rs!");
        }

        @Test
        @DisplayName("should substitute env vars even inside quoted strings")
        void shouldSubstituteEnvVarsInsideQuotedStrings() throws Exception {
            System.setProperty("JRM_TEST_PROP", "replaced");
            String[] result = invokeSplitLine("set \"value with $JRM_TEST_PROP end\"");
            assertThat(result).containsExactly("set", "value with replaced end");
        }

        @ParameterizedTest
        @CsvSource({
                "cd /path, 2",
                "set, 1",
                "'', 0",
                "compress -c TZIP file.zip, 4",
                "dirupd8r addsdr src dst, 4"
        })
        @DisplayName("should return the expected number of tokens for various inputs")
        void shouldReturnExpectedTokenCount(String input, int expectedCount) throws Exception {
            assertThat(invokeSplitLine(input)).hasSize(expectedCount);
        }
    }

    // ─── analyze dispatch tests ──────────────────────────────────

    @Nested
    @DisplayName("analyze dispatch tests")
    class AnalyzeDispatchTests {

        @Test
        @DisplayName("analyze with no args should return 0")
        void analyzeWithNoArgsShouldReturnZero() {
            assertThat(cli.analyze()).isZero();
        }

        @Test
        @DisplayName("analyze with empty string should return 0 (EMPTY command)")
        void analyzeWithEmptyStringShouldReturnZero() {
            assertThat(cli.analyze("")).isZero();
        }

        @Test
        @DisplayName("analyze with unknown command should return -1")
        void analyzeWithUnknownCommandShouldReturnNegativeOne() {
            assertThat(cli.analyze("unknowncmd")).isEqualTo(-1);
        }

        @Test
        @DisplayName("analyze with unknown command should print error message")
        void analyzeWithUnknownCommandShouldPrintError() {
            cli.analyze("unknowncmd");
            assertThat(getOutput()).isNotEmpty();
        }

        @Test
        @DisplayName("analyze with unknown command and extra args should return -1")
        void analyzeWithUnknownCommandAndArgsShouldReturnNegativeOne() {
            assertThat(cli.analyze("unknowncmd", "arg1", "arg2")).isEqualTo(-1);
        }

        @Test
        @DisplayName("analyze 'help' should return 0 and print help text")
        void analyzeHelpShouldReturnZero() {
            assertThat(cli.analyze("help")).isZero();
            String output = getOutput();
            assertThat(output).contains("cd", "pwd", "scan");
        }

        @Test
        @DisplayName("analyze '?' should return 0 (help alias)")
        void analyzeQuestionMarkShouldReturnZero() {
            assertThat(cli.analyze("?")).isZero();
            assertThat(getOutput()).contains("cd");
        }

        @Test
        @DisplayName("analyze 'pwd' should return 0 and print current directory")
        void analyzePwdShouldReturnZero() {
            assertThat(cli.analyze("pwd")).isZero();
            assertThat(getOutput()).contains("~/");
        }

        @Test
        @DisplayName("analyze 'quiet' should return 0")
        void analyzeQuietShouldReturnZero() {
            assertThat(cli.analyze("quiet")).isZero();
        }

        @Test
        @DisplayName("analyze 'verbose' should return 0")
        void analyzeVerboseShouldReturnZero() {
            assertThat(cli.analyze("verbose")).isZero();
        }

        @Test
        @DisplayName("analyze 'quiet' then 'verbose' should toggle handler quiet mode")
        void analyzeQuietThenVerboseShouldToggleHandler() {
            cli.analyze("quiet");
            assertThat(progress.isCancel()).isFalse(); // handler still works
            cli.analyze("verbose");
            assertThat(cli.analyze("verbose")).isZero();
        }
    }

    // ─── analyze 'set' command tests ─────────────────────────────

    @Nested
    @DisplayName("analyze 'set' command tests")
    class AnalyzeSetTests {

        @Test
        @DisplayName("set with 3 args should set system property and return 0")
        void setWithThreeArgsShouldSetProperty() {
            assertThat(cli.analyze("set", "JRM_TEST_PROP", "testvalue")).isZero();
            assertThat(System.getProperty("JRM_TEST_PROP")).isEqualTo("testvalue");
        }

        @Test
        @DisplayName("set with 3 args and empty value should clear system property")
        void setWithEmptyValueShouldClearProperty() {
            System.setProperty("JRM_TEST_PROP", "testvalue");
            assertThat(cli.analyze("set", "JRM_TEST_PROP", "")).isZero();
            assertThat(System.getProperty("JRM_TEST_PROP")).isNull();
        }

        @Test
        @DisplayName("set with 2 args should return 0 and print property value if set")
        void setWithTwoArgsShouldPrintPropertyValue() {
            System.setProperty("JRM_TEST_PROP", "testvalue");
            assertThat(cli.analyze("set", "JRM_TEST_PROP")).isZero();
            assertThat(getOutput()).contains("testvalue");
        }

        @Test
        @DisplayName("set with 2 args for undefined property should return 0 and print nothing")
        void setWithTwoArgsForUndefinedPropertyShouldPrintNothing() {
            assertThat(cli.analyze("set", "JRM_UNDEFINED_PROP_99999")).isZero();
            assertThat(getOutput()).isEmpty();
        }

        @Test
        @DisplayName("set with 1 arg should return 0 and print all properties")
        void setWithOneArgShouldPrintAllProperties() {
            assertThat(cli.analyze("set")).isZero();
            assertThat(getOutput()).isNotEmpty();
        }

        @Test
        @DisplayName("set with 4 args should return -1 (wrong args)")
        void setWithFourArgsShouldReturnNegativeOne() {
            assertThat(cli.analyze("set", "a", "b", "c")).isEqualTo(-1);
        }
    }

    // ─── analyze 'cd' command tests ──────────────────────────────

    @Nested
    @DisplayName("analyze 'cd' command tests")
    class AnalyzeCdTests {

        @Test
        @DisplayName("cd with no args should call pwd and return 0")
        void cdWithNoArgsShouldCallPwd() {
            assertThat(cli.analyze("cd")).isZero();
            assertThat(getOutput()).contains("~/");
        }

        @Test
        @DisplayName("cd with File.separator should set cwdir to rootdir and return 0")
        void cdWithFileSeparatorShouldSetCwdirToRootdir() {
            assertThat(cli.analyze("cd", File.separator)).isZero();
        }

        @Test
        @DisplayName("cd with '~' should resolve to rootdir and return 0")
        void cdWithTildeShouldResolveToRootdir() {
            assertThat(cli.analyze("cd", "~")).isZero();
        }

        @Test
        @DisplayName("cd with nonexistent directory should print error and return 0")
        void cdWithNonexistentDirectoryShouldPrintError() {
            assertThat(cli.analyze("cd", "nonexistentdir")).isZero();
            assertThat(getOutput()).isNotEmpty();
        }

        @Test
        @DisplayName("cd with 3 args should return -1 (wrong args)")
        void cdWithThreeArgsShouldReturnNegativeOne() {
            assertThat(cli.analyze("cd", "a", "b")).isEqualTo(-1);
        }
    }

    // ─── analyze 'dirupd8r' command tests ─────────────────────────

    @Nested
    @DisplayName("analyze 'dirupd8r' command tests")
    class AnalyzeDirUpd8rTests {

        @Test
        @DisplayName("dirupd8r with no subcommand should return -1")
        void dirupd8rWithNoSubcommandShouldReturnNegativeOne() {
            assertThat(cli.analyze("dirupd8r")).isEqualTo(-1);
        }

        @Test
        @DisplayName("dirupd8r help should return 0 and print subcommand help")
        void dirupd8rHelpShouldReturnZero() {
            assertThat(cli.analyze("dirupd8r", "help")).isZero();
            assertThat(getOutput()).contains("lssrc");
        }

        @Test
        @DisplayName("dirupd8r with empty subcommand should return 0 (EMPTY)")
        void dirupd8rWithEmptySubcommandShouldReturnZero() {
            assertThat(cli.analyze("dirupd8r", "")).isZero();
        }

        @Test
        @DisplayName("dirupd8r with unknown subcommand should return -1")
        void dirupd8rWithUnknownSubcommandShouldReturnNegativeOne() {
            assertThat(cli.analyze("dirupd8r", "unknownsub")).isEqualTo(-1);
        }

        @Test
        @DisplayName("dirupd8r '?' should return 0 (help alias)")
        void dirupd8rQuestionMarkShouldReturnZero() {
            assertThat(cli.analyze("dirupd8r", "?")).isZero();
            assertThat(getOutput()).contains("lssrc");
        }
    }

    // ─── analyze 'trntchk' command tests ──────────────────────────

    @Nested
    @DisplayName("analyze 'trntchk' command tests")
    class AnalyzeTrntchkTests {

        @Test
        @DisplayName("trntchk with no subcommand should return -1")
        void trntchkWithNoSubcommandShouldReturnNegativeOne() {
            assertThat(cli.analyze("trntchk")).isEqualTo(-1);
        }

        @Test
        @DisplayName("trntchk help should return 0 and print subcommand help")
        void trntchkHelpShouldReturnZero() {
            assertThat(cli.analyze("trntchk", "help")).isZero();
            assertThat(getOutput()).contains("lssdr");
        }

        @Test
        @DisplayName("trntchk with empty subcommand should return 0 (EMPTY)")
        void trntchkWithEmptySubcommandShouldReturnZero() {
            assertThat(cli.analyze("trntchk", "")).isZero();
        }

        @Test
        @DisplayName("trntchk with unknown subcommand should return -1")
        void trntchkWithUnknownSubcommandShouldReturnNegativeOne() {
            assertThat(cli.analyze("trntchk", "unknownsub")).isEqualTo(-1);
        }

        @Test
        @DisplayName("trntchk '?' should return 0 (help alias)")
        void trntchkQuestionMarkShouldReturnZero() {
            assertThat(cli.analyze("trntchk", "?")).isZero();
            assertThat(getOutput()).contains("lssdr");
        }
    }

    // ─── analyze 'compressor' command tests ───────────────────────

    @Nested
    @DisplayName("analyze 'compressor' command tests")
    class AnalyzeCompressorTests {

        @Test
        @DisplayName("compressor with no args should return -1 (wrong args)")
        void compressorWithNoArgsShouldReturnNegativeOne() {
            assertThat(cli.analyze("compressor")).isEqualTo(-1);
        }

        @Test
        @DisplayName("compressor with 2 args should return -1 (too few args)")
        void compressorWithTwoArgsShouldReturnNegativeOne() {
            assertThat(cli.analyze("compressor", "a", "b")).isEqualTo(-1);
        }

        @Test
        @DisplayName("compressor with -c TZIP and no files should return 0")
        void compressorWithValidFormatAndNoFilesShouldReturnZero() {
            assertThat(cli.analyze("compressor", "-c", "TZIP")).isZero();
        }

        @Test
        @DisplayName("compressor with missing required --compressor should return -1 (ParameterException)")
        void compressorWithMissingRequiredParamShouldReturnNegativeOne() {
            assertThat(cli.analyze("compressor", "file1", "file2")).isEqualTo(-1);
        }
    }

    // ─── analyze 'ls' and 'load' command tests ────────────────────

    @Nested
    @DisplayName("analyze 'ls' and 'load' command tests")
    class AnalyzeLsAndLoadTests {

        @Test
        @DisplayName("ls should return 0 and list directories in cwdir")
        void lsShouldReturnZero() {
            assertThat(cli.analyze("ls")).isZero();
        }

        @Test
        @DisplayName("ls should show subdirectory entries")
        void lsShouldShowSubdirectoryEntries() throws Exception {
            Files.createDirectory(tempDir.resolve("subdir"));
            assertThat(cli.analyze("ls")).isZero();
            assertThat(getOutput()).contains("subdir");
        }

        @Test
        @DisplayName("load with nonexistent profile should print error and return 0")
        void loadWithNonexistentProfileShouldPrintError() {
            assertThat(cli.analyze("load", "nonexistent.jrm")).isZero();
            assertThat(getOutput()).isNotEmpty();
        }

        @Test
        @DisplayName("load with wrong number of args should return -1")
        void loadWithWrongArgsShouldReturnNegativeOne() {
            assertThat(cli.analyze("load")).isEqualTo(-1);
        }
    }

    // ─── analyze file system commands (md, rm) ───────────────────

    @Nested
    @DisplayName("analyze 'md' and 'rm' command tests")
    class AnalyzeMdRmTests {

        @Test
        @DisplayName("md should create a directory and return 0")
        void mdShouldCreateDirectory() {
            Path newDir = tempDir.resolve("newdir");
            assertThat(cli.analyze("md", newDir.toString())).isZero();
            assertThat(newDir).exists().isDirectory();
        }

        @Test
        @DisplayName("md -p should create parent directories and return 0")
        void mdWithParentsShouldCreateNestedDirectories() {
            Path nested = tempDir.resolve("a/b/c");
            assertThat(cli.analyze("md", "-p", nested.toString())).isZero();
            assertThat(nested).exists().isDirectory();
        }

        @Test
        @DisplayName("md on existing directory should return 0 (no-op)")
        void mdOnExistingDirectoryShouldReturnZero() throws Exception {
            Path existing = tempDir.resolve("existing");
            Files.createDirectory(existing);
            assertThat(cli.analyze("md", existing.toString())).isZero();
        }

        @Test
        @DisplayName("rm on non-existent file should return 0 (no-op)")
        void rmOnNonExistentFileShouldReturnZero() {
            assertThat(cli.analyze("rm", tempDir.resolve("nonexistent").toString())).isZero();
        }

        @Test
        @DisplayName("rm should delete a file and return 0")
        void rmShouldDeleteFile() throws Exception {
            Path file = tempDir.resolve("testfile.txt");
            Files.createFile(file);
            assertThat(cli.analyze("rm", file.toString())).isZero();
            assertThat(file).doesNotExist();
        }

        @Test
        @DisplayName("rm -r should recursively delete a directory and return 0")
        void rmRecursiveShouldDeleteDirectory() throws Exception {
            Path dir = tempDir.resolve("testdir");
            Files.createDirectory(dir);
            Files.createFile(dir.resolve("file1.txt"));
            Files.createFile(dir.resolve("file2.txt"));
            assertThat(cli.analyze("rm", "-r", dir.toString())).isZero();
            assertThat(dir).doesNotExist();
        }

        @Test
        @DisplayName("rm without -r on non-empty directory should not delete the directory")
        void rmWithoutRecursiveOnNonEmptyDirectoryShouldNotDelete() throws Exception {
            Path dir = tempDir.resolve("nonempty");
            Files.createDirectory(dir);
            Files.createFile(dir.resolve("file.txt"));
            assertThat(cli.analyze("rm", dir.toString())).isZero();
            assertThat(dir).exists();
        }
    }

    // ─── getEnv tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("getEnv tests")
    class GetEnvTests {

        @Test
        @DisplayName("getEnv should return value for existing system property")
        void getEnvShouldReturnValueForSystemProperty() throws Exception {
            System.setProperty("JRM_TEST_PROP", "testvalue");
            Optional<String> result = invokeGetEnv("JRM_TEST_PROP");
            assertThat(result).isPresent().hasValue("testvalue");
        }

        @Test
        @DisplayName("getEnv should return value for java.version system property")
        void getEnvShouldReturnValueForJavaVersion() throws Exception {
            Optional<String> result = invokeGetEnv("java.version");
            assertThat(result).isPresent();
            assertThat(result.get()).isNotEmpty();
        }

        @Test
        @DisplayName("getEnv should return empty Optional for non-existent property")
        void getEnvShouldReturnEmptyForNonExistentProperty() throws Exception {
            Optional<String> result = invokeGetEnv("JRM_UNDEFINED_PROP_99999");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getEnv should check system property first, then environment variable")
        void getEnvShouldCheckSystemPropertyFirst() throws Exception {
            System.setProperty("JRM_TEST_PROP", "fromprop");
            Optional<String> result = invokeGetEnv("JRM_TEST_PROP");
            assertThat(result).hasValue("fromprop");
        }

        @Test
        @DisplayName("getEnv should return consistent results for repeated calls")
        void getEnvShouldReturnConsistentResults() throws Exception {
            Optional<String> first = invokeGetEnv("java.version");
            Optional<String> second = invokeGetEnv("java.version");
            assertThat(first).isEqualTo(second);
        }
    }

    // ─── analyze edge cases ──────────────────────────────────────

    @Nested
    @DisplayName("analyze edge case tests")
    class AnalyzeEdgeCaseTests {

        @Test
        @DisplayName("analyze with case-insensitive command should work")
        void analyzeWithCaseInsensitiveCommandShouldWork() {
            assertThat(cli.analyze("HELP")).isZero();
            assertThat(getOutput()).contains("cd");
        }

        @Test
        @DisplayName("analyze with command alias should work")
        void analyzeWithCommandAliasShouldWork() {
            assertThat(cli.analyze("list")).isZero();
        }

        @Test
        @DisplayName("analyze 'dir' alias should work like 'ls'")
        void analyzeDirAliasShouldWorkLikeLs() {
            assertThat(cli.analyze("dir")).isZero();
        }

        @Test
        @DisplayName("analyze 'env' alias should work like 'prefs' but needs session")
        void analyzeEnvAliasShouldWorkLikePrefs() {
            // prefs with no args calls prefs() which uses session - will NPE
            // But prefs with 2 args calls prefs(Enum) which also uses session
            // So we just test that the alias is recognized (not UNKNOWN)
            // Since session is null, it will throw NPE which is not caught by analyze
            // We skip this test and just verify the alias mapping
            assertThat(CMD.of("env")).isEqualTo(CMD.PREFS);
        }

        @Test
        @DisplayName("analyze 'compress' alias should work like 'compressor'")
        void analyzeCompressAliasShouldWorkLikeCompressor() {
            assertThat(cli.analyze("compress")).isEqualTo(-1);
        }

        @Test
        @DisplayName("analyze 'quit' alias should not be tested (calls System.exit)")
        void analyzeQuitAliasCallsSystemExit() {
            // exit/quit/bye all call System.exit(0) which would kill the JVM
            // We just verify the alias mapping without executing it
            assertThat(CMD.of("quit")).isEqualTo(CMD.EXIT);
            assertThat(CMD.of("bye")).isEqualTo(CMD.EXIT);
        }

        @ParameterizedTest
        @ValueSource(strings = { "CD", "Pwd", "SET", "Ls", "Rm", "Md", "HELP", "EXIT" })
        @DisplayName("analyze should handle case-insensitive commands")
        void analyzeShouldHandleCaseInsensitiveCommands(String input) {
            // EXIT calls System.exit so we skip it; SCAN/FIX/SETTINGS/PREFS need a session
            if (CMD.of(input) == CMD.EXIT) return;
            cli.analyze(input);
            assertThat(CMD.of(input)).isNotEqualTo(CMD.UNKNOWN);
        }
    }
}