package jrm.fx.ui.profile;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.text.TextAlignment;
import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareList;

/**
 * Table cell for have-count (e.g. "12/34") display, centered.
 *
 * @since 2.5
 */
public class WareListHaveCellFactory extends TableCell<AnywareList<? extends Anyware>, String> {

	@Override
	protected void updateItem(final String item, final boolean empty) {
		if (empty)
			setText("");
		else
			setText(item);
		setTextAlignment(TextAlignment.CENTER);
		setAlignment(Pos.CENTER);
		setGraphic(null);
	}
}
