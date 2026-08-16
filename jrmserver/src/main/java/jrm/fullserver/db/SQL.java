package jrm.fullserver.db;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Closeable;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;

import jrm.misc.Log;
import jrm.misc.SystemSettings;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;

/**
 * Abstract class for database operations. This class provides common methods for executing SQL queries and updates, as well as
 * utility methods for handling database interactions. It uses Apache Commons DbUtils for query execution and result handling. The
 * class also includes methods for converting Java beans to maps and updating beans from maps, which can be useful for working with
 * database records. Subclasses of SQL will implement specific database operations and may override some of the methods to provide
 * database-specific functionality.
 * 
 * @author jrm
 * 
 * @version 1.0
 * 
 * @since 2024-06
 */
public abstract class SQL implements SQLUtils, Closeable {
    /**
     * The database connection.
     * 
     * @return the database connection
     */
    protected @Getter Connection db;
    /**
     * Indicates whether the database connection should be closed when the SQL object is closed.
     * 
     * @return true if the connection should be closed, false otherwise
     */
    private final @Getter boolean shouldClose;
    /**
     * The SystemSettings object containing configuration for the database.
     * 
     * @return the SystemSettings object
     */
    private final @Getter SystemSettings settings;

    /**
     * The QueryRunner object from Apache Commons DbUtils for executing SQL queries and updates.
     */
    protected QueryRunner qryRunner = new QueryRunner();

    /**
     * Constructs a new SQL object with the specified shouldClose flag and SystemSettings. The database connection is initialized to
     * null.
     * 
     * @param shouldClose Whether the database connection should be closed when the SQL object is closed.
     * @param settings The SystemSettings object containing configuration for the database.
     */
    protected SQL(final boolean shouldClose, final SystemSettings settings) {
        this.db = null;
        this.shouldClose = shouldClose;
        this.settings = settings;
    }

    /**
     * Constructs a new SQL object with the specified database connection, shouldClose flag, and SystemSettings.
     * 
     * @param db The database connection to use for executing SQL queries and updates.
     * @param shouldClose Whether the database connection should be closed when the SQL object is closed.
     * @param settings The SystemSettings object containing configuration for the database.
     */
    protected SQL(final @NonNull Connection db, final boolean shouldClose, final SystemSettings settings) {
        this.db = db;
        this.shouldClose = shouldClose;
        this.settings = settings;
    }

    /**
     * Return a column from the first result row as a value of type T.
     * <p>
     * T must be compatible with the request column, otherwise a ClassCastException will be thrown at runtime
     * <p>
     * Note that this method is not type-safe, and it is the responsibility of the caller to ensure that the type T is compatible
     * with the requested column. If the type is not compatible, a ClassCastException may occur at runtime when trying to cast the
     * result to type T.
     * 
     * @param <T> the type of the value to return, must be compatible with the request column
     * @param select the sql request, the request column will have to be compatible with type T
     * @param cls the class of type T
     * 
     * @return the value of type T or null if no result
     * 
     * @throws SQLException if a database access error occurs
     */
    public <T> T getScalarValue(String select, Class<T> cls) throws SQLException {
        return qryRunner.query(db, select, new ScalarHandler<T>(1));
    }

    /**
     * Return a column from the first result row as an integer
     * <p>
     * Note that this method is not type-safe, and it is the responsibility of the caller to ensure that the requested column is
     * compatible with an integer type. If the column is not compatible, a ClassCastException may occur at runtime when trying to
     * cast the result to an integer. Additionally, if the column value is null, this method will return null instead of throwing an
     * exception.
     * 
     * @param select the sql request, the request column will have to be an integer
     * @param col the col from which to return the value
     * 
     * @return the integer value or null if no result
     * 
     * @throws SQLException if a database access error occurs
     */
    protected Integer getIntValue(String select, int col) throws SQLException {
        return qryRunner.query(db, select, new ScalarHandler<Integer>(col));
    }

    /**
     * Return a column from the first result row as a long, the request column will have to be an integer or a long value
     * <p>
     * Note that this method is not type-safe, and it is the responsibility of the caller to ensure that the requested column is
     * compatible with a long type. <br>
     * If the column is not compatible, a ClassCastException may occur at runtime when trying to cast the result to a long.
     * Additionally, if the column value is null, this method will return null instead of throwing an exception.
     * 
     * @param select the sql request, the request column will have to be a long
     * @param col the col from which to return the value
     * 
     * @return the long value or null if no result
     * 
     * @throws SQLException if a database access error occurs
     */
    protected Long getLongValue(String select, int col) throws SQLException {
        return qryRunner.query(db, select, new ScalarHandler<Long>(col));
    }

    /**
     * Return a column from the first result row as a long, with support for array parameters. If the database does not support
     * array parameters and an array parameter is found in the arguments, the method will convert the array parameter into a format
     * that can be used in the SQL query (e.g., by replacing "= ANY(?)" with "IN (?, ?, ...)" and expanding the array into
     * individual parameters).
     * <p>
     * Note that this method is not type-safe, and it is the responsibility of the caller to ensure that the requested column is
     * compatible with a long type. If the column is not compatible, a ClassCastException may occur at runtime when trying to cast
     * the result to a long. Additionally, if the column value is null, this method will return null instead of throwing an
     * exception.
     * 
     * @param select the sql request, the request column will have to be a long
     * @param args the optional arguments in case of a prepared statement
     * 
     * @return the long value or null if no result
     * 
     * @throws SQLException if a database access error occurs
     */
    @Override
    public Long getLongValue(String select, Object... args) throws SQLException {
        if (!supportsArrayParams() && findArrayParam(args) != -1) {
            val selectRef = new AtomicReference<String>(select);
            val argsRef = new AtomicReference<Object[]>(args);
            convertArrayParams(selectRef, argsRef);
            select = selectRef.get();
            args = argsRef.get();
        }
        return qryRunner.query(db, select, new ScalarHandler<Long>(1), args);
    }

    /**
     * Return the count of rows that would be returned by a given SQL SELECT query. If the database does not support array
     * parameters and an array parameter is found in the arguments, the method will convert the array parameter into a format that
     * can be used in the SQL query (e.g., by replacing "= ANY(?)" with "IN (?, ?, ...)" and expanding the array into individual
     * parameters).
     * 
     * @param select the sql request to count, it should be a SELECT query
     * @param args the optional arguments in case of a prepared statement
     * 
     * @return the count of rows or null if no result
     * 
     * @throws SQLException if a database access error occurs
     */
    @Override
    public Long count(String select, Object... args) throws SQLException {
        select = requireSelectStatement(select);
        if (!supportsArrayParams() && findArrayParam(args) != -1) {
            val selectRef = new AtomicReference<String>(select);
            val argsRef = new AtomicReference<Object[]>(args);
            convertArrayParams(selectRef, argsRef);
            select = selectRef.get();
            args = argsRef.get();
        }
        // select is validated as a single SELECT; values must use prepared args, not concatenation
        return getLongValue("SELECT COUNT(*) FROM (" + select + ") AS T", args);
    }

    /**
     * Return the count of rows in a given table. The method constructs a SQL query to count the rows in the specified table, and it
     * may use the context parameter to determine the appropriate table name or schema if necessary.
     * 
     * @param table the name of the table to count rows from
     * @param context an optional context that can be used to determine the appropriate table name or schema if necessary
     * 
     * @return the count of rows in the specified table or null if no result
     * 
     * @throws SQLException if a database access error occurs
     */
    @Override
    public Long countTbl(String table, String context) throws SQLException {
        // table/context are validated SQL identifiers (see requireSqlIdentifier); not bindable as parameters
        return getLongValue("SELECT COUNT(*) FROM " + getSQLTable(table, context));
    }

    /**
     * Return the first column from the first row as an integer
     * <p>
     * Note that this method assumes that the first column of the result set is an integer. <br>
     * If the first column is not an integer, a ClassCastException may occur at runtime when trying to cast the result to an
     * integer. It is the responsibility of the caller to ensure that the SQL query provided in the select parameter returns a
     * result set where the first column is compatible with an integer type.
     * 
     * @param select the sql request, the first column will have to be an integer
     * 
     * @return the integer value or null if no result
     * 
     * @throws SQLException if a database access error occurs
     */
    protected Integer getIntValue(String select) throws SQLException {
        return getIntValue(select, 1);
    }

    /**
     * Return a column from the first result row as a long
     * <p>
     * Note that this method is not type-safe, and it is the responsibility of the caller to ensure that the requested column is
     * compatible with a long type. <br>
     * If the column is not compatible, a ClassCastException may occur at runtime when trying to cast the result to a long.
     * Additionally, if the column value is null, this method will return null instead of throwing an exception.
     * 
     * @param select the sql request, the request column will have to be a long
     * 
     * @return the long value or null if no result
     * 
     * @throws SQLException if a database access error occurs
     */
    protected Long getLongValue(String select) throws SQLException {
        return getLongValue(select, 1);
    }

    /**
     * Return a list of values from a column.
     * 
     * @param <T> the type of the elements in the list
     * @param select the sql request
     * @param col the column index (1-based)
     * 
     * @return a list of values of type T
     * 
     * @throws SQLException if a database access error occurs
     */
    @Override
    public <T> List<T> getColumnList(String select, int col) throws SQLException {
        return qryRunner.query(db, select, new ColumnListHandler<T>(col));
    }

    /**
     * Create a schema if it does not exist, and optionally drop it if it already exists. The method checks if the database supports
     * schemas in data manipulation before attempting to create or drop the schema. If the drop parameter is true, the method will
     * first drop the existing schema before creating a new one. The method returns the name of the schema that was created or
     * dropped.
     * 
     * @param name the name of the schema to create or drop
     * @param drop whether to drop the existing schema before creating a new one
     * 
     * @return the name of the schema that was created or dropped
     * 
     * @throws SQLException if there is an error executing the SQL statement to create or drop the schema
     */
    public synchronized String createSchema(String name, boolean drop) throws SQLException {
        if (name != null && db.getMetaData().supportsSchemasInDataManipulation()) {
            if (drop)
                dropSchema(name);
            qryRunner.execute(db, "CREATE SCHEMA IF NOT EXISTS " + backquote(name));
        }
        return name;
    }

    /**
     * Drop a schema if it exists.
     * <p>
     * Note that this method will only attempt to drop the schema if the database supports schemas in data manipulation. If the
     * database does not support this feature, the method will simply return the name of the schema without performing any action.
     * If the database does support schemas in data manipulation, the method will execute a SQL statement to drop the schema if it
     * exists, using the "DROP SCHEMA IF EXISTS" syntax. The method returns the name of the schema that was dropped, or the name of
     * the schema if it was not dropped due to lack of support for schemas in data manipulation.
     * <p>
     * Note that dropping a schema will also drop all objects contained within that schema, such as tables, views, and stored
     * procedures. Therefore, it is important to use this method with caution, especially if the drop parameter is set to true in
     * the createSchema method, as it may result in data loss if the schema contains important objects. Always ensure that you have
     * a backup of your data before dropping a schema.
     * <p>
     * Note: The method may throw a SQLException if there is an error executing the SQL statement to drop the schema. It is
     * important to handle this exception appropriately in your application to avoid issues with database operations.
     * 
     * @param name the name of the schema to drop
     * 
     * @return the name of the schema that was dropped
     * 
     * @throws SQLException if there is an error executing the SQL statement to drop the schema
     */
    public String dropSchema(String name) throws SQLException {
        if (db.getMetaData().supportsSchemasInDataManipulation())
            qryRunner.execute(db, "DROP SCHEMA IF EXISTS " + backquote(name) + " CASCADE");
        return name;
    }

    /**
     * Transform a bean object into a map, where the keys are the property names of the bean and the values are the corresponding
     * property values. This method uses Java's Introspector to analyze the properties of the bean and extract their values. The
     * resulting map is a LinkedHashMap, which maintains the order of the properties as they are defined in the bean class.
     * 
     * @param bean the bean object source
     * 
     * @return the resulted map containing property names and values from the bean
     */
    protected LinkedHashMap<String, Object> convertBeanToMap(Object bean) {
        return convertBeanToMap(bean, null);
    }

    /**
     * Transform a bean object into a map, where the keys are the property names of the bean and the values are the corresponding
     * property values. This method uses Java's Introspector to analyze the properties of the bean and extract their values. The
     * resulting map is a LinkedHashMap, which maintains the order of the properties as they are defined in the bean class. If a set
     * of columns is provided, only those properties whose names are in the set will be included in the resulting map.
     * 
     * @param bean the bean object source
     * @param columns an optional set of property names to include in the resulting map; if null, all properties will be included
     * 
     * @return the resulted map containing property names and values from the bean
     */
    protected LinkedHashMap<String, Object> convertBeanToMap(final Object bean, Set<String> columns) {
        val set = new LinkedHashMap<String, Object>();
        try {
            if (columns != null)
                columns = columns.stream().map(Introspector::decapitalize).collect(Collectors.toSet());
            for (val prop : Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors()) {
                if (columns != null) {
                    if (columns.contains(prop.getName()))
                        set.put(prop.getName(), prop.getReadMethod().invoke(bean, new Object[0]));
                } else {
                    if (prop.getWriteMethod() != null)
                        set.put(prop.getName(), prop.getReadMethod().invoke(bean, new Object[0]));
                }
            }
        } catch (IntrospectionException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            Log.warn(e.getMessage());
        }
        return set;
    }

    /**
     * Update a bean with values from a map. This method uses Java's Introspector to analyze the properties of the bean and map the
     * values from the provided map to the corresponding bean properties. The map keys are expected to be the property names of the
     * bean.
     * 
     * @param bean the bean object to update
     * @param set the map containing the values to update the bean with, where keys are property names
     */
    protected void updateBeanFromMap(final Object bean, Map<String, Object> set) {
        try {
            Map<String, PropertyDescriptor> descriptors = Stream.of(Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors())
                    .collect(Collectors.toMap(e -> Introspector.decapitalize(e.getName()), e -> e));
            set.forEach((n, v) -> {
                try {
                    descriptors.get(Introspector.decapitalize(n)).getWriteMethod().invoke(bean, v);
                } catch (Exception e1) {
                    Log.err(e1.getMessage(), e1);
                }
            });
        } catch (IntrospectionException e1) {
            Log.err(e1.getMessage(), e1);
        }
    }

    /**
     * Links a schema from another database to the current database.
     *
     * @param otherdb the connection to the other database
     * @param schema the name of the schema to link
     * 
     * @throws SQLException if a database access error occurs
     */
    public void linkSchema(Connection otherdb, String schema) throws SQLException {
        qryRunner.execute(db, "CALL LINK_SCHEMA(?,?,?,?,?,?)", schema, "", otherdb.getMetaData().getURL(), otherdb.getMetaData().getUserName(), "", schema);
    }

    /**
     * Checks if a record exists for a given SQL query.
     * 
     * @param select the SQL query
     * @param args the optional arguments for the prepared statement
     * 
     * @return true if a result is returned, false otherwise
     * 
     * @throws SQLException if a database access error occurs
     */
    protected boolean hasResult(final String select, Object... args) throws SQLException {
        return qryRunner.query(db, select, new ScalarHandler<Object>(1), args) != null;
    }

    /**
     * Execute a SQL SELECT query and return the results as a list of maps, where each map represents a row in the result set with
     * column names as keys and corresponding values as map values. If the database does not support array parameters and an array
     * parameter is found in the arguments, the method will convert the array parameter into a format that can be used in the SQL
     * query (e.g., by replacing "= ANY(?)" with "IN (?, ?, ...)" and expanding the array into individual parameters).
     * <p>
     * Note: The method may throw a SQLException if there is an error executing the SQL query or processing the results. It is
     * important to handle this exception appropriately in your application to ensure that database operations are performed
     * correctly and to avoid issues with database connectivity or data integrity.
     * 
     * @param select the SQL query
     * @param args the optional arguments for the prepared statement
     * 
     * @return a Map representing the first row, or null if no result
     * 
     * @throws SQLException if a database access error occurs
     */
    @Override
    public List<Map<String, Object>> query(String select, Object... args) throws SQLException {
        if (!supportsArrayParams() && findArrayParam(args) != -1) {
            val selectRef = new AtomicReference<String>(select);
            val argsRef = new AtomicReference<Object[]>(args);
            convertArrayParams(selectRef, argsRef);
            select = selectRef.get();
            args = argsRef.get();
        }
        return qryRunner.query(db, select, new MapListHandler(), args);
    }

    /**
     * Execute a SQL query and return the results using the provided ResultSetHandler. If the database does not support array
     * parameters and an array parameter is found in the arguments, the method will convert the array parameter into a format that
     * can be used in the SQL query (e.g., by replacing "= ANY(?)" with "IN (?, ?, ...)" and expanding the array into individual
     * parameters).
     * 
     * @param <T> the type of the object to return
     * @param select the SQL query
     * @param beanListHandler the ResultSetHandler to use for processing the result set
     * @param args the optional arguments for the prepared statement
     * 
     * @return the result of the query processed by the handler
     * 
     * @throws SQLException if a database access error occurs
     */
    public <T> T queryHandler(String select, ResultSetHandler<T> beanListHandler, Object... args) throws SQLException {
        if (!supportsArrayParams() && findArrayParam(args) != -1) {
            val selectRef = new AtomicReference<String>(select);
            val argsRef = new AtomicReference<Object[]>(args);
            convertArrayParams(selectRef, argsRef);
            select = selectRef.get();
            args = argsRef.get();
        }
        return qryRunner.query(db, select, beanListHandler, args);
    }

    /**
     * Execute a SQL SELECT query and return the first row as a map, where the keys are column names and values are the
     * corresponding column values. If the database does not support array parameters and an array parameter is found in the
     * arguments, the method will convert the array parameter into a format that can be used in the SQL query (e.g., by replacing "=
     * ANY(?)" with "IN (?, ?, ...)" and expanding the array into individual parameters).
     * 
     * @param select the SQL query
     * @param args the optional arguments for the prepared statement
     * 
     * @return a Map representing the first row, or null if no result
     * 
     * @throws SQLException if a database access error occurs
     */
    @Override
    public Map<String, Object> queryFirst(String select, Object... args) throws SQLException {
        if (!select.contains(" LIMIT "))
            select += " LIMIT 1";
        if (!supportsArrayParams() && findArrayParam(args) != -1) {
            val selectRef = new AtomicReference<String>(select);
            val argsRef = new AtomicReference<Object[]>(args);
            convertArrayParams(selectRef, argsRef);
            select = selectRef.get();
            args = argsRef.get();
        }
        return qryRunner.query(db, select, new MapHandler(), args);
    }

    /**
     * Execute a SQL UPDATE, INSERT, or DELETE statement. If the database does not support array parameters and an array parameter
     * is found in the arguments, the method will convert the array parameter into a format that can be used in the SQL query (e.g.,
     * by replacing "= ANY(?)" with "IN (?, ?, ...)" and expanding the array into individual parameters).
     * 
     * @param update the SQL statement to execute
     * @param args the optional arguments for the prepared statement
     * 
     * @return the number of rows affected by the update
     * 
     * @throws SQLException if a database access error occurs
     */
    @Override
    public int update(String update, Object... args) throws SQLException {
        if (!supportsArrayParams() && findArrayParam(args) != -1) {
            val updateRef = new AtomicReference<String>(update);
            val argsRef = new AtomicReference<Object[]>(args);
            convertArrayParams(updateRef, argsRef);
            update = updateRef.get();
            args = argsRef.get();
        }
        return qryRunner.update(db, update, args);
    }

    /**
     * Executes a SQL INSERT statement, inserting the specified map of column names and values into the specified table.
     * <p>
     * Table/schema/column names are validated SQL identifiers ({@link SQLUtils#requireSqlIdentifier}); values are bound as
     * prepared-statement parameters and never concatenated into the SQL text.
     *
     * @param table the name of the table to insert into
     * @param context the optional context used to resolve the fully qualified table name
     * @param toset the map containing the column names and their corresponding values to insert
     * 
     * @throws SQLException if a database access error occurs or query execution fails
     * @throws IllegalArgumentException if {@code toset} is null/empty or any identifier is unsafe
     */
    public void insert(String table, String context, final Map<String, Object> toset) throws SQLException {
        if (toset == null || toset.isEmpty())
            throw new IllegalArgumentException("insert requires at least one column");
        final var columns = new ArrayList<String>(toset.size());
        final var values = new Object[toset.size()];
        var i = 0;
        for (final var entry : toset.entrySet()) {
            columns.add(entry.getKey());
            values[i++] = entry.getValue();
        }
        // identifiers validated via getSQLTable/makeCols; values bound as ?
        qryRunner.update(db, "INSERT INTO " + getSQLTable(table, context) + " (" + makeCols(columns) + ") VALUES(" + appendParam(values.length) + ")",
                values);
    }

    /**
     * Find the position of the first array parameter in the given arguments. This method iterates through the arguments and checks
     * if any of them is an array. If an array parameter is found, its position (index) is returned. If no array parameter is found,
     * the method returns -1.
     * <p>
     * Note: The method checks if each argument is not null before checking if it is an array. This is to avoid a
     * NullPointerException when calling getClass() on a null argument. If an argument is null, it will simply be skipped in the
     * check for array parameters.
     * 
     * @param args the array of arguments to check for array parameters
     * 
     * @return the index of the first array parameter, or -1 if no array parameter is found
     * 
     * @throws NullPointerException if the args parameter is null
     */
    int findArrayParam(Object[] args) {
        int pos = -1;
        if (args != null)
            for (var i = 0; i < args.length; i++)
                if (args[i] != null && args[i].getClass().isArray())
                    pos = i;
        return pos;
    }

    /**
     * Convert array parameters in the SQL query and arguments into a format that can be used in the SQL query. This method checks
     * for array parameters in the arguments and, if found, it expands them into individual parameters and modifies the SQL query
     * accordingly (e.g., by replacing "= ANY(?)" with "IN (?, ?, ...)"). The method uses AtomicReference to allow modification of
     * the query and arguments within the method.
     * 
     * @param queryRef a reference to the SQL query string that may contain array parameters
     * @param argsRef a reference to the array of arguments that may contain array parameters
     */
    void convertArrayParams(AtomicReference<String> queryRef, AtomicReference<Object[]> argsRef) {
        Object[] args = argsRef.get();
        String query = queryRef.get();
        int pos;
        if (args != null)
            while (-1 != (pos = findArrayParam(args))) {
                final var arrlen = Array.getLength(args[pos]);
                final var newargs = new Object[args.length - 1 + arrlen];
                System.arraycopy(args, 0, newargs, 0, pos);
                for (var i = 0; i < arrlen; i++)
                    newargs[i + pos] = Array.get(args[pos], i);
                for (var i = pos + 1; i < args.length; i++)
                    newargs[i - 1 + arrlen] = args[i];
                query = query.replaceFirst("=\\s*?ANY\\(\\?\\)", " IN(" + appendParam(arrlen) + ")");
                args = newargs;
            }
        argsRef.set(args);
        queryRef.set(query);
    }

    /**
     * Drop a table if it exists. The method executes a SQL statement to drop the specified table, using the "DROP TABLE IF EXISTS"
     * syntax. This ensures that the method will not throw an error if the table does not exist, and it will safely drop the table
     * if it does exist. The method may throw a SQLException if there is an error executing the SQL statement to drop the table, so
     * it is important to handle this exception appropriately in your application to avoid issues with database operations.
     * 
     * @param table the name of the table to drop
     * 
     * @throws SQLException if there is an error executing the SQL statement to drop the table
     */
    @Override
    public void dropTable(final String table) throws SQLException {
        qryRunner.update(db, "DROP TABLE IF EXISTS " + backquote(table));
    }

    /**
     * Close the database connection if the shouldClose flag is set to true. This method checks the shouldClose flag and, if it is
     * true, it attempts to close the database connection. If the connection is already closed, it will simply log that it will
     * close the connection without throwing an exception. If there is an error while trying to close the connection, the method
     * will catch the SQLException and ignore it, as there may not be much that can be done in case of an error during closing.
     */
    @Override
    public void close() {
        if (shouldClose)
            try {
                if (!db.isClosed()) {
                    Log.info("will close " + db.getMetaData().getURL());
                    db.close();
                }
            } catch (SQLException _) {
                // ignore
            }
    }

    /**
     * Check if the database supports the "NULLS FIRST" syntax in ORDER BY clauses. This method retrieves the database product name
     * from the database metadata and checks if it is equal to "H2". If the database is H2, it returns true, indicating that "NULLS
     * FIRST" is supported. For other databases, it returns false, indicating that "NULLS FIRST" is not supported. It is important
     * to note that this method may throw a SQLException if there is an error accessing the database metadata, so it should be
     * handled appropriately in your application.
     * 
     * @return true if "NULLS FIRST" is supported, false otherwise
     * 
     * @throws SQLException if there is an error accessing the database metadata
     */
    public boolean supportsNullsFirst() throws SQLException {
        return db.getMetaData().getDatabaseProductName().equals("H2");
    }

    /**
     * Get the maximum length of an index in the database. This method retrieves the database metadata and calls the
     * getMaxIndexLength() method to obtain the maximum index length supported by the database. The returned value indicates the
     * maximum number of bytes that an index can occupy in the database. It is important to note that this method may throw a
     * SQLException if there is an error accessing the database metadata, so it should be handled appropriately in your application.
     * 
     * @return the maximum index length supported by the database
     * 
     * @throws SQLException if there is an error accessing the database metadata
     */
    public int getMaxIndexLength() throws SQLException {
        return db.getMetaData().getMaxIndexLength();
    }

    /**
     * Check if the database supports updating multiple tables in a single SQL statement. This method retrieves the database product
     * name from the database metadata and checks if it is equal to "H2". If the database is H2, it returns false, indicating that
     * updating multiple tables in a single statement is not supported. For other databases, it returns true, indicating that this
     * feature is supported. It is important to note that this method may throw a SQLException if there is an error accessing the
     * database metadata, so it should be handled appropriately in your application.
     * 
     * @return true if updating multiple tables in a single statement is supported, false otherwise
     * 
     * @throws SQLException if there is an error accessing the database metadata
     */
    public boolean supportsMultipleTablesUpdate() throws SQLException {
        return !db.getMetaData().getDatabaseProductName().equals("H2");
    }

    /**
     * Check if the database supports the REPLACE INTO syntax for inserting or updating records. This method retrieves the database
     * product name from the database metadata and checks if it is equal to "H2". If the database is H2, it returns false,
     * indicating that the REPLACE INTO syntax is not supported. For other databases, it returns true, indicating that this feature
     * is supported. It is important to note that this method may throw a SQLException if there is an error accessing the database
     * metadata, so it should be handled appropriately in your application.
     * 
     * @return true if the REPLACE INTO syntax is supported, false otherwise
     * 
     * @throws SQLException if there is an error accessing the database metadata
     */
    public boolean supportsReplace() throws SQLException {
        return !db.getMetaData().getDatabaseProductName().equals("H2");
    }

    /**
     * Check if the database supports the DUMP command for exporting data. This method retrieves the database product name from the
     * database metadata and checks if it is equal to "H2". If the database is H2, it returns true, indicating that the DUMP command
     * is supported. For other databases, it returns false, indicating that this feature is not supported. It is important to note
     * that this method may throw a SQLException if there is an error accessing the database metadata, so it should be handled
     * appropriately in your application.
     * 
     * @return true if the DUMP command is supported, false otherwise
     * 
     * @throws SQLException if there is an error accessing the database metadata
     */
    public boolean supportsDump() throws SQLException {
        return db.getMetaData().getDatabaseProductName().equals("H2");
    }

    /**
     * Check if the database supports array parameters in SQL queries. This method retrieves the database product name from the
     * database metadata and checks if it is equal to "H2". If the database is H2, it returns true, indicating that array parameters
     * are supported. For other databases, it returns false, indicating that this feature is not supported. It is important to note
     * that this method may throw a SQLException if there is an error accessing the database metadata, so it should be handled
     * appropriately in your application.
     * 
     * @return true if array parameters are supported, false otherwise
     * 
     * @throws SQLException if there is an error accessing the database metadata
     */
    public boolean supportsArrayParams() throws SQLException {
        return db.getMetaData().getDatabaseProductName().equals("H2");
    }

    /**
     * Check if the database supports the INSERT IGNORE syntax for inserting records while ignoring duplicates. This method
     * retrieves the database product name from the database metadata and checks if it is equal to "H2". If the database is H2, it
     * returns false, indicating that the INSERT IGNORE syntax is not supported. For other databases, it returns true, indicating
     * that this feature is supported. It is important to note that this method may throw a SQLException if there is an error
     * accessing the database metadata, so it should be handled appropriately in your application.
     * 
     * @return true if the INSERT IGNORE syntax is supported, false otherwise
     * 
     * @throws SQLException if there is an error accessing the database metadata
     */
    public boolean supportsInsertIgnore() throws SQLException {
        return !db.getMetaData().getDatabaseProductName().equals("H2");
    }
}
