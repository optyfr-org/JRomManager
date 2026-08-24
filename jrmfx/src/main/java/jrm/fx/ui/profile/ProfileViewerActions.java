package jrm.fx.ui.profile;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import jrm.fx.ui.MainFrame;
import jrm.fx.ui.profile.filter.Keywords;
import jrm.misc.Log;
import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareList;
import jrm.profile.data.Entity;
import jrm.profile.data.ExportMode;
import jrm.profile.data.SoftwareList;
import jrm.profile.manager.Export.ExportType;
import jrm.security.Session;

final class ProfileViewerActions {

	record ExportMenuItems(
			MenuItem allAsMameDat,
			MenuItem allAsLogiqxDat,
			MenuItem allAsSoftwareLists,
			MenuItem filteredAsMameDat,
			MenuItem filteredAsLogiqxDat,
			MenuItem filteredAsSoftwareLists,
			MenuItem selectedAsSoftwareLists,
			MenuItem selectedFilteredAsSoftwareLists) {
	}

	record EntityMenuItems(
			MenuItem copyCrc,
			MenuItem copySha1,
			MenuItem copyName,
			MenuItem searchWeb) {
	}

	private final TableView<AnywareList<? extends Anyware>> tableWL;
	private final TableView<Anyware> tableW;
	private final TableView<jrm.profile.data.EntityBase> tableEntity;
	private final Session session;

	private final ExportMenuItems exportMenuItems;
	private final EntityMenuItems entityMenuItems;

	ProfileViewerActions(
			final TableView<AnywareList<? extends Anyware>> tableWL,
			final TableView<Anyware> tableW,
			final TableView<jrm.profile.data.EntityBase> tableEntity,
			final Session session,
			final ExportMenuItems exportMenuItems,
			final EntityMenuItems entityMenuItems) {
		this.tableWL = tableWL;
		this.tableW = tableW;
		this.tableEntity = tableEntity;
		this.session = session;
		this.exportMenuItems = exportMenuItems;
		this.entityMenuItems = entityMenuItems;
	}

	void refreshMenuItemAvailability() {
		final boolean has_machines = session.getCurrProfile().getMachineListList().getList().stream().mapToInt(ml -> ml.getList().size()).sum() > 0;
		final boolean has_filtered_machines = session.getCurrProfile().getMachineListList().getFilteredStream().mapToInt(m -> (int) m.countAll()).sum() > 0;
		final boolean has_selected_swlist = tableWL.getSelectionModel().getSelectedItems().size() == 1 && tableWL.getSelectionModel().getSelectedItem() instanceof SoftwareList;
		exportMenuItems.allAsMameDat().setDisable(!has_machines);
		exportMenuItems.allAsLogiqxDat().setDisable(!has_machines);
		exportMenuItems.allAsSoftwareLists().setDisable(session.getCurrProfile().getMachineListList().getSoftwareListList().isEmpty());
		exportMenuItems.filteredAsMameDat().setDisable(!has_filtered_machines);
		exportMenuItems.filteredAsLogiqxDat().setDisable(!has_filtered_machines);
		exportMenuItems.filteredAsSoftwareLists().setDisable(session.getCurrProfile().getMachineListList().getSoftwareListList().getFilteredStream().count() == 0);
		exportMenuItems.selectedAsSoftwareLists().setDisable(!has_selected_swlist);
		exportMenuItems.selectedFilteredAsSoftwareLists().setDisable(!has_selected_swlist);
	}

	void updateEMenuItemStates() {
		final boolean has_selected_entity = tableEntity.getSelectionModel().getSelectedItem() != null;
		entityMenuItems.copyCrc().setDisable(!has_selected_entity);
		entityMenuItems.copySha1().setDisable(!has_selected_entity);
		entityMenuItems.copyName().setDisable(!has_selected_entity);
		entityMenuItems.searchWeb().setDisable(!has_selected_entity);
	}

	void copyCrc() {
		if (tableEntity.getSelectionModel().getSelectedItem() != null && tableEntity.getSelectionModel().getSelectedItem() instanceof final Entity entity) {
			final var content = new ClipboardContent();
			content.putString(entity.getCrc());
			Clipboard.getSystemClipboard().setContent(content);
		}
	}

	void copySha1() {
		if (tableEntity.getSelectionModel().getSelectedItem() != null && tableEntity.getSelectionModel().getSelectedItem() instanceof final Entity entity) {
			final var content = new ClipboardContent();
			content.putString(entity.getSha1());
			Clipboard.getSystemClipboard().setContent(content);
		}
	}

	void copyName() {
		if (tableEntity.getSelectionModel().getSelectedItem() != null && tableEntity.getSelectionModel().getSelectedItem() instanceof final Entity entity) {
			final var content = new ClipboardContent();
			content.putString(entity.getName());
			Clipboard.getSystemClipboard().setContent(content);
		}
	}

	void searchWeb() {
		if (tableEntity.getSelectionModel().getSelectedItem() != null && tableEntity.getSelectionModel().getSelectedItem() instanceof final Entity entity) {
			try {
				final var name = entity.getName();
				final var crc = entity.getCrc();
				final var sha1 = entity.getSha1();
				final var hash = Optional.ofNullable(Optional.ofNullable(crc).orElse(sha1)).map(h -> '+' + h).orElse("");
				MainFrame.getApplication().getHostServices()
						.showDocument(new URI("https://www.google.com/search?q=" + URLEncoder.encode('"' + name + '"', "UTF-8") + hash).toString());
			} catch (IOException | URISyntaxException e1) {
				Log.err(e1.getMessage(), e1);
			}
		}
	}

	void selectNone() {
		tableW.getItems().forEach(ware -> ware.setSelected(false));
		tableW.refresh();
	}

	void selectAll() {
		tableW.getItems().forEach(ware -> ware.setSelected(true));
		tableW.refresh();
	}

	void selectInvert() {
		tableW.getItems().forEach(ware -> ware.setSelected(!ware.isSelected()));
		tableW.refresh();
	}

	void selectByKeywords() {
		final var lst = tableWL.getSelectionModel().getSelectedItem();
		new KW().filter(lst);
	}

	void exportFilteredAsLogiqxDat() {
		export(ExportType.DATAFILE, EnumSet.of(ExportMode.FILTERED), null);
	}

	void exportFilteredAsMameDat() {
		export(ExportType.MAME, EnumSet.of(ExportMode.FILTERED), null);
	}

	void exportFilteredAsSoftwareLists() {
		export(ExportType.SOFTWARELIST, EnumSet.of(ExportMode.FILTERED), null);
	}

	void exportAllAsLogiqxDat() {
		export(ExportType.DATAFILE, EnumSet.of(ExportMode.ALL), null);
	}

	void exportAllAsMameDat() {
		export(ExportType.MAME, EnumSet.of(ExportMode.ALL), null);
	}

	void exportAllAsSoftwareLists() {
		export(ExportType.SOFTWARELIST, EnumSet.of(ExportMode.ALL), null);
	}

	void exportSelectedFilteredAsSoftwareLists() {
		if (tableWL.getSelectionModel().getSelectedItem() instanceof final SoftwareList sl)
			export(ExportType.SOFTWARELIST, EnumSet.of(ExportMode.FILTERED), sl);
	}

	void exportSelectedAsSoftwareLists() {
		if (tableWL.getSelectionModel().getSelectedItem() instanceof final SoftwareList sl)
			export(ExportType.SOFTWARELIST, EnumSet.of(ExportMode.ALL), sl);
	}

	private void export(final ExportType type, final Set<ExportMode> modes, final SoftwareList selection) {
		MainFrame.export(tableWL.getScene().getWindow(), session, type, modes, selection);
	}

	private class KW extends jrm.profile.filter.Keywords {
		@Override
		protected void showFilter(final String[] keywords, final KFCallBack callback) {
			try {
				new Keywords((ProfileViewer) tableWL.getScene().getWindow(), keywords, tableWL.getSelectionModel().getSelectedItem(), callback);
			} catch (URISyntaxException | IOException e1) {
				Log.err(e1.getMessage(), e1);
			}
		}

		@Override
		protected void updateList() {
			tableW.refresh();
		}
	}
}
