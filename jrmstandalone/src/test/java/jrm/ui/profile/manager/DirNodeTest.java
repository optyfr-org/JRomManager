package jrm.ui.profile.manager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.tree.TreeNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("DirNode.buildDirTree")
class DirNodeTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectory(tempDir.resolve("subdir1"));
        Files.createDirectory(tempDir.resolve("subdir2"));
        Files.createDirectories(tempDir.resolve("subdir1").resolve("nested1"));
        Files.createFile(tempDir.resolve("file.txt"));
    }

    @Test
    @DisplayName("builds child nodes for subdirectories only")
    void buildsSubdirectoriesOnly() {
        final var node = new DirNode(tempDir.toFile());
        assertEquals(2, node.getChildCount());
        assertNotNull(findChild(node, "subdir1"));
        assertEquals(1, findChild(node, "subdir1").getChildCount());
    }

    @Test
    @DisplayName("reload picks up a new subdirectory")
    void reloadPicksUpNewDirectory() throws IOException {
        final var node = new DirNode(tempDir.toFile());
        assertEquals(2, node.getChildCount());
        Files.createDirectory(tempDir.resolve("subdir3"));
        node.reload();
        assertEquals(3, node.getChildCount());
    }

    @Test
    @DisplayName("does not overflow past the maximum depth")
    void capsDeepDirectoryChain() throws IOException {
        createNestedDirectories(DirNode.MAX_DIR_DEPTH + 5);
        assertDoesNotThrow(() -> new DirNode(tempDir.toFile()));
        assertEquals(DirNode.MAX_DIR_DEPTH, maxDepth(new DirNode(tempDir.toFile())));
    }

    private void createNestedDirectories(final int depth) throws IOException {
        var current = tempDir.resolve("deep");
        Files.createDirectory(current);
        for (var i = 0; i < depth; i++) {
            current = current.resolve("d");
            Files.createDirectory(current);
        }
    }

    private static DirNode findChild(final DirNode node, final String name) {
        for (int i = 0; i < node.getChildCount(); i++) {
            final var child = (DirNode) node.getChildAt(i);
            if (child.getDir().getFile().getName().equals(name))
                return child;
        }
        return null;
    }

    private static int maxDepth(final TreeNode node) {
        var deepest = 0;
        for (int i = 0; i < node.getChildCount(); i++)
            deepest = Math.max(deepest, 1 + maxDepth(node.getChildAt(i)));
        return deepest;
    }
}
