package jrm.fx.ui.controls;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.text.TextAlignment;

/**
 * A table cell that renders numeric size values right-aligned.
 * <p>
 * Used for entity size columns in profile viewer.
 *
 * @param <T> the row type of the table
 * @since 2.5
 */
public class SizeCellFactory<T> extends TableCell<T, Long> {

	@Override
	protected void updateItem(final Long item, final boolean empty) {
		if (item == null || empty)
			setText("");
		else
			setText(item.toString());
		setTextAlignment(TextAlignment.RIGHT);
		setAlignment(Pos.CENTER_RIGHT);
		setGraphic(null);
	}
}
