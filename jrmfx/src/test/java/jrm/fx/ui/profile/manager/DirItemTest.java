package jrm.fx.ui.profile.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;
import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxRecordedStage;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import jrm.profile.manager.Dir;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * TestFX tests for {@link DirItem}.
 * <p>
 * Tests the tree item that represents a directory in the profile manager, including directory tree building and reload
 * functionality.
 *
 * @since 3.0.5
 */
@TestFxApplication(DirItemTest.TestApp.class)
@DisplayName("DirItem Tests")
class DirItemTest {

    /**
     * Test application for JavaFX component tests.
     */
    public static class TestApp extends Application implements TestFxRecordedStage {
        private Stage primaryStage;

        @Override
        public void start(Stage primaryStage) {
            this.primaryStage = primaryStage;
            primaryStage.setScene(new Scene(new StackPane(), 400, 300));
            primaryStage.show();
        }

        @Override
        public Stage recordedStage() {
            return primaryStage;
        }
    }

    /** JUnit 5 temporary directory. */
    @TempDir
    Path tempDir;

    /** The root directory file under test. */
    private File testDir;

    /**
     * Creates a test directory structure with subdirectories and nested directories.
     *
     * @throws IOException if directory creation fails
     */
    @BeforeEach
    void setUp() throws IOException {
        testDir = tempDir.toFile();

        Files.createDirectory(tempDir.resolve("subdir1"));
        Files.createDirectory(tempDir.resolve("subdir2"));
        Files.createDirectory(tempDir.resolve("subdir3"));

        Files.createDirectory(tempDir.resolve("subdir1/nested1"));
        Files.createDirectory(tempDir.resolve("subdir1/nested2"));
        Files.createDirectory(tempDir.resolve("subdir2/nested3"));
    }

    /**
     * Verifies that a {@link DirItem} can be created from a file and correctly wraps the directory path.
     */
    @Test
    @DisplayName("Should create DirItem from File")
    void shouldCreateDirItemFromFile() {
        DirItem item = new DirItem(testDir);

        assertThat(item).as("DirItem should be created").isNotNull();
        assertThat(item.getValue()).as("Dir should be set").isNotNull();
        assertThat(item.getValue().getFile()).as("File should match").isEqualTo(testDir);
    }

    /**
     * Verifies that a newly created {@link DirItem} is expanded by default.
     */
    @Test
    @DisplayName("Should expand DirItem by default")
    void shouldExpandDirItemByDefault() {
        DirItem item = new DirItem(testDir);

        assertThat(item.isExpanded()).as("DirItem should be expanded by default").isTrue();
    }

    /**
     * Verifies that a {@link DirItem} has a non-null graphic icon.
     */
    @Test
    @DisplayName("Should have graphic icon")
    void shouldHaveGraphicIcon() {
        DirItem item = new DirItem(testDir);

        assertThat(item.getGraphic()).as("Graphic should be set").isNotNull();
    }

    /**
     * Verifies that the directory tree is built with direct subdirectories as children.
     */
    @Test
    @DisplayName("Should build directory tree with subdirectories")
    void shouldBuildDirectoryTreeWithSubdirectories() {
        DirItem item = new DirItem(testDir);

        assertThat(item.getChildren()).as("Should have children").isNotEmpty();
        assertThat(item.getChildren()).as("Should have 3 subdirectories").hasSize(3);
    }

    /**
     * Verifies that nested directory trees are built recursively.
     */
    @Test
    @DisplayName("Should build nested directory tree")
    void shouldBuildNestedDirectoryTree() {
        DirItem item = new DirItem(testDir);

        DirItem subdir1 = item.getChildren().stream()
                .filter(child -> child.getValue().getFile().getName().equals("subdir1"))
                .map(DirItem.class::cast)
                .findFirst()
                .orElse(null);

        assertThat(subdir1).as("subdir1 should exist").isNotNull();
        assertThat(subdir1.getChildren()).as("subdir1 should have 2 nested directories").hasSize(2);
    }

    /**
     * Verifies that {@link DirItem#reload()} picks up newly created subdirectories.
     *
     * @throws IOException if directory creation fails
     */
    @Test
    @DisplayName("Should reload directory tree")
    void shouldReloadDirectoryTree() throws IOException {
        DirItem item = new DirItem(testDir);

        int initialChildCount = item.getChildren().size();
        assertThat(initialChildCount).as("Should have 3 initial subdirectories").isEqualTo(3);

        Files.createDirectory(tempDir.resolve("subdir4"));

        item.reload();

        assertThat(item.getChildren()).as("Should have 4 subdirectories after reload").hasSize(4);
    }

    /**
     * Verifies that a {@link DirItem} remains expanded after a reload.
     */
    @Test
    @DisplayName("Should remain expanded after reload")
    void shouldRemainExpandedAfterReload() {
        DirItem item = new DirItem(testDir);

        assertThat(item.isExpanded()).as("Should be expanded initially").isTrue();

        item.reload();

        assertThat(item.isExpanded()).as("Should remain expanded after reload").isTrue();
    }

    /**
     * Verifies that a {@link DirItem} for an empty directory has no children.
     *
     * @throws IOException if directory creation fails
     */
    @Test
    @DisplayName("Should handle empty directory")
    void shouldHandleEmptyDirectory() throws IOException {
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectory(emptyDir);

        DirItem item = new DirItem(emptyDir.toFile());

        assertThat(item.getChildren()).as("Empty directory should have no children").isEmpty();
    }

    /**
     * Verifies that only directories are included in the tree, not regular files.
     *
     * @throws IOException if file creation fails
     */
    @Test
    @DisplayName("Should only include directories in tree")
    void shouldOnlyIncludeDirectoriesInTree() throws IOException {
        Files.createFile(tempDir.resolve("file.txt"));

        DirItem item = new DirItem(testDir);

        assertThat(item.getChildren()).as("Should only include directories, not files").hasSize(3);

        assertThat(item.getChildren())
                .as("All children should be directories")
                .allMatch(child -> child.getValue().getFile().isDirectory());
    }

    /**
     * Verifies that a leaf directory with no subdirectories is marked as a leaf node.
     *
     * @throws IOException if directory creation fails
     */
    @Test
    @DisplayName("Should handle directory with no subdirectories")
    void shouldHandleDirectoryWithNoSubdirectories() throws IOException {
        Path leafDir = tempDir.resolve("leaf");
        Files.createDirectory(leafDir);
        Files.createFile(leafDir.resolve("file.txt"));

        DirItem item = new DirItem(leafDir.toFile());

        assertThat(item.getChildren()).as("Leaf directory should have no children").isEmpty();
        assertThat(item.isLeaf()).as("Should be a leaf node").isTrue();
    }

    /**
     * Verifies that children are cleared after a reload when all subdirectories have been deleted.
     *
     * @throws IOException if file system operations fail
     */
    @Test
    @DisplayName("Should clear children on reload")
    void shouldClearChildrenOnReload() throws IOException {
        DirItem item = new DirItem(testDir);

        assertThat(item.getChildren()).as("Should have children initially").isNotEmpty();

        Files.walk(tempDir)
                .filter(Files::isDirectory)
                .filter(path -> !path.equals(tempDir))
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException _) {
                         /* Ignore exceptions during deletion */
                    }
                });

        item.reload();

        assertThat(item.getChildren()).as("Should have no children after deleting all subdirectories").isEmpty();
    }

    /**
     * Verifies that the {@link Dir} value is preserved after a reload.
     */
    @Test
    @DisplayName("Should preserve Dir value after reload")
    void shouldPreserveDirValueAfterReload() {
        DirItem item = new DirItem(testDir);
        Dir originalDir = item.getValue();

        item.reload();

        assertThat(item.getValue()).as("Dir value should be preserved after reload").isEqualTo(originalDir);
    }

    @Test
    @DisplayName("Should include directories up to the maximum depth")
    void shouldIncludeDirectoriesUpToMaxDepth() throws IOException {
        createNestedDirectories(DirItem.MAX_DIR_DEPTH);

        assertThat(maxDepth(new DirItem(testDir))).isEqualTo(DirItem.MAX_DIR_DEPTH);
    }

    @Test
    @DisplayName("Should stop scanning past the maximum depth")
    void shouldStopScanningPastMaxDepth() throws IOException {
        createNestedDirectories(DirItem.MAX_DIR_DEPTH + 5);

        assertThatCode(() -> new DirItem(testDir)).doesNotThrowAnyException();
        assertThat(maxDepth(new DirItem(testDir))).isEqualTo(DirItem.MAX_DIR_DEPTH);
    }

    private void createNestedDirectories(final int depth) throws IOException {
        var current = tempDir;
        for (var i = 0; i < depth; i++) {
            current = current.resolve("d" + i);
            Files.createDirectory(current);
        }
    }

    private static int maxDepth(final DirItem node) {
        var deepest = 0;
        for (final var child : node.getChildren())
            deepest = Math.max(deepest, 1 + maxDepth((DirItem) child));
        return deepest;
    }
}
