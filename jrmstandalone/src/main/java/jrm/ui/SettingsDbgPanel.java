package jrm.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.SoftBevelBorder;

import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.misc.SettingsEnum;
import jrm.security.Session;

/**
 * Panel for debug and diagnostic settings.
 * <p>
 * Provides controls for configuring log level, monitoring memory usage,
 * and other debugging options.
 */
public class SettingsDbgPanel extends JPanel {
    /** Format string for memory usage display. */
    private static final String XX_MIB = "%.2f MiB";

    /** Combo box for selecting the log level. */
    private JComboBox<Level> cbLogLevel;
    /** Label displaying current memory usage. */
    private JLabel lblMemoryUsage;
    /** Scheduler for periodic memory usage updates. */
    final transient ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    /** Available log levels for selection. */
    final Level[] levels = new Level[] { Level.OFF, Level.SEVERE, Level.WARNING, Level.INFO, Level.CONFIG, Level.FINE, Level.FINER, Level.FINEST, Level.ALL };

    /**
     * Constructs the debug settings panel.
     *
     * @param session the user session for accessing settings
     */
    public SettingsDbgPanel(final Session session) {
        final GridBagLayout gblDebug = new GridBagLayout();
        gblDebug.columnWidths = new int[] { 50, 0, 0, 10, 0 };
        gblDebug.rowHeights = new int[] { 0, 0, 0, 0, 0, 0, 0 };
        gblDebug.columnWeights = new double[] { 1.0, 1.0, 0.0, 1.0, Double.MIN_VALUE };
        gblDebug.rowWeights = new double[] { 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE };
        this.setLayout(gblDebug);

        JLabel lblLogLevel = new JLabel(Messages.getString("MainFrame.lblLogLevel.text")); //$NON-NLS-1$
        final GridBagConstraints gbcLblLogLevel = new GridBagConstraints();
        gbcLblLogLevel.anchor = GridBagConstraints.EAST;
        gbcLblLogLevel.fill = GridBagConstraints.VERTICAL;
        gbcLblLogLevel.insets = new Insets(0, 0, 5, 5);
        gbcLblLogLevel.gridx = 0;
        gbcLblLogLevel.gridy = 1;
        this.add(lblLogLevel, gbcLblLogLevel);

        cbLogLevel = new JComboBox<>(new DefaultComboBoxModel<>(levels));
        final GridBagConstraints gbcCbLogLevel = new GridBagConstraints();
        gbcCbLogLevel.gridwidth = 2;
        gbcCbLogLevel.insets = new Insets(0, 0, 5, 5);
        gbcCbLogLevel.fill = GridBagConstraints.HORIZONTAL;
        gbcCbLogLevel.gridx = 1;
        gbcCbLogLevel.gridy = 1;
        this.add(cbLogLevel, gbcCbLogLevel);
        cbLogLevel.addActionListener(_ -> {
            session.getUser().getSettings().setProperty(SettingsEnum.debug_level, cbLogLevel.getSelectedItem().toString());
            Log.setLevel(Level.parse(cbLogLevel.getSelectedItem().toString()));
        }); // $NON-NLS-1$
        cbLogLevel.setSelectedItem(Level.parse(session.getUser().getSettings().getProperty(SettingsEnum.debug_level))); // $NON-NLS-1$

        JLabel lblMemory = new JLabel(Messages.getString("MainFrame.lblMemory.text")); //$NON-NLS-1$
        lblMemory.setHorizontalAlignment(SwingConstants.TRAILING);
        final GridBagConstraints gbcLblMemory = new GridBagConstraints();
        gbcLblMemory.anchor = GridBagConstraints.EAST;
        gbcLblMemory.insets = new Insets(0, 0, 5, 5);
        gbcLblMemory.gridx = 0;
        gbcLblMemory.gridy = 2;
        this.add(lblMemory, gbcLblMemory);

        lblMemoryUsage = new JLabel(" "); //$NON-NLS-1$
        lblMemoryUsage.setBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));
        final GridBagConstraints gbcLblMemoryUsage = new GridBagConstraints();
        gbcLblMemoryUsage.fill = GridBagConstraints.BOTH;
        gbcLblMemoryUsage.insets = new Insets(0, 0, 5, 5);
        gbcLblMemoryUsage.gridx = 1;
        gbcLblMemoryUsage.gridy = 2;
        this.add(lblMemoryUsage, gbcLblMemoryUsage);

        JButton btnGc = new JButton(Messages.getString("MainFrame.btnGc.text")); //$NON-NLS-1$
        btnGc.addActionListener(_ -> {
            System.gc(); // NOSONAR
            updateMemory();
        });
        final GridBagConstraints gbcBtnGc = new GridBagConstraints();
        gbcBtnGc.fill = GridBagConstraints.HORIZONTAL;
        gbcBtnGc.insets = new Insets(0, 0, 5, 5);
        gbcBtnGc.gridx = 2;
        gbcBtnGc.gridy = 2;
        this.add(btnGc, gbcBtnGc);

        scheduler.scheduleAtFixedRate(this::updateMemory, 0, 20, TimeUnit.SECONDS);
    }

    /**
     * Update memory.
     */
    void updateMemory() {
        final Runtime rt = Runtime.getRuntime();
        lblMemoryUsage.setText(String.format(Messages.getString("MainFrame.MemoryUsage"), String.format(XX_MIB, rt.totalMemory() / 1048576.0), //$NON-NLS-1$
                String.format(XX_MIB, (rt.totalMemory() - rt.freeMemory()) / 1048576.0), String.format(XX_MIB, rt.freeMemory() / 1048576.0),
                String.format(XX_MIB, rt.maxMemory() / 1048576.0))); // $NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }

}
