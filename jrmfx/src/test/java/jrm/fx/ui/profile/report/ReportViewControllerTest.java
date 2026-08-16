package jrm.fx.ui.profile.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;
import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxRecordedStage;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import jrm.profile.report.Report;
import jrm.profile.report.SubjectSet;

/**
 * Tests for {@link ReportViewController}.
 *
 * @since 3.0.5
 */
@TestFxApplication(ReportViewControllerTest.TestApp.class)
@DisplayName("ReportViewController Tests")
class ReportViewControllerTest {

	/**
	 * Test application.
	 */
	public static class TestApp extends Application implements TestFxRecordedStage {
		private Stage primaryStage;
		static ReportViewController controller;

		@Override
		public Stage recordedStage() {
			return primaryStage;
		}

		@Override
		public void start(Stage primaryStage) throws Exception {
			this.primaryStage = primaryStage;

			controller = new ReportViewController();

			// Inject FXML fields via reflection
			TreeView<Object> treeview = new TreeView<>();
			ContextMenu menu = new ContextMenu();
			MenuItem openAllNodes = new MenuItem();
			MenuItem closeAllNodes = new MenuItem();
			CheckMenuItem showok = new CheckMenuItem();
			CheckMenuItem hidemissing = new CheckMenuItem();
			MenuItem detail = new MenuItem();
			MenuItem copyCrc = new MenuItem();
			MenuItem copySha1 = new MenuItem();
			MenuItem copyName = new MenuItem();
			MenuItem searchWeb = new MenuItem();
			Button download = new Button();
			Button copyReport = new Button();
			Button createFixDat = new Button();
			MenuButton exportAs = new MenuButton();
			javafx.scene.control.TextArea summary = new javafx.scene.control.TextArea();

			injectField(controller, "treeview", treeview);
			injectField(controller, "menu", menu);
			injectField(controller, "openAllNodes", openAllNodes);
			injectField(controller, "closeAllNodes", closeAllNodes);
			injectField(controller, "showok", showok);
			injectField(controller, "hidemissing", hidemissing);
			injectField(controller, "detail", detail);
			injectField(controller, "copyCrc", copyCrc);
			injectField(controller, "copySha1", copySha1);
			injectField(controller, "copyName", copyName);
			injectField(controller, "searchWeb", searchWeb);
			injectField(controller, "download", download);
			injectField(controller, "copyReport", copyReport);
			injectField(controller, "createFixDat", createFixDat);
			injectField(controller, "exportAs", exportAs);
			injectField(controller, "summary", summary);

			controller.initialize(null, null);

			VBox root = new VBox();
			Scene scene = new Scene(root, 800, 600);
			primaryStage.setScene(scene);
			primaryStage.show();
		}

		private static void injectField(Object target, String fieldName, Object value) {
			try {
				var field = ReportViewController.class.getDeclaredField(fieldName);
				field.setAccessible(true);
				field.set(target, value);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException("Failed to inject field " + fieldName, e);
			}
		}

		/**
		 * Get controller.
		 *
		 * @return controller
		 */
		public static ReportViewController getController() {
			return controller;
		}
	}

	/**
	 * Verifies that the controller is properly initialized by {@link TestApp}.
	 */
	@Test
	@DisplayName("Should initialize controller")
	void shouldInitializeController() {
		ReportViewController controller = TestApp.getController();
		assertThat(controller).isNotNull();
	}

	/**
	 * Verifies that setting a {@code null} report produces an empty root node.
	 */
	@Test
	@DisplayName("Should set null report")
	void shouldSetNullReport() {
		ReportViewController controller = TestApp.getController();
		controller.setReport(null);
		TreeView<Object> treeview = getField(controller, "treeview");
		assertThat(treeview.getRoot()).isNotNull();
		assertThat(treeview.getRoot().getValue()).isNull();
	}

	/**
	 * Verifies that setting an empty report produces a root node with the mock report.
	 */
	@Test
	@DisplayName("Should set empty report")
	void shouldSetEmptyReport() {
		ReportViewController controller = TestApp.getController();
		Report mockReport = mock(Report.class);
		when(mockReport.stream(java.util.Collections.emptySet())).thenReturn(java.util.stream.Stream.empty());
		controller.setReport(mockReport);
		TreeView<Object> treeview = getField(controller, "treeview");
		assertThat(treeview.getRoot()).isNotNull();
		assertThat(treeview.getRoot().getValue()).isEqualTo(mockReport);
	}

	/**
	 * Verifies that setting a report with a single {@link SubjectSet} creates one child node.
	 */
	@Test
	@DisplayName("Should set report with data")
	void shouldSetReportWithData() {
		ReportViewController controller = TestApp.getController();
		Report mockReport = mock(Report.class);
		SubjectSet mockSubjectSet = mock(SubjectSet.class);
		when(mockReport.stream(java.util.Collections.emptySet())).thenReturn(java.util.stream.Stream.of(mockSubjectSet));
		when(mockSubjectSet.stream(java.util.Collections.emptySet())).thenReturn(java.util.stream.Stream.empty());
		controller.setReport(mockReport);
		TreeView<Object> treeview = getField(controller, "treeview");
		assertThat(treeview.getRoot()).isNotNull();
		assertThat(treeview.getRoot().getChildren()).hasSize(1);
	}

	/**
	 * Verifies that calling {@code detail} with no tree selection does not throw.
	 *
	 * @throws Exception if reflective method access fails
	 */
	@Test
	@DisplayName("Should handle detail with no selection")
	void shouldHandleDetailWithNoSelection() throws Exception {
		ReportViewController controller = TestApp.getController();
		TreeView<Object> treeview = getField(controller, "treeview");
		treeview.getSelectionModel().clearSelection();
		
		Method detailMethod = ReportViewController.class.getDeclaredMethod("detail", javafx.event.ActionEvent.class);
		detailMethod.setAccessible(true);
		detailMethod.invoke(controller, (Object) null);
		assertThat(treeview.getSelectionModel().getSelectedItem()).isNull();
	}

	/**
	 * Verifies that calling {@code copyCrc} with no tree selection does not throw.
	 *
	 * @throws Exception if reflective method access fails
	 */
	@Test
	@DisplayName("Should handle copyCrc with no selection")
	void shouldHandleCopyCrcWithNoSelection() throws Exception {
		ReportViewController controller = TestApp.getController();
		TreeView<Object> treeview = getField(controller, "treeview");
		treeview.getSelectionModel().clearSelection();
		
		Method copyCrcMethod = ReportViewController.class.getDeclaredMethod("copyCrc", javafx.event.ActionEvent.class);
		copyCrcMethod.setAccessible(true);
		copyCrcMethod.invoke(controller, (Object) null);
		assertThat(treeview.getSelectionModel().getSelectedItem()).isNull();
	}

	/**
	 * Verifies that calling {@code copySha1} with no tree selection does not throw.
	 *
	 * @throws Exception if reflective method access fails
	 */
	@Test
	@DisplayName("Should handle copySha1 with no selection")
	void shouldHandleCopySha1WithNoSelection() throws Exception {
		ReportViewController controller = TestApp.getController();
		TreeView<Object> treeview = getField(controller, "treeview");
		treeview.getSelectionModel().clearSelection();
		
		Method copySha1Method = ReportViewController.class.getDeclaredMethod("copySha1", javafx.event.ActionEvent.class);
		copySha1Method.setAccessible(true);
		copySha1Method.invoke(controller, (Object) null);
		assertThat(treeview.getSelectionModel().getSelectedItem()).isNull();
	}

	/**
	 * Verifies that calling {@code copyName} with no tree selection does not throw.
	 *
	 * @throws Exception if reflective method access fails
	 */
	@Test
	@DisplayName("Should handle copyName with no selection")
	void shouldHandleCopyNameWithNoSelection() throws Exception {
		ReportViewController controller = TestApp.getController();
		TreeView<Object> treeview = getField(controller, "treeview");
		treeview.getSelectionModel().clearSelection();
		
		Method copyNameMethod = ReportViewController.class.getDeclaredMethod("copyName", javafx.event.ActionEvent.class);
		copyNameMethod.setAccessible(true);
		copyNameMethod.invoke(controller, (Object) null);
		assertThat(treeview.getSelectionModel().getSelectedItem()).isNull();
	}

	/**
	 * Verifies that calling {@code searchWeb} with no tree selection does not throw.
	 *
	 * @throws Exception if reflective method access fails
	 */
	@Test
	@DisplayName("Should handle searchWeb with no selection")
	void shouldHandleSearchWebWithNoSelection() throws Exception {
		ReportViewController controller = TestApp.getController();
		TreeView<Object> treeview = getField(controller, "treeview");
		treeview.getSelectionModel().clearSelection();
		
		Method searchWebMethod = ReportViewController.class.getDeclaredMethod("searchWeb", javafx.event.ActionEvent.class);
		searchWebMethod.setAccessible(true);
		searchWebMethod.invoke(controller, (Object) null);
		assertThat(treeview.getSelectionModel().getSelectedItem()).isNull();
	}

	/**
	 * Verifies that calling {@code openAllNodes} on an empty tree does not throw.
	 *
	 * @throws Exception if reflective method access fails
	 */
	@Test
	@DisplayName("Should handle openAllNodes with empty tree")
	void shouldHandleOpenAllNodesWithEmptyTree() throws Exception {
		ReportViewController controller = TestApp.getController();
		controller.setReport(null);
		TreeView<Object> treeview = getField(controller, "treeview");
		
		Method openAllNodesMethod = ReportViewController.class.getDeclaredMethod("openAllNodes", javafx.event.ActionEvent.class);
		openAllNodesMethod.setAccessible(true);
		openAllNodesMethod.invoke(controller, (Object) null);
		assertThat(treeview.getRoot()).isNotNull();
	}

	/**
	 * Verifies that calling {@code closeAllNodes} on an empty tree does not throw.
	 *
	 * @throws Exception if reflective method access fails
	 */
	@Test
	@DisplayName("Should handle closeAllNodes with empty tree")
	void shouldHandleCloseAllNodesWithEmptyTree() throws Exception {
		ReportViewController controller = TestApp.getController();
		controller.setReport(null);
		TreeView<Object> treeview = getField(controller, "treeview");
		
		Method closeAllNodesMethod = ReportViewController.class.getDeclaredMethod("closeAllNodes", javafx.event.ActionEvent.class);
		closeAllNodesMethod.setAccessible(true);
		closeAllNodesMethod.invoke(controller, (Object) null);
		assertThat(treeview.getRoot()).isNotNull();
	}

	/**
	 * Verifies that calling {@code download} with a {@code null} report does not throw.
	 *
	 * @throws Exception if reflective method access fails
	 */
	@Test
	@DisplayName("Should handle download with null report")
	void shouldHandleDownloadWithNullReport() throws Exception {
		ReportViewController controller = TestApp.getController();
		controller.setReport(null);
		TreeView<Object> treeview = getField(controller, "treeview");
		
		Method downloadMethod = ReportViewController.class.getDeclaredMethod("download", javafx.event.ActionEvent.class);
		downloadMethod.setAccessible(true);
		downloadMethod.invoke(controller, (Object) null);
		assertThat(treeview.getRoot()).isNotNull();
	}

	/**
	 * Verifies that the {@code showok} check menu item toggles correctly between
	 * selected and deselected states.
	 *
	 * @throws Exception if reflective method access fails
	 */
	@Test
	@DisplayName("Should handle showok toggle")
	void shouldHandleShowokToggle() throws Exception {
		ReportViewController controller = TestApp.getController();
		CheckMenuItem showok = getField(controller, "showok");
		controller.setReport(null);
		
		showok.setSelected(true);
		Method showokMethod = ReportViewController.class.getDeclaredMethod("showok", javafx.event.ActionEvent.class);
		showokMethod.setAccessible(true);
		showokMethod.invoke(controller, (Object) null);
		assertThat(showok.isSelected()).isTrue();
		
		showok.setSelected(false);
		showokMethod.invoke(controller, (Object) null);
		assertThat(showok.isSelected()).isFalse();
	}

	/**
	 * Verifies that the {@code hidemissing} check menu item toggles correctly between
	 * selected and deselected states.
	 *
	 * @throws Exception if reflective method access fails
	 */
	@Test
	@DisplayName("Should handle copyReport with null report")
	void shouldHandleCopyReportWithNullReport() throws Exception {
		ReportViewController controller = TestApp.getController();
		controller.setReport(null);
		Method copyReportMethod = ReportViewController.class.getDeclaredMethod("copyReport", javafx.event.ActionEvent.class);
		copyReportMethod.setAccessible(true);
		copyReportMethod.invoke(controller, (Object) null);
		Button copyReport = getField(controller, "copyReport");
		assertThat(copyReport.isDisabled()).isTrue();
	}

	@Test
	@DisplayName("Should handle createFixDat with null report")
	void shouldHandleCreateFixDatWithNullReport() throws Exception {
		ReportViewController controller = TestApp.getController();
		controller.setReport(null);
		Method createFixDatMethod = ReportViewController.class.getDeclaredMethod("createFixDat", javafx.event.ActionEvent.class);
		createFixDatMethod.setAccessible(true);
		createFixDatMethod.invoke(controller, (Object) null);
		Button createFixDat = getField(controller, "createFixDat");
		assertThat(createFixDat.isDisabled()).isTrue();
	}

	@Test
	@DisplayName("Should handle hidemissing toggle")
	void shouldHandleHidemissingToggle() throws Exception {
		ReportViewController controller = TestApp.getController();
		CheckMenuItem hidemissing = getField(controller, "hidemissing");
		controller.setReport(null);
		
		hidemissing.setSelected(true);
		Method hidemissingMethod = ReportViewController.class.getDeclaredMethod("hidemissing", javafx.event.ActionEvent.class);
		hidemissingMethod.setAccessible(true);
		hidemissingMethod.invoke(controller, (Object) null);
		assertThat(hidemissing.isSelected()).isTrue();
		
		hidemissing.setSelected(false);
		hidemissingMethod.invoke(controller, (Object) null);
		assertThat(hidemissing.isSelected()).isFalse();
	}

	/**
	 * Retrieves a private field value from an object via reflection.
	 *
	 * @param <T>       the expected field type
	 * @param target    the object whose field to read
	 * @param fieldName the name of the field
	 * @return the field value, cast to {@code T}
	 * @throws RuntimeException if the field cannot be accessed
	 */
	@SuppressWarnings("unchecked")
	private static <T> T getField(Object target, String fieldName) {
		try {
			var field = ReportViewController.class.getDeclaredField(fieldName);
			field.setAccessible(true);
			return (T) field.get(target);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Failed to get field " + fieldName, e);
		}
	}
}
