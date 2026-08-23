package jrm.fx.ui.profile;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareList;

/**
 * Table cell for software/machine list description, with tooltip.
 *
 * @since 2.5
 */
public class WareListDescCellFactory extends TableCell<AnywareList<? extends Anyware>, String> {

	@Override
	protected void updateItem(final String item, final boolean empty) {
		if (empty)
			setText("");
		else
			setText(item);
		setTooltip(new Tooltip(getText()));
		setAlignment(Pos.CENTER_LEFT);
		setGraphic(null);
	}
}
