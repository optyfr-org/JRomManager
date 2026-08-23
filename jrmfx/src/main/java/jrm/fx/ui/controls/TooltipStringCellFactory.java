package jrm.fx.ui.controls;

import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;

/**
 * Simple table cell that shows text with a tooltip on non-empty items.
 *
 * @param <T> row type
 * @since 2.5
 */
public class TooltipStringCellFactory<T> extends TableCell<T, String> {

	@Override
	protected void updateItem(final String item, final boolean empty) {
		if (empty)
			setText("");
		else
			setText(item);
		setTooltip(new Tooltip(getText()));
		setAlignment(javafx.geometry.Pos.CENTER_LEFT);
		setGraphic(null);
	}
}
