package jrm.fx.ui.controls;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;

/**
 * A table cell that renders hash strings (CRC, MD5, SHA-1) using monospaced font.
 * <p>
 * Used in profile entity tables for consistent fixed-width hash display.
 *
 * @param <T> the row type of the table
 * @since 2.5
 */
public class HashCellFactory<T> extends TableCell<T, String> {

	@Override
	protected void updateItem(final String item, final boolean empty) {
		if (item == null || empty)
			setText("");
		else
			setText(item);
		styleProperty().bind(new SimpleStringProperty("-fx-font-family: monospaced;"));
		setAlignment(Pos.CENTER_LEFT);
		setGraphic(null);
	}
}
