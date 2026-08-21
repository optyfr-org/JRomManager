package jrm.server.shared.datasources;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.server.shared.TestDataSets;
import jrm.server.shared.TestWebSessions;
import jrm.server.shared.WebSession;

/**
 * Unit tests for {@link ReportTreeXMLResponse}.
 */
@DisplayName("ReportTreeXMLResponse")
class ReportTreeXMLResponseTest {

    @TempDir
    Path workPath;

    private WebSession session;

    @BeforeEach
    void setUp() {
        TestWebSessions.setWorkPath(workPath);
        session = TestWebSessions.newAdminSession("report-tree-test");
    }

    @AfterEach
    void tearDown() {
        TestWebSessions.resetStaticState();
    }

    @Test
    @DisplayName("fetch root returns empty report structure")
    void fetchRootEmpty() throws Exception {
        final String xml = """
                <request>
                  <operationType>fetch</operationType>
                  <data>
                    <ParentID>0</ParentID>
                  </data>
                </request>
                """;
        final String output = TestDataSets.processResponse(new ReportTreeXMLResponse(TestDataSets.xmlRequest(session, xml)));
        assertThat(output)
            .contains("<status>0</status>")
            .contains("<totalRows>0</totalRows>")
            .contains("<infos>")
            .contains("<summary>");
    }

    @Test
    @DisplayName("copy custom operation returns copyable report text")
    void copyReportText() throws Exception {
        final String xml = """
                <request>
                  <operationType>custom</operationType>
                  <operationId>copy</operationId>
                  <data>
                    <ParentID>0</ParentID>
                  </data>
                </request>
                """;
        final String output = TestDataSets.processResponse(new ReportTreeXMLResponse(TestDataSets.xmlRequest(session, xml)));
        assertThat(output).contains("<status>0</status>").contains("<Text>");
    }

}
