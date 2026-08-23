package jrm.fx.ui.profile;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import jrm.fx.ui.MainFrame;
import jrm.profile.data.Disk;
import jrm.profile.data.EntityBase;
import jrm.profile.data.Rom;
import jrm.profile.data.Sample;

/**
 * Table cell that renders an entity's base name with a type-specific icon (ROM, disk, or sample).
 * <p>
 * Used for the name column in the profile viewer's entity details table.
 *
 * @since 2.5
 */
public class EntityNameCellFactory extends TableCell<EntityBase, EntityBase> {

	private final Image romSmall = MainFrame.getIcon("/jrm/resicons/rom_small.png");
	private final Image drive = MainFrame.getIcon("/jrm/resicons/icons/drive.png");
	private final Image sound = MainFrame.getIcon("/jrm/resicons/icons/sound.png");

	@Override
	protected void updateItem(final EntityBase item, final boolean empty) {
		if (item == null || empty) {
			setText("");
			setGraphic(null);
		} else {
			setText(item.getBaseName());
			final var i = switch (item) {
				case final Rom _ -> new ImageView(romSmall);
				case final Disk _ -> new ImageView(drive);
				case final Sample _ -> new ImageView(sound);
				default -> null;
			};
			if (i != null) {
				i.setPreserveRatio(true);
				i.getStyleClass().add("icon");
				setGraphic(i);
			}
		}
		setAlignment(Pos.CENTER_LEFT);
	}
}
