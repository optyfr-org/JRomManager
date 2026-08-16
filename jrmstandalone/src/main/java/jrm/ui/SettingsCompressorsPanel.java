package jrm.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import jrm.compressors.SevenZipOptions;
import jrm.compressors.ZipLevel;
import jrm.compressors.ZipOptions;
import jrm.compressors.ZipTempThreshold;
import jrm.locale.Messages;
import jrm.misc.SettingsEnum;
import jrm.security.Session;
import jrm.ui.basic.JFileDropMode;
import jrm.ui.basic.JFileDropTextField;
import jrm.ui.basic.JTextFieldHintUI;

/**
 * Panel for compressor settings.
 * <p>
 * Provides controls for configuring 7-Zip and ZIP compression options, including command paths,
 * thread counts, compression levels, and solid archive settings.
 */
public class SettingsCompressorsPanel extends JPanel {
    /** Combo box for 7-Zip command options. */
    private JComboBox<SevenZipOptions> cb7zArgs;
    /** Combo box for ZIP command options. */
    private JComboBox<ZipOptions> cbZipEArgs;
    /** Checkbox to enable 7-Zip solid archives. */
    private JCheckBox ckbx7zSolid;
    /** Text field for the 7-Zip command path. */
    private JFileDropTextField tf7zCmd;
    /** Text field for the 7-Zip thread count. */
    private JTextField tf7zThreads;
    /** Text field for the ZIP command path. */
    private JFileDropTextField tfZipECmd;
    /** Text field for the ZIP thread count. */
    private JTextField tfZipEThreads;
    /** Combo box for ZIP compression level. */
    private JComboBox<ZipLevel> cbbxZipLevel;
    /** Combo box for ZIP temporary file threshold. */
    private JComboBox<ZipTempThreshold> cbbxZipTempThreshold;

    /**
     * Constructs the compressors settings panel.
     *
     * @param session the user session for accessing settings
     */
    public SettingsCompressorsPanel(final Session session) {
        this.setLayout(new BorderLayout(0, 0));

        JTabbedPane compressorsPane = new JTabbedPane(SwingConstants.TOP);
        this.add(compressorsPane);

        JPanel panelZip = new JPanel();
        compressorsPane.addTab(Messages.getString("MainFrame.Zip"), null, panelZip, null); //$NON-NLS-1$
        GridBagLayout gblPanelZip = new GridBagLayout();
        gblPanelZip.columnWidths = new int[] { 1, 0, 1, 0 };
        gblPanelZip.rowHeights = new int[] { 0, 20, 20, 0, 0 };
        gblPanelZip.columnWeights = new double[] { 1.0, 1.0, 1.0, Double.MIN_VALUE };
        gblPanelZip.rowWeights = new double[] { 1.0, 0.0, 0.0, 1.0, Double.MIN_VALUE };
        panelZip.setLayout(gblPanelZip);

        JLabel lblTemporaryFilesThreshold = new JLabel(Messages.getString("MainFrame.lblTemporaryFilesThreshold.text")); //$NON-NLS-1$
        lblTemporaryFilesThreshold.setHorizontalAlignment(SwingConstants.TRAILING);
        GridBagConstraints gbcLblTemporaryFilesThreshold = new GridBagConstraints();
        gbcLblTemporaryFilesThreshold.fill = GridBagConstraints.HORIZONTAL;
        gbcLblTemporaryFilesThreshold.insets = new Insets(0, 0, 5, 5);
        gbcLblTemporaryFilesThreshold.gridx = 0;
        gbcLblTemporaryFilesThreshold.gridy = 1;
        panelZip.add(lblTemporaryFilesThreshold, gbcLblTemporaryFilesThreshold);

        cbbxZipTempThreshold = new JComboBox<>();
        cbbxZipTempThreshold.setModel(new DefaultComboBoxModel<>(ZipTempThreshold.values()));
        cbbxZipTempThreshold.setSelectedItem(ZipTempThreshold.valueOf(session.getUser().getSettings().getProperty(SettingsEnum.zip_temp_threshold))); // $NON-NLS-1$
        cbbxZipTempThreshold
                .addActionListener(_ -> session.getUser().getSettings().setProperty(SettingsEnum.zip_temp_threshold, cbbxZipTempThreshold.getSelectedItem().toString()));
        cbbxZipTempThreshold.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
                setText(((ZipTempThreshold) value).getDesc());
                return this;
            }
        });
        GridBagConstraints gbcCbbxZipTempThreshold = new GridBagConstraints();
        gbcCbbxZipTempThreshold.insets = new Insets(0, 0, 5, 5);
        gbcCbbxZipTempThreshold.fill = GridBagConstraints.HORIZONTAL;
        gbcCbbxZipTempThreshold.gridx = 1;
        gbcCbbxZipTempThreshold.gridy = 1;
        panelZip.add(cbbxZipTempThreshold, gbcCbbxZipTempThreshold);

        JLabel lblCompressionLevel = new JLabel(Messages.getString("MainFrame.lblCompressionLevel.text")); //$NON-NLS-1$
        lblCompressionLevel.setHorizontalAlignment(SwingConstants.TRAILING);
        GridBagConstraints gbcLblCompressionLevel = new GridBagConstraints();
        gbcLblCompressionLevel.fill = GridBagConstraints.HORIZONTAL;
        gbcLblCompressionLevel.insets = new Insets(0, 0, 5, 5);
        gbcLblCompressionLevel.gridx = 0;
        gbcLblCompressionLevel.gridy = 2;
        panelZip.add(lblCompressionLevel, gbcLblCompressionLevel);

        cbbxZipLevel = new JComboBox<>();
        cbbxZipLevel.setModel(new DefaultComboBoxModel<>(ZipLevel.values()));
        cbbxZipLevel.setSelectedItem(ZipLevel.valueOf(session.getUser().getSettings().getProperty(SettingsEnum.zip_compression_level))); // $NON-NLS-1$
        cbbxZipLevel.addActionListener(_ -> session.getUser().getSettings().setProperty(SettingsEnum.zip_compression_level, cbbxZipLevel.getSelectedItem().toString()));
        cbbxZipLevel.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
                setText(((ZipLevel) value).getName());
                return this;
            }
        });
        GridBagConstraints gbcCbbxZipLevel = new GridBagConstraints();
        gbcCbbxZipLevel.fill = GridBagConstraints.BOTH;
        gbcCbbxZipLevel.insets = new Insets(0, 0, 5, 5);
        gbcCbbxZipLevel.gridx = 1;
        gbcCbbxZipLevel.gridy = 2;
        panelZip.add(cbbxZipLevel, gbcCbbxZipLevel);

        JPanel panelZipE = new JPanel();
        compressorsPane.addTab(Messages.getString("MainFrame.ZipExternal"), null, panelZipE, null); //$NON-NLS-1$
        final GridBagLayout gblPanelZipE = new GridBagLayout();
        gblPanelZipE.columnWidths = new int[] { 85, 246, 40, 0 };
        gblPanelZipE.rowHeights = new int[] { 0, 28, 28, 28, 0, 0 };
        gblPanelZipE.columnWeights = new double[] { 0.0, 1.0, 0.0, Double.MIN_VALUE };
        gblPanelZipE.rowWeights = new double[] { 1.0, 0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE };
        panelZipE.setLayout(gblPanelZipE);

        JLabel lblZipECmd = new JLabel(Messages.getString("MainFrame.lblZipECmd.text")); //$NON-NLS-1$
        final GridBagConstraints gbcLblZipECmd = new GridBagConstraints();
        gbcLblZipECmd.anchor = GridBagConstraints.EAST;
        gbcLblZipECmd.insets = new Insets(5, 5, 5, 5);
        gbcLblZipECmd.gridx = 0;
        gbcLblZipECmd.gridy = 1;
        panelZipE.add(lblZipECmd, gbcLblZipECmd);

        tfZipECmd = new JFileDropTextField(txt -> session.getUser().getSettings().setProperty(SettingsEnum.zip_cmd, txt));// $NON-NLS-1$
        tfZipECmd.setMode(JFileDropMode.FILE);
        tfZipECmd.setUI(new JTextFieldHintUI(Messages.getString("MainFrame.DropDirHint"), Color.gray)); //$NON-NLS-1$
        tfZipECmd.setText(session.getUser().getSettings().getProperty(SettingsEnum.zip_cmd)); // $NON-NLS-1$
        final GridBagConstraints gbcTFZipECmd = new GridBagConstraints();
        gbcTFZipECmd.insets = new Insets(0, 0, 5, 0);
        gbcTFZipECmd.fill = GridBagConstraints.BOTH;
        gbcTFZipECmd.gridx = 1;
        gbcTFZipECmd.gridy = 1;
        panelZipE.add(tfZipECmd, gbcTFZipECmd);
        tfZipECmd.setColumns(30);

        JButton btnZipECmd = new JButton(""); //$NON-NLS-1$
        btnZipECmd.setIcon(MainFrame.getIcon("/jrm/resicons/icons/disk.png")); //$NON-NLS-1$
        final GridBagConstraints gbcBtnZipECmd = new GridBagConstraints();
        gbcBtnZipECmd.fill = GridBagConstraints.BOTH;
        gbcBtnZipECmd.insets = new Insets(0, 0, 5, 5);
        gbcBtnZipECmd.gridx = 2;
        gbcBtnZipECmd.gridy = 1;
        panelZipE.add(btnZipECmd, gbcBtnZipECmd);

        JLabel lblZipEArgs = new JLabel(Messages.getString("MainFrame.lblZipEArgs.text")); //$NON-NLS-1$
        final GridBagConstraints gbcLblZipEArgs = new GridBagConstraints();
        gbcLblZipEArgs.anchor = GridBagConstraints.EAST;
        gbcLblZipEArgs.insets = new Insets(0, 5, 5, 5);
        gbcLblZipEArgs.gridx = 0;
        gbcLblZipEArgs.gridy = 2;
        panelZipE.add(lblZipEArgs, gbcLblZipEArgs);

        cbZipEArgs = new JComboBox<>();
        cbZipEArgs.setEditable(false);
        cbZipEArgs.setModel(new DefaultComboBoxModel<>(ZipOptions.values()));
        cbZipEArgs.setRenderer(new DefaultListCellRenderer() {

            @Override
            public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
                setText(((ZipOptions) value).getDesc());
                return this;
            }
        });
        cbZipEArgs.addActionListener(_ -> session.getUser().getSettings().setProperty(SettingsEnum.zip_level, cbZipEArgs.getSelectedItem().toString())); // $NON-NLS-1$
        cbZipEArgs.setSelectedItem(ZipOptions.valueOf(session.getUser().getSettings().getProperty(SettingsEnum.zip_level))); // $NON-NLS-1$
        final GridBagConstraints gbcCbZipEArgs = new GridBagConstraints();
        gbcCbZipEArgs.insets = new Insets(0, 0, 5, 5);
        gbcCbZipEArgs.gridwidth = 2;
        gbcCbZipEArgs.fill = GridBagConstraints.BOTH;
        gbcCbZipEArgs.gridx = 1;
        gbcCbZipEArgs.gridy = 2;
        panelZipE.add(cbZipEArgs, gbcCbZipEArgs);

        JLabel lblZipEThreads = new JLabel(Messages.getString("MainFrame.lblZipEThreads.text")); //$NON-NLS-1$
        final GridBagConstraints gbcLblZipEThreads = new GridBagConstraints();
        gbcLblZipEThreads.insets = new Insets(0, 0, 5, 5);
        gbcLblZipEThreads.anchor = GridBagConstraints.EAST;
        gbcLblZipEThreads.gridx = 0;
        gbcLblZipEThreads.gridy = 3;
        panelZipE.add(lblZipEThreads, gbcLblZipEThreads);

        tfZipEThreads = new JTextField();
        tfZipEThreads.setText("1"); //$NON-NLS-1$
        final GridBagConstraints gbcTFZipEThreads = new GridBagConstraints();
        gbcTFZipEThreads.fill = GridBagConstraints.VERTICAL;
        gbcTFZipEThreads.anchor = GridBagConstraints.WEST;
        gbcTFZipEThreads.insets = new Insets(0, 0, 5, 5);
        gbcTFZipEThreads.gridx = 1;
        gbcTFZipEThreads.gridy = 3;
        panelZipE.add(tfZipEThreads, gbcTFZipEThreads);
        tfZipEThreads.setColumns(4);

        JLabel lblZipEWarning = new JLabel();
        lblZipEWarning.setVerticalAlignment(SwingConstants.TOP);
        lblZipEWarning.setText(Messages.getString("MainFrame.lblZipEWarning.text")); //$NON-NLS-1$
        lblZipEWarning.setHorizontalAlignment(SwingConstants.CENTER);
        lblZipEWarning.setFont(new Font("Tahoma", Font.BOLD | Font.ITALIC, 11)); //$NON-NLS-1$
        lblZipEWarning.setBackground(UIManager.getColor("Button.background")); //$NON-NLS-1$
        final GridBagConstraints gbcLblZipEWarning = new GridBagConstraints();
        gbcLblZipEWarning.gridwidth = 3;
        gbcLblZipEWarning.gridx = 0;
        gbcLblZipEWarning.gridy = 4;
        panelZipE.add(lblZipEWarning, gbcLblZipEWarning);

        JPanel panel7Zip = new JPanel();
        compressorsPane.addTab(Messages.getString("MainFrame.7zExternal"), null, panel7Zip, null); //$NON-NLS-1$
        compressorsPane.setEnabledAt(2, true);
        final GridBagLayout gblPanel7Zip = new GridBagLayout();
        gblPanel7Zip.columnWidths = new int[] { 85, 123, 0, 40, 0 };
        gblPanel7Zip.rowHeights = new int[] { 0, 28, 28, 28, 0, 0 };
        gblPanel7Zip.columnWeights = new double[] { 0.0, 1.0, 1.0, 0.0, Double.MIN_VALUE };
        gblPanel7Zip.rowWeights = new double[] { 1.0, 0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE };
        panel7Zip.setLayout(gblPanel7Zip);

        JLabel lbl7zCmd = new JLabel(Messages.getString("MainFrame.lbl7zCmd.text")); //$NON-NLS-1$
        final GridBagConstraints gbcLbl7zCmd = new GridBagConstraints();
        gbcLbl7zCmd.anchor = GridBagConstraints.EAST;
        gbcLbl7zCmd.insets = new Insets(5, 5, 5, 5);
        gbcLbl7zCmd.gridx = 0;
        gbcLbl7zCmd.gridy = 1;
        panel7Zip.add(lbl7zCmd, gbcLbl7zCmd);

        tf7zCmd = new JFileDropTextField(txt -> session.getUser().getSettings().setProperty(SettingsEnum.sevenzip_cmd, txt)); // $NON-NLS-1$
        tf7zCmd.setUI(new JTextFieldHintUI(Messages.getString("MainFrame.DropDirHint"), Color.gray)); //$NON-NLS-1$
        tf7zCmd.setMode(JFileDropMode.FILE);
        tf7zCmd.setText(session.getUser().getSettings().getProperty(SettingsEnum.sevenzip_cmd)); // $NON-NLS-1$
        tf7zCmd.setColumns(30);
        final GridBagConstraints gbcTF7zCmd = new GridBagConstraints();
        gbcTF7zCmd.gridwidth = 2;
        gbcTF7zCmd.fill = GridBagConstraints.BOTH;
        gbcTF7zCmd.insets = new Insets(0, 0, 5, 0);
        gbcTF7zCmd.gridx = 1;
        gbcTF7zCmd.gridy = 1;
        panel7Zip.add(tf7zCmd, gbcTF7zCmd);

        JButton btn7zCmd = new JButton(""); //$NON-NLS-1$
        btn7zCmd.setIcon(MainFrame.getIcon("/jrm/resicons/icons/disk.png")); //$NON-NLS-1$
        final GridBagConstraints gbcBtn7zCmd = new GridBagConstraints();
        gbcBtn7zCmd.fill = GridBagConstraints.BOTH;
        gbcBtn7zCmd.insets = new Insets(0, 0, 5, 5);
        gbcBtn7zCmd.gridx = 3;
        gbcBtn7zCmd.gridy = 1;
        panel7Zip.add(btn7zCmd, gbcBtn7zCmd);

        JLabel lbl7zArgs = new JLabel(Messages.getString("MainFrame.lbl7zArgs.text")); //$NON-NLS-1$
        final GridBagConstraints gbcLbl7zArgs = new GridBagConstraints();
        gbcLbl7zArgs.anchor = GridBagConstraints.EAST;
        gbcLbl7zArgs.insets = new Insets(0, 5, 5, 5);
        gbcLbl7zArgs.gridx = 0;
        gbcLbl7zArgs.gridy = 2;
        panel7Zip.add(lbl7zArgs, gbcLbl7zArgs);

        cb7zArgs = new JComboBox<>();
        cb7zArgs.addActionListener(_ -> session.getUser().getSettings().setProperty(SettingsEnum.sevenzip_level, cb7zArgs.getSelectedItem().toString())); // $NON-NLS-1$
        cb7zArgs.setEditable(false);
        cb7zArgs.setModel(new DefaultComboBoxModel<>(SevenZipOptions.values()));
        cb7zArgs.setRenderer(new DefaultListCellRenderer() {

            @Override
            public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
                setText(((SevenZipOptions) value).getName());
                return this;
            }
        });
        cb7zArgs.setSelectedItem(SevenZipOptions.valueOf(session.getUser().getSettings().getProperty(SettingsEnum.sevenzip_level))); // $NON-NLS-1$
        final GridBagConstraints gbcCb7zArgs = new GridBagConstraints();
        gbcCb7zArgs.fill = GridBagConstraints.BOTH;
        gbcCb7zArgs.gridwidth = 3;
        gbcCb7zArgs.insets = new Insets(0, 0, 5, 5);
        gbcCb7zArgs.gridx = 1;
        gbcCb7zArgs.gridy = 2;
        panel7Zip.add(cb7zArgs, gbcCb7zArgs);

        JLabel lbl7zThreads = new JLabel(Messages.getString("MainFrame.lbl7zThreads.text")); //$NON-NLS-1$
        final GridBagConstraints gbcLbl7zThreads = new GridBagConstraints();
        gbcLbl7zThreads.insets = new Insets(0, 0, 5, 5);
        gbcLbl7zThreads.anchor = GridBagConstraints.EAST;
        gbcLbl7zThreads.gridx = 0;
        gbcLbl7zThreads.gridy = 3;
        panel7Zip.add(lbl7zThreads, gbcLbl7zThreads);

        tf7zThreads = new JTextField();
        tf7zThreads.setText(Integer.toString(session.getUser().getSettings().getProperty(SettingsEnum.sevenzip_threads, Integer.class))); // $NON-NLS-1$
        tf7zThreads.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(final FocusEvent e) {
                session.getUser().getSettings().setProperty(SettingsEnum.sevenzip_threads, tf7zThreads.getText()); // $NON-NLS-1$
            }
        });
        tf7zThreads.addActionListener(_ -> session.getUser().getSettings().setProperty(SettingsEnum.sevenzip_threads, tf7zThreads.getText())); // $NON-NLS-1$
        final GridBagConstraints gbcTF7zThreads = new GridBagConstraints();
        gbcTF7zThreads.fill = GridBagConstraints.VERTICAL;
        gbcTF7zThreads.anchor = GridBagConstraints.WEST;
        gbcTF7zThreads.insets = new Insets(0, 0, 5, 5);
        gbcTF7zThreads.gridx = 1;
        gbcTF7zThreads.gridy = 3;
        panel7Zip.add(tf7zThreads, gbcTF7zThreads);
        tf7zThreads.setColumns(4);

        ckbx7zSolid = new JCheckBox(Messages.getString("MainFrame.ckbx7zSolid.text")); //$NON-NLS-1$
        ckbx7zSolid.setSelected(session.getUser().getSettings().getProperty(SettingsEnum.sevenzip_solid, Boolean.class)); // $NON-NLS-1$
        ckbx7zSolid.addActionListener(_ -> session.getUser().getSettings().setProperty(SettingsEnum.sevenzip_solid, ckbx7zSolid.isSelected()));
        final GridBagConstraints gbcCkbx7zSolid = new GridBagConstraints();
        gbcCkbx7zSolid.insets = new Insets(0, 0, 5, 5);
        gbcCkbx7zSolid.gridx = 2;
        gbcCkbx7zSolid.gridy = 3;
        panel7Zip.add(ckbx7zSolid, gbcCkbx7zSolid);

        JLabel lbl7zWarning = new JLabel();
        lbl7zWarning.setVerticalAlignment(SwingConstants.TOP);
        lbl7zWarning.setText(Messages.getString("MainFrame.lbl7zWarning.text")); //$NON-NLS-1$
        lbl7zWarning.setHorizontalAlignment(SwingConstants.CENTER);
        lbl7zWarning.setFont(new Font("Tahoma", Font.BOLD | Font.ITALIC, 11)); //$NON-NLS-1$
        lbl7zWarning.setBackground(UIManager.getColor("Button.background")); //$NON-NLS-1$
        final GridBagConstraints gbcLbl7zWarning = new GridBagConstraints();
        gbcLbl7zWarning.gridwidth = 4;
        gbcLbl7zWarning.gridx = 0;
        gbcLbl7zWarning.gridy = 4;
        panel7Zip.add(lbl7zWarning, gbcLbl7zWarning);

    }

}
