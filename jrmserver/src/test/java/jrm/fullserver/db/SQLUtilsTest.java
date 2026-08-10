package jrm.fullserver.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for the default utility methods of {@link SQLUtils}.
 *
 * <p>
 * A minimal stub implementing the abstract methods ({@code getDb}, {@code getContext}, {@code count}, {@code countTbl},
 * {@code getLongValue}, {@code getIntValue}, {@code getColumnList}, {@code query}, {@code queryFirst}, {@code update},
 * {@code insert}, {@code hasResult}) is used so that all default string-building methods can be exercised in isolation.
 * </p>
 */
@DisplayName("SQLUtils default methods")
class SQLUtilsTest {

    /** Minimal stub providing a mock connection and fixed context. */
    private static final class StubSQLUtils implements SQLUtils {
        private final Connection connection;
        private final String context;

        StubSQLUtils(final Connection connection, final String context) {
            this.connection = connection;
            this.context = context;
        }

        @Override
        public Connection getDb() {
            return connection;
        }

        @Override
        public String getContext() {
            return context;
        }

        @Override
        public Long count(final String select, final Object... args) {
            return 0L;
        }

        @Override
        public Long countTbl(final String table, final String context) {
            return 0L;
        }

        @Override
        public Long getLongValue(final String select, final Object... args) {
            return 0L;
        }

        @Override
        public int update(final String update, final Object... args) {
            return 0;
        }

        @Override
        public void dropTable(final String table) {
            // no-op
        }

        @Override
        public List<Map<String, Object>> query(final String select, final Object... args) {
            return List.of();
        }

        @Override
        public Map<String, Object> queryFirst(final String select, final Object... args) {
            return Map.of();
        }

        @Override
        public <T> List<T> getColumnList(final String select, final int col) {
            return List.of();
        }
    }

    private SQLUtils utils;

    @BeforeEach
    void setUp() throws SQLException {
        final Connection connection = mock(Connection.class);
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.supportsSchemasInDataManipulation()).thenReturn(true);
        utils = new StubSQLUtils(connection, "ctx");
    }

    @Nested
    @DisplayName("backquote")
    class BackquoteTest {
        @Test
        @DisplayName("wraps name in backticks")
        void wrapsName() {
            assertThat(utils.backquote("col")).isEqualTo("`col`");
        }

        @Test
        @DisplayName("returns null for null input")
        void returnsNull() {
            assertThat(utils.backquote(null)).isNull();
        }

        @Test
        @DisplayName("rejects injection characters")
        void rejectsInjectionChars() {
            assertThatThrownBy(() -> utils.backquote("a`b")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> utils.backquote("t; DROP TABLE x")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> utils.backquote("1bad")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects blank identifier")
        void rejectsBlank() {
            assertThatThrownBy(() -> utils.backquote("  ")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("requireSqlIdentifier")
    class RequireSqlIdentifierTest {
        @Test
        @DisplayName("accepts alphanumerics and underscore")
        void acceptsSafe() {
            assertThat(utils.requireSqlIdentifier("USERS")).isEqualTo("USERS");
            assertThat(utils.requireSqlIdentifier("count_table")).isEqualTo("count_table");
        }

        @Test
        @DisplayName("returns null for null")
        void nullOk() {
            assertThat(utils.requireSqlIdentifier(null)).isNull();
        }
    }

    @Nested
    @DisplayName("requireSelectStatement")
    class RequireSelectStatementTest {
        @Test
        @DisplayName("accepts SELECT statements")
        void acceptsSelect() {
            assertThat(utils.requireSelectStatement("SELECT * FROM USERS")).isEqualTo("SELECT * FROM USERS");
        }

        @Test
        @DisplayName("accepts parenthesized SELECT")
        void acceptsParenthesized() {
            assertThat(utils.requireSelectStatement("(SELECT 1)")).isEqualTo("(SELECT 1)");
        }

        @Test
        @DisplayName("rejects multi-statement input")
        void rejectsSemicolon() {
            assertThatThrownBy(() -> utils.requireSelectStatement("SELECT 1; DROP TABLE USERS"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects non-SELECT")
        void rejectsNonSelect() {
            assertThatThrownBy(() -> utils.requireSelectStatement("DELETE FROM USERS"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("append / appendComma / prependComma")
    class AppendTest {
        @Test
        @DisplayName("append adds separator when builder is non-empty")
        void appendWithSeparator() {
            final var sb = new StringBuilder("a");
            utils.append(sb, ", ", "b");
            assertThat(sb).hasToString("a, b");
        }

        @Test
        @DisplayName("append does not add separator when builder is empty")
        void appendEmptyBuilder() {
            final var sb = new StringBuilder();
            utils.append(sb, ", ", "b");
            assertThat(sb).hasToString("b");
        }

        @Test
        @DisplayName("append skips empty value")
        void appendEmptyValue() {
            final var sb = new StringBuilder("a");
            utils.append(sb, ", ", "");
            assertThat(sb).hasToString("a");
        }

        @Test
        @DisplayName("appendComma adds comma when non-empty")
        void appendCommaNonEmpty() {
            final var sb = new StringBuilder("a");
            utils.appendComma(sb, "b");
            assertThat(sb).hasToString("a, b");
        }

        @Test
        @DisplayName("appendComma does not add comma when empty")
        void appendCommaEmpty() {
            final var sb = new StringBuilder();
            utils.appendComma(sb, "b");
            assertThat(sb).hasToString("b");
        }

        @Test
        @DisplayName("prependComma prepends with comma when non-empty")
        void prependCommaNonEmpty() {
            final var sb = new StringBuilder("a");
            utils.prependComma(sb, "b");
            assertThat(sb).hasToString("b, a");
        }

        @Test
        @DisplayName("prependComma does not add comma when empty")
        void prependCommaEmpty() {
            final var sb = new StringBuilder();
            utils.prependComma(sb, "b");
            assertThat(sb).hasToString("b");
        }
    }

    @Nested
    @DisplayName("appendParam")
    class AppendParamTest {
        @Test
        @DisplayName("appends N placeholders separated by commas")
        void appendN() {
            final var sb = new StringBuilder();
            utils.appendParam(sb, 3);
            assertThat(sb).hasToString("?, ?, ?");
        }

        @Test
        @DisplayName("appendParam(int) creates a new builder when null")
        void appendParamNullBuilder() {
            assertThat(utils.appendParam(2)).hasToString("?, ?");
        }

        @Test
        @DisplayName("appendParam zero produces empty")
        void appendParamZero() {
            assertThat(utils.appendParam(0).toString()).isEmpty();
        }
    }

    @Nested
    @DisplayName("makeCols")
    class MakeColsTest {
        @Test
        @DisplayName("backquotes each column separated by commas")
        void makeColsBasic() {
            assertThat(utils.makeCols(List.of("a", "b"))).hasToString("`a`, `b`");
        }

        @Test
        @DisplayName("withParenthesis wraps when more than one column")
        void makeColsWithParenthesis() {
            assertThat(utils.makeCols(List.of("a", "b"), true)).hasToString("(`a`, `b`)");
        }

        @Test
        @DisplayName("withParenthesis does not wrap for single column")
        void makeColsSingleWithParenthesis() {
            assertThat(utils.makeCols(List.of("a"), true)).hasToString("`a`");
        }

        @Test
        @DisplayName("Iterable overload works")
        void makeColsIterable() {
            assertThat(utils.makeCols(Arrays.asList("x", "y"))).hasToString("`x`, `y`");
        }
    }

    @Nested
    @DisplayName("makeSet")
    class MakeSetTest {
        @Test
        @DisplayName("collection overload produces col=? pairs")
        void makeSetCollection() {
            assertThat(utils.makeSet(List.of("a", "b"))).hasToString("`a`=?, `b`=?");
        }

        @Test
        @DisplayName("LinkedHashMap overload produces col=? pairs")
        void makeSetMap() {
            final LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("a", 1);
            map.put("b", 2);
            assertThat(utils.makeSet(map)).hasToString("`a`=?, `b`=?");
        }

        @Test
        @DisplayName("Set of entries overload produces col=? pairs")
        void makeSetEntries() {
            final LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("a", 1);
            final Set<Entry<String, Object>> entries = map.entrySet();
            assertThat(utils.makeSet(entries)).hasToString("`a`=?");
        }
    }

    @Nested
    @DisplayName("notNull")
    class NotNullTest {
        @ParameterizedTest
        @CsvSource({ "true,NOT NULL", "false,NULL" })
        @DisplayName("returns NOT NULL or NULL")
        void notNull(final boolean required, final String expected) {
            assertThat(utils.notNull(required)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getSQLValue")
    class GetSQLValueTest {
        @Test
        @DisplayName("string value wrapped in quotes")
        void stringValue() {
            assertThat(utils.getSQLValue("hello", true, false)).isEqualTo("'hello'");
        }

        @Test
        @DisplayName("null string with notNull returns empty quotes")
        void nullStringNotNull() {
            assertThat(utils.getSQLValue(null, true, true)).isEqualTo("''");
        }

        @Test
        @DisplayName("null string nullable returns NULL")
        void nullStringNullable() {
            assertThat(utils.getSQLValue(null, true, false)).isEqualTo("NULL");
        }

        @Test
        @DisplayName("non-string value returned as-is")
        void nonStringValue() {
            assertThat(utils.getSQLValue("42", false, false)).isEqualTo("42");
        }

        @Test
        @DisplayName("null non-string with notNull returns 0")
        void nullNonStringNotNull() {
            assertThat(utils.getSQLValue(null, false, true)).isEqualTo("0");
        }

        @Test
        @DisplayName("empty non-string nullable returns NULL")
        void emptyNonStringNullable() {
            assertThat(utils.getSQLValue("", false, false)).isEqualTo("NULL");
        }
    }

    @Nested
    @DisplayName("getDefaultValue dispatch")
    class GetDefaultValueTest {
        @Test
        @DisplayName("CHAR type dispatches to getCharDefaultValue")
        void charType() {
            assertThat(utils.getDefaultValue("abc", JDBCType.CHAR, false)).isEqualTo(" DEFAULT 'abc'");
        }

        @Test
        @DisplayName("VARCHAR null notNull returns DEFAULT ''")
        void varcharNullNotNull() {
            assertThat(utils.getDefaultValue(null, JDBCType.VARCHAR, true)).isEqualTo(" DEFAULT ''");
        }

        @Test
        @DisplayName("BOOLEAN null notNull returns DEFAULT FALSE")
        void booleanNullNotNull() {
            assertThat(utils.getDefaultValue(null, JDBCType.BOOLEAN, true)).isEqualTo(" DEFAULT FALSE");
        }

        @Test
        @DisplayName("BOOLEAN value returns DEFAULT value")
        void booleanValue() {
            assertThat(utils.getDefaultValue("TRUE", JDBCType.BOOLEAN, false)).isEqualTo(" DEFAULT TRUE");
        }

        @Test
        @DisplayName("INTEGER null notNull returns DEFAULT 0")
        void integerNullNotNull() {
            assertThat(utils.getDefaultValue(null, JDBCType.INTEGER, true)).isEqualTo(" DEFAULT 0");
        }

        @Test
        @DisplayName("INTEGER value returns DEFAULT value")
        void integerValue() {
            assertThat(utils.getDefaultValue("5", JDBCType.INTEGER, false)).isEqualTo(" DEFAULT 5");
        }

        @Test
        @DisplayName("default branch null notNull returns DEFAULT 0")
        void defaultBranchNull() {
            assertThat(utils.getDefaultValue(null, JDBCType.BIGINT, true)).isEqualTo(" DEFAULT 0");
        }
    }

    @Nested
    @DisplayName("getIterable / getReversedIterable")
    class IterableTest {
        @Test
        @DisplayName("getIterable iterates in order")
        void getIterableOrder() {
            final var iter = utils.getIterable(List.of(1, 2, 3)).iterator();
            assertThat(iter.next()).isEqualTo(1);
            assertThat(iter.next()).isEqualTo(2);
            assertThat(iter.next()).isEqualTo(3);
        }

        @Test
        @DisplayName("getReversedIterable iterates in reverse")
        void getReversedIterableOrder() {
            final var iter = utils.getReversedIterable(List.of(1, 2, 3)).iterator();
            assertThat(iter.next()).isEqualTo(3);
            assertThat(iter.next()).isEqualTo(2);
            assertThat(iter.next()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("str")
    class StrTest {
        @Test
        @DisplayName("wraps in quotes and escapes single quotes")
        void escapesQuotes() {
            assertThat(utils.str("O'Reilly")).isEqualTo("'O''Reilly'");
        }

        @Test
        @DisplayName("doubleBackSlashes escapes backslashes")
        void doubleBackSlashes() {
            assertThat(utils.str("C:\\path", true)).isEqualTo("'C:\\\\path'");
        }

        @Test
        @DisplayName("without doubleBackSlashes does not escape backslashes")
        void noDoubleBackSlashes() {
            assertThat(utils.str("C:\\path", false)).isEqualTo("'C:\\path'");
        }
    }

    @Nested
    @DisplayName("val")
    class ValTest {
        @Test
        @DisplayName("parses integer string")
        void parsesInteger() {
            assertThat(utils.val("42").intValue()).isEqualTo(42);
        }

        @Test
        @DisplayName("parses decimal string")
        void parsesDecimal() {
            assertThat(utils.val("3.14").doubleValue()).isCloseTo(3.14, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("strict mode uses NumberUtils")
        void strictMode() {
            assertThat(utils.val("0x10", true).intValue()).isEqualTo(16);
        }

        @Test
        @DisplayName("stops at non-digit characters in non-strict mode")
        void nonStrictStopsAtNonDigit() {
            assertThat(utils.val("12abc").intValue()).isEqualTo(12);
        }
    }

    @Nested
    @DisplayName("getSQLTable")
    class GetSQLTableTest {
        @Test
        @DisplayName("prefixes with context when schemas supported")
        void withContext() throws SQLException {
            assertThat(utils.getSQLTable("users")).isEqualTo("`ctx`.`users`");
        }

        @Test
        @DisplayName("returns backquoted table when context null")
        void nullContext() throws SQLException {
            utils = new StubSQLUtils(utils.getDb(), null);
            assertThat(utils.getSQLTable("users")).isEqualTo("`users`");
        }

        @Test
        @DisplayName("returns backquoted table when schemas not supported")
        void schemasNotSupported() throws SQLException {
            final Connection connection = mock(Connection.class);
            final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
            when(connection.getMetaData()).thenReturn(metaData);
            when(metaData.supportsSchemasInDataManipulation()).thenReturn(false);
            utils = new StubSQLUtils(connection, "ctx");
            assertThat(utils.getSQLTable("users")).isEqualTo("`users`");
        }
    }
}