package jrm.fx.ui.profile;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.image.ImageView;
import jrm.profile.data.Anyware;
import jrm.profile.data.Samples;

/**
 * Cell for sample-of column.
 */
public class SampleOfCellFactory extends TableCell<Anyware, Object> {

	private static final javafx.scene.image.Image folderClosedGray = jrm.fx.ui.MainFrame.getIcon("/jrm/resicons/folder_closed_gray.png");

	@Override
	protected void updateItem(final Object item, final boolean empty) {
		if (item == null || empty) {
			setText("");
			setGraphic(null);
		} else if (item instanceof final Samples s) {
			final ImageView i = new ImageView(ProfileViewerIcons.getStatusIcon(s.getStatus()));
			i.setPreserveRatio(true);
			i.getStyleClass().add("icon");
			setGraphic(i);
			setText(s.getBaseName());
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
