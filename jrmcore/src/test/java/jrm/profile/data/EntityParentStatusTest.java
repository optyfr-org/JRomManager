package jrm.profile.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jrm.misc.ProfileSettings;
import jrm.profile.Profile;
import jrm.profile.scan.options.MergeOptions;

/**
 * Tests that disk/ROM status resolution does not recurse without bounds.
 */
@DisplayName("Entity parent status walk")
class EntityParentStatusTest {

    private Profile profile;

    @BeforeEach
    void setUp() {
        profile = mock(Profile.class);
        final var settings = mock(ProfileSettings.class);
        when(profile.getSettings()).thenReturn(settings);
        when(settings.getMergeMode()).thenReturn(MergeOptions.SPLIT);
        when(profile.getProperty(anyString(), eq(true))).thenReturn(true);
    }

    @Test
    @DisplayName("disk getStatus stops on a cyclic parent chain")
    void diskStatusStopsOnCycle() {
        final var a = machine("a");
        final var b = machine("b");
        a.setParent(b);
        b.setParent(a);
        final var disk = new Disk(a);
        disk.setSha1("abc");
        a.getDisks().add(disk);

        assertThatCode(disk::getStatus).doesNotThrowAnyException();
        assertThat(disk.getStatus()).isEqualTo(EntityStatus.UNKNOWN);
    }

    @Test
    @DisplayName("rom getStatus stops on a cyclic parent chain")
    void romStatusStopsOnCycle() {
        final var a = machine("a");
        final var b = machine("b");
        a.setParent(b);
        b.setParent(a);
        final var rom = new Rom(a);
        rom.setName("foo");
        rom.setCrc("12345678");
        a.getRoms().add(rom);

        assertThatCode(rom::getStatus).doesNotThrowAnyException();
        assertThat(rom.getStatus()).isEqualTo(EntityStatus.UNKNOWN);
    }

    private Machine machine(final String name) {
        final var machine = new Machine(profile);
        machine.setName(name);
        return machine;
    }
}
