package jrm.server.shared.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.eclipsesource.json.JsonObject;

import jrm.misc.SettingsEnum;
import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;

/**
 * Regression tests: Global.setProperty must not accept paths that escape the session sandbox
 * (arbitrary file overwrite via dir2dat.dst_file).
 */
@DisplayName("GlobalActions path validation")
class GlobalActionsPathValidationTest {

    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private WebSession webSession;
    private ActionsMgr mgr;
    private final List<String> sentMessages = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("shared"));
        Files.createDirectories(tempDir.resolve("users").resolve("admin"));
        webSession = TestWebSessions.newAdminSession("global-path-validation");
        sentMessages.clear();
        mgr = mock(ActionsMgr.class);
        when(mgr.getSession()).thenReturn(webSession);
        when(mgr.isOpen()).thenReturn(true);
        try {
            doAnswer(inv -> {
                sentMessages.add(inv.getArgument(0));
                return null;
            }).when(mgr).send(anyString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
        System.clearProperty(JRM_DIR_PROP);
    }

    @Test
    @DisplayName("rejects absolute path outside sandbox for dir2dat.dst_file")
    void rejectsAbsoluteEscapeDst() {
        final Path outside = tempDir.resolve("..").resolve("outside-overwrite.dat").toAbsolutePath().normalize();
        setProperty(SettingsEnum.dir2dat_dst_file.toString(), outside.toString());

        assertThat(webSession.getUser().getSettings().getProperty(SettingsEnum.dir2dat_dst_file)).isNullOrEmpty();
        assertThat(sentMessages).hasSize(1);
        assertThat(sentMessages.get(0)).doesNotContain("outside-overwrite");
    }

    @Test
    @DisplayName("rejects traversal via %work for dir2dat.dst_file")
    void rejectsWorkTraversalDst() {
        setProperty(SettingsEnum.dir2dat_dst_file.toString(), "%work/../../outside.dat");

        assertThat(webSession.getUser().getSettings().getProperty(SettingsEnum.dir2dat_dst_file)).isNullOrEmpty();
    }

    @Test
    @DisplayName("accepts %work destination and stores canonical key")
    void acceptsWorkDst() {
        setProperty(SettingsEnum.dir2dat_dst_file.toString(), "%work/export/out.dat");

        assertThat(webSession.getUser().getSettings().getProperty(SettingsEnum.dir2dat_dst_file))
                .isEqualTo("%work/export/out.dat");
        assertThat(sentMessages.get(0)).contains(SettingsEnum.dir2dat_dst_file.toString());
    }

    @Test
    @DisplayName("accepts legacy enum name() key and canonicalizes storage")
    void acceptsLegacyEnumNameKey() {
        setProperty(SettingsEnum.dir2dat_dst_file.name(), "%work/legacy.dat");

        assertThat(webSession.getUser().getSettings().getProperty(SettingsEnum.dir2dat_dst_file))
                .isEqualTo("%work/legacy.dat");
        assertThat(webSession.getUser().getSettings().getProperty(SettingsEnum.dir2dat_dst_file.name(), null)).isNull();
    }

    @Test
    @DisplayName("non-admin cannot set dir2dat.dst_file under %shared")
    void nonAdminCannotWriteSharedDst() {
        webSession = TestWebSessions.newSession("global-path-user", "user", new String[] { "user" });
        when(mgr.getSession()).thenReturn(webSession);

        setProperty(SettingsEnum.dir2dat_dst_file.toString(), "%shared/evil.dat");

        assertThat(webSession.getUser().getSettings().getProperty(SettingsEnum.dir2dat_dst_file)).isNullOrEmpty();
    }

    private void setProperty(String key, String value) {
        final JsonObject params = new JsonObject();
        params.add(key, value);
        final JsonObject jso = new JsonObject();
        jso.add("cmd", "Global.setProperty");
        jso.add("params", params);
        new GlobalActions(mgr).setProperty(jso);
    }
}
