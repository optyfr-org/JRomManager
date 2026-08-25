package jrm.profile.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.misc.Tree.Node;

/**
 * Tests for {@link DirTree} filesystem walk limits.
 */
@DisplayName("DirTree")
class DirTreeTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("includes sibling directories under the root")
    void includesSiblingDirectories() throws IOException {
        Files.createDirectory(tempDir.resolve("alpha"));
        Files.createDirectory(tempDir.resolve("beta"));
        Files.createFile(tempDir.resolve("ignored.txt"));

        final var tree = new DirTree(tempDir.toFile());

        assertThat(tree.getRoot().getChildCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("includes directories up to the maximum depth")
    void includesDirectoriesUpToMaxDepth() throws IOException {
        createNestedDirectories(DirTree.MAX_DIR_DEPTH);

        final var tree = new DirTree(tempDir.toFile());

        assertThat(maxDepth(tree.getRoot())).isEqualTo(DirTree.MAX_DIR_DEPTH);
    }

    @Test
    @DisplayName("stops scanning past the maximum depth")
    void stopsPastMaxDepth() throws IOException {
        createNestedDirectories(DirTree.MAX_DIR_DEPTH + 5);

        assertThatCode(() -> new DirTree(tempDir.toFile())).doesNotThrowAnyException();
        assertThat(maxDepth(new DirTree(tempDir.toFile()).getRoot())).isEqualTo(DirTree.MAX_DIR_DEPTH);
    }

    private void createNestedDirectories(final int depth) throws IOException {
        var current = tempDir;
        for (var i = 0; i < depth; i++) {
            current = current.resolve("d" + i);
            Files.createDirectory(current);
        }
    }

    private static int maxDepth(final Node<Dir> node) {
        var deepest = 0;
        for (final Node<Dir> child : node)
            deepest = Math.max(deepest, 1 + maxDepth(child));
        return deepest;
    }
}
