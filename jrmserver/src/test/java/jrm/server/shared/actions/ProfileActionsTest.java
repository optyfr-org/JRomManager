package jrm.server.shared.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.eclipsesource.json.JsonObject;

import jrm.misc.ProfileSettingsEnum;
import jrm.server.shared.TestDataSets;
import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;

/**
 * Unit tests for {@link ProfileActions}.
 */
@DisplayName("ProfileActions")
class ProfileActionsTest {

    @TempDir
    Path workPath;

    private WebSession webSession;
    private ActionsMgr mgr;
    private final List<String> sentMessages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TestWebSessions.setWorkPath(workPath);
        webSession = TestWebSessions.newAdminSession("profile-actions-test");
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
    }

    @Nested
    @DisplayName("loaded")
    class LoadedTest {
        @Test
        @DisplayName("sends Profile.loaded with profile structure")
        void loadedWithProfile() {
            TestDataSets.loadA5200Profile(webSession);

            new ProfileActions(mgr).loaded(webSession.getCurrProfile());

            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Profile.loaded").contains("\"success\":true").contains("\"name\":");
        }

        @Test
        @DisplayName("sends Profile.loaded with success false when profile is null")
        void loadedWithNullProfile() {
            new ProfileActions(mgr).loaded(null);

            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Profile.loaded").contains("\"success\":false");
        }

        @Test
        @DisplayName("does not send when websocket is not open")
        void loadedNotOpen() {
            when(mgr.isOpen()).thenReturn(false);
            new ProfileActions(mgr).loaded(null);
            assertThat(sentMessages).isEmpty();
        }
    }

    @Nested
    @DisplayName("setProperty")
    class SetPropertyTest {
        @Test
        @DisplayName("sets boolean property on the current profile")
        void setBooleanProperty() {
            TestDataSets.loadA5200Profile(webSession);

            final JsonObject params = new JsonObject();
            params.add("filter_missing", true);
            final JsonObject jso = new JsonObject();
            jso.add("cmd", "Profile.setProperty");
            jso.add("params", params);

            new ProfileActions(mgr).setProperty(jso);

            assertThat(webSession.getCurrProfile().getProperty("filter_missing", false)).isTrue();
        }

        @Test
        @DisplayName("sets number and string properties on the current profile")
        void setNumberAndStringProperties() {
            TestDataSets.loadA5200Profile(webSession);

            final JsonObject params = new JsonObject();
            params.add("filter_missing", true);
            params.add("some_count", 42);
            params.add("some_path", "%work/roms");
            final JsonObject jso = new JsonObject();
            jso.add("cmd", "Profile.setProperty");
            jso.add("params", params);

            new ProfileActions(mgr).setProperty(jso);

            assertThat(webSession.getCurrProfile().getProperty("filter_missing", false)).isTrue();
            assertThat(webSession.getCurrProfile().getProperty("some_count", 0)).isEqualTo(42);
            assertThat(webSession.getCurrProfile().getProperty("some_path", "")).isEqualTo("%work/roms");
        }

        @Test
        @DisplayName("non-admin cannot set roms_dest_dir to %shared")
        void nonAdminCannotSetSharedDest() {
            webSession = TestWebSessions.newSession("profile-actions-user", "user", new String[] { "user" });
            when(mgr.getSession()).thenReturn(webSession);
            TestDataSets.loadA5200Profile(webSession);
            final String before = webSession.getCurrProfile().getProperty(ProfileSettingsEnum.roms_dest_dir);

            final JsonObject params = new JsonObject();
            params.add(ProfileSettingsEnum.roms_dest_dir.toString(), "%shared");
            final JsonObject jso = new JsonObject();
            jso.add("cmd", "Profile.setProperty");
            jso.add("params", params);

            new ProfileActions(mgr).setProperty(jso);

            assertThat(webSession.getCurrProfile().getProperty(ProfileSettingsEnum.roms_dest_dir)).isEqualTo(before);
            assertThat(sentMessages.stream().anyMatch(m -> m.contains("Write access denied"))).isTrue();
        }

        @Test
        @DisplayName("admin can set roms_dest_dir to %shared")
        void adminCanSetSharedDest() {
            TestDataSets.loadA5200Profile(webSession);

            final JsonObject params = new JsonObject();
            params.add(ProfileSettingsEnum.roms_dest_dir.toString(), "%shared");
            final JsonObject jso = new JsonObject();
            jso.add("cmd", "Profile.setProperty");
            jso.add("params", params);

            new ProfileActions(mgr).setProperty(jso);

            assertThat(webSession.getCurrProfile().getProperty(ProfileSettingsEnum.roms_dest_dir)).isEqualTo("%shared");
        }

    }

    @Nested
    @DisplayName("scanned and fixed")
    class ScannedAndFixedTest {
        @Test
        @DisplayName("scanned sends scan result summary with report flag")
        void scannedSendsSummary() {
            TestDataSets.loadA5200Profile(webSession);

            new ProfileActions(mgr).scanned(null, false);

            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Profile.scanned").contains("\"success\":false");
        }

        @Test
        @DisplayName("fixed sends fix result summary")
        void fixedSendsSummary() {
            new ProfileActions(mgr).fixed(null);

            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Profile.fixed").contains("\"success\":false");
        }

    }

    @Nested
    @DisplayName("imported")
    class ImportedTest {
        @Test
        @DisplayName("imported sends imported profile file info")
        void importedSendsFileInfo() {
            final File file = new File(workPath.toFile(), "profile.nfo");

            new ProfileActions(mgr).imported(file);

            assertThat(sentMessages).hasSize(1);
            assertThat(sentMessages.get(0)).contains("Profile.imported").contains("\"name\":\"profile.nfo\"");
        }
    }
}
