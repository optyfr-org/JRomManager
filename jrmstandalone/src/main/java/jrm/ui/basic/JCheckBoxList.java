/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.basic;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Predicate;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import jrm.misc.Ideone;

/**
 * A list component that renders each item as a tristate checkbox.
 * <p>
 * This component extends {@link JList} to provide checkbox-based selection for each element.
 * Items can be individually toggled by clicking, and bulk operations (select all, select none,
 * invert selection, and predicate-based selection) are supported. Uses {@link JTristateCheckBox}
 * to support partial selection states.
 * </p>
 *
 * @param <E> the type of elements in this list
 * @see JTristateCheckBox
 * @see JList
 */
public class JCheckBoxList<E> extends JList<E> {

    /** The border used for cells that do not have focus. */
    protected static final Border noFocusBorder = new EmptyBorder(1, 1, 1, 1);

    /**
     * Constructs a new checkbox list with an empty model.
     * <p>
     * Sets up the cell renderer and mouse listener for checkbox toggling.
     * </p>
     */
    public JCheckBoxList() {
        setCellRenderer(new CellRenderer());

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent e) {
                final int index = locationToIndex(e.getPoint());

                if (index != -1 && e.getButton() == MouseEvent.BUTTON1) {
                    checkboxes[index].setSelected(!checkboxes[index].isSelected());
                    for (final ListSelectionListener l : getListSelectionListeners())
                        l.valueChanged(new ListSelectionEvent(this, index, index, false));
                    repaint();
                }
                super.mousePressed(e);
            }
        });

        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Also enables or disables the list based on whether the model has elements.
     * </p>
     *
     * @param model the {@link ListModel} to set
     */
    @Override
    public void setModel(final ListModel<E> model) {
        super.setModel(model);
        setEnabled(model.getSize() > 0);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the selection state of the checkbox at the given index.
     * </p>
     *
     * @param index the index of the item to check
     * @return {@code true} if the checkbox at the given index is selected
     */
    @Override
    public boolean isSelectedIndex(final int index) {
        return checkboxes[index].isSelected();
    }

    /**
     * Selects all checkboxes in the list.
     */
    public void selectAll() {
        for (final JCheckBox checkbox : checkboxes)
            checkbox.setSelected(true);
        for (final ListSelectionListener l : getListSelectionListeners())
            l.valueChanged(new ListSelectionEvent(this, 0, checkboxes.length - 1, false));
        repaint();
    }

    /**
     * Deselects all checkboxes in the list.
     */
    public void selectNone() {
        for (final JCheckBox checkbox : checkboxes)
            checkbox.setSelected(false);
        for (final ListSelectionListener l : getListSelectionListeners())
            l.valueChanged(new ListSelectionEvent(this, 0, checkboxes.length - 1, false));
        repaint();
    }

    /**
     * Inverts the selection state of all checkboxes in the list.
     */
    public void selectInvert() {
        for (final JCheckBox checkbox : checkboxes)
            checkbox.setSelected(!checkbox.isSelected());
        for (final ListSelectionListener l : getListSelectionListeners())
            l.valueChanged(new ListSelectionEvent(this, 0, checkboxes.length - 1, false));
        repaint();
    }

    /**
     * Selects or deselects items matching the given predicate.
     *
     * @param predicate the {@link Predicate} to test each element against
     * @param selected {@code true} to select matching items, {@code false} to deselect them
     */
    public void select(Predicate<E> predicate, boolean selected) {
        Ideone ideone = new Ideone();
        for (int i = 0; i < checkboxes.length; i++) {
            if (predicate.test(getModel().getElementAt(i))) {
                checkboxes[i].setSelected(selected);
                ideone.add(i, i + 1);
            }
        }
        ideone.merge().forEach(i -> {
            for (final ListSelectionListener l : getListSelectionListeners())
                l.valueChanged(new ListSelectionEvent(this, i.getStart(), i.getEnd() - 1, false));
        });
        repaint();
    }

    /** The array of tristate checkboxes, one per list element. */
    JTristateCheckBox[] checkboxes = null;

    /**
     * Cell renderer that displays each list element as a {@link JTristateCheckBox}.
     */
    public class CellRenderer implements ListCellRenderer<E> {

        /**
         * {@inheritDoc}
         * <p>
         * Returns a {@link JTristateCheckBox} configured with the element's text, selection state,
         * and visual styling based on the list's selection and focus state.
         * </p>
         *
         * @param list the enclosing {@link JList}
         * @param value the value at the given index
         * @param index the cell index
         * @param isSelected whether the cell is selected
         * @param cellHasFocus whether the cell has focus
         * @return the configured {@link Component} for rendering
         */
        @Override
        public Component getListCellRendererComponent(final JList<? extends E> list, final E value, final int index, final boolean isSelected, final boolean cellHasFocus) {
            if (checkboxes == null || checkboxes.length != list.getModel().getSize())
                checkboxes = new JTristateCheckBox[list.getModel().getSize()];
            if (checkboxes[index] == null) {
                checkboxes[index] = new JTristateCheckBox();
                checkboxes[index].setFont(getFont());
                checkboxes[index].setFocusPainted(false);
                checkboxes[index].setBorderPainted(true);
            }
            checkboxes[index].setText(value.toString());
            checkboxes[index].setEnabled(isEnabled());
            checkboxes[index].setBackground(isSelected ? getSelectionBackground() : getBackground());
            checkboxes[index].setForeground(isSelected ? getSelectionForeground() : getForeground());
            checkboxes[index].setBorder(isSelected ? UIManager.getBorder("List.focusCellHighlightBorder") : JCheckBoxList.noFocusBorder); //$NON-NLS-1$
            return checkboxes[index];
        }
    }
}
