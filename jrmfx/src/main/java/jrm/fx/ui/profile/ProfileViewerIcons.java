package jrm.fx.ui.profile;

import javafx.scene.image.Image;
import jrm.fx.ui.MainFrame;
import jrm.profile.data.AnywareStatus;

final class ProfileViewerIcons {
	private ProfileViewerIcons() {
		/* This utility class should not be instantiated */
	}

	static final Image diskMultipleGreen = MainFrame.getIcon("/jrm/resicons/disk_multiple_green.png");
	static final Image diskMultipleOrange = MainFrame.getIcon("/jrm/resicons/disk_multiple_orange.png");
	static final Image diskMultipleRed = MainFrame.getIcon("/jrm/resicons/disk_multiple_red.png");
	static final Image diskMultipleGray = MainFrame.getIcon("/jrm/resicons/disk_multiple_gray.png");
	static final Image folderClosedGreen = MainFrame.getIcon("/jrm/resicons/folder_closed_green.png");
	static final Image folderClosedOrange = MainFrame.getIcon("/jrm/resicons/folder_closed_orange.png");
	static final Image folderClosedRed = MainFrame.getIcon("/jrm/resicons/folder_closed_red.png");
	static final Image folderClosedGray = MainFrame.getIcon("/jrm/resicons/folder_closed_gray.png");
	static final Image bulletGreen = MainFrame.getIcon("/jrm/resicons/icons/bullet_green.png");
	static final Image bulletRed = MainFrame.getIcon("/jrm/resicons/icons/bullet_red.png");
	static final Image bulletBlack = MainFrame.getIcon("/jrm/resicons/icons/bullet_black.png");

	static Image getStatusIcon(final AnywareStatus status) {
		return switch (status) {
			case COMPLETE -> folderClosedGreen;
			case PARTIAL -> folderClosedOrange;
			case MISSING -> folderClosedRed;
			case UNKNOWN -> folderClosedGray;
			default -> folderClosedGray;
		};
	}
}
