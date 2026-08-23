package jrm.fx.ui.profile;

import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import jrm.fx.ui.MainFrame;
import jrm.profile.data.EntityBase;

/**
 * Table cell that renders an entity's status as a colored bullet icon (green/red/black).
 * <p>
 * Used for the status column in the profile viewer's entity (ROM/disk/sample) table.
 *
 * @since 2.5
 */
public class EntityStatusCellFactory extends TableCell<EntityBase, EntityBase> {

	private static final Image bulletGreen = MainFrame.getIcon("/jrm/resicons/icons/bullet_green.png");
	private static final Image bulletRed = MainFrame.getIcon("/jrm/resicons/icons/bullet_red.png");
	private static final Image bulletBlack = MainFrame.getIcon("/jrm/resicons/icons/bullet_black.png");

	@Override
	protected void updateItem(final EntityBase item, final boolean empty) {
		if (item == null || empty) {
			setText("");
			setGraphic(null);
		} else {
			final var i = new ImageView(switch (item.getStatus()) {
				case KO -> bulletRed;
				case OK -> bulletGreen;
				case UNKNOWN -> bulletBlack;
				default -> bulletBlack;
			});
			i.setPreserveRatio(true);
			i.getStyleClass().add("icon");
			setGraphic(i);
		}
		setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		setAlignment(Pos.CENTER);
	}
}
