/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.profile;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TableModelEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.SerializationUtils;

import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.Profile;
import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareList;
import jrm.profile.data.AnywareStatus;
import jrm.profile.data.EntityStatus;
import jrm.profile.data.ExportMode;
import jrm.profile.data.Machine;
import jrm.profile.data.MachineList;
import jrm.profile.data.Software;
import jrm.profile.data.SoftwareList;
import jrm.profile.filter.Keywords;
import jrm.profile.manager.Export;
import jrm.profile.manager.Export.ExportType;
import jrm.profile.manager.ProfileNFOMame;
import jrm.profile.manager.ProfileNFOMame.MameStatus;
import jrm.security.Session;
import jrm.ui.MainFrame;
import jrm.ui.basic.JRMFileChooser;
import jrm.ui.basic.Popup;
import jrm.ui.profile.data.AnywareListListModel;
import jrm.ui.profile.data.AnywareListModel;
import jrm.ui.profile.data.AnywareModel;
import jrm.ui.profile.data.MachineListListModel;
import jrm.ui.profile.data.MachineListModel;
import jrm.ui.profile.data.SoftwareListModel;
import jrm.ui.profile.filter.KeywordFilter;
import jrm.ui.progress.SwingWorkerProgress;
import lombok.val;

/**
 * Dialog for viewing and managing profile data.
 * <p>
 * Displays machines, software lists, and anyware lists with filtering and export capabilities.
 */
@SuppressWarnings("serial")
public class ProfileViewer extends JDialog {

    /** Preference key for exporting as software lists DAT. */
    private static final String PROFILE_VIEWER_AS_SW_LISTS_DAT = "ProfileViewer.AsSWListsDat";
    /** Error message constant. */
    private static final String PROFILE_VIEWER_ERROR = "ProfileViewer.Error";

    /** Table for displaying entities (machines/software). */
    private JTable tableEntity;
    /** Table for displaying software lists. */
    private JTable tableW;
    /** Table for displaying anyware lists. */
    private JTable tableWL;

    /** Text field for searching/filtering the displayed data. */
    private JTextField txtSearch;

    /**
     * Constructs a new profile viewer dialog.
     *
     * @param session the security session for accessing profile data
     * @param owner the parent window
     * @param profile the profile to display
     */
    @SuppressWarnings("unchecked")
    public ProfileViewer(final Session session, final Window owner, final Profile profile) {
        super();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                session.getUser().getSettings().setProperty("ProfileViewer.Bounds", Hex.encodeHexString(SerializationUtils.serialize(getBounds()))); //$NON-NLS-1$
            }
        });
        setIconImage(MainFrame.getIcon("/jrm/resicons/rom.png").getImage()); //$NON-NLS-1$
        setTitle(Messages.getString("ProfileViewer.this.title")); //$NON-NLS-1$

        final var splitPane = new JSplitPane();
        splitPane.setResizeWeight(0.5);
        splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        getContentPane().add(splitPane, BorderLayout.CENTER);

        final var panelWare = new JPanel();
        splitPane.setLeftComponent(panelWare);
        panelWare.setLayout(new BorderLayout(0, 0));

        final var splitPaneWLW = new JSplitPane();
        splitPaneWLW.setOneTouchExpandable(true);
        splitPaneWLW.setContinuousLayout(true);
        splitPaneWLW.setResizeWeight(0.25);
        panelWare.add(splitPaneWLW, BorderLayout.CENTER);

        tableEntity = new JTable();
        tableEntity.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableEntity.setPreferredScrollableViewportSize(new Dimension(1200, 300));
        tableEntity.setShowGrid(false);
        tableEntity.setShowHorizontalLines(false);
        tableEntity.setShowVerticalLines(false);
        tableEntity.setRowSelectionAllowed(true);
        tableEntity.setFillsViewportHeight(true);
        tableEntity.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        final var panelW = new JPanel();
        splitPaneWLW.setRightComponent(panelW);
        panelW.setLayout(new BorderLayout(0, 0));

        final var toolBarW = new JToolBar();
        panelW.add(toolBarW, BorderLayout.SOUTH);

        final var tglbtnMissingW = new JToggleButton(""); //$NON-NLS-1$
        tglbtnMissingW.setSelected(true);
        tglbtnMissingW.setToolTipText(Messages.getString("ProfileViewer.tglbtnMissingW.toolTipText")); //$NON-NLS-1$
        tglbtnMissingW.setIcon(MainFrame.getIcon("/jrm/resicons/folder_closed_red.png")); //$NON-NLS-1$
        toolBarW.add(tglbtnMissingW);

        final var tglbtnPartialW = new JToggleButton(""); //$NON-NLS-1$
        tglbtnPartialW.setSelected(true);
        tglbtnPartialW.setToolTipText(Messages.getString("ProfileViewer.tglbtnPartialW.toolTipText")); //$NON-NLS-1$
        tglbtnPartialW.setIcon(MainFrame.getIcon("/jrm/resicons/folder_closed_orange.png")); //$NON-NLS-1$
        toolBarW.add(tglbtnPartialW);

        final var tglbtnCompleteW = new JToggleButton(""); //$NON-NLS-1$
        tglbtnCompleteW.setSelected(true);
        tglbtnCompleteW.setIcon(MainFrame.getIcon("/jrm/resicons/folder_closed_green.png")); //$NON-NLS-1$
        tglbtnCompleteW.setToolTipText(Messages.getString("ProfileViewer.tglbtnCompleteW.toolTipText")); //$NON-NLS-1$
        toolBarW.add(tglbtnCompleteW);

        final var panelSearch = new JPanel();
        panelSearch.setBorder(null);
        toolBarW.add(panelSearch);
        final var gblPanelSearch = new GridBagLayout();
        gblPanelSearch.columnWidths = new int[] { 286, 166, 0 };
        gblPanelSearch.rowHeights = new int[] { 20, 0 };
        gblPanelSearch.columnWeights = new double[] { 1.0, 0.0, Double.MIN_VALUE };
        gblPanelSearch.rowWeights = new double[] { 1.0, Double.MIN_VALUE };
        panelSearch.setLayout(gblPanelSearch);

        final var lblSearch = new JLabel(Messages.getString("ProfileViewer.lblSearch.text")); //$NON-NLS-1$
        final var gbcLblSearch = new GridBagConstraints();
        gbcLblSearch.insets = new Insets(0, 0, 0, 5);
        gbcLblSearch.anchor = GridBagConstraints.EAST;
        gbcLblSearch.gridx = 0;
        gbcLblSearch.gridy = 0;
        panelSearch.add(lblSearch, gbcLblSearch);

        txtSearch = new JTextField();
        txtSearch.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(final KeyEvent e) {
                search();
            }
        });
        final var gbcTxtSearch = new GridBagConstraints();
        gbcTxtSearch.fill = GridBagConstraints.VERTICAL;
        gbcTxtSearch.anchor = GridBagConstraints.WEST;
        gbcTxtSearch.gridx = 1;
        gbcTxtSearch.gridy = 0;
        panelSearch.add(txtSearch, gbcTxtSearch);
        txtSearch.setText(""); //$NON-NLS-1$
        txtSearch.setColumns(20);

        tglbtnMissingW.addItemListener(_ -> setFilterW(tglbtnMissingW.isSelected(), tglbtnPartialW.isSelected(), tglbtnCompleteW.isSelected()));
        tglbtnPartialW.addItemListener(_ -> setFilterW(tglbtnMissingW.isSelected(), tglbtnPartialW.isSelected(), tglbtnCompleteW.isSelected()));
        tglbtnCompleteW.addItemListener(_ -> setFilterW(tglbtnMissingW.isSelected(), tglbtnPartialW.isSelected(), tglbtnCompleteW.isSelected()));

        final var scrollPaneW = new JScrollPane();
        panelW.add(scrollPaneW);

        tableW = new JTable();
        scrollPaneW.setViewportView(tableW);
        tableW.setPreferredScrollableViewportSize(new Dimension(600, 400));
        tableW.setFillsViewportHeight(true);
        tableW.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableW.setShowGrid(false);
        tableW.setShowHorizontalLines(false);
        tableW.setShowVerticalLines(false);
        tableW.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        final var popupWMenu = new JPopupMenu();
        Popup.addPopup(tableW, popupWMenu);

        final var mntmCollectKeywords = new JMenuItem(Messages.getString("ProfileViewer.mntmCollectKeywords.text")); //$NON-NLS-1$
        mntmCollectKeywords.addActionListener(_ -> new KW().filter(((AnywareListModel<Anyware>) tableW.getModel()).getList()));
        popupWMenu.add(mntmCollectKeywords);

        final var mntmSelectNone = new JMenuItem(Messages.getString("ProfileViewer.mntmSelectNone.text")); //$NON-NLS-1$
        mntmSelectNone.addActionListener(_ -> {
            final AnywareListModel<Anyware> list = (AnywareListModel<Anyware>) tableW.getModel();
            list.getList().getFilteredStream().forEach(ware -> ware.setSelected(false));
            list.fireTableChanged(new TableModelEvent(list, 0, list.getRowCount() - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE));
        });
        popupWMenu.add(mntmSelectNone);

        final var mntmSelectAll = new JMenuItem(Messages.getString("ProfileViewer.mntmSelectAll.text")); //$NON-NLS-1$
        mntmSelectAll.addActionListener(_ -> {
            final AnywareListModel<Anyware> list = (AnywareListModel<Anyware>) tableW.getModel();
            list.getList().getFilteredStream().forEach(ware -> ware.setSelected(true));
            list.fireTableChanged(new TableModelEvent(list, 0, list.getRowCount() - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE));
        });
        popupWMenu.add(mntmSelectAll);

        final var mntmSelectInvert = new JMenuItem(Messages.getString("ProfileViewer.mntmSelectInvert.text")); //$NON-NLS-1$
        mntmSelectInvert.addActionListener(_ -> {
            final AnywareListModel<Anyware> list = (AnywareListModel<Anyware>) tableW.getModel();
            list.getList().getFilteredStream().forEach(ware -> ware.setSelected(!ware.isSelected()));
            list.fireTableChanged(new TableModelEvent(list, 0, list.getRowCount() - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE));
        });
        popupWMenu.add(mntmSelectInvert);

        final var panel = new JPanel();
        splitPaneWLW.setLeftComponent(panel);
        panel.setLayout(new BorderLayout(0, 0));

        final var scrollPaneWL = new JScrollPane();
        panel.add(scrollPaneWL);

        tableWL = new JTable();
        scrollPaneWL.setViewportView(tableWL);
        tableWL.setPreferredScrollableViewportSize(new Dimension(300, 400));
        tableWL.setModel(new MachineListListModel(profile.getMachineListList()));
        tableWL.setTableHeader(new JTableHeader(tableWL.getColumnModel()) {
            @Override
            public String getToolTipText(final MouseEvent e) {
                return columnModel.getColumn(columnModel.getColumnIndexAtX(e.getPoint().x)).getHeaderValue().toString();
            }
        });
        tableWL.setFillsViewportHeight(true);
        tableWL.setShowGrid(false);
        tableWL.setShowHorizontalLines(false);
        tableWL.setShowVerticalLines(false);
        tableWL.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        final var popupMenu = new JPopupMenu();
        Popup.addPopup(tableWL, popupMenu);

        final var mnExportAll = new JMenu(Messages.getString("ProfileViewer.ExportAll")); //$NON-NLS-1$
        popupMenu.add(mnExportAll);

        final var mnExportAllFiltered = new JMenu(Messages.getString("ProfileViewer.Filtered")); //$NON-NLS-1$
        mnExportAll.add(mnExportAllFiltered);

        final var mntmFilteredAsLogiqxDat = new JMenuItem(Messages.getString("ProfileViewer.AsLogiqxDat")); //$NON-NLS-1$
        mnExportAllFiltered.add(mntmFilteredAsLogiqxDat);
        mntmFilteredAsLogiqxDat.addActionListener(_ -> export(session, ExportType.DATAFILE, EnumSet.of(ExportMode.FILTERED), null));

        final var mntmFilteredAsMameDat = new JMenuItem(Messages.getString("ProfileViewer.AsMameDat")); //$NON-NLS-1$
        mnExportAllFiltered.add(mntmFilteredAsMameDat);
        mntmFilteredAsMameDat.addActionListener(_ -> export(session, ExportType.MAME, EnumSet.of(ExportMode.FILTERED), null));

        final var mntmFilteredAsSoftwareLists = new JMenuItem(Messages.getString(PROFILE_VIEWER_AS_SW_LISTS_DAT)); // $NON-NLS-1$
        mnExportAllFiltered.add(mntmFilteredAsSoftwareLists);
        mntmFilteredAsSoftwareLists.addActionListener(_ -> export(session, ExportType.SOFTWARELIST, EnumSet.of(ExportMode.FILTERED), null));

        final var mntmAllAsLogiqxDat = new JMenuItem(Messages.getString("ProfileViewer.AsLogiqxDat")); //$NON-NLS-1$
        mntmAllAsLogiqxDat.setEnabled(false);
        mnExportAll.add(mntmAllAsLogiqxDat);
        mntmAllAsLogiqxDat.addActionListener(_ -> export(session, ExportType.DATAFILE, EnumSet.of(ExportMode.ALL), null));

        final var mntmAllAsMameDat = new JMenuItem(Messages.getString("ProfileViewer.AsMameDat")); //$NON-NLS-1$
        mntmAllAsMameDat.setEnabled(false);
        mnExportAll.add(mntmAllAsMameDat);
        mntmAllAsMameDat.addActionListener(_ -> export(session, ExportType.MAME, EnumSet.of(ExportMode.ALL), null));

        final var mntmAllAsSoftwareLists = new JMenuItem(Messages.getString(PROFILE_VIEWER_AS_SW_LISTS_DAT)); // $NON-NLS-1$
        mntmAllAsSoftwareLists.setEnabled(false);
        mnExportAll.add(mntmAllAsSoftwareLists);
        mntmAllAsSoftwareLists.addActionListener(_ -> export(session, ExportType.SOFTWARELIST, EnumSet.of(ExportMode.ALL), null));

        final var mnExportSelected = new JMenu(Messages.getString("ProfileViewer.ExportSelected")); //$NON-NLS-1$
        popupMenu.add(mnExportSelected);

        final var mnExportSelectedFiltered = new JMenu(Messages.getString("ProfileViewer.Filtered")); //$NON-NLS-1$
        mnExportSelected.add(mnExportSelectedFiltered);

        final var mntmSelectedFilteredAsSoftwareList = new JMenuItem(Messages.getString("ProfileViewer.AsSWListDat")); //$NON-NLS-1$
        mnExportSelectedFiltered.add(mntmSelectedFilteredAsSoftwareList);
        mntmSelectedFilteredAsSoftwareList.addActionListener(
                _ -> export(session, ExportType.SOFTWARELIST, EnumSet.of(ExportMode.FILTERED), (SoftwareList) tableWL.getModel().getValueAt(tableWL.getSelectedRow(), 0)));

        final var mntmSelectedAsSoftwareLists = new JMenuItem(Messages.getString(PROFILE_VIEWER_AS_SW_LISTS_DAT)); // $NON-NLS-1$
        mntmSelectedAsSoftwareLists.setEnabled(false);
        mnExportSelected.add(mntmSelectedAsSoftwareLists);
        mntmSelectedAsSoftwareLists.addActionListener(
                _ -> export(session, ExportType.SOFTWARELIST, EnumSet.of(ExportMode.ALL), (SoftwareList) tableWL.getModel().getValueAt(tableWL.getSelectedRow(), 0)));

        popupMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuCanceled(final PopupMenuEvent e) {
                // unused
            }

            @Override
            public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
                // unused
            }

            @Override
            public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
                final boolean has_machines = session.getCurrProfile().getMachineListList().getList().stream().mapToInt(ml -> ml.getList().size()).sum() > 0;
                final boolean has_filtered_machines = session.getCurrProfile().getMachineListList().getFilteredStream().mapToInt(m -> (int) m.countAll()).sum() > 0;
                final boolean has_selected_swlist = tableWL.getSelectedRowCount() == 1 && tableWL.getModel() instanceof MachineListListModel model
                        && model.getValueAt(tableWL.getSelectedRow(), 0) instanceof SoftwareList;
                mntmAllAsMameDat.setEnabled(has_machines);
                mntmAllAsLogiqxDat.setEnabled(has_machines);
                mntmAllAsSoftwareLists.setEnabled(!session.getCurrProfile().getMachineListList().getSoftwareListList().isEmpty());
                mntmFilteredAsMameDat.setEnabled(has_filtered_machines);
                mntmFilteredAsLogiqxDat.setEnabled(has_filtered_machines);
                mntmFilteredAsSoftwareLists.setEnabled(session.getCurrProfile().getMachineListList().getSoftwareListList().getFilteredStream().count() > 0);
                mntmSelectedAsSoftwareLists.setEnabled(has_selected_swlist);
                mntmSelectedFilteredAsSoftwareList.setEnabled(has_selected_swlist);
            }
        });

        final var toolBarWL = new JToolBar();
        panel.add(toolBarWL, BorderLayout.SOUTH);

        final var tglbtnMissingWL = new JToggleButton(""); //$NON-NLS-1$
        tglbtnMissingWL.setSelected(true);
        tglbtnMissingWL.setIcon(MainFrame.getIcon("/jrm/resicons/disk_multiple_red.png")); //$NON-NLS-1$
        tglbtnMissingWL.setToolTipText(Messages.getString("ProfileViewer.tglbtnMissingWL.toolTipText")); //$NON-NLS-1$
        toolBarWL.add(tglbtnMissingWL);

        final var tglbtnPartialWL = new JToggleButton(""); //$NON-NLS-1$
        tglbtnPartialWL.setSelected(true);
        tglbtnPartialWL.setIcon(MainFrame.getIcon("/jrm/resicons/disk_multiple_orange.png")); //$NON-NLS-1$
        tglbtnPartialWL.setToolTipText(Messages.getString("ProfileViewer.tglbtnPartialWL.toolTipText")); //$NON-NLS-1$
        toolBarWL.add(tglbtnPartialWL);

        final var tglbtnCompleteWL = new JToggleButton(""); //$NON-NLS-1$
        tglbtnCompleteWL.setSelected(true);
        tglbtnCompleteWL.setIcon(MainFrame.getIcon("/jrm/resicons/disk_multiple_green.png")); //$NON-NLS-1$
        tglbtnCompleteWL.setToolTipText(Messages.getString("ProfileViewer.tglbtnCompleteWL.toolTipText")); //$NON-NLS-1$
        toolBarWL.add(tglbtnCompleteWL);

        tglbtnMissingWL.addItemListener(_ -> setFilterWL(tglbtnMissingWL.isSelected(), tglbtnPartialWL.isSelected(), tglbtnCompleteWL.isSelected()));
        tglbtnPartialWL.addItemListener(_ -> setFilterWL(tglbtnMissingWL.isSelected(), tglbtnPartialWL.isSelected(), tglbtnCompleteWL.isSelected()));
        tglbtnCompleteWL.addItemListener(_ -> setFilterWL(tglbtnMissingWL.isSelected(), tglbtnPartialWL.isSelected(), tglbtnCompleteWL.isSelected()));

        tableWL.getSelectionModel().addListSelectionListener(this::selectItemWL);
        tableW.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                clickItemW(session, e);
            }

        });
        tableW.getSelectionModel().addListSelectionListener(this::selecteItemW);

        final var panelEntity = new JPanel();
        splitPane.setRightComponent(panelEntity);
        panelEntity.setLayout(new BorderLayout(0, 0));

        final var toolBarEntity = new JToolBar();
        panelEntity.add(toolBarEntity, BorderLayout.SOUTH);

        final var tglbtnBad = new JToggleButton(""); //$NON-NLS-1$
        tglbtnBad.setSelected(true);
        tglbtnBad.setIcon(MainFrame.getIcon("/jrm/resicons/icons/bullet_red.png")); //$NON-NLS-1$
        tglbtnBad.setToolTipText(Messages.getString("ProfileViewer.tglbtnBad.toolTipText")); //$NON-NLS-1$
        toolBarEntity.add(tglbtnBad);

        final var tglbtnOK = new JToggleButton(""); //$NON-NLS-1$
        tglbtnOK.setSelected(true);
        tglbtnOK.setToolTipText(Messages.getString("ProfileViewer.tglbtnOK.toolTipText")); //$NON-NLS-1$
        tglbtnOK.setIcon(MainFrame.getIcon("/jrm/resicons/icons/bullet_green.png")); //$NON-NLS-1$
        toolBarEntity.add(tglbtnOK);

        tglbtnBad.addItemListener(_ -> setFilterE(tglbtnBad.isSelected(), tglbtnOK.isSelected()));
        tglbtnOK.addItemListener(_ -> setFilterE(tglbtnBad.isSelected(), tglbtnOK.isSelected()));

        final var scrollPaneEntity = new JScrollPane();
        panelEntity.add(scrollPaneEntity, BorderLayout.CENTER);

        scrollPaneEntity.setViewportView(tableEntity);

        final var popupEntMenu = new JPopupMenu();
        Popup.addPopup(tableEntity, popupEntMenu);

        final var mntmCopyCRC = new JMenuItem("Copy CRC");
        mntmCopyCRC.addActionListener(_ -> copyHash(3));
        popupEntMenu.add(mntmCopyCRC);

        final var mntmCopySHA1 = new JMenuItem("Copy SHA1");
        mntmCopySHA1.addActionListener(_ -> copyHash(5));
        popupEntMenu.add(mntmCopySHA1);

        final var mntmCopyName = new JMenuItem("Copy Name");
        mntmCopyName.addActionListener(_ -> copyHash(1));
        popupEntMenu.add(mntmCopyName);

        final var mntmSearchWeb = new JMenuItem("Search on the Web");
        mntmSearchWeb.addActionListener(_ -> {
            val index = tableEntity.getSelectedRow();
            if (index >= 0 && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                try {
                    val name = tableEntity.getModel().getValueAt(index, 1).toString();
                    val crc = tableEntity.getModel().getValueAt(index, 3);
                    val sha1 = tableEntity.getModel().getValueAt(index, 5);
                    val hash = Optional.ofNullable(Optional.ofNullable(crc).orElse(sha1)).map(h -> '+' + h.toString()).orElse("");
                    Desktop.getDesktop().browse(new URI("https://www.google.com/search?q=" + URLEncoder.encode('"' + name + '"', "UTF-8") + hash));
                } catch (IOException | URISyntaxException e1) {
                    Log.err(e1.getMessage(), e1);
                }
            }
        });
        popupEntMenu.add(mntmSearchWeb);

        popupEntMenu.addPopupMenuListener(new PopupMenuListener() {

            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                final boolean has_selected_entity = tableEntity.getSelectedRowCount() > 0;
                mntmCopyCRC.setEnabled(has_selected_entity);
                mntmCopySHA1.setEnabled(has_selected_entity);
                mntmCopyName.setEnabled(has_selected_entity);
                mntmSearchWeb.setEnabled(has_selected_entity);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                // unused
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                // unused
            }
        });

        reset(profile);
        pack();
        try {
            setBounds(SerializationUtils.deserialize(
                    Hex.decodeHex(session.getUser().getSettings().getProperty("ProfileViewer.Bounds", Hex.encodeHexString(SerializationUtils.serialize(getBounds())))))); //$NON-NLS-1$
        } catch (final DecoderException e1) {
            Log.err(e1.getMessage(), e1);
        }
        setVisible(true);
    }

    /**
     * Copies the hash value from the specified column of the selected entity to the clipboard.
     *
     * @param col the column index containing the hash to copy
     * @throws HeadlessException if the operation requires a display that is not available
     */
    private void copyHash(int col) throws HeadlessException {
        val index = tableEntity.getSelectedRow();
        if (index >= 0) {
            val hash = tableEntity.getModel().getValueAt(index, col);
            if (hash != null)
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(hash.toString()), null);
        }
    }

    /**
     * Handles selection changes in the software list table.
     *
     * @param e the list selection event
     */
    private void selecteItemW(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
            final var model = (ListSelectionModel) e.getSource();
            final var tablemodel = tableW.getModel();
            if (model != null && tablemodel != null) {
                if (!model.isSelectionEmpty()) {
                    final var anyware = new AnywareModel((Anyware) tablemodel.getValueAt(model.getMinSelectionIndex(), 0));
                    anyware.reset();
                    tableEntity.setModel(anyware);
                    tableEntity.setTableHeader(new JTableHeader(tableEntity.getColumnModel()) {
                        @Override
                        public String getToolTipText(final MouseEvent e) {
                            return columnModel.getColumn(columnModel.getColumnIndexAtX(e.getPoint().x)).getHeaderValue().toString();
                        }
                    });
                    readjustColumnsE(anyware);
                } else {
                    tableEntity.setModel(new DefaultTableModel());
                }
            }
        }
    }

    /**
     * Adjusts column widths and renderers for the entity table.
     *
     * @param anyware the anyware model containing column configuration
     */
    private void readjustColumnsE(final AnywareModel anyware) {
        for (var i = 0; i < tableEntity.getColumnModel().getColumnCount(); i++) {
            final TableColumn column = tableEntity.getColumnModel().getColumn(i);
            column.setCellRenderer(anyware.getColumnRenderer(i));
            final int width = anyware.getColumnWidth(i);
            if (width > 0) {
                column.setMinWidth(width / 2);
                column.setPreferredWidth(width);
            } else if (width < 0) {
                final var component = column.getCellRenderer().getTableCellRendererComponent(tableEntity, null, false, false, 0, i);
                final var format = "%0" + (-width) + "d";
                final int pixwidth = component.getFontMetrics(component.getFont()).stringWidth(String.format(format, 0)); // $NON-NLS-1$
                                                                                                                          // //$NON-NLS-2$
                column.setMinWidth(pixwidth / 2);
                column.setPreferredWidth(pixwidth);
                column.setMaxWidth(pixwidth);
            }
        }
    }

    /**
     * Handles double-click events on items in the software table.
     *
     * @param session the current session
     * @param e the mouse event
     * @throws HeadlessException if the operation requires a display that is not available
     */
    private void clickItemW(final Session session, final MouseEvent e) throws HeadlessException {
        if (e.getClickCount() != 2)
            return;
        int row = tableW.getSelectedRow();
        if (row < 0)
            return;
        @SuppressWarnings("unchecked")
        final var tablemodel = (AnywareListModel<Anyware>) tableW.getModel();
        final var column = tableW.columnAtPoint(e.getPoint());
        final Object obj = tablemodel.getValueAt(row, column);
        if (!(obj instanceof Anyware))
            return;
        final Anyware ware = (Anyware) obj;
        if (column > 1)
            jumpTo(tableW, tablemodel.getList().find(ware));
        else if (ware.getStatus() == AnywareStatus.COMPLETE) {
            if (session.getCurrProfile() != null) {
                final var profile = session.getCurrProfile();
                if (profile.getNfo().getMame().getStatus() == MameStatus.UPTODATE)
                    launchMame(ware, profile);
                else
                    JOptionPane.showMessageDialog(ProfileViewer.this,
                            String.format(Messages.getString("ProfileViewer.MameNotAvailableOrObsolete"), profile.getNfo().getMame().getStatus()), //$NON-NLS-1$
                            Messages.getString(PROFILE_VIEWER_ERROR), JOptionPane.ERROR_MESSAGE); // $NON-NLS-2$
            } else
                JOptionPane.showMessageDialog(ProfileViewer.this, Messages.getString("ProfileViewer.NoProfile"), Messages.getString(PROFILE_VIEWER_ERROR), //$NON-NLS-1$
                        JOptionPane.ERROR_MESSAGE); // $NON-NLS-2$
        } else
            JOptionPane.showMessageDialog(ProfileViewer.this, String.format(Messages.getString("ProfileViewer.CantLaunchIncompleteSet"), ware.getStatus()), //$NON-NLS-1$
                    Messages.getString(PROFILE_VIEWER_ERROR), JOptionPane.ERROR_MESSAGE); // $NON-NLS-2$
    }

    /**
     * Scrolls the target table to the specified row and selects it.
     *
     * @param target the table to scroll
     * @param row the row index to select
     */
    private void jumpTo(final JTable target, int row) {
        if (row >= 0) {
            target.setRowSelectionInterval(row, row);
            target.scrollRectToVisible(target.getCellRect(row, 0, true));
        }
    }

    /**
     * Launches MAME with the specified anyware and profile configuration.
     *
     * @param ware the anyware (machine or software) to launch
     * @param profile the profile containing MAME configuration
     * @throws HeadlessException if the operation requires a display that is not available
     */
    private void launchMame(final Anyware ware, final Profile profile) throws HeadlessException {
        final ProfileNFOMame mame = profile.getNfo().getMame();
        
        // Validate MAME executable before launching to prevent arbitrary program execution
        if (mame.getFile() == null) {
            JOptionPane.showMessageDialog(ProfileViewer.this, "MAME executable is not configured for this profile.", 
                    Messages.getString("ProfileViewer.Exception"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (!mame.getFile().exists() || !mame.getFile().canExecute()) {
            JOptionPane.showMessageDialog(ProfileViewer.this, 
                    "MAME executable does not exist or is not executable: " + mame.getFile().getAbsolutePath(), 
                    Messages.getString("ProfileViewer.Exception"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Additional security check: verify the executable name contains "mame"
        final String fileName = mame.getFile().getName().toLowerCase();
        if (!fileName.contains("mame")) {
            JOptionPane.showMessageDialog(ProfileViewer.this, 
                    "The configured executable does not appear to be a MAME binary: " + mame.getFile().getName(), 
                    Messages.getString("ProfileViewer.Exception"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        String[] args = null;
        if (ware instanceof Software) {
            args = getMameArgsSofware(ware, profile, mame, args);
        } else {
            args = getMameArgsMachine(ware, profile, mame);
        }
        if (args != null) {
            final ProcessBuilder pb = new ProcessBuilder(args).directory(mame.getFile().getParentFile()).redirectErrorStream(true)
                    .redirectOutput(new File(mame.getFile().getParentFile(), "JRomManager.log")); //$NON-NLS-1$
            try {
                pb.start().waitFor();
            } catch (IOException e1) {
                JOptionPane.showMessageDialog(ProfileViewer.this, e1.getMessage(), Messages.getString("ProfileViewer.Exception"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            } catch (InterruptedException e1) {
                JOptionPane.showMessageDialog(ProfileViewer.this, e1.getMessage(), Messages.getString("ProfileViewer.Exception"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Builds MAME command-line arguments for launching a machine.
     *
     * @param ware the machine to launch
     * @param profile the profile containing ROM paths
     * @param mame the MAME configuration
     * @return the command-line arguments array
     */
    private String[] getMameArgsMachine(final Anyware ware, final Profile profile, final ProfileNFOMame mame) {
        String[] args;
        final List<String> rompaths = new ArrayList<>(Collections.singletonList(profile.getProperty(ProfileSettingsEnum.roms_dest_dir))); // $NON-NLS-1$
                                                                                                                                          // //$NON-NLS-2$
        if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.disks_dest_dir_enabled, Boolean.class))) // $NON-NLS-1$
            rompaths.add(profile.getProperty(ProfileSettingsEnum.disks_dest_dir)); // $NON-NLS-1$ //$NON-NLS-2$
        args = new String[] { mame.getFile().getAbsolutePath(), ware.getBaseName(), "-homepath", mame.getFile().getParent(), "-rompath", //$NON-NLS-1$ //$NON-NLS-2$
                rompaths.stream().collect(Collectors.joining(";")) }; //$NON-NLS-1$
        return args;
    }

    /**
     * Builds MAME command-line arguments for launching software.
     *
     * @param ware the software to launch
     * @param profile the profile containing ROM paths
     * @param mame the MAME configuration
     * @param args initial arguments (may be null)
     * @return the command-line arguments array
     * @throws HeadlessException if the operation requires a display that is not available
     */
    private String[] getMameArgsSofware(final Anyware ware, final Profile profile, final ProfileNFOMame mame, String[] args) throws HeadlessException {
        final List<String> rompaths = new ArrayList<>(Collections.singletonList(profile.getProperty(ProfileSettingsEnum.roms_dest_dir))); // $NON-NLS-1$
                                                                                                                                          // //$NON-NLS-2$
        if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.swroms_dest_dir_enabled, Boolean.class))) // $NON-NLS-1$
            rompaths.add(profile.getProperty(ProfileSettingsEnum.swroms_dest_dir)); // $NON-NLS-1$ //$NON-NLS-2$
        if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.disks_dest_dir_enabled, Boolean.class))) // $NON-NLS-1$
            rompaths.add(profile.getProperty(ProfileSettingsEnum.disks_dest_dir)); // $NON-NLS-1$ //$NON-NLS-2$
        if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.swdisks_dest_dir_enabled, Boolean.class))) // $NON-NLS-1$
            rompaths.add(profile.getProperty(ProfileSettingsEnum.swdisks_dest_dir)); // $NON-NLS-1$ //$NON-NLS-2$
        Log.debug(() -> ((Software) ware).getSl().getBaseName() + ", " + ((Software) ware).getCompatibility()); //$NON-NLS-1$
        JList<Machine> machines = new JList<>(
                profile.getMachineListList().getSortedMachines(((Software) ware).getSl().getBaseName(), ((Software) ware).getCompatibility()).toArray(new Machine[0]));
        machines.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (machines.getModel().getSize() > 0)
            machines.setSelectedIndex(0);
        JOptionPane.showMessageDialog(ProfileViewer.this, machines);
        final var machine = machines.getSelectedValue();
        if (machine != null) {
            final var device = new StringBuilder(); // $NON-NLS-1$
            for (final var dev : machine.getDevices()) {
                if (Objects.equals(((Software) ware).getParts().get(0).getIntrface(), dev.getIntrface()) && dev.getInstance() != null) {
                    device.append("-").append(dev.getInstance().getName()); //$NON-NLS-1$
                    break;
                }
            }
            Log.debug(() -> "-> " + machine.getBaseName() + " " + device + " " + ware.getBaseName()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            args = new String[] { mame.getFile().getAbsolutePath(), machine.getBaseName(), device.toString(), ware.getBaseName(), "-homepath", mame.getFile().getParent(), //$NON-NLS-1$
                    "-rompath", rompaths.stream().collect(Collectors.joining(";")) }; //$NON-NLS-1$ //$NON-NLS-2$
        }
        return args;
    }

    /**
     * Handles selection changes in the anyware list table.
     *
     * @param e the list selection event
     */
    private void selectItemWL(ListSelectionEvent e) {
        if (e.getValueIsAdjusting())
            return;
        final var model = (ListSelectionModel) e.getSource();
        final var tablemodel = tableWL.getModel();
        if (model != null && tablemodel != null) {
            if (!model.isSelectionEmpty()) {
                final AnywareList<?> awlist = (AnywareList<?>) tablemodel.getValueAt(model.getMinSelectionIndex(), 0);
                final AnywareListModel<?> anywarelist = awlist instanceof MachineList ml ? new MachineListModel(ml) : new SoftwareListModel((SoftwareList) awlist);
                anywarelist.reset();
                tableW.setModel(anywarelist);
                tableW.setTableHeader(new JTableHeader(tableW.getColumnModel()) {
                    @Override
                    public String getToolTipText(final MouseEvent e) {
                        return columnModel.getColumn(columnModel.getColumnIndexAtX(e.getPoint().x)).getHeaderValue().toString();
                    }
                });
                if (tableW.getRowCount() > 0)
                    tableW.setRowSelectionInterval(0, 0);
                readjustColumnsW(anywarelist);
            } else {
                tableW.setModel(new DefaultTableModel());
                tableEntity.setModel(new DefaultTableModel());
            }
        }
    }

    /**
     * Adjusts column widths for the anyware list table.
     *
     * @param anywarelist the anyware list model containing column configuration
     */
    private void readjustColumnsW(final AnywareListModel<?> anywarelist) {
        for (var i = 0; i < tableW.getColumnModel().getColumnCount(); i++) {
            final TableColumn column = tableW.getColumnModel().getColumn(i);
            column.setCellRenderer(anywarelist.getColumnRenderer(i));
            final int width = anywarelist.getColumnWidth(i);
            if (width > 0) {
                column.setMinWidth(width / 2);
                column.setPreferredWidth(width);
            } else if (width < 0) {
                column.setMinWidth(-width);
                column.setMaxWidth(-width);
            }
        }
    }

    private class KW extends Keywords {
        @SuppressWarnings("unchecked")
        final AnywareListModel<Anyware> list = (AnywareListModel<Anyware>) tableW.getModel();

        @Override
        protected void showFilter(String[] keywords, KFCallBack callback) {
            new KeywordFilter(ProfileViewer.this, keywords, list.getList(), this::filterCallBack);
        }

        @Override
        protected void updateList() {
            list.fireTableChanged(new TableModelEvent(list, 0, list.getRowCount() - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE));
        }
    }

    /**
     * Exports profile data in the specified format.
     *
     * @param session the current session
     * @param type the export format type
     * @param modes the export modes (filtered or all)
     * @param selection the specific software list to export, or null for all
     */
    private void export(final Session session, final ExportType type, final Set<ExportMode> modes, final SoftwareList selection) {
        final var fnef = new FileNameExtensionFilter(Messages.getString("MainFrame.DatFile"), "xml", "dat"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        new JRMFileChooser<Void>(JFileChooser.SAVE_DIALOG, JFileChooser.FILES_ONLY,
                Optional.ofNullable(session.getUser().getSettings().getProperty("MainFrame.ChooseExeOrDatToExport", (String) null)).map(File::new).orElse(null), null, //$NON-NLS-1$
                Arrays.asList(fnef), Messages.getString("ProfileViewer.ChooseDestinationFile"), false).show(ProfileViewer.this, chooser -> { //$NON-NLS-1$
                    session.getUser().getSettings().setProperty("MainFrame.ChooseExeOrDatToExport", chooser.getCurrentDirectory().getAbsolutePath()); //$NON-NLS-1$
                    final var selectedfile = chooser.getSelectedFile();
                    final var file = fnef.accept(selectedfile) ? selectedfile : new File(selectedfile.getAbsolutePath() + ".xml"); //$NON-NLS-1$
                    new SwingWorkerProgress<Void, Void>(ProfileViewer.this) {
                        @Override
                        protected Void doInBackground() throws Exception {
                            Export.export(session.getCurrProfile(), file, type, modes, selection, this);
                            return null;
                        }

                        @Override
                        public void done() {
                            close();
                        }
                    }.execute();
                    return null;
                });
    }

    /**
     * Sets the filter WL.
     *
     * @param missing the missing
     * @param partial the partial
     * @param complete the complete
     */
    public void setFilterWL(final boolean missing, final boolean partial, final boolean complete) {
        final EnumSet<AnywareStatus> filter = EnumSet.of(AnywareStatus.UNKNOWN);
        if (missing)
            filter.add(AnywareStatus.MISSING);
        if (partial)
            filter.add(AnywareStatus.PARTIAL);
        if (complete)
            filter.add(AnywareStatus.COMPLETE);
        ((AnywareListListModel) tableWL.getModel()).setFilter(filter);
        if (tableWL.getRowCount() > 0)
            tableWL.setRowSelectionInterval(0, 0);
    }

    /**
     * Sets the filter W.
     *
     * @param missing the missing
     * @param partial the partial
     * @param complete the complete
     */
    public void setFilterW(final boolean missing, final boolean partial, final boolean complete) {
        final EnumSet<AnywareStatus> filter = EnumSet.of(AnywareStatus.UNKNOWN);
        if (missing)
            filter.add(AnywareStatus.MISSING);
        if (partial)
            filter.add(AnywareStatus.PARTIAL);
        if (complete)
            filter.add(AnywareStatus.COMPLETE);
        ((AnywareListModel<?>) tableW.getModel()).setFilter(filter);
        if (tableW.getRowCount() > 0)
            tableW.setRowSelectionInterval(0, 0);
    }

    /**
     * Sets the filter E.
     *
     * @param ko the ko
     * @param ok the ok
     */
    public void setFilterE(final boolean ko, final boolean ok) {
        final EnumSet<EntityStatus> filter = EnumSet.of(EntityStatus.UNKNOWN);
        if (ko)
            filter.add(EntityStatus.KO);
        if (ok)
            filter.add(EntityStatus.OK);
        ((AnywareModel) tableEntity.getModel()).setFilter(filter);
        if (tableEntity.getRowCount() > 0)
            tableEntity.setRowSelectionInterval(0, 0);
    }

    /**
     * Clear.
     */
    public void clear() {
        tableEntity.setModel(new DefaultTableModel());
        tableW.setModel(new DefaultTableModel());
        tableWL.setModel(new DefaultTableModel());
    }

    /**
     * Reset.
     *
     * @param profile the profile
     */
    public void reset(final Profile profile) {
        final var model = new MachineListListModel(profile.getMachineListList());
        model.reset();
        if (tableWL.getModel() != model)
            tableWL.setModel(model);
        for (var i = 0; i < tableWL.getColumnModel().getColumnCount(); i++) {
            final var column = tableWL.getColumnModel().getColumn(i);
            column.setCellRenderer(model.getColumnRenderer(i));
            final int width = model.getColumnWidth(i);
            if (width > 0) {
                column.setMinWidth(width / 2);
                column.setPreferredWidth(width);
            } else if (width < 0) {
                column.setMinWidth(-width);
                column.setMaxWidth(-width);
            }
        }
        if (tableWL.getRowCount() > 0)
            tableWL.setRowSelectionInterval(0, 0);
    }

    /**
     * Reload.
     */
    public void reload() {
        var tablemodel = tableWL.getModel();
        if (tablemodel instanceof MachineListListModel mlm)
            mlm.fireTableChanged(new TableModelEvent(tablemodel, 0, mlm.getRowCount() - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE));
        tablemodel = tableW.getModel();
        if (tablemodel instanceof AnywareListModel<?> alm)
            alm.fireTableChanged(new TableModelEvent(tablemodel, 0, alm.getRowCount() - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE));
        tablemodel = tableEntity.getModel();
        if (tablemodel instanceof AnywareModel am)
            am.fireTableChanged(new TableModelEvent(tablemodel, 0, am.getRowCount() - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE));
    }

    /**
     * Searches for the text entered in the search field and selects the matching row.
     */
    private void search() {
        final String search = txtSearch.getText();
        final int row = ((AnywareListModel<?>) tableW.getModel()).getList().find(search);
        if (row >= 0) {
            tableW.setRowSelectionInterval(row, row);
            tableW.scrollRectToVisible(tableW.getCellRect(row, 0, true));
        }
    }
}
