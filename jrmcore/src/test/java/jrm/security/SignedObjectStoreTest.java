package jrm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link SignedObjectStore}: signed round-trip, bare-stream rejection, and legacy DirScan HMAC envelopes.
 */
@DisplayName("SignedObjectStore tests")
class SignedObjectStoreTest {

    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session session;

    @BeforeEach
    void setUp() throws IOException {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("JRomManager"));
        session = new Session("signed-object-store-test");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(JRM_DIR_PROP);
    }

    @Test
    @DisplayName("signed write/read round-trip should preserve object")
    void signedRoundTripShouldPreserveObject() throws Exception {
        final var original = Map.of("a", 1L, "b", 2L);
        final File file = tempDir.resolve("signed.cache").toFile();

        SignedObjectStore.write(session, file, original);
        @SuppressWarnings("unchecked")
        final Map<String, Long> loaded = (Map<String, Long>) SignedObjectStore.read(session, file);

        assertThat(loaded).containsExactlyInAnyOrderEntriesOf(original);
        assertThat(Files.readAllBytes(file.toPath())).startsWith((byte) 'J', (byte) 'R', (byte) 'M', (byte) 'H');
    }

    @Test
    @DisplayName("legacy bare Java stream should be rejected")
    void legacyBareJavaStreamShouldBeRejected() throws Exception {
        final var original = new HashMap<String, String>();
        original.put("key", "value");
        final File file = tempDir.resolve("legacy.cache").toFile();
        try (final var oos = new ObjectOutputStream(Files.newOutputStream(file.toPath()))) {
            oos.writeObject(original);
        }

        assertThatThrownBy(() -> SignedObjectStore.read(session, file))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unsigned Java serialization");
    }

    @Test
    @DisplayName("legacy length-prefixed HMAC envelope should still load")
    void legacyLengthPrefixedHmacShouldLoad() throws Exception {
        final var original = Map.of("legacy", true);
        final byte[] serialized = serialize(original);
        final byte[] hmac = computeLegacyHmac(session, serialized);
        final File file = tempDir.resolve("legacy-hmac.cache").toFile();
        try (final var out = Files.newOutputStream(file.toPath())) {
            out.write((hmac.length >> 24) & 0xFF);
            out.write((hmac.length >> 16) & 0xFF);
            out.write((hmac.length >> 8) & 0xFF);
            out.write(hmac.length & 0xFF);
            out.write(hmac);
            out.write(serialized);
        }

        @SuppressWarnings("unchecked")
        final Map<String, Boolean> loaded = (Map<String, Boolean>) SignedObjectStore.read(session, file);
        assertThat(loaded).containsExactlyInAnyOrderEntriesOf(original);
    }

    @Test
    @DisplayName("tampered signed payload should be rejected")
    void tamperedSignedPayloadShouldBeRejected() throws Exception {
        final File file = tempDir.resolve("tampered.cache").toFile();
        SignedObjectStore.write(session, file, "trusted");
        final byte[] bytes = Files.readAllBytes(file.toPath());
        bytes[bytes.length - 1] ^= 0x01;
        Files.write(file.toPath(), bytes);

        assertThatThrownBy(() -> SignedObjectStore.read(session, file))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("integrity");
    }

    private static byte[] serialize(final Object object) throws IOException {
        final var baos = new ByteArrayOutputStream();
        try (final var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
        }
        return baos.toByteArray();
    }

    private static byte[] computeLegacyHmac(final Session session, final byte[] data) throws Exception {
        final var workPath = session.getUser().getSettings().getWorkPath().toString();
        final var keyMaterial = ("JRM-CACHE-INTEGRITY-" + workPath).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final var key = java.security.MessageDigest.getInstance("SHA-256").digest(keyMaterial);
        final var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
