package jrm.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import jrm.locale.Messages;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.filter.CatVer.Category;
import jrm.profile.filter.CatVer.Category.SubCategory;
import jrm.profile.filter.NPlayer;
import jrm.security.Session;
import jrm.ui.basic.JCheckBoxList;
import jrm.ui.basic.JCheckBoxTree;
import jrm.ui.basic.JFileDropMode;
import jrm.ui.basic.JFileDropTextField;
import jrm.ui.basic.JTextFieldHintUI;
import jrm.ui.basic.NGTreeNode;
import jrm.ui.basic.Popup;
import jrm.ui.profile.filter.CatVerModel;
import jrm.ui.profile.filter.CatVerNode;
import jrm.ui.profile.filter.CatVerNode.CategoryNode;
import jrm.ui.profile.filter.NPlayersModel;

/**
 * Panel for configuring advanced scanner filters.
 * <p>
 * Provides controls for filtering by number of players and CatVer category tree.
 * Supports drag-and-drop for CatVer and NPlayers data files.
 */
public class ScannerAdvFilterPanel extends JPanel {
    /** Popup menu listener that performs no actions on CatVer menu events. */
    private final class CatVerPopupMenuListener implements PopupMenuListener {
        @Override
        public void popupMenuCanceled(final PopupMenuEvent e) {
            // do nothing
        }

        @Override
        public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
            // do nothing
        }

        @Override
        public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
            // do nothing
        }
    }

    /** Label for the mature content category. */
    private static final String MATURE = "* Mature *";

    /** Checkbox list for filtering by number of players. */
    private JCheckBoxList<NPlayer> listNPlayers;
    /** Text field for the CatVer data file path. */
    private JFileDropTextField tfCatVer;
    /** Text field for the NPlayers data file path. */
    private JFileDropTextField tfNPlayers;
    /** Checkbox tree for selecting CatVer categories. */
    private JCheckBoxTree treeCatVer;

    /**
     * Constructs the advanced filters panel.
     *
     * @param session the current user session providing access to settings
     */
    public ScannerAdvFilterPanel(final Session session) {
        final GridBagLayout gblScannerAdvFilters = new GridBagLayout();
        gblScannerAdvFilters.columnWidths = new int[] { 0, 0, 0 };
        gblScannerAdvFilters.rowHeights = new int[] { 0, 0, 0 };
        gblScannerAdvFilters.columnWeights = new double[] { 1.0, 1.0, Double.MIN_VALUE };
        gblScannerAdvFilters.rowWeights = new double[] { 0.0, 1.0, Double.MIN_VALUE };
        this.setLayout(gblScannerAdvFilters);

        tfNPlayers = new JFileDropTextField(txt -> dropNPLayersIni(session, txt));
        tfNPlayers.setMode(JFileDropMode.FILE);
        tfNPlayers.setUI(new JTextFieldHintUI(Messages.getString("MainFrame.DropNPlayersIniHere"), Color.gray)); //$NON-NLS-1$
        tfNPlayers.setEditable(false);
        final GridBagConstraints gbcTFNPlayers = new GridBagConstraints();
        gbcTFNPlayers.insets = new Insets(0, 0, 5, 5);
        gbcTFNPlayers.fill = GridBagConstraints.HORIZONTAL;
        gbcTFNPlayers.gridx = 0;
        gbcTFNPlayers.gridy = 0;
        this.add(tfNPlayers, gbcTFNPlayers);

        tfCatVer = new JFileDropTextField(txt -> dropCatVerIni(session, txt));
        tfCatVer.setMode(JFileDropMode.FILE);
        tfCatVer.setUI(new JTextFieldHintUI(Messages.getString("MainFrame.DropCatVerIniHere"), Color.gray)); //$NON-NLS-1$
        tfCatVer.setEditable(false);
        final GridBagConstraints gbcTFCatVer = new GridBagConstraints();
        gbcTFCatVer.insets = new Insets(0, 0, 5, 0);
        gbcTFCatVer.fill = GridBagConstraints.HORIZONTAL;
        gbcTFCatVer.gridx = 1;
        gbcTFCatVer.gridy = 0;
        this.add(tfCatVer, gbcTFCatVer);

        JScrollPane scrollPaneNPlayers = new JScrollPane();
        scrollPaneNPlayers.setViewportBorder(new TitledBorder(null, Messages.getString("MainFrame.NPlayers"), TitledBorder.LEADING, TitledBorder.TOP, null, null)); //$NON-NLS-1$
        final GridBagConstraints gbcScrollPaneNPlayers = new GridBagConstraints();
        gbcScrollPaneNPlayers.insets = new Insets(0, 0, 0, 5);
        gbcScrollPaneNPlayers.fill = GridBagConstraints.BOTH;
        gbcScrollPaneNPlayers.gridx = 0;
        gbcScrollPaneNPlayers.gridy = 1;
        this.add(scrollPaneNPlayers, gbcScrollPaneNPlayers);

        listNPlayers = new JCheckBoxList<>();
        listNPlayers.setCellRenderer(getNPlayersCellRenderer(session));
        listNPlayers.addListSelectionListener(e -> listNPLayersValueChanged(session, e));
        listNPlayers.setEnabled(false);
        scrollPaneNPlayers.setViewportView(listNPlayers);

        JPopupMenu popupMenuNPlay = new JPopupMenu();
        Popup.addPopup(listNPlayers, popupMenuNPlay);

        JMenuItem mntmSelectAllNPlay = new JMenuItem(Messages.getString("MainFrame.SelectAll")); //$NON-NLS-1$
        mntmSelectAllNPlay.addActionListener(_ -> listNPlayers.selectAll());
        popupMenuNPlay.add(mntmSelectAllNPlay);

        JMenuItem mntmSelectNoneNPlay = new JMenuItem(Messages.getString("MainFrame.SelectNone")); //$NON-NLS-1$
        mntmSelectNoneNPlay.addActionListener(_ -> listNPlayers.selectNone());
        popupMenuNPlay.add(mntmSelectNoneNPlay);

        JMenuItem mntmInvertSelectionNPlay = new JMenuItem(Messages.getString("MainFrame.InvertSelection")); //$NON-NLS-1$
        mntmInvertSelectionNPlay.addActionListener(_ -> listNPlayers.selectInvert());
        popupMenuNPlay.add(mntmInvertSelectionNPlay);

        JSeparator separator1 = new JSeparator();
        popupMenuNPlay.add(separator1);

        JMenuItem mntmClearNPlayers = new JMenuItem(Messages.getString("ScannerAdvFilterPanel.mntmClear_1.text")); //$NON-NLS-1$
        mntmClearNPlayers.addActionListener(_ -> listNPlayersClear(session));
        popupMenuNPlay.add(mntmClearNPlayers);

        JScrollPane scrollPaneCatVer = new JScrollPane();
        scrollPaneCatVer.setViewportBorder(new TitledBorder(null, Messages.getString("MainFrame.Categories"), TitledBorder.LEADING, TitledBorder.TOP, null, null)); //$NON-NLS-1$
        final GridBagConstraints gbcScrollPaneCatVer = new GridBagConstraints();
        gbcScrollPaneCatVer.fill = GridBagConstraints.BOTH;
        gbcScrollPaneCatVer.gridx = 1;
        gbcScrollPaneCatVer.gridy = 1;
        this.add(scrollPaneCatVer, gbcScrollPaneCatVer);

        treeCatVer = new JCheckBoxTree(new CatVerModel());
        treeCatVer.addCheckChangeEventListener(_ -> {
            session.getCurrProfile().saveSettings();
            if (MainFrame.getProfileViewer() != null)
                MainFrame.getProfileViewer().reset(session.getCurrProfile());
        });
        treeCatVer.setEnabled(false);
        scrollPaneCatVer.setViewportView(treeCatVer);

        JPopupMenu popupMenuCat = new JPopupMenu();
        popupMenuCat.addPopupMenuListener(new CatVerPopupMenuListener());
        Popup.addPopup(treeCatVer, popupMenuCat);

        JMenu mnSelectCat = new JMenu(Messages.getString("MainFrame.Select")); //$NON-NLS-1$
        popupMenuCat.add(mnSelectCat);

        JMenuItem mntmSelectAllCat = new JMenuItem(Messages.getString("MainFrame.All")); //$NON-NLS-1$
        mntmSelectAllCat.addActionListener(_ -> treeCatVer.selectAll());
        mnSelectCat.add(mntmSelectAllCat);

        JMenuItem mntmSelectMatureCat = new JMenuItem(Messages.getString("MainFrame.Mature")); //$NON-NLS-1$
        mntmSelectMatureCat.addActionListener(_ -> catVerMatureSelect(session));
        mnSelectCat.add(mntmSelectMatureCat);

        JMenu mnUnselectCat = new JMenu(Messages.getString("MainFrame.Unselect")); //$NON-NLS-1$
        popupMenuCat.add(mnUnselectCat);

        JMenuItem mntmUnselectAllCat = new JMenuItem(Messages.getString("MainFrame.All")); //$NON-NLS-1$
        mntmUnselectAllCat.addActionListener(_ -> treeCatVer.selectNone());
        mnUnselectCat.add(mntmUnselectAllCat);

        JMenuItem mntmUnselectMatureCat = new JMenuItem(Messages.getString("MainFrame.Mature")); //$NON-NLS-1$
        mntmUnselectMatureCat.addActionListener(_ -> catVerMatureUnselect(session));
        mnUnselectCat.add(mntmUnselectMatureCat);

        JSeparator separator = new JSeparator();
        popupMenuCat.add(separator);

        JMenuItem mntmClearCat = new JMenuItem(Messages.getString("ScannerAdvFilterPanel.mntmClear.text")); //$NON-NLS-1$
        mntmClearCat.addActionListener(_ -> catVerClear(session));
        popupMenuCat.add(mntmClearCat);

    }

    /**
     * @param session
     */
    private void catVerClear(final Session session) {
        session.getCurrProfile().setProperty(ProfileSettingsEnum.filter_catver_ini, null); // $NON-NLS-1$
        session.getCurrProfile().setCatver(null);
        session.getCurrProfile().saveSettings();
        tfCatVer.setText(null);
        treeCatVer.setModel(new CatVerModel());
    }

    /**
     * @param session
     */
    private void catVerMatureUnselect(final Session session) {
        final List<NGTreeNode> matureNodes = getMatures(session);
        treeCatVer.unselect(matureNodes.toArray(new NGTreeNode[0]));
    }

    /**
     * @param session
     * 
     * @return
     */
    protected List<NGTreeNode> getMatures(final Session session) {
        final List<NGTreeNode> matureNodes = new ArrayList<>();
        for (final Category cat : session.getCurrProfile().getCatver()) {
            final CatVerModel catvermodel = (CatVerModel) treeCatVer.getModel();
            final CategoryNode catnode = ((CatVerNode) catvermodel.getRoot()).getNode(cat);
            if (cat.name.endsWith(MATURE)) // $NON-NLS-1$
                matureNodes.add(catnode);
            else
                for (final SubCategory subcat : cat)
                    if (subcat.name.endsWith(MATURE)) // $NON-NLS-1$
                        matureNodes.add(catnode.getNode(subcat));
        }
        return matureNodes;
    }

    /**
     * @param session
     */
    private void catVerMatureSelect(final Session session) {
        final List<NGTreeNode> matureNodes = getMatures(session);
        treeCatVer.select(matureNodes.toArray(new NGTreeNode[0]));
    }

    /**
     * @param session
     */
    private void listNPlayersClear(final Session session) {
        session.getCurrProfile().setNplayers(null);
        session.getCurrProfile().saveSettings();
        tfNPlayers.setText(null);
        listNPlayers.setModel(new DefaultListModel<>());
    }

    /**
     * @param session
     * @param e
     */
    private void listNPLayersValueChanged(final Session session, ListSelectionEvent e) {
        if (!e.getValueIsAdjusting() && e.getFirstIndex() != -1) {
            for (int index = e.getFirstIndex(); index <= e.getLastIndex() && index < listNPlayers.getModel().getSize(); index++)
                listNPlayers.getModel().getElementAt(index).setSelected(session.getCurrProfile(), listNPlayers.isSelectedIndex(index));
            if (MainFrame.getProfileViewer() != null)
                MainFrame.getProfileViewer().reset(session.getCurrProfile());
        }
    }

    /**
     * @param session
     * 
     * @return
     */
    private JCheckBoxList<NPlayer>.CellRenderer getNPlayersCellRenderer(final Session session) {
        return listNPlayers.new CellRenderer() {
            @Override
            public Component getListCellRendererComponent(final JList<? extends NPlayer> list, final NPlayer value, final int index, final boolean isSelected,
                    final boolean cellHasFocus) {
                final JCheckBox checkbox = (JCheckBox) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                checkbox.setSelected(value.isSelected(session.getCurrProfile()));
                return checkbox;
            }
        };
    }

    /**
     * @param session
     * @param txt
     */
    private void dropCatVerIni(final Session session, String txt) {
        session.getCurrProfile().setProperty(ProfileSettingsEnum.filter_catver_ini, txt); // $NON-NLS-1$
        session.getCurrProfile().loadCatVer(null);
        session.getCurrProfile().saveSettings();
        treeCatVer.setModel(session.getCurrProfile().getCatver() != null ? new CatVerModel(new CatVerNode(session.getCurrProfile().getCatver())) : new CatVerModel());
    }

    /**
     * @param session
     * @param txt
     */
    private void dropNPLayersIni(final Session session, String txt) {
        session.getCurrProfile().setProperty(ProfileSettingsEnum.filter_nplayers_ini, txt); // $NON-NLS-1$
        session.getCurrProfile().loadNPlayers(null);
        session.getCurrProfile().saveSettings();
        listNPlayers.setModel(new NPlayersModel(session.getCurrProfile().getNplayers()));
    }

    public void initProfileSettings(final Session session) {
        tfNPlayers.setText(session.getCurrProfile().getNplayers() != null ? session.getCurrProfile().getNplayers().file.getAbsolutePath() : null);
        listNPlayers.setModel(new NPlayersModel(session.getCurrProfile().getNplayers()));
        tfCatVer.setText(session.getCurrProfile().getCatver() != null ? session.getCurrProfile().getCatver().file.getAbsolutePath() : null);
        treeCatVer.setModel(session.getCurrProfile().getCatver() != null ? new CatVerModel(new CatVerNode(session.getCurrProfile().getCatver())) : new CatVerModel());
    }
}
