package jrm.ui.basic;

import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * A table cell renderer that centers text horizontally within cells.
 * <p>
 * This renderer extends {@link DefaultTableCellRenderer} and sets the horizontal alignment
 * to {@link SwingConstants#CENTER}, providing centered text display for table cells.
 * </p>
 *
 * @see DefaultTableCellRenderer
 * @see SwingConstants#CENTER
 */
public class CenteredTableCellRenderer extends DefaultTableCellRenderer {

    /**
     * Constructs a new centered table cell renderer.
     * <p>
     * Sets the horizontal alignment to center.
     * </p>
     */
    public CenteredTableCellRenderer() {
        setHorizontalAlignment(SwingConstants.CENTER);
    }

}
