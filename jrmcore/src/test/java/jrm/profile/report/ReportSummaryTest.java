package jrm.profile.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jrm.profile.Profile;
import jrm.profile.data.Machine;
import jrm.profile.data.MachineList;
import jrm.profile.data.MachineListList;
import jrm.profile.data.Rom;
import jrm.profile.data.SoftwareListList;
import jrm.profile.manager.Export.ExportType;

/**
 * Tests for report summary, missing/partial title listing, copyable text, and fixDAT type resolution.
 */
@DisplayName("Report summary and fixDAT helpers")
class ReportSummaryTest {

    private static Machine machine(String name) {
        final Profile profile = mock(Profile.class);
        when(profile.getSettings()).thenReturn(null);
        final Machine machine = new Machine(profile);
        machine.setName(name);
        machine.description.append(name);
        return machine;
    }

    private static SubjectSet missing(String name) {
        final SubjectSet ss = new SubjectSet(machine(name));
        ss.setMissing();
        return ss;
    }

    private static SubjectSet foundOk(String name) {
        final SubjectSet ss = new SubjectSet(machine(name));
        ss.setFound();
        return ss;
    }

    private static SubjectSet foundIncomplete(String name) {
        final Machine m = machine(name);
        final SubjectSet ss = new SubjectSet(m);
        ss.setFound();
        ss.add(new EntryMissing(new Rom(m)));
        return ss;
    }

    private static SubjectSet createPartial(String name) {
        final Machine m = machine(name);
        final SubjectSet ss = new SubjectSet(m);
        ss.setCreate();
        ss.add(new EntryMissing(new Rom(m)));
        return ss;
    }

    private static SubjectSet createComplete(String name) {
        final SubjectSet ss = new SubjectSet(machine(name));
        ss.setCreate();
        return ss;
    }

    @Nested
    @DisplayName("listIncompleteTitles")
    class ListIncompleteTitles {
        @Test
        @DisplayName("includes missing, found-incomplete, and partial-create titles")
        void includesMissingAndPartial() {
            final Report report = new Report();
            report.add(missing("alpha"));
            report.add(foundOk("beta"));
            report.add(foundIncomplete("gamma"));
            report.add(createPartial("delta"));
            report.add(createComplete("epsilon"));

            assertThat(report.listIncompleteTitles())
                    .extracting(SubjectSet::getWareName)
                    .containsExactly("alpha", "delta", "gamma");
        }

        @Test
        @DisplayName("returns empty list when every set is complete")
        void emptyWhenComplete() {
            final Report report = new Report();
            report.add(foundOk("okset"));
            report.add(createComplete("newset"));

            assertThat(report.listIncompleteTitles()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getSummaryText / toCopyableText")
    class SummaryText {
        @Test
        @DisplayName("starts with stats and lists missing titles")
        void summaryListsTitles() {
            final Report report = new Report();
            report.add(missing("pacman"));
            report.add(foundIncomplete("galaga"));

            final String summary = report.getSummaryText();
            assertThat(summary)
                .contains(report.getStats().getStatus())
                .contains("pacman")
                .contains("galaga")
                .contains("MISSING")
                .contains("PARTIAL");
        }

        @Test
        @DisplayName("copyable text includes summary and subject details")
        void copyableIncludesDetails() {
            final Report report = new Report();
            report.add(missing("dkong"));

            final String text = report.toCopyableText();
            assertThat(text).contains(report.getSummaryText().trim());
            assertThat(text).contains("dkong");
        }
    }

    @Nested
    @DisplayName("resolveFixDatType")
    class ResolveFixDatType {
        @Test
        @DisplayName("null profile defaults to DATAFILE")
        void nullProfile() {
            assertThat(Report.resolveFixDatType(null)).isEqualTo(ExportType.DATAFILE);
        }

        @Test
        @DisplayName("machine profile uses DATAFILE")
        void machineProfile() {
            final Profile profile = mock(Profile.class);
            final MachineListList lists = mock(MachineListList.class);
            final MachineList machines = mock(MachineList.class);
            final SoftwareListList software = mock(SoftwareListList.class);
            when(profile.getMachineListList()).thenReturn(lists);
            when(lists.isEmpty()).thenReturn(false);
            when(lists.get(0)).thenReturn(machines);
            when(machines.size()).thenReturn(3);
            when(lists.getSoftwareListList()).thenReturn(software);
            when(software.isEmpty()).thenReturn(true);

            assertThat(Report.resolveFixDatType(profile)).isEqualTo(ExportType.DATAFILE);
        }

        @Test
        @DisplayName("software-list-only profile uses SOFTWARELIST")
        void softwareOnly() {
            final Profile profile = mock(Profile.class);
            final MachineListList lists = mock(MachineListList.class);
            final MachineList machines = mock(MachineList.class);
            final SoftwareListList software = mock(SoftwareListList.class);
            when(profile.getMachineListList()).thenReturn(lists);
            when(lists.isEmpty()).thenReturn(false);
            when(lists.get(0)).thenReturn(machines);
            when(machines.size()).thenReturn(0);
            when(lists.getSoftwareListList()).thenReturn(software);
            when(software.isEmpty()).thenReturn(false);

            assertThat(Report.resolveFixDatType(profile)).isEqualTo(ExportType.SOFTWARELIST);
        }
    }
}
