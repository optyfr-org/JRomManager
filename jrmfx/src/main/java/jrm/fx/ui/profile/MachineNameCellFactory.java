package jrm.fx.ui.profile;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import jrm.fx.ui.MainFrame;
import jrm.profile.data.Anyware;
import jrm.profile.data.Machine;

/**
 * Cell factory for machine name column, with icon based on type (bios/device/mechanical/normal) and double-click to launch.
 *
 * @since 2.5
 */
public class MachineNameCellFactory extends TableCell<Anyware, Machine> {  // note: Anyware? wait Machine row? actually column is for Anyware but cast

	private static final Image applicationOSXTerminal = MainFrame.getIcon("/jrm/resicons/icons/application_osx_terminal.png");
	private static final Image computer = MainFrame.getIcon("/jrm/resicons/icons/computer.png");
	private static final Image wrench = MainFrame.getIcon("/jrm/resicons/icons/wrench.png");
	private static final Image joystick = MainFrame.getIcon("/jrm/resicons/icons/joystick.png");

	@Override
	protected void updateItem(final Machine item, final boolean empty) {
		if (empty) {
			setText("");
			setGraphic(null);
		} else {
			setText(item.getBaseName());
			setUserData(item);
			setTooltip(new Tooltip(item.getName()));
			final ImageView i;
			if (item.isIsbios())
				i = new ImageView(applicationOSXTerminal);
			else if (item.isIsdevice())
				i = new ImageView(computer);
			else if (item.isIsmechanical())
				i = new ImageView(wrench);
			else
				i = new ImageView(joystick);
			i.setPreserveRatio(true);
			i.getStyleClass().add("icon");
			setGraphic(i);
		}
		setAlignment(Pos.CENTER_LEFT);
	}
}
