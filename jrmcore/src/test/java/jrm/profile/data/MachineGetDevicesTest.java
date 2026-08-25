package jrm.profile.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jrm.misc.ProfileSettings;
import jrm.profile.Profile;

/**
 * Tests for {@link Machine#getDevices} iterative device-graph walk.
 */
@DisplayName("Machine.getDevices")
class MachineGetDevicesTest {

    private Profile profile;

    @BeforeEach
    void setUp() {
        profile = mock(Profile.class);
        final var settings = mock(ProfileSettings.class);
        when(profile.getSettings()).thenReturn(settings);
        when(profile.getProperty(anyString(), eq(true))).thenReturn(true);
    }

    @Test
    @DisplayName("adds this machine and direct devices when not recursing")
    void addsDirectDevicesWithoutRecurse() {
        final var root = machine("root");
        final var child = machine("child");
        final var grandchild = machine("grandchild");
        link(root, child);
        link(child, grandchild);

        final var machines = new HashSet<Machine>();
        root.getDevices(machines, false, false, false);

        assertThat(machines).containsExactlyInAnyOrder(root, child);
    }

    @Test
    @DisplayName("walks the device graph when recursing")
    void walksDeviceGraphWhenRecursing() {
        final var root = machine("root");
        final var child = machine("child");
        final var grandchild = machine("grandchild");
        link(root, child);
        link(child, grandchild);

        final var machines = new HashSet<Machine>();
        root.getDevices(machines, false, false, true);

        assertThat(machines).containsExactlyInAnyOrder(root, child, grandchild);
    }

    @Test
    @DisplayName("stops on a cyclic device graph")
    void stopsOnCyclicDevices() {
        final var a = machine("a");
        final var b = machine("b");
        link(a, b);
        link(b, a);

        final var machines = new HashSet<Machine>();
        assertThatCode(() -> a.getDevices(machines, false, false, true)).doesNotThrowAnyException();
        assertThat(machines).containsExactlyInAnyOrder(a, b);
    }

    @Test
    @DisplayName("does not exceed the device depth cap")
    void capsDeepDeviceChain() {
        final var extra = 20;
        final var chain = new Machine[Machine.MAX_DEVICE_DEPTH + extra + 1];
        for (int i = 0; i < chain.length; i++) {
            chain[i] = machine("m" + i);
            if (i > 0)
                link(chain[i - 1], chain[i]);
        }

        final var machines = new HashSet<Machine>();
        assertThatCode(() -> chain[0].getDevices(machines, false, false, true)).doesNotThrowAnyException();
        assertThat(machines).hasSize(Machine.MAX_DEVICE_DEPTH + 1);
        assertThat(machines).contains(chain[0], chain[Machine.MAX_DEVICE_DEPTH]);
        assertThat(machines).doesNotContain(chain[Machine.MAX_DEVICE_DEPTH + 1]);
    }

    @Test
    @DisplayName("does not walk devices of a BIOS when excludeBios is set")
    void skipsDevicesOfExcludedBios() {
        final var root = machine("root");
        final var bios = machine("bios");
        bios.setIsbios(true);
        final var nested = machine("nested");
        link(root, bios);
        link(bios, nested);

        final var machines = new HashSet<Machine>();
        root.getDevices(machines, true, false, true);

        assertThat(machines).containsExactlyInAnyOrder(root, bios);
    }

    @Test
    @DisplayName("partial mode keeps only devices listed in device_ref")
    void partialKeepsDeviceRefOnly() {
        final var root = machine("root");
        final var listed = machine("listed");
        final var omitted = machine("omitted");
        link(root, listed);
        link(root, omitted);
        root.getDeviceRef().add(listed.getName());

        final var machines = new HashSet<Machine>();
        root.getDevices(machines, false, true, false);

        assertThat(machines).containsExactlyInAnyOrder(root, listed);
    }

    private Machine machine(final String name) {
        final var machine = new Machine(profile);
        machine.setName(name);
        return machine;
    }

    private static void link(final Machine parent, final Machine device) {
        parent.getDeviceMachines().put(device.getName(), device);
    }
}
