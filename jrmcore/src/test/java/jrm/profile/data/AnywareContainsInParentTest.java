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

/**
 * Tests for {@link Anyware#containsInParent} parent-chain walking.
 */
@DisplayName("Anyware.containsInParent")
class AnywareContainsInParentTest {

    private Profile profile;

    @BeforeEach
    void setUp() {
        profile = mock(Profile.class);
        final var settings = mock(ProfileSettings.class);
        when(profile.getSettings()).thenReturn(settings);
        when(settings.getImplicitMerge()).thenReturn(true);
        when(profile.getProperty(anyString(), eq(true))).thenReturn(true);
    }

    @Test
    @DisplayName("finds a ROM on a selected parent")
    void findsRomOnParent() {
        final var parent = machine("parent");
        final var child = machine("child");
        child.setParent(parent);
        final var rom = new Rom(child);
        rom.setMerge("shared");
        parent.getRoms().add(rom);

        assertThat(child.containsInParent(child, rom, false)).isTrue();
    }

    @Test
    @DisplayName("finds a disk on a selected parent")
    void findsDiskOnParent() {
        final var parent = machine("parent");
        final var child = machine("child");
        child.setParent(parent);
        final var disk = new Disk(child);
        disk.setMerge("shared");
        parent.getDisks().add(disk);

        assertThat(child.containsInParent(child, disk)).isTrue();
    }

    @Test
    @DisplayName("stops on a cyclic parent chain")
    void stopsOnCyclicParents() {
        final var a = machine("a");
        final var b = machine("b");
        a.setParent(b);
        b.setParent(a);
        final var rom = new Rom(a);
        rom.setMerge("missing");

        assertThatCode(() -> a.containsInParent(a, rom, false)).doesNotThrowAnyException();
        assertThat(a.containsInParent(a, rom, false)).isFalse();
    }

    private Machine machine(final String name) {
        final var machine = new Machine(profile);
        machine.setName(name);
        return machine;
    }
}
