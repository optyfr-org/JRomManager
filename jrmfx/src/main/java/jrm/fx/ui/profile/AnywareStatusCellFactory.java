package jrm.fx.ui.profile;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.image.ImageView;
import jrm.profile.data.Anyware;

/**
 * Table cell that renders ware (machine/software) status using folder icon graphic.
 *
 * @since 2.5
 */
public class AnywareStatusCellFactory extends TableCell<Anyware, Anyware> {

	@Override
	protected void updateItem(final Anyware item, final boolean empty) {
		if (item == null || empty)
			setGraphic(null);
		else {
			final ImageView i = new ImageView(ProfileViewerIcons.getStatusIcon(item.getStatus()));
			setGraphic(i);
			i.setPreserveRatio(true);
			i.getStyleClass().add("icon");
		}
		setAlignment(Pos.CENTER);
		setText("");
	}
}
