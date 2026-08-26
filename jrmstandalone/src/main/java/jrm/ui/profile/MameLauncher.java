package jrm.ui.profile;

import java.awt.HeadlessException;
import java.io.File;
import java.io.IOException;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;

import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.profile.Profile;
import jrm.profile.data.Anyware;
import jrm.profile.data.Machine;
import jrm.profile.data.Software;
import jrm.profile.manager.MameExecutable;
import jrm.profile.manager.MameLaunch;
import jrm.profile.manager.ProfileNFOMame;
import jrm.profile.manager.ProfileNFOMame.MameStatus;

public final class MameLauncher {

	private static final String PROFILE_VIEWER_EXCEPTION = "ProfileViewer.Exception";

	private MameLauncher() {
	}

	/**
	 * Attempts to launch MAME for the ware if conditions are met.
	 *
	 * @param ware    selected ware (machine or software)
	 * @param profile current profile
	 */
	public static void launch(final Anyware ware, final Profile profile) throws HeadlessException {
		if (profile == null) {
			JOptionPane.showMessageDialog(null, Messages.getString("ProfileViewer.NoProfile"), Messages.getString(PROFILE_VIEWER_EXCEPTION), JOptionPane.ERROR_MESSAGE);
			return;
		}
		final ProfileNFOMame mame = profile.getNfo().getMame();
		if (mame.getStatus() != MameStatus.UPTODATE) {
			JOptionPane.showMessageDialog(null, String.format(Messages.getString("ProfileViewer.MameNotAvailableOrObsolete"), mame.getStatus()), Messages.getString(PROFILE_VIEWER_EXCEPTION), JOptionPane.ERROR_MESSAGE);
			return;
		}
		launchInternal(ware, profile);
	}

	private static void launchInternal(final Anyware ware, final Profile profile) throws HeadlessException {
		final ProfileNFOMame mame = profile.getNfo().getMame();

		if (mame.getFile() == null) {
			JOptionPane.showMessageDialog(null, "MAME executable is not configured for this profile.", Messages.getString(PROFILE_VIEWER_EXCEPTION), JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (!MameExecutable.isLaunchable(mame.getFile())) {
			JOptionPane.showMessageDialog(null, "MAME executable does not exist or is not a native executable: " + mame.getFile().getAbsolutePath(), Messages.getString(PROFILE_VIEWER_EXCEPTION), JOptionPane.ERROR_MESSAGE);
			return;
		}

		String[] args = null;
		try {
			if (ware instanceof Software) {
				args = getMameArgsSoftware(ware, profile, mame, args);
			} else {
				args = getMameArgsMachine(ware, profile, mame);
			}
			if (args != null) {
				final ProcessBuilder pb = new ProcessBuilder(args).directory(mame.getFile().getParentFile()).redirectErrorStream(true)
						.redirectOutput(new File(mame.getFile().getParentFile(), "JRomManager.log")); //$NON-NLS-1$
				pb.start().waitFor();
			}
		} catch (IllegalArgumentException | IOException e1) {
			JOptionPane.showMessageDialog(null, e1.getMessage(), Messages.getString(PROFILE_VIEWER_EXCEPTION), JOptionPane.ERROR_MESSAGE);
		}  catch (final InterruptedException e1) {
			JOptionPane.showMessageDialog(null, e1.getMessage(), Messages.getString(PROFILE_VIEWER_EXCEPTION), JOptionPane.ERROR_MESSAGE);
			Thread.currentThread().interrupt();
		}
	}

	private static String[] getMameArgsMachine(final Anyware ware, final Profile profile, final ProfileNFOMame mame) {
		return MameLaunch.machine(mame.getFile(), ware.getBaseName(), mame.getFile().getParent(), MameLaunch.romPaths(profile, false))
				.toArray(String[]::new);
	}

	private static String[] getMameArgsSoftware(final Anyware ware, final Profile profile, final ProfileNFOMame mame, String[] args) throws HeadlessException {
		Log.debug(() -> ((Software) ware).getSl().getBaseName() + ", " + ((Software) ware).getCompatibility()); //$NON-NLS-1$
		final JList<Machine> machines = new JList<>(
				profile.getMachineListList().getSortedMachines(((Software) ware).getSl().getBaseName(), ((Software) ware).getCompatibility()).toArray(new Machine[0]));
		machines.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		if (machines.getModel().getSize() > 0)
			machines.setSelectedIndex(0);
		JOptionPane.showMessageDialog(null, machines);
		final var machine = machines.getSelectedValue();
		if (machine != null) {
			final var device = MameLaunch.deviceInstance(ware, machine);
			Log.debug(() -> "-> " + machine.getBaseName() + " " + device + " " + ware.getBaseName()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			args = MameLaunch.software(mame.getFile(), machine.getBaseName(), device, ware.getBaseName(), mame.getFile().getParent(),
					MameLaunch.romPaths(profile, true)).toArray(String[]::new);
		}
		return args;
	}
}
