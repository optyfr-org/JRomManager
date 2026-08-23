package jrm.fx.ui.profile;

import java.util.Optional;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.image.ImageView;
import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareList;
import jrm.profile.data.MachineList;

/**
 * Cell for clone-of / rom-of / sample-of columns, showing icon + name, or gray for missing.
 * <p>
 * Value may be Anyware or plain String name.
 *
 * @since 2.5
 */
public class AnywareCloneCellFactory extends TableCell<Anyware, Object> {

	// Note: folderClosedGray accessed via controller or duplicate load
	private static final javafx.scene.image.Image folderClosedGray = jrm.fx.ui.MainFrame.getIcon("/jrm/resicons/folder_closed_gray.png");

	@Override
	protected void updateItem(final Object item, final boolean empty) {
		if (item == null || empty) {
			setText("");
			setGraphic(null);
		} else if (item instanceof final Anyware aw) {
			final ImageView i = new ImageView(ProfileViewerController.getStatusIcon(aw.getStatus()));
			i.setPreserveRatio(true);
			i.getStyleClass().add("icon");
			setGraphic(i);
			setUserData(aw);
			setText(aw.getBaseName());
		} else {
			final ImageView i = new ImageView(folderClosedGray);
			i.setPreserveRatio(true);
			i.getStyleClass().add("icon");
			setGraphic(i);
			setText(item.toString());
		}
		setAlignment(Pos.CENTER_LEFT);
	}
}
