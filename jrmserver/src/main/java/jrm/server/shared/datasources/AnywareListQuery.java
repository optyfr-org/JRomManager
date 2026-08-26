package jrm.server.shared.datasources;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareList;
import jrm.server.shared.datasources.XMLRequest.Operation;

final class AnywareListQuery<T extends Anyware> {
	private final AnywareList<T> list;
	private final Operation operation;

	AnywareListQuery(AnywareList<T> list, Operation operation) {
		this.list = list;
		this.operation = operation;
	}

	Predicate<T> getFilter() {
		// simplified, actual impl would parse from operation
		return a -> true;
	}

	void filter() { /* delegate impl */ }

	Comparator<T> getSorter() {
		return (a, b) -> 0;
	}

	Stream<T> buildStream() {
		return list.getList().stream().filter(getFilter());
	}

	List<T> buildList() {
		return buildStream().toList();
	}

	T find(String name) {
		return null;
	}

	void selectAll() { /* delegate impl */ }
	void selectNone() { /* delegate impl */}
	void selectInvert() { /* delegate impl */}
}
