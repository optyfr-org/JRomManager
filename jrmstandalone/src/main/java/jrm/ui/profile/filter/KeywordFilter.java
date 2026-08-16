/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.profile.filter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.activation.ActivationDataFlavor;
import javax.activation.DataHandler;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.TransferHandler;
import javax.swing.border.TitledBorder;

import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareList;
import jrm.profile.filter.Keywords.KFCallBack;
import jrm.ui.MainFrame;

/**
 * Modal dialog for filtering machines/software by keywords.
 * <p>
 * Provides a dual-list interface where users can select keywords to include
 * or exclude from the filter. Supports drag-and-drop for moving keywords
 * between lists.
 */
public class KeywordFilter extends JDialog {

    /** Source keyword list showing available keywords. */
    private JList<String> keywordSrc;

    /** Destination keyword list showing selected filter keywords. */
    private JList<String> keywordDst;

    /** List model for the destination keyword list. */
    private DefaultListModel<String> dstmodel = new DefaultListModel<>();

    /**
     * Instantiates a new keyword filter.
     *
     * @param owner the owner
     * @param src the src
     * @param callback the callback
     */
    public KeywordFilter(Window owner, String[] src, AnywareList<Anyware> list, KFCallBack callback) {
        super(owner, Messages.getString("KeywordFilter.Title"), ModalityType.APPLICATION_MODAL); //$NON-NLS-1$
        setIconImage(MainFrame.getIcon("/jrm/resicons/rom.png").getImage()); //$NON-NLS-1$

        JPanel panel = new JPanel();
        getContentPane().add(panel, BorderLayout.SOUTH);
        GridBagLayout gblPanel = new GridBagLayout();
        gblPanel.columnWidths = new int[] { 217, 217, 0 };
        gblPanel.rowHeights = new int[] { 23, 0 };
        gblPanel.columnWeights = new double[] { 1.0, 1.0, Double.MIN_VALUE };
        gblPanel.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
        panel.setLayout(gblPanel);

        JButton btnCancel = new JButton(Messages.getString("KeywordFilter.Cancel")); //$NON-NLS-1$
        btnCancel.addActionListener(_ -> dispose());
        btnCancel.setHorizontalAlignment(SwingConstants.LEADING);
        GridBagConstraints gbcBtnCancel = new GridBagConstraints();
        gbcBtnCancel.anchor = GridBagConstraints.WEST;
        gbcBtnCancel.fill = GridBagConstraints.VERTICAL;
        gbcBtnCancel.insets = new Insets(5, 5, 5, 5);
        gbcBtnCancel.gridx = 0;
        gbcBtnCancel.gridy = 0;
        panel.add(btnCancel, gbcBtnCancel);

        JButton btnFilter = new JButton(Messages.getString("KeywordFilter.Filter")); //$NON-NLS-1$
        btnFilter.addActionListener(_ -> {
            callback.call(list, getFilter());
            dispose();
        });
        btnFilter.setHorizontalAlignment(SwingConstants.TRAILING);
        GridBagConstraints gbcBtnFilter = new GridBagConstraints();
        gbcBtnFilter.insets = new Insets(5, 5, 5, 5);
        gbcBtnFilter.anchor = GridBagConstraints.EAST;
        gbcBtnFilter.fill = GridBagConstraints.VERTICAL;
        gbcBtnFilter.gridx = 1;
        gbcBtnFilter.gridy = 0;
        panel.add(btnFilter, gbcBtnFilter);

        JSplitPane keywordSplitPane = new JSplitPane();
        keywordSplitPane.setResizeWeight(0.5);
        keywordSplitPane.setOneTouchExpandable(true);
        keywordSplitPane.setContinuousLayout(true);
        getContentPane().add(keywordSplitPane, BorderLayout.CENTER);

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setBorder(new TitledBorder(null, Messages.getString("KeywordFilter.Available"), TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0))); //$NON-NLS-1$
        keywordSplitPane.setLeftComponent(scrollPane);

        keywordSrc = new JList<>();
        keywordSrc.setVisibleRowCount(16);
        scrollPane.setViewportView(keywordSrc);
        keywordSrc.setDragEnabled(true);
        keywordSrc.setDropMode(DropMode.INSERT);
        StringMoveHandler.createFor(keywordSrc);
        DefaultListModel<String> model = new DefaultListModel<>();
        for (String s : src)
            model.addElement(s);
        keywordSrc.setModel(model);

        JScrollPane scrollPane1 = new JScrollPane();
        scrollPane1.setBorder(new TitledBorder(null, Messages.getString("KeywordFilter.Used"), TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0))); //$NON-NLS-1$
        keywordSplitPane.setRightComponent(scrollPane1);

        keywordDst = new JList<>();
        keywordDst.setVisibleRowCount(16);
        scrollPane1.setViewportView(keywordDst);

        keywordDst.setDragEnabled(true);
        keywordDst.setDropMode(DropMode.INSERT);
        StringMoveHandler.createFor(keywordDst);
        keywordDst.setModel(dstmodel);

        pack();
        setLocationRelativeTo(owner);
        setVisible(true);
    }

    /**
     * Gets the filter.
     *
     * @return the filter
     */
    public List<String> getFilter() {
        return Collections.list(dstmodel.elements());
    }

    /**
     * The Interface CallBack.
     */
    public interface CallBack {

        /**
         * Call.
         *
         * @param filter the filter
         */
        public void call(KeywordFilter filter);
    }

    /**
     * The Class StringMoveHandler.
     */
    private static class StringMoveHandler extends TransferHandler {

        /** The object array flavor. */
        private DataFlavor objectArrayFlavor = new ActivationDataFlavor(Object[].class, DataFlavor.javaJVMLocalObjectMimeType, "Array of items"); //$NON-NLS-1$

        /** The list. */
        // We'll be moving the strings of this list
        private JList<String> list;

        /**
         * Instantiates a new string move handler.
         */
        // Clients should use a static factory method to instantiate the handler
        private StringMoveHandler() {
        }

        /**
         * Creates the for.
         *
         * @param list the list
         * 
         * @return the string move handler
         */
        public static StringMoveHandler createFor(JList<String> list) {
            StringMoveHandler handler = new StringMoveHandler();
            list.setTransferHandler(handler);
            handler.list = list;
            return handler;
        }

        @Override
        public boolean canImport(TransferSupport info) {
            return info.isDataFlavorSupported(objectArrayFlavor);
        }

        @Override
        public boolean importData(TransferSupport transferSupport) {
            Transferable t = transferSupport.getTransferable();
            boolean success = false;
            try {
                Object[] importedData = (Object[]) t.getTransferData(objectArrayFlavor);
                addToListModel(importedData);
                success = true;
            } catch (UnsupportedFlavorException | IOException e) {
                Log.err(e.getMessage(), e);
            }
            return success;
        }

        /**
         * Adds the to list model.
         *
         * @param importedData the imported data
         */
        private void addToListModel(Object[] importedData) {
            JList.DropLocation loc = list.getDropLocation();
            int dropIndex = loc.getIndex();

            DefaultListModel<String> listModel = (DefaultListModel<String>) list.getModel();
            for (int i = 0; i < importedData.length; i++) {
                final Object elem = importedData[i];
                if (elem instanceof String s) {
                    listModel.add(dropIndex + i, s);
                } else {
                    Log.err(() -> "Imported data contained something else than strings: " + elem); //$NON-NLS-1$
                }
            }
        }

        @Override
        public int getSourceActions(JComponent c) {
            return TransferHandler.MOVE;
        }

        @Override
        public Transferable createTransferable(JComponent source) {
            // We need the values from the list as an object array, otherwise the data
            // flavor won't match in importData
            @SuppressWarnings("deprecation")
            Object[] valuesToTransfer = list.getSelectedValues();
            return new DataHandler(valuesToTransfer, objectArrayFlavor.getMimeType());
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            if (action == TransferHandler.MOVE) {
                try {
                    Object[] exportedData = (Object[]) data.getTransferData(objectArrayFlavor);
                    removeFromListModel(exportedData);
                } catch (UnsupportedFlavorException | IOException e) {
                    Log.err(e.getMessage(), e);
                }
            }
        }

        /**
         * Removes the from list model.
         *
         * @param dataToRemove the data to remove
         */
        private void removeFromListModel(Object[] dataToRemove) {
            DefaultListModel<String> listModel = (DefaultListModel<String>) list.getModel();
            for (Object elemToRemove : dataToRemove) {
                boolean removedSuccessfully = listModel.removeElement(elemToRemove);
                if (!removedSuccessfully) {
                    Log.err("Source model did not contain exported data"); //$NON-NLS-1$
                }
            }
        }
    }

}
