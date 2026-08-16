/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.profile.report;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.border.BevelBorder;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.SerializationUtils;

import jrm.aui.progress.StatusHandler;
import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.profile.report.FilterOptions;
import jrm.security.Session;
import jrm.ui.MainFrame;

/**
 * Dialog displaying the scan report as a tree view.
 * <p>
 * Shows the hierarchical report structure with filtering options and status bar.
 *
 * @author optyfr
 */
public class ReportFrame extends JDialog implements StatusHandler {

    /** Label displaying the current processing status. */
    private final JLabel lblStatus = new JLabel(""); //$NON-NLS-1$

    /** The report view component. */
    private final ReportView view;

    /**
     * Constructs the report frame.
     *
     * @param session the user session providing access to report data
     * @param owner the parent window
     * @throws HeadlessException if the environment does not support a display
     */
    public ReportFrame(final Session session, final Window owner) throws HeadlessException {
        super(); // $NON-NLS-1$
        setTitle(Messages.getString("ReportFrame.Title")); //$NON-NLS-1$
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                session.getUser().getSettings().setProperty("ReportFrame.Bounds", Hex.encodeHexString(SerializationUtils.serialize(getBounds()))); //$NON-NLS-1$
            }

            @Override
            public void windowOpened(WindowEvent e) {
                session.getReport().getHandler().filter(session.getReport().getHandler().getFilterOptions().toArray(new FilterOptions[0]));
            }
        });
        setTitle(Messages.getString("ReportFrame.Title")); //$NON-NLS-1$
        setPreferredSize(new Dimension(800, 600));
        setMinimumSize(new Dimension(400, 300));
        setIconImage(MainFrame.getIcon("/jrm/resicons/rom.png").getImage()); //$NON-NLS-1$
        final GridBagLayout gridBagLayout = new GridBagLayout();
        gridBagLayout.columnWidths = new int[] { 784, 0 };
        gridBagLayout.rowHeights = new int[] { 280, 24, 0 };
        gridBagLayout.columnWeights = new double[] { 1.0, Double.MIN_VALUE };
        gridBagLayout.rowWeights = new double[] { 1.0, 0.0, Double.MIN_VALUE };
        getContentPane().setLayout(gridBagLayout);

        view = new ReportView(session.getReport());
        final GridBagConstraints gbcScrollPane = new GridBagConstraints();
        gbcScrollPane.fill = GridBagConstraints.BOTH;
        gbcScrollPane.gridx = 0;
        gbcScrollPane.gridy = 0;
        getContentPane().add(view, gbcScrollPane);

        session.getReport().setStatusHandler(this);

        final GridBagConstraints gbcLblStatus = new GridBagConstraints();
        gbcLblStatus.ipadx = 2;
        gbcLblStatus.insets = new Insets(2, 2, 2, 2);
        gbcLblStatus.fill = GridBagConstraints.BOTH;
        gbcLblStatus.gridx = 0;
        gbcLblStatus.gridy = 1;
        lblStatus.setBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null, null));
        getContentPane().add(lblStatus, gbcLblStatus);

        pack();

        try {
            setBounds(SerializationUtils.deserialize(Hex.decodeHex(
                    session.getUser().getSettings().getProperty("ReportFrame.Bounds", Hex.encodeHexString(SerializationUtils.serialize(new Rectangle(10, 10, 800, 600))))))); //$NON-NLS-1$
        } catch (final DecoderException e1) {
            Log.err(e1.getMessage(), e1);
        }
    }

    @Override
    public void setStatus(final String text) {
        lblStatus.setText(text);
    }

    private boolean needUpdate = false;

    public void setNeedUpdate(boolean needUpdate) {
        this.needUpdate = needUpdate;
        if (isVisible())
            update();
    }

    private void update() {
        if (needUpdate) {
            view.update();
            needUpdate = false;
        }
    }

    @Override
    public void setVisible(boolean b) {
        update();
        super.setVisible(b);
    }
}
