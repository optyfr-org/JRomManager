/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.basic;

import java.awt.Graphics;

import javax.swing.Icon;
import javax.swing.JCheckBox;

import jrm.ui.MainFrame;

/**
 * A checkbox component that supports three visual states: selected, unselected, and half-selected (indeterminate).
 * <p>
 * This checkbox extends {@link JCheckBox} to add a tri-state rendering capability. When in the half-selected
 * state, a distinct icon is displayed to indicate partial selection, commonly used in tree structures
 * where some (but not all) children are selected.
 * </p>
 *
 * @see JCheckBox
 */
public class JTristateCheckBox extends JCheckBox {

    /** Whether this checkbox is in the half-selected (indeterminate) state. */
    private boolean halfState;

    /** The icon displayed when the checkbox is selected. */
    private static Icon selected = MainFrame.getIcon("/jrm/resicons/selected.png"); //$NON-NLS-1$

    /** The icon displayed when the checkbox is unselected. */
    private static Icon unselected = MainFrame.getIcon("/jrm/resicons/unselected.png"); //$NON-NLS-1$

    /** The icon displayed when the checkbox is in the half-selected state. */
    private static Icon halfselected = MainFrame.getIcon("/jrm/resicons/halfselected.png"); //$NON-NLS-1$

    /**
     * Constructs a new tristate checkbox with no text and no initial selection.
     */
    public JTristateCheckBox() {
        super();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the appropriate icon based on the current selection and half-selection state before painting.
     * If the checkbox is selected, the half-state is cleared.
     * </p>
     *
     * @param g the {@link Graphics} context to paint with
     */
    @Override
    public void paint(final Graphics g) {
        final Icon icon;
        if (isSelected()) {
            halfState = false;
            icon = JTristateCheckBox.selected;
        } else if (halfState)
            icon = JTristateCheckBox.halfselected;
        else
            icon = JTristateCheckBox.unselected;
        setIcon(icon);
        super.paint(g);
    }

    /**
     * Checks whether this checkbox is in the half-selected (indeterminate) state.
     *
     * @return {@code true} if this checkbox is half-selected, {@code false} otherwise
     */
    public boolean isHalfSelected() {
        return halfState;
    }

    /**
     * Sets or clears the half-selected (indeterminate) state.
     * <p>
     * When set to {@code true}, the checkbox is deselected and the half-selected icon is displayed.
     * </p>
     *
     * @param halfState {@code true} to enter the half-selected state, {@code false} to clear it
     */
    public void setHalfSelected(final boolean halfState) {
        this.halfState = halfState;
        if (halfState) {
            setSelected(false);
            repaint();
        }
    }
}
