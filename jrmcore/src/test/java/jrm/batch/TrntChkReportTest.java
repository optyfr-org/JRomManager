package jrm.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.profile.report.FilterOptions;
import jrm.security.Session;

/**
 * Unit tests for {@link TrntChkReport}, covering child node creation, status propagation, filtering, cloning, save/load round-trip,
 * and the {@link TrntChkReport.Child} and {@link TrntChkReport.ChildData} inner classes.
 */
@DisplayName("TrntChkReport tests")
class TrntChkReportTest {

    /** System property used by {@code GlobalSettings} to locate the work directory in server mode. */
    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session session;

    @BeforeEach
    void setUp() throws IOException {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("JRomManager"));
        session = new Session("trntchk-report-test");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(JRM_DIR_PROP);
    }

    // ──────────────────────────────────────────────────────────────
    // Construction
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("construction")
    class ConstructionTests {

        @Test
        @DisplayName("constructor should set file and initialize empty nodes list")
        void constructorShouldSetFileAndInitializeNodes() {
            var src = new File("test.torrent");

            var report = new TrntChkReport(src);

            assertThat(report.getFile()).isEqualTo(src);
            assertThat(report.getFileModified()).isZero();
            assertThat(report.getNodes()).isEmpty();
            assertThat(report.getAll()).isEmpty();
        }

        @Test
        @DisplayName("constructor with null file should still initialize nodes")
        void constructorWithNullFileShouldInitializeNodes() {
            var report = new TrntChkReport(null);

            assertThat(report.getFile()).isNull();
            assertThat(report.getNodes()).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Adding child nodes
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("add child nodes")
    class AddChildTests {

        @Test
        @DisplayName("add at root level should create child with title and add to nodes")
        void addAtRootShouldCreateChildWithTitle() {
            var report = new TrntChkReport(new File("test.torrent"));

            var child = report.add("root-node");

            assertThat(child).isNotNull();
            assertThat(child.getData().getTitle()).isEqualTo("root-node");
            assertThat(child.getParent()).isNull();
            assertThat(report.getNodes()).hasSize(1).contains(child);
            assertThat(report.getAll()).containsValue(child);
        }

        @Test
        @DisplayName("add at root level should assign unique uid")
        void addAtRootShouldAssignUniqueUid() {
            var report = new TrntChkReport(new File("test.torrent"));

            var child1 = report.add("node1");
            var child2 = report.add("node2");

            assertThat(child1.getUid()).isNotEqualTo(child2.getUid());
        }

        @Test
        @DisplayName("child add(String) should create nested child with parent reference")
        void childAddStringShouldCreateNestedChild() {
            var report = new TrntChkReport(new File("test.torrent"));
            var parent = report.add("parent");

            var child = parent.add("child");

            assertThat(child.getData().getTitle()).isEqualTo("child");
            assertThat(child.getParent()).isSameAs(parent);
            assertThat(parent.getChildren()).hasSize(1).contains(child);
            assertThat(report.getAll()).containsValue(child);
        }

        @Test
        @DisplayName("child add(Child) should create copy of data under new parent")
        void childAddChildShouldCreateCopyOfData() {
            var report = new TrntChkReport(new File("test.torrent"));
            var original = report.add("original");
            original.getData().setLength(42L);

            var parent = report.add("parent");
            var copy = parent.add(original);

            assertThat(copy.getData()).isSameAs(original.getData());
            assertThat(copy.getParent()).isSameAs(parent);
            assertThat(parent.getChildren()).hasSize(1).contains(copy);
        }

        @Test
        @DisplayName("adding multiple children should preserve order")
        void addingMultipleChildrenShouldPreserveOrder() {
            var report = new TrntChkReport(new File("test.torrent"));

            report.add("first");
            report.add("second");
            report.add("third");

            assertThat(report.getNodes()).hasSize(3);
            assertThat(report.getNodes().get(0).getData().getTitle()).isEqualTo("first");
            assertThat(report.getNodes().get(1).getData().getTitle()).isEqualTo("second");
            assertThat(report.getNodes().get(2).getData().getTitle()).isEqualTo("third");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // ChildData
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ChildData")
    class ChildDataTests {

        @Test
        @DisplayName("default ChildData should have null length and UNKNOWN status")
        void defaultChildDataShouldHaveNullLengthAndUnknownStatus() {
            var report = new TrntChkReport(new File("test.torrent"));
            var child = report.add("node");

            assertThat(child.getData().getLength()).isNull();
            assertThat(child.getData().getStatus()).isEqualTo(TrntChkReport.Status.UNKNOWN);
        }

        @Test
        @DisplayName("setLength should update length and return this for chaining")
        void setLengthShouldUpdateLengthAndReturnThis() {
            var report = new TrntChkReport(new File("test.torrent"));
            var child = report.add("node");

            var returned = child.getData().setLength(123L);

            assertThat(returned).isSameAs(child.getData());
            assertThat(child.getData().getLength()).isEqualTo(123L);
        }

        @Test
        @DisplayName("setLength with null should clear length")
        void setLengthWithNullShouldClearLength() {
            var report = new TrntChkReport(new File("test.torrent"));
            var child = report.add("node");
            child.getData().setLength(99L);

            child.getData().setLength(null);

            assertThat(child.getData().getLength()).isNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // setStatus and propagation
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("setStatus and propagation")
    class SetStatusTests {

        @Test
        @DisplayName("setStatus should update node status")
        void setStatusShouldUpdateNodeStatus() {
            var report = new TrntChkReport(new File("test.torrent"));
            var child = report.add("node");

            child.setStatus(TrntChkReport.Status.OK);

            assertThat(child.getData().getStatus()).isEqualTo(TrntChkReport.Status.OK);
        }

        @Test
        @DisplayName("setStatus should propagate to children with UNKNOWN status")
        void setStatusShouldPropagateToUnknownChildren() {
            var report = new TrntChkReport(new File("test.torrent"));
            var parent = report.add("parent");
            var child1 = parent.add("child1");
            var child2 = parent.add("child2");

            parent.setStatus(TrntChkReport.Status.MISSING);

            assertThat(child1.getData().getStatus()).isEqualTo(TrntChkReport.Status.MISSING);
            assertThat(child2.getData().getStatus()).isEqualTo(TrntChkReport.Status.MISSING);
        }

        @Test
        @DisplayName("setStatus should propagate to children with OK status")
        void setStatusShouldPropagateToOkChildren() {
            var report = new TrntChkReport(new File("test.torrent"));
            var parent = report.add("parent");
            var child = parent.add("child");
            child.setStatus(TrntChkReport.Status.OK);

            parent.setStatus(TrntChkReport.Status.SIZE);

            assertThat(child.getData().getStatus()).isEqualTo(TrntChkReport.Status.SIZE);
        }

        @Test
        @DisplayName("setStatus should not override non-OK and non-UNKNOWN child status")
        void setStatusShouldNotOverrideNonOkNonUnknownChildStatus() {
            var report = new TrntChkReport(new File("test.torrent"));
            var parent = report.add("parent");
            var child = parent.add("child");
            child.setStatus(TrntChkReport.Status.SHA1);

            parent.setStatus(TrntChkReport.Status.MISSING);

            assertThat(child.getData().getStatus()).isEqualTo(TrntChkReport.Status.SHA1);
        }

        @Test
        @DisplayName("setStatus on node without children should not throw")
        void setStatusOnNodeWithoutChildrenShouldNotThrow() {
            assertDoesNotThrow(() -> {
                var report = new TrntChkReport(new File("test.torrent"));
                var child = report.add("node");
                child.setStatus(TrntChkReport.Status.OK);
            });
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Filter and stream
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("filter and stream")
    class FilterStreamTests {

        @Test
        @DisplayName("filter with SHOWOK should include OK nodes")
        void filterWithShowOkShouldIncludeOkNodes() {
            var report = new TrntChkReport(new File("test.torrent"));
            report.add("ok-node").setStatus(TrntChkReport.Status.OK);
            report.add("missing-node").setStatus(TrntChkReport.Status.MISSING);

            var filtered = report.filter(Set.of(FilterOptions.SHOWOK));

            assertThat(filtered).hasSize(2);
        }

        @Test
        @DisplayName("filter without SHOWOK should exclude OK nodes")
        void filterWithoutShowOkShouldExcludeOkNodes() {
            var report = new TrntChkReport(new File("test.torrent"));
            report.add("ok-node").setStatus(TrntChkReport.Status.OK);
            report.add("missing-node").setStatus(TrntChkReport.Status.MISSING);

            var filtered = report.filter(new HashSet<>());

            assertThat(filtered).hasSize(1);
            assertThat(filtered.get(0).getData().getTitle()).isEqualTo("missing-node");
        }

        @Test
        @DisplayName("filter with HIDEMISSING should exclude MISSING nodes")
        void filterWithHideMissingShouldExcludeMissingNodes() {
            var report = new TrntChkReport(new File("test.torrent"));
            report.add("ok-node").setStatus(TrntChkReport.Status.OK);
            report.add("missing-node").setStatus(TrntChkReport.Status.MISSING);

            var filtered = report.filter(Set.of(FilterOptions.SHOWOK, FilterOptions.HIDEMISSING));

            assertThat(filtered).hasSize(1);
            assertThat(filtered.get(0).getData().getTitle()).isEqualTo("ok-node");
        }

        @Test
        @DisplayName("stream should return same count as filter")
        void streamShouldReturnSameCountAsFilter() {
            var report = new TrntChkReport(new File("test.torrent"));
            report.add("ok-node").setStatus(TrntChkReport.Status.OK);
            report.add("missing-node").setStatus(TrntChkReport.Status.MISSING);
            report.add("size-node").setStatus(TrntChkReport.Status.SIZE);

            var streamCount = report.stream(new HashSet<>()).count();

            assertThat(streamCount).isEqualTo(2);
        }

        @Test
        @DisplayName("filter on empty report should return empty list")
        void filterOnEmptyReportShouldReturnEmptyList() {
            var report = new TrntChkReport(new File("test.torrent"));

            var filtered = report.filter(Set.of(FilterOptions.SHOWOK));

            assertThat(filtered).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Clone
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("clone")
    class CloneTests {

        @Test
        @DisplayName("clone should return new report with filtered nodes")
        void cloneShouldReturnNewReportWithFilteredNodes() {
            var report = new TrntChkReport(new File("test.torrent"));
            report.add("ok-node").setStatus(TrntChkReport.Status.OK);
            report.add("missing-node").setStatus(TrntChkReport.Status.MISSING);

            var cloned = report.clone(new HashSet<>());

            assertThat(cloned).isNotSameAs(report);
            assertThat(cloned.getNodes()).hasSize(1);
            assertThat(cloned.getNodes().get(0).getData().getTitle()).isEqualTo("missing-node");
        }

        @Test
        @DisplayName("clone with SHOWOK should preserve all nodes")
        void cloneWithShowOkShouldPreserveAllNodes() {
            var report = new TrntChkReport(new File("test.torrent"));
            report.add("ok-node").setStatus(TrntChkReport.Status.OK);
            report.add("missing-node").setStatus(TrntChkReport.Status.MISSING);

            var cloned = report.clone(Set.of(FilterOptions.SHOWOK));

            assertThat(cloned.getNodes()).hasSize(2);
        }

        @Test
        @DisplayName("clone should share all map with original")
        void cloneShouldShareAllMapWithOriginal() {
            var report = new TrntChkReport(new File("test.torrent"));
            report.add("node1");

            var cloned = report.clone(Set.of(FilterOptions.SHOWOK));

            assertThat(cloned.getAll()).isSameAs(report.getAll());
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Child.copy()
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Child.copy()")
    class ChildCopyTests {

        @Test
        @DisplayName("copy should create shallow copy with same data")
        void copyShouldCreateShallowCopyWithSameData() {
            var report = new TrntChkReport(new File("test.torrent"));
            var original = report.add("original");
            original.getData().setLength(100L);
            original.setStatus(TrntChkReport.Status.OK);

            var copy = original.copy();

            assertThat(copy).isNotSameAs(original);
            assertThat(copy.getUid()).isEqualTo(original.getUid());
            assertThat(copy.getData()).isSameAs(original.getData());
            assertThat(copy.getParent()).isSameAs(original.getParent());
            assertThat(copy.getChildren()).isSameAs(original.getChildren());
        }
    }

    // ──────────────────────────────────────────────────────────────
    // toString
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("report toString should contain all node titles")
        void reportToStringShouldContainAllNodeTitles() {
            var report = new TrntChkReport(new File("test.torrent"));
            report.add("alpha");
            report.add("beta");

            var str = report.toString();

            assertThat(str).contains("alpha", "beta");
        }

        @Test
        @DisplayName("child toString should contain title, length, and status")
        void childToStringShouldContainTitleLengthAndStatus() {
            var report = new TrntChkReport(new File("test.torrent"));
            var child = report.add("test-node");
            child.getData().setLength(42L);
            child.setStatus(TrntChkReport.Status.OK);

            var str = child.toString();

            assertThat(str).contains("test-node", "42", "OK");
        }

        @Test
        @DisplayName("child toString should prefix children with |_ ")
        void childToStringShouldPrefixChildrenWithPipe() {
            var report = new TrntChkReport(new File("test.torrent"));
            var parent = report.add("parent");
            parent.add("child");

            var str = parent.toString();

            assertThat(str).contains("|_ child");
        }

        @Test
        @DisplayName("empty report toString should be empty")
        void emptyReportToStringShouldBeEmpty() {
            var report = new TrntChkReport(new File("test.torrent"));

            assertThat(report.toString()).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Save / Load
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save and load")
    class SaveLoadTests {

        @Test
        @DisplayName("save and load should preserve nodes and their data")
        void saveAndLoadShouldPreserveNodesAndData() {
            var srcFile = new File("test.torrent");
            var report = new TrntChkReport(srcFile);
            report.add("root1").setStatus(TrntChkReport.Status.OK);
            var parent = report.add("root2");
            parent.getData().setLength(999L);
            parent.add("child1").setStatus(TrntChkReport.Status.MISSING);

            var reportFile = report.getReportFile(session);
            report.save(reportFile);

            var loaded = TrntChkReport.load(session, srcFile);

            assertThat(loaded).isNotNull();
            assertThat(loaded.getNodes()).hasSize(2);
            assertThat(loaded.getNodes().get(0).getData().getTitle()).isEqualTo("root1");
            assertThat(loaded.getNodes().get(0).getData().getStatus()).isEqualTo(TrntChkReport.Status.OK);
            assertThat(loaded.getNodes().get(1).getData().getTitle()).isEqualTo("root2");
            assertThat(loaded.getNodes().get(1).getData().getLength()).isEqualTo(999L);
            assertThat(loaded.getNodes().get(1).getChildren()).hasSize(1);
            assertThat(loaded.getNodes().get(1).getChildren().get(0).getData().getTitle()).isEqualTo("child1");
            assertThat(loaded.getNodes().get(1).getChildren().get(0).getData().getStatus())
                    .isEqualTo(TrntChkReport.Status.MISSING);
        }

        @Test
        @DisplayName("load should set file and fileModified on loaded report")
        void loadShouldSetFileAndFileModified() {
            var srcFile = new File("test.torrent");
            var report = new TrntChkReport(srcFile);
            report.add("node");

            var reportFile = report.getReportFile(session);
            report.save(reportFile);

            var loaded = TrntChkReport.load(session, srcFile);

            assertThat(loaded.getFile()).isEqualTo(srcFile);
            assertThat(loaded.getFileModified()).isGreaterThan(0L);
        }

        @Test
        @DisplayName("load non-existent report should return null")
        void loadNonExistentReportShouldReturnNull() {
            var result = TrntChkReport.load(session, new File("nonexistent.torrent"));

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("save and load deeply nested child chain should preserve all nodes")
        void saveAndLoadDeeplyNestedChildChainShouldPreserveAllNodes() {
            var srcFile = new File("deep.torrent");
            var report = new TrntChkReport(srcFile);
            // Build a chain deeper than the default 100-object depth limit.
            // This mimics the nested Child graph that torrent-check reports can produce piece-by-piece.
            var root = report.add("Piece 1");
            var current = root;
            for (int i = 2; i <= 200; i++) {
                current = current.add("Piece " + i);
            }

            var reportFile = report.getReportFile(session);
            report.save(reportFile);

            var loaded = TrntChkReport.load(session, srcFile);

            assertThat(loaded).isNotNull();
            assertThat(loaded.getNodes()).hasSize(1);
            int depth = 0;
            current = loaded.getNodes().get(0);
            while (current != null) {
                depth++;
                var children = current.getChildren();
                if (children == null || children.isEmpty()) {
                    break;
                }
                current = children.get(0);
            }
            assertThat(depth).isEqualTo(200);
        }

        @Test
        @DisplayName("save should create report file in reports directory")
        void saveShouldCreateReportFileInReportsDirectory() {
            var srcFile = new File("my-torrent.torrent");
            var report = new TrntChkReport(srcFile);
            report.add("node");

            var reportFile = report.getReportFile(session);
            report.save(reportFile);

            assertThat(reportFile).exists();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Status enum
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Status enum")
    class StatusEnumTests {

        @Test
        @DisplayName("should have all expected status values")
        void shouldHaveAllExpectedStatusValues() {
            assertThat(TrntChkReport.Status.values())
                    .containsExactlyInAnyOrder(
                            TrntChkReport.Status.OK,
                            TrntChkReport.Status.SIZE,
                            TrntChkReport.Status.SHA1,
                            TrntChkReport.Status.MISSING,
                            TrntChkReport.Status.SKIPPED,
                            TrntChkReport.Status.UNKNOWN);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Options enum
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Options enum")
    class OptionsEnumTests {

        @Test
        @DisplayName("should have all expected option values")
        void shouldHaveAllExpectedOptionValues() {
            assertThat(TorrentChecker.Options.values())
                    .containsExactlyInAnyOrder(
                            TorrentChecker.Options.REMOVEUNKNOWNFILES,
                            TorrentChecker.Options.REMOVEWRONGSIZEDFILES,
                            TorrentChecker.Options.DETECTARCHIVEDFOLDERS);
        }
    }
}