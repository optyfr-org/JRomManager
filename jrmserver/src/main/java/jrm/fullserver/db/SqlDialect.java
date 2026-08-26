package jrm.fullserver.db;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

final class SqlDialect {
	private static final Pattern ANY_PARAM = Pattern.compile("=\\s*?ANY\\(\\?\\)");

	private final Connection db;

	SqlDialect(Connection db) {
		this.db = db;
	}

	boolean supportsNullsFirst() throws SQLException {
		return db.getMetaData().getDatabaseProductName().equals("H2");
	}

	boolean supportsMultipleTablesUpdate() throws SQLException {
		return !db.getMetaData().getDatabaseProductName().equals("H2");
	}

	boolean supportsReplace() throws SQLException {
		return !db.getMetaData().getDatabaseProductName().equals("H2");
	}

	boolean supportsDump() throws SQLException {
		return db.getMetaData().getDatabaseProductName().equals("H2");
	}

	boolean supportsArrayParams() throws SQLException {
		return db.getMetaData().getDatabaseProductName().equals("H2");
	}

	boolean supportsInsertIgnore() throws SQLException {
		return !db.getMetaData().getDatabaseProductName().equals("H2");
	}

	int findArrayParam(Object[] args) {
		if (args != null)
			for (var i = 0; i < args.length; i++)
				if (args[i] != null && args[i].getClass().isArray())
					return i;
		return -1;
	}

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
				query = ANY_PARAM.matcher(query).replaceFirst(" IN(" + appendParam(arrlen) + ")");
				args = newargs;
			}
		argsRef.set(args);
		queryRef.set(query);
	}

	private CharSequence appendParam(int count) {
		final var sb = new StringBuilder();
		for (var i = 0; i < count; i++) {
			if (i > 0)
				sb.append(", ");
			sb.append("?");
		}
		return sb;
	}
}
