package jrm.fx.ui;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.FileUtils;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import jrm.fx.ui.controls.Dialogs;
import jrm.fx.ui.profile.manager.DirItem;
import jrm.fx.ui.progress.ProgressTask;
import jrm.locale.Messages;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.profile.manager.DatFileSearch;
import jrm.profile.manager.Import;
import jrm.profile.manager.ProfileNFO;
import jrm.security.Session;

final class ProfileImportTasks {

	private final ProfilePanelController controller;

	ProfileImportTasks(ProfilePanelController controller) {
		this.controller = controller;
	}

	ImportDatTask newImportDatTask(Stage owner, List<File> files, boolean sl) throws IOException, URISyntaxException {
		return new ImportDatTask(owner, files, sl);
	}

	UpdateFromMameTask newUpdateFromMameTask(Stage owner, ProfileNFO nfo) throws IOException, URISyntaxException {
		return new UpdateFromMameTask(owner, nfo);
	}

	final class ImportDatTask extends ProgressTask<Void> {
		private final List<File> files;
		private final boolean sl;
		final List<ImportWithBaseFile> imprts = new ArrayList<>();

		ImportDatTask(Stage owner, List<File> files, boolean sl) throws IOException, URISyntaxException {
			super(owner);
			this.files = files;
			this.sl = sl;
		}

		@Override
		protected Void call() throws Exception {
			for (final var basefile : files) {
				for (final var file : DatFileSearch.searchDats(basefile, new ArrayList<>())) {
					setProgress(Messages.getString("MainFrame.ImportingFromMame"), -1);
					imprts.add(new ImportWithBaseFile(new Import(controller.session, file, sl, this), basefile));
				}
			}
			return null;
		}

		@Override
		protected void succeeded() {
			this.close();
			var rejected = 0;
			for (final var imprt : imprts) {
				if (imprt.imprt.getFile() == null) {
					rejected++;
					continue;
				}
				try {
					doImportDat(imprt, sl);
				} catch (IOException e) {
					Log.err(e.getMessage(), e);
				}
			}
			if (rejected > 0) {
				Dialogs.showAlert(rejected == 1
					? "Could not import 1 selected file. Only DAT/XML files and native MAME/MESS executables are accepted."
					: "Could not import " + rejected + " selected files. Only DAT/XML files and native MAME/MESS executables are accepted.");
			}
			final var theNode = controller.profilesTree.getSelectionModel().getSelectedItem();
			if (theNode instanceof DirItem d) {
				d.reload();
				controller.populate(d);
			} else {
				Log.err(Messages.getString("MainFrame.NodeNotFound"));
			}
		}

		@Override
		protected void failed() {
			if (getException() instanceof BreakException) {
				Dialogs.showAlert("Cancelled");
			} else {
				this.close();
				Optional.ofNullable(getException().getCause()).ifPresentOrElse(cause -> {
					Log.err(cause.getMessage(), cause);
					Dialogs.showError(cause);
				}, () -> {
					Log.err(getException().getMessage(), getException());
					Dialogs.showError(getException());
				});
			}
		}

		private void doImportDat(final ImportWithBaseFile imprt, final boolean sl) throws IllegalArgumentException, IOException {
			final var selDir = controller.profilesTree.getSelectionModel().getSelectedItem().getValue().getFile().toPath();
			final var currDir = selDir.resolve(imprt.basefile.toPath().getParent().relativize(imprt.imprt.getOrgFile().toPath().getParent())).toFile();
			Files.createDirectories(currDir.toPath());

			if (!imprt.imprt.isMame()) {
				var fileRef = new AtomicReference<File>(new File(currDir, imprt.imprt.getFile().getName()));
				int mode = controller.importDatExistsChoose(fileRef);  // delegate UI choice back to controller for now
				if (mode == 3) return;
				if (!fileRef.get().exists() || mode == 0) {
					try {
						FileUtils.copyFile(imprt.imprt.getFile(), fileRef.get());
					} catch (IOException e) {
						Log.err(e.getMessage(), e);
					}
				}
			} else {
				final var layout = new VBox();
				layout.setPrefWidth(300);
				final var label = new Label("Choose a name to save JRM file for import of " + imprt.imprt.getOrgFile());
				label.setWrapText(true);
				layout.getChildren().add(label);
				final var nameField = new TextField(imprt.imprt.getFile().getName());
				layout.getChildren().add(nameField);
				final var result = Dialogs.showConfirmation("Choose a name to save JRM file", layout, ButtonType.APPLY);
				final var fileName = result.filter(t -> t == ButtonType.APPLY)
					.map(_ -> nameField.getText())
					.filter(t -> !t.isBlank())
					.map(t -> t.endsWith(".jrm") ? t : (t + ".jrm"))
					.orElse(imprt.imprt.getFile().getName());
				ProfileImportTasks.this.doImportDat(controller.session, sl, imprt.imprt, currDir.toPath().resolve(fileName).toFile());
			}
		}
	}

	final class UpdateFromMameTask extends ProgressTask<Import> {
		private final ProfileNFO nfo;

		UpdateFromMameTask(Stage owner, ProfileNFO nfo) throws IOException, URISyntaxException {
			super(owner);
			this.nfo = nfo;
		}

		@Override
		protected Import call() throws Exception {
			return new Import(controller.session, nfo.getMame().getFile(), nfo.getMame().isSL(), this);
		}

		@Override
		protected void succeeded() {
			try {
				this.close();
				doUpdateFromMame(controller.session, nfo, get());
			} catch (InterruptedException e) {
				Log.err(e.getMessage(), e);
				Thread.currentThread().interrupt();
			} catch (ExecutionException | IOException e) {
				Log.err(e.getMessage(), e);
				Dialogs.showError(e);
			}
		}

		@Override
		protected void failed() {
			if (getException() instanceof BreakException) {
				Dialogs.showAlert("Cancelled");
			} else {
				this.close();
				Optional.ofNullable(getException().getCause()).ifPresentOrElse(cause -> {
					Log.err(cause.getMessage(), cause);
					Dialogs.showError(cause);
				}, () -> {
					Log.err(getException().getMessage(), getException());
					Dialogs.showError(getException());
				});
			}
		}

		private void doUpdateFromMame(final Session session, final ProfileNFO nfo, Import imprt) throws IOException {
			if (imprt == null || !imprt.canApplyMameUpdate(nfo.getMame().isSL())) {
				Dialogs.showAlert("Could not update from MAME. The executable is missing, is not a native MAME/MESS binary, or did not return listxml data.");
				return;
			}
			nfo.getMame().deleteAlongside(nfo.getFile());
			nfo.getMame().setFileroms(new File(nfo.getFile().getParentFile(), imprt.getRomsFile().getName()));
			Files.copy(imprt.getRomsFile().toPath(), nfo.getMame().getFileroms().toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
			if (nfo.getMame().isSL()) {
				nfo.getMame().setFilesl(new File(nfo.getFile().getParentFile(), imprt.getSlFile().getName()));
				Files.copy(imprt.getSlFile().toPath(), nfo.getMame().getFilesl().toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
			}
			nfo.getMame().setUpdated();
			nfo.getStats().reset();
			nfo.save(session);
			controller.profilesList.refresh();
		}
	}

	@lombok.AllArgsConstructor
	static final class ImportWithBaseFile {
		Import imprt;
		File basefile;
	}

	// Helpers that the tasks need (some UI choice delegated back to controller for minimal change)
	void doImportDat(final Session session, final boolean sl, final Import imprt, final File target) {
		controller.performImportDat(session, sl, imprt, target);
	}
}
