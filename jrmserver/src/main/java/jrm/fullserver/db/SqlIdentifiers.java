package jrm.fullserver.db;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Set;

public interface SqlIdentifiers {
	default String requireSqlIdentifier(String name) {
		if (name == null)
			return null;
		if (name.isBlank())
			throw new IllegalArgumentException("SQL identifier must not be blank");
		final int len = name.length();
		for (var i = 0; i < len; i++) {
			final char c = name.charAt(i);
			if (c == '_' || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))
				continue;
			throw new IllegalArgumentException("Invalid SQL identifier: " + name);
		}
		if (name.charAt(0) >= '0' && name.charAt(0) <= '9')
			throw new IllegalArgumentException("Invalid SQL identifier: " + name);
		return name;
	}

	default String backquote(String name) {
		name = requireSqlIdentifier(name);
		if (name == null)
			return null;
		return "`" + name + "`";
	}

	default String requireSelectStatement(String select) {
		if (select == null || select.isBlank())
			throw new IllegalArgumentException("SQL select must not be blank");
		final String trimmed = select.strip();
		if (trimmed.indexOf(';') >= 0)
			throw new IllegalArgumentException("SQL select must be a single statement");
		final int len = trimmed.length();
		var i = 0;
		if (len >= 2 && trimmed.charAt(0) == '(') {
			while (i < len && (trimmed.charAt(i) == '(' || Character.isWhitespace(trimmed.charAt(i))))
				i++;
		}
		if (len - i < 6 || !trimmed.regionMatches(true, i, "SELECT", 0, 6))
			throw new IllegalArgumentException("SQL select must start with SELECT");
		final char after = i + 6 < len ? trimmed.charAt(i + 6) : ' ';
		if (!Character.isWhitespace(after) && after != '(' && after != '*')
			throw new IllegalArgumentException("SQL select must start with SELECT");
		return trimmed;
	}

	default void append(StringBuilder str, final String separator, CharSequence toAppend) {
		if (!toAppend.isEmpty()) {
			if (!str.isEmpty())
				str.append(separator);
			str.append(toAppend);
		}
	}

	default void appendComma(StringBuilder str, CharSequence toAppend) {
		if (!str.isEmpty())
			str.append(", ");
		str.append(toAppend);
	}

	default void prependComma(StringBuilder str, CharSequence toPrepend) {
		if (!str.isEmpty())
			str.insert(0, ", ");
		str.insert(0, toPrepend);
	}

	default CharSequence appendParam(StringBuilder str, int count) {
		if (str == null)
			str = new StringBuilder();
		for (var i = 0; i < count; i++)
			appendComma(str, "?");
		return str;
	}

	default CharSequence appendParam(int count) {
		return appendParam(null, count);
	}

	default CharSequence makeCols(Collection<String> cols, boolean withParenthesis) {
		final var set = new StringBuilder();
		cols.forEach(col -> appendComma(set, backquote(col)));
		if (withParenthesis && cols.size() > 1)
			set.insert(0, '(').append(')');
		return set;
	}

	default CharSequence makeCols(Iterable<String> cols) {
		final var set = new StringBuilder();
		for (final var col : cols)
			appendComma(set, backquote(col));
		return set;
	}

	default CharSequence makeCols(Collection<String> cols) {
		return makeCols(cols, false);
	}

	default CharSequence makeSet(Collection<String> cols) {
		final var set = new StringBuilder();
		cols.forEach(col -> appendComma(set, backquote(col) + "=?"));
		return set;
	}

	default CharSequence makeSet(LinkedHashMap<String, Object> map) {
		final var set = new StringBuilder();
		map.forEach((col, _) -> appendComma(set, backquote(col) + "=?"));
		return set;
	}

	default CharSequence makeSet(Set<Entry<String, Object>> map) {
		final var set = new StringBuilder();
		map.forEach(entry -> appendComma(set, backquote(entry.getKey()) + "=?"));
		return set;
	}
}
