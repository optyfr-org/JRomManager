package jrm.fx.ui.profile;

import java.awt.HeadlessException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import jrm.fx.ui.controls.Dialogs;
import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.profile.Profile;
import jrm.profile.data.Anyware;
import jrm.profile.data.Machine;
import jrm.profile.manager.ProfileNFOMame;
import jrm.profile.manager.ProfileNFOMame.MameStatus;
import jrm.profile.data.Software;
import jrm.profile.manager.MameExecutable;
import jrm.profile.manager.MameLaunch;

/**
 * Handles launching MAME for a selected machine or software entry from the profile viewer.
 * <p>
 * Validates executable, builds args using MameLaunch, starts process.
 *
 * @since 2.5
 */
public final class MameLauncher {

	private MameLauncher() {
	}

	/**
	 * Attempts to launch MAME for the ware if conditions are met.
	 *
	 * @param ware    selected ware (machine or software)
	 * @param profile current profile
	 */
	public static void launch(final Anyware ware, final Profile profile) {
		if (profile == null) {
			Dialogs.showAlert(Messages.getString("ProfileViewer.NoProfile"));
			return;
		}
		final ProfileNFOMame mame = profile.getNfo().getMame();
		if (mame.getStatus() != MameStatus.UPTODATE) {
			Dialogs.showAlert(String.format(Messages.getString("ProfileViewer.MameNotAvailableOrObsolete"), mame.getStatus()));
			return;
		}
		launchInternal(ware, profile);
	}

	private static void launchInternal(final Anyware ware, final Profile profile) throws HeadlessException {
		final ProfileNFOMame mame = profile.getNfo().getMame();

		if (mame.getFile() == null) {
			Dialogs.showAlert("MAME executable is not configured for this profile.");
			return;
		}

		if (!MameExecutable.isLaunchable(mame.getFile())) {
			Dialogs.showAlert("MAME executable does not exist or is not a native executable: " + mame.getFile().getAbsolutePath());
			return;
		}

		final var args = new ArrayList<String>();
		try {
			if (ware instanceof Software) {
				getMameArgsSoftware(ware, profile, mame, args);
			} else {
				getMameArgsMachine(ware, profile, mame, args);
			}
			if (!args.isEmpty()) {
				final ProcessBuilder pb = new ProcessBuilder(args).directory(mame.getFile().getParentFile()).redirectErrorStream(true)
						.redirectOutput(new File(mame.getFile().getParentFile(), "JRomManager.log"));
				pb.start().waitFor();
			}
		} catch (final IllegalArgumentException e1) {
			Dialogs.showAlert(e1.getMessage());
		} catch (final IOException e1) {
			Dialogs.showError(e1);
		} catch (final InterruptedException e1) {
			Dialogs.showError(e1);
			Thread.currentThread().interrupt();
		}
	}

	private static void getMameArgsMachine(final Anyware ware, final Profile profile, final ProfileNFOMame mame, final ArrayList<String> args) {
		args.addAll(MameLaunch.machine(mame.getFile(), ware.getBaseName(), mame.getFile().getParent(), MameLaunch.romPaths(profile, false)));
	}

	private static void getMameArgsSoftware(final Anyware ware, final Profile profile, final ProfileNFOMame mame, final ArrayList<String> args) throws HeadlessException {
		Log.debug(() -> ((Software) ware).getSl().getBaseName() + ", " + ((Software) ware).getCompatibility());
		final var machines = new javafx.scene.control.ChoiceDialog<Machine>(null,
				profile.getMachineListList().getSortedMachines(((Software) ware).getSl().getBaseName(), ((Software) ware).getCompatibility()));
		final var machine = machines.showAndWait();
		machine.ifPresent(m -> {
			final var device = MameLaunch.deviceInstance(ware, m);
			Log.debug(() -> "-> " + m.getBaseName() + " " + device + " " + ware.getBaseName());
			args.addAll(MameLaunch.software(mame.getFile(), m.getBaseName(), device, ware.getBaseName(), mame.getFile().getParent(),
					MameLaunch.romPaths(profile, true)));
		});
	}
}
