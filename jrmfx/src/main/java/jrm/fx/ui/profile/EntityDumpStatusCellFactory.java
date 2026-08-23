package jrm.fx.ui.profile;

import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import jrm.fx.ui.MainFrame;
import jrm.profile.data.Disk;
import jrm.profile.data.Entity;
import jrm.profile.data.Entity.Status;
import jrm.profile.data.EntityBase;
import jrm.profile.data.Rom;

/**
 * Table cell that renders an entity's dump status (verified/good/baddump/nodump) as an icon.
 * <p>
 * Used in the dump status column of the profile entity table.
 *
 * @since 2.5
 */
public class EntityDumpStatusCellFactory extends TableCell<EntityBase, Status> {

	private static final Image verified = MainFrame.getIcon("/jrm/resicons/icons/star.png");
	private static final Image good = MainFrame.getIcon("/jrm/resicons/icons/tick.png");
	private static final Image baddump = MainFrame.getIcon("/jrm/resicons/icons/delete.png");
	private static final Image nodump = MainFrame.getIcon("/jrm/resicons/icons/error.png");

	@Override
	protected void updateItem(final Entity.Status item, final boolean empty) {
		if (item == null || empty) {
			setText("");
			setGraphic(null);
		} else {
			final ImageView i = new ImageView(switch (item) {
				case baddump -> baddump;
				case good -> good;
				case nodump -> nodump;
				case verified -> verified;
				default -> null;
			});
			i.setPreserveRatio(true);
			i.getStyleClass().add("icon");
			setGraphic(i);
		}
		setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		setAlignment(Pos.CENTER);
	}
}
