package jrm.fx.ui.profile;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javafx.scene.control.TableView;
import jrm.locale.Messages;
import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareList;
import jrm.profile.data.AnywareStatus;
import jrm.profile.data.Machine;
import jrm.profile.data.MachineList;
import jrm.profile.data.SoftwareList;

/**
 * Helper for resolving clone-of, rom-of, sample-of relations for the profile viewer tables.
 * Extracted to reduce controller size.
 */
final class ProfileViewerWareRelations {

	private final TableView<AnywareList<? extends Anyware>> tableWL;

	ProfileViewerWareRelations(final TableView<AnywareList<? extends Anyware>> tableWL) {
		this.tableWL = tableWL;
	}

	Object getCloneOfValue(final Anyware ware) {
		final AnywareList<? extends Anyware> machineList = tableWL.getSelectionModel().getSelectedItem();
		return Optional.ofNullable(ware.getCloneof()).map(cloneof -> machineList.containsName(cloneof) ? machineList.getByName(cloneof) : cloneof).orElse(null);
	}

	Object getSampleOfValue(final Object value) {
		if (!(value instanceof final Machine m)) {
			return null;
		}
		final AnywareList<? extends Anyware> awList = tableWL.getSelectionModel().getSelectedItem();
		if (!(awList instanceof final MachineList machineList)) {
			return null;
		}
		return Optional.ofNullable(m.getSampleof())
				.map(sampleof -> machineList.samplesets.containsName(sampleof) ? machineList.samplesets.getByName(sampleof) : sampleof).orElse(null);
	}

	private final Map<String, String> haveCache = new HashMap<>();

	String getWLHave(final AnywareList<? extends Anyware> list) {
		return haveCache.computeIfAbsent(list.getName(), _ -> {
			final long[] ht = { 0, 0 };
			list.getFilteredStream().forEach(t -> {
				if (t.getStatus() == AnywareStatus.COMPLETE)
					ht[0]++;
				ht[1]++;
			});
			return String.format("%d/%d", ht[0], ht[1]);
		});
	}

	static String getWLDescription(final AnywareList<? extends Anyware> list) {
		if (list instanceof final SoftwareList sl)
			return sl.getDescription().toString();
		return Messages.getString("MachineListList.AllMachines");
	}

	void clearHaveCache() {
		haveCache.clear();
	}
}
