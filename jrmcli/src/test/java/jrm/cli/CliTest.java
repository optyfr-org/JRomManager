package jrm.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CliTest {

    @Nested
    @DisplayName("CMD enum tests")
    class CmdTests {

        @ParameterizedTest
        @CsvSource({
                "cd, CD",
                "pwd, PWD",
                "set, SET",
                "ls, LS",
                "list, LS",
                "dir, LS",
                "rm, RM",
                "del, RM",
                "md, MD",
                "mkdir, MD",
                "quiet, QUIET",
                "verbose, VERBOSE",
                "prefs, PREFS",
                "env, PREFS",
                "load, LOAD",
                "settings, SETTINGS",
                "scan, SCAN",
                "scanresult, SCANRESULT",
                "scanresults, SCANRESULT",
                "fix, FIX",
                "dirupdater, DIRUPD8R",
                "dirupd8r, DIRUPD8R",
                "torrentchecker, TRNTCHK",
                "trntchk, TRNTCHK",
                "compressor, COMPRESSOR",
                "compress, COMPRESSOR",
                "exit, EXIT",
                "quit, EXIT",
                "bye, EXIT",
                "help, HELP",
                "?, HELP",
                "'', EMPTY"
        })
        @DisplayName("of() should return correct enum for valid command names")
        void ofShouldReturnCorrectEnum(String input, CMD expected) {
            assertThat(CMD.of(input)).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = { "CD", "Pwd", "LS", "HELP", "EXIT", "DirUpd8r" })
        @DisplayName("of() should be case insensitive")
        void ofShouldBeCaseInsensitive(String input) {
            assertThat(CMD.of(input)).isNotEqualTo(CMD.UNKNOWN);
        }

        @ParameterizedTest
        @ValueSource(strings = { "unknown", "invalid", "foo", "bar", "notacommand" })
        @DisplayName("of() should return UNKNOWN for invalid commands")
        void ofShouldReturnUnknownForInvalidCommands(String input) {
            assertThat(CMD.of(input)).isEqualTo(CMD.UNKNOWN);
        }

        @Test
        @DisplayName("toString() should return first alias")
        void toStringShouldReturnFirstAlias() {
            assertThat(CMD.CD).hasToString("cd");
            assertThat(CMD.LS).hasToString("ls");
            assertThat(CMD.EXIT).hasToString("exit");
            assertThat(CMD.DIRUPD8R).hasToString("dirupdater");
        }

        @Test
        @DisplayName("allStrings() should return all aliases")
        void allStringsShouldReturnAllAliases() {
            assertThat(CMD.LS.allStrings().toList())
                    .containsExactly("ls", "list", "dir");

            assertThat(CMD.EXIT.allStrings().toList())
                    .containsExactly("exit", "quit", "bye");

            assertThat(CMD.DIRUPD8R.allStrings().toList())
                    .containsExactly("dirupdater", "dirupd8r");
        }

        @Test
        @DisplayName("allStrings() should return single alias for commands with one name")
        void allStringsShouldReturnSingleAlias() {
            assertThat(CMD.CD.allStrings().toList())
                    .containsExactly("cd");

            assertThat(CMD.SCAN.allStrings().toList())
                    .containsExactly("scan");
        }
    }

    @Nested
    @DisplayName("CMD advanced tests")
    class CmdAdvancedTests {

        @ParameterizedTest
        @CsvSource({
                "Cd, CD",
                "PwD, PWD",
                "SeT, SET",
                "Ls, LS",
                "Rm, RM",
                "Md, MD",
                "ScAn, SCAN"
        })
        @DisplayName("of() should handle arbitrary mixed case input")
        void ofShouldHandleArbitraryMixedCase(String input, CMD expected) {
            assertThat(CMD.of(input)).isEqualTo(expected);
        }

        @Test
        @DisplayName("of() with null input should throw NullPointerException")
        void ofWithNullInputShouldThrowNpe() {
            assertThatThrownBy(() -> CMD.of(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = { " cd", "cd ", " cd ", "\tcd", "c d" })
        @DisplayName("of() with whitespace should return UNKNOWN")
        void ofWithWhitespaceShouldReturnUnknown(String input) {
            assertThat(CMD.of(input)).isEqualTo(CMD.UNKNOWN);
        }

        @ParameterizedTest
        @ValueSource(strings = { "!@#$%", "123", "cd!", "scan-Result" })
        @DisplayName("of() with special characters should return UNKNOWN")
        void ofWithSpecialCharactersShouldReturnUnknown(String input) {
            assertThat(CMD.of(input)).isEqualTo(CMD.UNKNOWN);
        }

        @Test
        @DisplayName("toString() should be consistent across multiple calls")
        void toStringShouldBeConsistent() {
            for (CMD cmd : CMD.values()) {
                String first = cmd.toString();
                String second = cmd.toString();
                assertThat(first).isEqualTo(second);
            }
        }

        @Test
        @DisplayName("of(enum.toString()) round-trip should return the same enum for all non-UNKNOWN values")
        void roundTripOfToStringShouldReturnSameEnum() {
            for (CMD cmd : CMD.values()) {
                if (cmd == CMD.UNKNOWN)
                    continue;
                assertThat(CMD.of(cmd.toString()))
                        .as("Round-trip for %s", cmd)
                        .isEqualTo(cmd);
            }
        }

        @Test
        @DisplayName("allStrings() should preserve insertion order of aliases")
        void allStringsShouldPreserveInsertionOrder() {
            assertThat(CMD.LS.allStrings().toList())
                    .containsExactly("ls", "list", "dir");
            assertThat(CMD.EXIT.allStrings().toList())
                    .containsExactly("exit", "quit", "bye");
        }

        @Test
        @DisplayName("allStrings() should return distinct aliases for each enum value")
        void allStringsShouldReturnDistinctAliases() {
            for (CMD cmd : CMD.values()) {
                List<String> aliases = cmd.allStrings().toList();
                assertThat(aliases)
                        .as("Enum %s should have no duplicate aliases", cmd)
                        .doesNotHaveDuplicates();
            }
        }

        @Test
        @DisplayName("of() should be idempotent - same input always returns same result")
        void ofShouldBeIdempotent() {
            for (String input : Arrays.asList("cd", "unknown", "", "SCAN", "xyz")) {
                CMD first = CMD.of(input);
                CMD second = CMD.of(input);
                assertThat(first).isSameAs(second);
            }
        }

        @Test
        @DisplayName("all aliases should be lowercase internally")
        void allAliasesShouldBeLowercase() {
            for (CMD cmd : CMD.values()) {
                cmd.allStrings().forEach(alias -> assertThat(alias)
                        .as("Alias '%s' of %s should be lowercase", alias, cmd)
                        .isEqualTo(alias.toLowerCase()));
            }
        }

        @ParameterizedTest
        @CsvSource({
                "CD, 1",
                "PWD, 1",
                "SET, 1",
                "LS, 3",
                "RM, 2",
                "MD, 2",
                "QUIET, 1",
                "VERBOSE, 1",
                "PREFS, 2",
                "LOAD, 1",
                "SETTINGS, 1",
                "SCAN, 1",
                "SCANRESULT, 2",
                "FIX, 1",
                "DIRUPD8R, 2",
                "TRNTCHK, 2",
                "COMPRESSOR, 2",
                "EXIT, 3",
                "HELP, 2",
                "EMPTY, 1",
                "UNKNOWN, 0"
        })
        @DisplayName("each enum value should have the expected number of aliases")
        void eachEnumShouldHaveExpectedAliasCount(CMD cmd, int expectedCount) {
            assertThat(cmd.allStrings().toList()).hasSize(expectedCount);
        }

        @Test
        @DisplayName("values() should return enums in declaration order")
        void valuesShouldReturnInDeclarationOrder() {
            CMD[] values = CMD.values();
            assertThat(values[0]).isEqualTo(CMD.CD);
            assertThat(values[1]).isEqualTo(CMD.PWD);
            assertThat(values[2]).isEqualTo(CMD.SET);
            assertThat(values[3]).isEqualTo(CMD.LS);
            assertThat(values[4]).isEqualTo(CMD.RM);
            assertThat(values[5]).isEqualTo(CMD.MD);
            assertThat(values[6]).isEqualTo(CMD.QUIET);
            assertThat(values[7]).isEqualTo(CMD.VERBOSE);
            assertThat(values[8]).isEqualTo(CMD.PREFS);
            assertThat(values[9]).isEqualTo(CMD.LOAD);
            assertThat(values[10]).isEqualTo(CMD.SETTINGS);
            assertThat(values[11]).isEqualTo(CMD.SCAN);
            assertThat(values[12]).isEqualTo(CMD.SCANRESULT);
            assertThat(values[13]).isEqualTo(CMD.FIX);
            assertThat(values[14]).isEqualTo(CMD.DIRUPD8R);
            assertThat(values[15]).isEqualTo(CMD.TRNTCHK);
            assertThat(values[16]).isEqualTo(CMD.COMPRESSOR);
            assertThat(values[17]).isEqualTo(CMD.EXIT);
            assertThat(values[18]).isEqualTo(CMD.HELP);
            assertThat(values[19]).isEqualTo(CMD.EMPTY);
            assertThat(values[20]).isEqualTo(CMD.UNKNOWN);
        }

        @Test
        @DisplayName("enum should contain exactly the expected number of values")
        void enumShouldContainExpectedValueCount() {
            assertThat(CMD.values()).hasSize(21);
        }

        @Test
        @DisplayName("valueOf() should work for all declared enum constants")
        void valueOfShouldWorkForAllConstants() {
            assertThat(CMD.valueOf("CD")).isEqualTo(CMD.CD);
            assertThat(CMD.valueOf("PWD")).isEqualTo(CMD.PWD);
            assertThat(CMD.valueOf("SET")).isEqualTo(CMD.SET);
            assertThat(CMD.valueOf("LS")).isEqualTo(CMD.LS);
            assertThat(CMD.valueOf("RM")).isEqualTo(CMD.RM);
            assertThat(CMD.valueOf("MD")).isEqualTo(CMD.MD);
            assertThat(CMD.valueOf("QUIET")).isEqualTo(CMD.QUIET);
            assertThat(CMD.valueOf("VERBOSE")).isEqualTo(CMD.VERBOSE);
            assertThat(CMD.valueOf("PREFS")).isEqualTo(CMD.PREFS);
            assertThat(CMD.valueOf("LOAD")).isEqualTo(CMD.LOAD);
            assertThat(CMD.valueOf("SETTINGS")).isEqualTo(CMD.SETTINGS);
            assertThat(CMD.valueOf("SCAN")).isEqualTo(CMD.SCAN);
            assertThat(CMD.valueOf("SCANRESULT")).isEqualTo(CMD.SCANRESULT);
            assertThat(CMD.valueOf("FIX")).isEqualTo(CMD.FIX);
            assertThat(CMD.valueOf("DIRUPD8R")).isEqualTo(CMD.DIRUPD8R);
            assertThat(CMD.valueOf("TRNTCHK")).isEqualTo(CMD.TRNTCHK);
            assertThat(CMD.valueOf("COMPRESSOR")).isEqualTo(CMD.COMPRESSOR);
            assertThat(CMD.valueOf("EXIT")).isEqualTo(CMD.EXIT);
            assertThat(CMD.valueOf("HELP")).isEqualTo(CMD.HELP);
            assertThat(CMD.valueOf("EMPTY")).isEqualTo(CMD.EMPTY);
            assertThat(CMD.valueOf("UNKNOWN")).isEqualTo(CMD.UNKNOWN);
        }

        @Test
        @DisplayName("valueOf() should throw IllegalArgumentException for invalid name")
        void valueOfShouldThrowForInvalidName() {
            assertThatThrownBy(() -> CMD.valueOf("INVALID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("allStrings() stream should be consumable multiple times via separate calls")
        void allStringsShouldBeConsumableMultipleTimes() {
            List<String> first = CMD.HELP.allStrings().toList();
            List<String> second = CMD.HELP.allStrings().toList();
            assertThat(first).isEqualTo(second);
        }

        @Test
        @DisplayName("HELP should have exactly '?' as its second alias")
        void helpShouldHaveQuestionMarkAsSecondAlias() {
            assertThat(CMD.HELP.allStrings().toList())
                    .hasSize(2)
                    .element(1)
                    .isEqualTo("?");
        }
    }

    @Nested
    @DisplayName("CMD_DIRUPD8R enum tests")
    class CmdDirupd8rTests {

        @ParameterizedTest
        @CsvSource({
                "lssrc, LSSRC",
                "lssdr, LSSDR",
                "clearsrc, CLEARSRC",
                "clearsdr, CLEARSDR",
                "addsrc, ADDSRC",
                "addsdr, ADDSDR",
                "start, START",
                "presets, PRESETS",
                "settings, SETTINGS",
                "help, HELP",
                "?, HELP",
                "'', EMPTY"
        })
        @DisplayName("of() should return correct enum for valid command names")
        void ofShouldReturnCorrectEnum(String input, CMD_DIRUPD8R expected) {
            assertThat(CMD_DIRUPD8R.of(input)).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = { "LSSRC", "Start", "HELP", "Settings" })
        @DisplayName("of() should be case insensitive")
        void ofShouldBeCaseInsensitive(String input) {
            assertThat(CMD_DIRUPD8R.of(input)).isNotEqualTo(CMD_DIRUPD8R.UNKNOWN);
        }

        @ParameterizedTest
        @ValueSource(strings = { "unknown", "invalid", "foo", "notacommand" })
        @DisplayName("of() should return UNKNOWN for invalid commands")
        void ofShouldReturnUnknownForInvalidCommands(String input) {
            assertThat(CMD_DIRUPD8R.of(input)).isEqualTo(CMD_DIRUPD8R.UNKNOWN);
        }

        @Test
        @DisplayName("toString() should return first alias")
        void toStringShouldReturnFirstAlias() {
            assertThat(CMD_DIRUPD8R.LSSRC).hasToString("lssrc");
            assertThat(CMD_DIRUPD8R.START).hasToString("start");
            assertThat(CMD_DIRUPD8R.HELP).hasToString("help");
        }

        @Test
        @DisplayName("allStrings() should return all aliases")
        void allStringsShouldReturnAllAliases() {
            assertThat(CMD_DIRUPD8R.HELP.allStrings().toList())
                    .containsExactly("help", "?");
        }

        @Test
        @DisplayName("allStrings() should return single alias for commands with one name")
        void allStringsShouldReturnSingleAlias() {
            assertThat(CMD_DIRUPD8R.START.allStrings().toList())
                    .containsExactly("start");

            assertThat(CMD_DIRUPD8R.LSSRC.allStrings().toList())
                    .containsExactly("lssrc");
        }

    }

    @Nested
    @DisplayName("CMD_TRNTCHK enum tests")
    class CmdTrntchkTests {

        @ParameterizedTest
        @CsvSource({
                "lssdr, LSSDR",
                "clearsdr, CLEARSDR",
                "addsdr, ADDSDR",
                "start, START",
                "help, HELP",
                "?, HELP",
                "'', EMPTY"
        })
        @DisplayName("of() should return correct enum for valid command names")
        void ofShouldReturnCorrectEnum(String input, CMD_TRNTCHK expected) {
            assertThat(CMD_TRNTCHK.of(input)).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = { "LSSDR", "Start", "HELP", "Addsdr" })
        @DisplayName("of() should be case insensitive")
        void ofShouldBeCaseInsensitive(String input) {
            assertThat(CMD_TRNTCHK.of(input)).isNotEqualTo(CMD_TRNTCHK.UNKNOWN);
        }

        @ParameterizedTest
        @ValueSource(strings = { "unknown", "invalid", "foo", "notacommand" })
        @DisplayName("of() should return UNKNOWN for invalid commands")
        void ofShouldReturnUnknownForInvalidCommands(String input) {
            assertThat(CMD_TRNTCHK.of(input)).isEqualTo(CMD_TRNTCHK.UNKNOWN);
        }

        @Test
        @DisplayName("toString() should return first alias")
        void toStringShouldReturnFirstAlias() {
            assertThat(CMD_TRNTCHK.LSSDR).hasToString("lssdr");
            assertThat(CMD_TRNTCHK.START).hasToString("start");
            assertThat(CMD_TRNTCHK.HELP).hasToString("help");
        }

        @Test
        @DisplayName("allStrings() should return all aliases")
        void allStringsShouldReturnAllAliases() {
            assertThat(CMD_TRNTCHK.HELP.allStrings().toList())
                    .containsExactly("help", "?");
        }

        @Test
        @DisplayName("allStrings() should return single alias for commands with one name")
        void allStringsShouldReturnSingleAlias() {
            assertThat(CMD_TRNTCHK.START.allStrings().toList())
                    .containsExactly("start");

            assertThat(CMD_TRNTCHK.LSSDR.allStrings().toList())
                    .containsExactly("lssdr");
        }

        @Test
        @DisplayName("EMPTY should map from empty string")
        void emptyShouldMapFromEmptyString() {
            assertThat(CMD_TRNTCHK.of("")).isEqualTo(CMD_TRNTCHK.EMPTY);
        }

        @Test
        @DisplayName("UNKNOWN should have no aliases")
        void unknownShouldHaveNoAliases() {
            assertThat(CMD_TRNTCHK.UNKNOWN.allStrings().toList()).isEmpty();
        }

        @Test
        @DisplayName("all enum values except EMPTY and UNKNOWN should have at least one alias")
        void allFunctionalValuesShouldHaveAtLeastOneAlias() {
            for (CMD_TRNTCHK cmd : CMD_TRNTCHK.values()) {
                if (cmd == CMD_TRNTCHK.EMPTY || cmd == CMD_TRNTCHK.UNKNOWN)
                    continue;
                assertThat(cmd.allStrings().toList())
                        .as("Enum %s should have at least one alias", cmd)
                        .isNotEmpty();
            }
        }

        @ParameterizedTest
        @ValueSource(strings = { "lssdr", "clearsdr", "addsdr", "start", "help", "?" })
        @DisplayName("of() should never return UNKNOWN for any defined alias")
        void ofShouldNeverReturnUnknownForDefinedAlias(String alias) {
            assertThat(CMD_TRNTCHK.of(alias)).isNotEqualTo(CMD_TRNTCHK.UNKNOWN);
        }

        @Test
        @DisplayName("enum should contain exactly the expected number of values")
        void enumShouldContainExpectedValueCount() {
            assertThat(CMD_TRNTCHK.values()).hasSize(7);
        }

        @Nested
        @DisplayName("CMD_TRNTCHK advanced tests")
        class CmdTrntchkAdvancedTests {

            @ParameterizedTest
            @CsvSource({
                    "LsSdR, LSSDR",
                    "ClEaRsDr, CLEARSDR",
                    "AdDsDr, ADDSDR",
                    "StArT, START",
                    "HeLp, HELP"
            })
            @DisplayName("of() should handle arbitrary mixed case input")
            void ofShouldHandleArbitraryMixedCase(String input, CMD_TRNTCHK expected) {
                assertThat(CMD_TRNTCHK.of(input)).isEqualTo(expected);
            }

            @Test
            @DisplayName("of() with null input should throw NullPointerException")
            void ofWithNullInputShouldThrowNpe() {
                assertThatThrownBy(() -> CMD_TRNTCHK.of(null))
                        .isInstanceOf(NullPointerException.class);
            }

            @ParameterizedTest
            @ValueSource(strings = { " lssdr", "lssdr ", " lssdr ", "\tlssdr", "ls sdr" })
            @DisplayName("of() with whitespace should return UNKNOWN")
            void ofWithWhitespaceShouldReturnUnknown(String input) {
                assertThat(CMD_TRNTCHK.of(input)).isEqualTo(CMD_TRNTCHK.UNKNOWN);
            }

            @ParameterizedTest
            @ValueSource(strings = { "!@#$%", "123", "ls-sdr", "ls_sdr", "lssdr!" })
            @DisplayName("of() with special characters should return UNKNOWN")
            void ofWithSpecialCharactersShouldReturnUnknown(String input) {
                assertThat(CMD_TRNTCHK.of(input)).isEqualTo(CMD_TRNTCHK.UNKNOWN);
            }

            @Test
            @DisplayName("toString() should be consistent across multiple calls")
            void toStringShouldBeConsistent() {
                for (CMD_TRNTCHK cmd : CMD_TRNTCHK.values()) {
                    String first = cmd.toString();
                    String second = cmd.toString();
                    assertThat(first).isEqualTo(second);
                }
            }

            @Test
            @DisplayName("of(enum.toString()) round-trip should return the same enum for all non-UNKNOWN values")
            void roundTripOfToStringShouldReturnSameEnum() {
                for (CMD_TRNTCHK cmd : CMD_TRNTCHK.values()) {
                    if (cmd == CMD_TRNTCHK.UNKNOWN)
                        continue;
                    assertThat(CMD_TRNTCHK.of(cmd.toString()))
                            .as("Round-trip for %s", cmd)
                            .isEqualTo(cmd);
                }
            }

            @Test
            @DisplayName("allStrings() should preserve insertion order of aliases")
            void allStringsShouldPreserveInsertionOrder() {
                // HELP is defined as HELP("help", "?") - order must be preserved
                List<String> helpAliases = CMD_TRNTCHK.HELP.allStrings().toList();
                assertThat(helpAliases).containsExactly("help", "?");
            }

            @Test
            @DisplayName("allStrings() should return distinct aliases for each enum value")
            void allStringsShouldReturnDistinctAliases() {
                for (CMD_TRNTCHK cmd : CMD_TRNTCHK.values()) {
                    List<String> aliases = cmd.allStrings().toList();
                    assertThat(aliases)
                            .as("Enum %s should have no duplicate aliases", cmd)
                            .doesNotHaveDuplicates();
                }
            }

            @Test
            @DisplayName("no two enum values should share the same alias")
            void noTwoEnumsShouldShareSameAlias() {
                Map<String, Long> aliasCounts = Arrays.stream(CMD_TRNTCHK.values())
                        .flatMap(CMD_TRNTCHK::allStrings)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

                assertThat(aliasCounts.entrySet())
                        .filteredOn(e -> e.getValue() > 1)
                        .as("No alias should be shared by multiple enum values")
                        .isEmpty();
            }

            @Test
            @DisplayName("of() should be idempotent - same input always returns same result")
            void ofShouldBeIdempotent() {
                for (String input : Arrays.asList("lssdr", "unknown", "", "START", "xyz")) {
                    CMD_TRNTCHK first = CMD_TRNTCHK.of(input);
                    CMD_TRNTCHK second = CMD_TRNTCHK.of(input);
                    assertThat(first).isSameAs(second);
                }
            }

            @Test
            @DisplayName("all aliases should be lowercase internally")
            void allAliasesShouldBeLowercase() {
                for (CMD_TRNTCHK cmd : CMD_TRNTCHK.values()) {
                    cmd.allStrings().forEach(alias -> assertThat(alias)
                            .as("Alias '%s' of %s should be lowercase", alias, cmd)
                            .isEqualTo(alias.toLowerCase()));
                }
            }

            @ParameterizedTest
            @CsvSource({
                    "LSSDR, 1",
                    "CLEARSDR, 1",
                    "ADDSDR, 1",
                    "START, 1",
                    "HELP, 2",
                    "EMPTY, 1",
                    "UNKNOWN, 0"
            })
            @DisplayName("each enum value should have the expected number of aliases")
            void eachEnumShouldHaveExpectedAliasCount(CMD_TRNTCHK cmd, int expectedCount) {
                assertThat(cmd.allStrings().toList()).hasSize(expectedCount);
            }

            @Test
            @DisplayName("values() should return enums in declaration order")
            void valuesShouldReturnInDeclarationOrder() {
                CMD_TRNTCHK[] values = CMD_TRNTCHK.values();
                assertThat(values[0]).isEqualTo(CMD_TRNTCHK.LSSDR);
                assertThat(values[1]).isEqualTo(CMD_TRNTCHK.CLEARSDR);
                assertThat(values[2]).isEqualTo(CMD_TRNTCHK.ADDSDR);
                assertThat(values[3]).isEqualTo(CMD_TRNTCHK.START);
                assertThat(values[4]).isEqualTo(CMD_TRNTCHK.HELP);
                assertThat(values[5]).isEqualTo(CMD_TRNTCHK.EMPTY);
                assertThat(values[6]).isEqualTo(CMD_TRNTCHK.UNKNOWN);
            }

            @Test
            @DisplayName("valueOf() should work for all declared enum constants")
            void valueOfShouldWorkForAllConstants() {
                assertThat(CMD_TRNTCHK.valueOf("LSSDR")).isEqualTo(CMD_TRNTCHK.LSSDR);
                assertThat(CMD_TRNTCHK.valueOf("CLEARSDR")).isEqualTo(CMD_TRNTCHK.CLEARSDR);
                assertThat(CMD_TRNTCHK.valueOf("ADDSDR")).isEqualTo(CMD_TRNTCHK.ADDSDR);
                assertThat(CMD_TRNTCHK.valueOf("START")).isEqualTo(CMD_TRNTCHK.START);
                assertThat(CMD_TRNTCHK.valueOf("HELP")).isEqualTo(CMD_TRNTCHK.HELP);
                assertThat(CMD_TRNTCHK.valueOf("EMPTY")).isEqualTo(CMD_TRNTCHK.EMPTY);
                assertThat(CMD_TRNTCHK.valueOf("UNKNOWN")).isEqualTo(CMD_TRNTCHK.UNKNOWN);
            }

            @Test
            @DisplayName("valueOf() should throw IllegalArgumentException for invalid name")
            void valueOfShouldThrowForInvalidName() {
                assertThatThrownBy(() -> CMD_TRNTCHK.valueOf("INVALID"))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            @DisplayName("allStrings() stream should be consumable multiple times via separate calls")
            void allStringsShouldBeConsumableMultipleTimes() {
                List<String> first = CMD_TRNTCHK.HELP.allStrings().toList();
                List<String> second = CMD_TRNTCHK.HELP.allStrings().toList();
                assertThat(first).isEqualTo(second);
            }

            @Test
            @DisplayName("HELP should have exactly '?' as its second alias")
            void helpShouldHaveQuestionMarkAsSecondAlias() {
                assertThat(CMD_TRNTCHK.HELP.allStrings().toList())
                        .hasSize(2)
                        .element(1)
                        .isEqualTo("?");
            }
        }
    }
}
