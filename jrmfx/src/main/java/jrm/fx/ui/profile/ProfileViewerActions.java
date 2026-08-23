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
	private final TableView<AnywareList<? extends Anyware>> tableWL;
	private final TableView<Anyware> tableW;
	private final TableView<jrm.profile.data.EntityBase> tableEntity;
	private final Session session;

	private final MenuItem mntmAllAsMameDat;
	private final MenuItem mntmAllAsLogiqxDat;
	private final MenuItem mntmAllAsSoftwareLists;
	private final MenuItem mntmFilteredAsMameDat;
	private final MenuItem mntmFilteredAsLogiqxDat;
	private final MenuItem mntmFilteredAsSoftwareLists;
	private final MenuItem mntmSelectedAsSoftwareLists;
	private final MenuItem mntmSelectedFilteredAsSoftwareLists;

	private final MenuItem mntmCopyCrc;
	private final MenuItem mntmCopySha1;
	private final MenuItem mntmCopyName;
	private final MenuItem mntmSearchWeb;

	ProfileViewerActions(
			final TableView<AnywareList<? extends Anyware>> tableWL,
			final TableView<Anyware> tableW,
			final TableView<jrm.profile.data.EntityBase> tableEntity,
			final Session session,
			final MenuItem mntmAllAsMameDat,
			final MenuItem mntmAllAsLogiqxDat,
			final MenuItem mntmAllAsSoftwareLists,
			final MenuItem mntmFilteredAsMameDat,
			final MenuItem mntmFilteredAsLogiqxDat,
			final MenuItem mntmFilteredAsSoftwareLists,
			final MenuItem mntmSelectedAsSoftwareLists,
			final MenuItem mntmSelectedFilteredAsSoftwareLists,
			final MenuItem mntmCopyCrc,
			final MenuItem mntmCopySha1,
			final MenuItem mntmCopyName,
			final MenuItem mntmSearchWeb) {
		this.tableWL = tableWL;
		this.tableW = tableW;
		this.tableEntity = tableEntity;
		this.session = session;
		this.mntmAllAsMameDat = mntmAllAsMameDat;
		this.mntmAllAsLogiqxDat = mntmAllAsLogiqxDat;
		this.mntmAllAsSoftwareLists = mntmAllAsSoftwareLists;
		this.mntmFilteredAsMameDat = mntmFilteredAsMameDat;
		this.mntmFilteredAsLogiqxDat = mntmFilteredAsLogiqxDat;
		this.mntmFilteredAsSoftwareLists = mntmFilteredAsSoftwareLists;
		this.mntmSelectedAsSoftwareLists = mntmSelectedAsSoftwareLists;
		this.mntmSelectedFilteredAsSoftwareLists = mntmSelectedFilteredAsSoftwareLists;
		this.mntmCopyCrc = mntmCopyCrc;
		this.mntmCopySha1 = mntmCopySha1;
		this.mntmCopyName = mntmCopyName;
		this.mntmSearchWeb = mntmSearchWeb;
	}

	void refreshMenuItemAvailability() {
		final boolean has_machines = session.getCurrProfile().getMachineListList().getList().stream().mapToInt(ml -> ml.getList().size()).sum() > 0;
		final boolean has_filtered_machines = session.getCurrProfile().getMachineListList().getFilteredStream().mapToInt(m -> (int) m.countAll()).sum() > 0;
		final boolean has_selected_swlist = tableWL.getSelectionModel().getSelectedItems().size() == 1 && tableWL.getSelectionModel().getSelectedItem() instanceof SoftwareList;
		mntmAllAsMameDat.setDisable(!has_machines);
		mntmAllAsLogiqxDat.setDisable(!has_machines);
		mntmAllAsSoftwareLists.setDisable(session.getCurrProfile().getMachineListList().getSoftwareListList().isEmpty());
		mntmFilteredAsMameDat.setDisable(!has_filtered_machines);
		mntmFilteredAsLogiqxDat.setDisable(!has_filtered_machines);
		mntmFilteredAsSoftwareLists.setDisable(session.getCurrProfile().getMachineListList().getSoftwareListList().getFilteredStream().count() == 0);
		mntmSelectedAsSoftwareLists.setDisable(!has_selected_swlist);
		mntmSelectedFilteredAsSoftwareLists.setDisable(!has_selected_swlist);
	}

	void updateEMenuItemStates() {
		final boolean has_selected_entity = tableEntity.getSelectionModel().getSelectedItem() != null;
		mntmCopyCrc.setDisable(!has_selected_entity);
		mntmCopySha1.setDisable(!has_selected_entity);
		mntmCopyName.setDisable(!has_selected_entity);
		mntmSearchWeb.setDisable(!has_selected_entity);
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
