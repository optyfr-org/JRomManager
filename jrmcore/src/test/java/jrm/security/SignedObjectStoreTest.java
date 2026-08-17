package jrm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
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
 * Tests for {@link SignedObjectStore}: signed round-trip, bare-stream rejection, and rejection of
 * predictable work-path HMAC envelopes.
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
        session = new Session("signed-object-store-test", "JRomManager", new String[] { "admin" });
    }

    @AfterEach
    void tearDown() {
        CacheIntegrityKey.clearCache();
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
        final byte[] written = Files.readAllBytes(file.toPath());
        assertThat(written).startsWith((byte) 'J', (byte) 'R', (byte) 'M', (byte) 'H');
        assertThat(written[4]).isEqualTo((byte) 2);
        assertThat(Files.size(CacheIntegrityKey.keyFile(session))).isEqualTo(32);
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
    @DisplayName("legacy length-prefixed HMAC envelope should be rejected")
    void legacyLengthPrefixedHmacShouldBeRejected() throws Exception {
        final var original = Map.of("legacy", true);
        final byte[] serialized = serialize(original);
        final byte[] hmac = computeWorkPathHmac(session, serialized);
        final File file = tempDir.resolve("legacy-hmac.cache").toFile();
        try (final var out = Files.newOutputStream(file.toPath())) {
            out.write((hmac.length >> 24) & 0xFF);
            out.write((hmac.length >> 16) & 0xFF);
            out.write((hmac.length >> 8) & 0xFF);
            out.write(hmac.length & 0xFF);
            out.write(hmac);
            out.write(serialized);
        }

        assertThatThrownBy(() -> SignedObjectStore.read(session, file))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Legacy");
    }

    @Test
    @DisplayName("JRMH v1 envelope should be rejected")
    void jrmhV1EnvelopeShouldBeRejected() throws Exception {
        final byte[] serialized = serialize("legacy-v1");
        final byte[] hmac = computeWorkPathHmac(session, serialized);
        final File file = tempDir.resolve("v1.cache").toFile();
        try (final var out = Files.newOutputStream(file.toPath())) {
            out.write(new byte[] { 'J', 'R', 'M', 'H', 1 });
            out.write((hmac.length >> 24) & 0xFF);
            out.write((hmac.length >> 16) & 0xFF);
            out.write((hmac.length >> 8) & 0xFF);
            out.write(hmac.length & 0xFF);
            out.write(hmac);
            out.write(serialized);
        }

        assertThatThrownBy(() -> SignedObjectStore.read(session, file))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("version");
    }

    @Test
    @DisplayName("JRMH envelope signed with work-path key should be rejected")
    void workPathDerivedSignedPayloadShouldBeRejected() throws Exception {
        final byte[] serialized = serialize("forged");
        final byte[] hmac = computeWorkPathHmac(session, serialized);
        final File file = tempDir.resolve("forged.cache").toFile();
        try (final var out = Files.newOutputStream(file.toPath())) {
            out.write(new byte[] { 'J', 'R', 'M', 'H', 2 });
            out.write((hmac.length >> 24) & 0xFF);
            out.write((hmac.length >> 16) & 0xFF);
            out.write((hmac.length >> 8) & 0xFF);
            out.write(hmac.length & 0xFF);
            out.write(hmac);
            out.write(serialized);
        }

        assertThatThrownBy(() -> SignedObjectStore.read(session, file))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("integrity");
        assertThat(Files.readAllBytes(CacheIntegrityKey.keyFile(session)))
                .isNotEqualTo(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(("JRM-CACHE-INTEGRITY-" + session.getUser().getSettings().getWorkPath())
                                .getBytes(StandardCharsets.UTF_8)));
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

    private static byte[] computeWorkPathHmac(final Session session, final byte[] data) throws Exception {
        final var workPath = session.getUser().getSettings().getWorkPath().toString();
        final var keyMaterial = ("JRM-CACHE-INTEGRITY-" + workPath).getBytes(StandardCharsets.UTF_8);
        final var key = java.security.MessageDigest.getInstance("SHA-256").digest(keyMaterial);
        final var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
