package jrm.fullserver.db;

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * SQLUtils is an interface that provides utility methods for working with SQL databases. It includes methods for backquoting names,
 * appending and prepending values to strings, creating parameter placeholders, and constructing SQL statements for columns and
 * sets. The interface also defines methods for getting the database connection, context, and executing various SQL operations such
 * as counting records, updating records, dropping tables, and querying data. The utility methods in this interface are designed to
 * simplify the process of constructing SQL statements and handling database interactions in a consistent manner across different
 * implementations of the DB interface.
 * 
 * @author jrm
 * 
 * @version 1.0
 * 
 * @since 2024-06
 */
public interface SQLUtils extends SqlIdentifiers {


    /**
     * Converts a collection of items into an Iterable. This method allows for easy iteration over the items in the collection using
     * a for-each loop or other iteration constructs. It returns an Iterable that provides an iterator over the elements in the
     * collection.
     * 
     * @param <T> The type of elements in the collection.
     * @param coll The collection of items to be converted into an Iterable.
     * 
     * @return An Iterable that provides an iterator over the elements in the collection.
     */
    default <T> Iterable<T> getIterable(Collection<T> coll) {
        return coll::iterator;
    }

    /**
     * Converts a collection of items into an Iterable that iterates over the items in reverse order. This method allows for easy
     * iteration over the items in the collection in reverse using a for-each loop or other iteration constructs. It returns an
     * Iterable that provides a descending iterator over the elements in the collection.
     * 
     * @param coll The collection of items to be converted into a reversed Iterable.
     * @param <T> The type of elements in the collection.
     * 
     * @return An Iterable that provides a descending iterator over the elements in the collection.
     */
    default <T> Iterable<T> getReversedIterable(Collection<T> coll) {
        return () -> new LinkedList<>(coll).descendingIterator();
    }


    /**
     * Returns a string indicating whether a column is nullable or not based on the provided boolean value. If the required
     * parameter is true, it returns "NOT NULL", indicating that the column cannot be null. If the required parameter is false, it
     * returns "NULL", indicating that the column can be null. This method is useful for constructing SQL statements that define
     * column constraints.
     * 
     * @param required A boolean value indicating whether the column is required (not nullable) or not.
     * 
     * @return A string indicating whether the column is "NOT NULL" or "NULL" based on the provided boolean value.
     */
    default String notNull(boolean required) {
        return required ? "NOT NULL" : "NULL";
    }

    /**
     * Converts a string value into its corresponding SQL representation based on whether it is a string type and whether it is not
     * null. If the value is a string type (isStr is true), it returns the value enclosed in single quotes, or "''" if the value is
     * null and notNull is true, or "NULL" if the value is null and notNull is false. If the value is not a string type (isStr is
     * false), it returns the value as is, or "0" if the value is null or empty and notNull is true, or "NULL" if the value is null
     * or empty and notNull is false.
     * 
     * @param value The string value to be converted into its SQL representation.
     * @param isStr A boolean indicating whether the value should be treated as a string type.
     * @param notNull A boolean indicating whether the value should be treated as not null.
     * 
     * @return The SQL representation of the input value based on the provided parameters.
     */
    default String getSQLValue(String value, boolean isStr, boolean notNull) {
        if (isStr) {
            if (value == null)
                return notNull ? "''" : "NULL";
            return "'" + value + "'";
        }
        if (value == null || value.isEmpty())
            return notNull ? "0" : "NULL";
        return value;
    }

    /**
     * Retrieves the database connection associated with this SQLUtils instance. This method is abstract and must be implemented by
     * any class that implements the SQLUtils interface. The returned Connection object can be used to execute SQL statements and
     * interact with the database. It may throw a SQLException if there is an error while retrieving the database connection.
     * 
     * @return The Connection object associated with this SQLUtils instance.
     */
    public Connection getDb();

    /**
     * Retrieves the context associated with this SQLUtils instance. The context is typically used to specify the schema or database
     * name when constructing SQL statements. This method is abstract and must be implemented by any class that implements the
     * SQLUtils interface. The returned string represents the context for SQL operations.
     * 
     * @return The context associated with this SQLUtils instance.
     */
    String getContext();

    /**
     * Constructs a fully qualified SQL table name based on the provided table name and the context of this SQLUtils instance. If
     * the context is not null and the database supports schemas in data manipulation, it returns the table name prefixed with the
     * context (schema) and separated by a dot. Otherwise, it returns just the backquoted table name. This method may throw a
     * SQLException if there is an error while retrieving database metadata.
     * 
     * @param table The name of the table for which to construct the SQL table name.
     * 
     * @return The fully qualified SQL table name based on the provided table name and context.
     * 
     * @throws SQLException If an error occurs while retrieving database metadata.
     */
    default String getSQLTable(String table) throws SQLException {
        return getSQLTable(table, getContext());
    }

    /**
     * Constructs a fully qualified SQL table name based on the provided table name and context. If the context is not null and the
     * database supports schemas in data manipulation, it returns the table name prefixed with the context (schema) and separated by
     * a dot. Otherwise, it returns just the backquoted table name. This method may throw a SQLException if there is an error while
     * retrieving database metadata.
     * 
     * @param table The name of the table for which to construct the SQL table name.
     * @param context The context (schema) to be used when constructing the SQL table name.
     * 
     * @return The fully qualified SQL table name based on the provided table name and context.
     * 
     * @throws SQLException If an error occurs while retrieving database metadata.
     */
    default String getSQLTable(String table, String context) throws SQLException {
        // identifiers validated in backquote/requireSqlIdentifier; cannot be bound as JDBC parameters
        if (context != null && getDb().getMetaData().supportsSchemasInDataManipulation())
            return backquote(context) + '.' + backquote(table);
        return backquote(table);
    }

    /**
     * Constructs a default value string for a SQL column based on the provided value, JDBC type, and nullability. The method
     * determines the appropriate default value format based on the JDBC type of the column. For character types (e.g., VARCHAR,
     * CHAR), it calls getCharDefaultValue to construct the default value string. For boolean types, it calls getBoolDefaultValue,
     * and for integer types, it calls getIntDefaultValue. For other types, it checks if the value is null or empty and returns a
     * default value accordingly.
     * 
     * @param value The default value to be used for the SQL column.
     * @param type The JDBCType of the column for which the default value is being constructed.
     * @param notNull A boolean indicating whether the column is not nullable.
     * 
     * @return A string representing the default value for the SQL column based on the provided parameters.
     */
    default String getDefaultValue(String value, JDBCType type, boolean notNull) {
        return switch (type) {
            case VARCHAR, LONGNVARCHAR, LONGVARCHAR, CLOB, CHAR, NCHAR, NCLOB -> getCharDefaultValue(value, notNull);
            case BOOLEAN -> getBoolDefaultValue(value, notNull);
            case INTEGER, TINYINT, SMALLINT -> getIntDefaultValue(value, notNull);
            default -> {
                if (value == null || value.isEmpty())
                    yield " DEFAULT " + (notNull ? "0" : "NULL");
                yield " DEFAULT " + value;
            }
        };
    }

    /**
     * Constructs a default value string for an integer SQL column based on the provided value and nullability. If the value is null
     * or empty, it returns " DEFAULT 0" if the column is not nullable, or " DEFAULT NULL" if the column is nullable. If the value
     * is not empty, it returns " DEFAULT " followed by the value. This method is useful for constructing SQL statements that define
     * default values for integer columns.
     * 
     * @param value The default value to be used for the integer SQL column.
     * @param notNull A boolean indicating whether the column is not nullable.
     * 
     * @return A string representing the default value for the integer SQL column based on the provided parameters.
     */
    default String getIntDefaultValue(String value, boolean notNull) {
        if (value == null || value.isEmpty())
            return " DEFAULT " + (notNull ? "0" : "NULL");
        return " DEFAULT " + value;
    }

    /**
     * Constructs a default value string for a boolean SQL column based on the provided value and nullability. If the value is null
     * or empty, it returns " DEFAULT FALSE" if the column is not nullable, or " DEFAULT NULL" if the column is nullable. If the
     * value is not empty, it returns " DEFAULT " followed by the value. This method is useful for constructing SQL statements that
     * define default values for boolean columns.
     * 
     * @param value The default value to be used for the boolean SQL column.
     * @param notNull A boolean indicating whether the column is not nullable.
     * 
     * @return A string representing the default value for the boolean SQL column based on the provided parameters.
     */
    default String getBoolDefaultValue(String value, boolean notNull) {
        if (value == null || value.isEmpty())
            return " DEFAULT " + (notNull ? "FALSE" : "NULL");
        return " DEFAULT " + value;
    }

    /**
     * Constructs a default value string for a character SQL column based on the provided value and nullability. If the value is
     * null or empty, it returns " DEFAULT ''" if the column is not nullable, or " DEFAULT NULL" if the column is nullable. If the
     * value is not empty, it returns " DEFAULT '" followed by the value and a closing single quote. This method is useful for
     * constructing SQL statements that define default values for character columns.
     * 
     * @param value The default value to be used for the character SQL column.
     * @param notNull A boolean indicating whether the column is not nullable.
     * 
     * @return A string representing the default value for the character SQL column based on the provided parameters.
     */
    default String getCharDefaultValue(String value, boolean notNull) {
        if (value == null || value.isEmpty())
            return " DEFAULT " + (notNull ? "''" : "NULL");
        return " DEFAULT '" + value + "'";
    }

    /**
     * Parses a string into a Number. This method is a convenience overload of the val method that defaults the strict parameter to
     * false, meaning that it will manually parse the string by iterating through its characters and building a valid number string,
     * allowing for a single dot and optional signs at the beginning. It returns the parsed Number, or null if the string cannot be
     * parsed.
     * <p>
     * Note: If strict parsing is desired, the val method with the strict parameter should be used, which utilizes
     * NumberUtils.createNumber for more flexible number formats.
     * 
     * @param str The string to parse.
     * 
     * @return The parsed Number, or null if the string cannot be parsed.
     */
    public default Number val(String str) {
        return val(str, false);
    }

    /**
     * Parses a string into a Number with an option for strict parsing. If strict is true, it uses NumberUtils.createNumber to parse
     * the string, which allows for more flexible number formats. If strict is false, it manually parses the string by iterating
     * through its characters and building a valid number string, allowing for a single dot and optional signs at the beginning.
     * This method returns the parsed Number, or null if the string cannot be parsed.
     * 
     * @param str The string to parse.
     * @param strict Whether to use strict parsing with NumberUtils.createNumber or to manually parse the string.
     * 
     * @return The parsed Number, or null if the string cannot be parsed.
     */
    public default Number val(String str, boolean strict) {
        if (strict)
            return NumberUtils.createNumber(str);
        final var validStr = new StringBuilder();
        var seenDot = false; // when this is true, dots are not allowed
        var seenDigit = false; // when this is true, signs are not allowed
        for (var i = 0; i < str.length(); i++) {
            final var c = str.charAt(i);
            if (c == '.' && !seenDot) {
                seenDot = true;
                validStr.append(c);
            } else if ((c == '-' || c == '+') && !seenDigit) {
                validStr.append(c);
            } else if (Character.isDigit(c)) {
                seenDigit = true;
                validStr.append(c);
            } else if (!Character.isWhitespace(c))
                break;
        }
        return NumberUtils.createNumber(validStr.toString());
    }

    /**
     * Escapes a string for use in SQL statements by surrounding it with single quotes and escaping existing single quotes.
     * <p>
     * This method is designed to prevent SQL injection attacks by ensuring that any single quotes in the input string are properly
     * escaped. It replaces each single quote in the input string with two single quotes, which is the standard way to escape single
     * quotes in SQL. The resulting string is then enclosed in single quotes to make it safe for use in SQL queries.
     * <p>
     * For example, if the input string is "O'Reilly", the output of this method would be "'O''Reilly'", which can be safely used in
     * an SQL statement without risking SQL injection vulnerabilities.
     * <p>
     * Note: While this method provides a way to escape strings for SQL statements, it is generally recommended to use prepared
     * statements with parameterized queries to prevent SQL injection, as this approach is more secure and less error-prone than
     * manually escaping strings.
     * <p>
     * This method takes a string as input and returns a new string that is safe to use in SQL statements. It replaces any single
     * quotes in the input string with two single quotes to prevent SQL injection attacks and ensures that the resulting string is
     * properly enclosed in single quotes for use in SQL queries.
     * 
     * @param v The string to escape.
     * 
     * @return The escaped string enclosed in single quotes.
     */
    public default String str(final String v) {
        return "'" + v.replace("'", "''") + "'";
    }

    /**
     * Escapes a string for use in SQL statements by surrounding it with single quotes and escaping existing single quotes and
     * optionally backslashes.
     * <p>
     * This method is designed to prevent SQL injection attacks by ensuring that any single quotes and optionally backslashes in the
     * input string are properly escaped. It replaces each single quote in the input string with two single quotes, and if
     * doubleBackSlashes is true, it also replaces each backslash with two backslashes. The resulting string is then enclosed in
     * single quotes to make it safe for use in SQL queries.
     * <p>
     * For example, if the input string is "C:\path\to\file", and doubleBackSlashes is true, the output of this method would be
     * "'C:\\path\\to\\file'", which can be safely used in an SQL statement without risking SQL injection vulnerabilities.
     * <p>
     * Note: While this method provides a way to escape strings for SQL statements, it is generally recommended to use prepared
     * statements with parameterized queries to prevent SQL injection, as this approach is more secure and less error-prone than
     * manually escaping strings.
     * 
     * @param v The string to escape.
     * @param doubleBackSlashes Whether to also escape backslashes in the input string.
     * 
     * @return The escaped string enclosed in single quotes.
     */
    public default String str(final String v, boolean doubleBackSlashes) {
        if (doubleBackSlashes)
            return str(v).replace("\\", "\\\\");
        return str(v);
    }

    /**
     * Executes a SQL count query with the specified select statement and arguments. This method is abstract and must be implemented
     * by any class that implements the SQLUtils interface. It takes a SQL select statement and an optional list of arguments to be
     * used in the query, and returns the count result as a Long. It may throw a SQLException if there is an error while executing
     * the query.
     * 
     * @param select The SQL select statement to be executed for counting records.
     * @param args Optional arguments to be used in the SQL query.
     * 
     * @return The count result as a Long.
     * 
     * @throws SQLException If an error occurs while executing the SQL query.
     */
    abstract Long count(String select, Object... args) throws SQLException;

    /**
     * Executes a SQL count query on a specified table with an optional context. This method is abstract and must be implemented by
     * any class that implements the SQLUtils interface. It takes the name of the table to count records from and an optional
     * context (such as a schema) to be used in the query, and returns the count result as a Long. It may throw a SQLException if
     * there is an error while executing the query.
     * 
     * @param table The name of the table to count records from.
     * @param context Optional context (such as a schema) to be used in the SQL query.
     * 
     * @return The count result as a Long.
     * 
     * @throws SQLException If an error occurs while executing the SQL query.
     */
    abstract Long countTbl(String table, String context) throws SQLException;

    /**
     * Executes a SQL query with the specified select statement and arguments, and returns the result as a Long. This method is
     * abstract and must be implemented by any class that implements the SQLUtils interface. It takes a SQL select statement and an
     * optional list of arguments to be used in the query, and returns the result as a Long. It may throw a SQLException if there is
     * an error while executing the query.
     * 
     * @param select The SQL select statement to be executed.
     * @param args Optional arguments to be used in the SQL query.
     * 
     * @return The result of the query as a Long.
     * 
     * @throws SQLException If an error occurs while executing the SQL query.
     */
    abstract Long getLongValue(String select, Object... args) throws SQLException;

    /**
     * Executes a SQL update statement with the specified update query and arguments. This method is abstract and must be
     * implemented by any class that implements the SQLUtils interface. It takes a SQL update statement and an optional list of
     * arguments to be used in the update, and returns the number of rows affected by the update as an integer. It may throw a
     * SQLException if there is an error while executing the update statement.
     * 
     * @param update The SQL update statement to be executed.
     * @param args Optional arguments to be used in the SQL update statement.
     * 
     * @return The number of rows affected by the update as an integer.
     * 
     * @throws SQLException If an error occurs while executing the SQL update statement.
     */
    abstract int update(final String update, Object... args) throws SQLException;

    /**
     * Executes a SQL statement to drop a table with the specified name. This method is abstract and must be implemented by any
     * class that implements the SQLUtils interface. It takes the name of the table to be dropped as a parameter and executes the
     * appropriate SQL statement to remove the table from the database. It may throw a SQLException if there is an error while
     * executing the drop table statement.
     * 
     * @param table The name of the table to be dropped.
     * 
     * @throws SQLException If an error occurs while executing the SQL drop table statement.
     */
    abstract void dropTable(final String table) throws SQLException;

    /**
     * Executes a SQL query with the specified select statement and arguments, and returns the result as a list of maps. Each map in
     * the list represents a row from the query result, with column names as keys and corresponding values as values. This method is
     * abstract and must be implemented by any class that implements the SQLUtils interface. It takes a SQL select statement and an
     * optional list of arguments to be used in the query, and returns the result as a list of maps. It may throw a SQLException if
     * there is an error while executing the query.
     * 
     * @param select The SQL select statement to be executed.
     * @param args Optional arguments to be used in the SQL query.
     * 
     * @return A list of maps representing the query result, where each map corresponds to a row with column names as keys and their
     *         respective values.
     * 
     * @throws SQLException If an error occurs while executing the SQL query.
     */
    abstract List<Map<String, Object>> query(final String select, Object... args) throws SQLException;

    /**
     * Executes a SQL query with the specified select statement and arguments, and returns the first result as a map. The map
     * represents a single row from the query result, with column names as keys and corresponding values as values. This method is
     * abstract and must be implemented by any class that implements the SQLUtils interface. It takes a SQL select statement and an
     * optional list of arguments to be used in the query, and returns the first result as a map. It may throw a SQLException if
     * there is an error while executing the query.
     * 
     * @param select The SQL select statement to be executed.
     * @param args Optional arguments to be used in the SQL query.
     * 
     * @return A map representing the first result of the query, where column names are keys and their respective values are values.
     *         Returns null if no results are found.
     * 
     * @throws SQLException If an error occurs while executing the SQL query.
     */
    abstract Map<String, Object> queryFirst(final String select, Object... args) throws SQLException;

    /**
     * Executes a SQL SELECT query and returns a list of values from the specified column.
     * <p>
     * This method retrieves a single column across all result rows from the query results and casts them to the specified type
     * {@code T}.
     * </p>
     *
     * @param <T> the type of the elements in the returned list
     * @param select the SQL query statement to execute
     * @param col the 1-based index of the column to retrieve values from
     * 
     * @return a list of values of type {@code T} from the specified column
     * 
     * @throws SQLException if a database access error occurs or query execution fails
     */
    abstract <T> List<T> getColumnList(String select, int col) throws SQLException;

}