package jrm.io.torrent.bencoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jrm.io.torrent.TorrentException;
import jrm.io.torrent.bencoding.types.BList;

/**
 * Unit tests for {@link Reader} nesting limits.
 */
@DisplayName("Reader")
class ReaderTest {

    @Test
    @DisplayName("parses nested lists up to the maximum depth")
    void parsesNestedListsAtMaxDepth() throws TorrentException {
        final var decoded = new Reader(nestedLists(Reader.MAX_NESTING_DEPTH).getBytes(StandardCharsets.US_ASCII)).read();

        assertThat(decoded).isInstanceOf(BList.class);
    }

    @Test
    @DisplayName("rejects lists nested deeper than the maximum depth")
    void rejectsListsDeeperThanMaxDepth() {
        final var payload = nestedLists(Reader.MAX_NESTING_DEPTH + 1).getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> new Reader(payload).read())
            .isInstanceOf(TorrentException.class)
            .hasMessageContaining("maximum depth");
    }

    @Test
    @DisplayName("rejects dictionaries nested deeper than the maximum depth")
    void rejectsDictionariesDeeperThanMaxDepth() {
        final var payload = nestedDictionaries(Reader.MAX_NESTING_DEPTH + 1).getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> new Reader(payload).read())
            .isInstanceOf(TorrentException.class)
            .hasMessageContaining("maximum depth");
    }

    private static String nestedLists(final int depth) {
        return "l".repeat(depth) + "e".repeat(depth);
    }

    private static String nestedDictionaries(final int depth) {
        final var encoded = new StringBuilder();
        for (var i = 0; i < depth; i++)
            encoded.append("d1:k");
        encoded.append("i0e");
        encoded.append("e".repeat(depth));
        return encoded.toString();
    }
}
