package jrm.fx.ui.profile;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import jrm.fx.ui.MainFrame;
import jrm.locale.Messages;
import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareList;
import jrm.profile.data.AnywareStatus;
import jrm.profile.data.MachineList;
import jrm.profile.data.SoftwareList;

/**
 * Table cell for software/machine list name, with status icon (green/orange/red/gray disk).
 *
 * @since 2.5
 */
public class WareListNameCellFactory extends TableCell<AnywareList<? extends Anyware>, AnywareList<? extends Anyware>> {

	private static final Image diskMultipleGreen = MainFrame.getIcon("/jrm/resicons/disk_multiple_green.png");
	private static final Image diskMultipleOrange = MainFrame.getIcon("/jrm/resicons/disk_multiple_orange.png");
	private static final Image diskMultipleRed = MainFrame.getIcon("/jrm/resicons/disk_multiple_red.png");
	private static final Image diskMultipleGray = MainFrame.getIcon("/jrm/resicons/disk_multiple_gray.png");

	@Override
	protected void updateItem(final AnywareList<? extends Anyware> item, final boolean empty) {
		if (empty) {
			setText("");
			setGraphic(null);
		} else {
			final var i = new ImageView(switch (item.getStatus()) {
				case COMPLETE -> diskMultipleGreen;
				case PARTIAL -> diskMultipleOrange;
				case MISSING -> diskMultipleRed;
				case UNKNOWN -> diskMultipleGray;
				default -> diskMultipleGray;

			});
			i.setPreserveRatio(true);
			i.getStyleClass().add("icon");
			setGraphic(i);
			if (item instanceof final SoftwareList sl)
				setText(sl.getName());
			else if (item instanceof MachineList)
				setText(Messages.getString("MachineListListRenderer.*"));
		}
		setTooltip(new Tooltip(getText()));
		setAlignment(Pos.CENTER_LEFT);
	}
}
