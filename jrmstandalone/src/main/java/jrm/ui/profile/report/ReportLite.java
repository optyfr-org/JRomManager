package jrm.ui.profile.report;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.util.concurrent.ExecutionException;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import jrm.misc.Log;
import jrm.profile.report.Report;
import jrm.security.Session;

/**
 * Lightweight dialog displaying a single report file.
 * <p>
 * Loads the report asynchronously and displays it in a simple dialog.
 */
public class ReportLite extends JDialog {
    /** The parent window that opened this dialog. */
    private Window parentWindow;

    /**
     * Constructs the lightweight report dialog.
     *
     * @param session the user session
     * @param parent the parent window
     * @param reportFile the report file to display
     */
    public ReportLite(final Session session, Window parent, File reportFile) {
        super(parent);
        this.parentWindow = parent;
        parent.setEnabled(false);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(600, 600);
        setLocationRelativeTo(parent);
        setVisible(true);
        getContentPane().setLayout(new BorderLayout());
        JLabel wait = new JLabel("Loading...");
        wait.setFont(getFont().deriveFont(14.0f));
        wait.setHorizontalAlignment(SwingConstants.CENTER);
        wait.setBorder(new EmptyBorder(5, 5, 5, 5));
        getContentPane().add(wait, BorderLayout.CENTER);
        JPanel buttonPane = new JPanel();
        buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
        getContentPane().add(buttonPane, BorderLayout.SOUTH);
        JButton okButton = new JButton("OK");
        buttonPane.add(okButton);
        okButton.addActionListener(_ -> ReportLite.this.dispose());
        getRootPane().setDefaultButton(okButton);
        new SwingWorker<Report, Void>() {
            @Override
            protected Report doInBackground() throws Exception {
                try {
                    return Report.load(session, reportFile);
                } catch (Exception e) {
                    Log.err(e.getMessage(), e);
                }
                return null; // NOSONAR
            }

            @Override
            protected void done() {
                try {
                    getContentPane().remove(wait);
                    getContentPane().add(new ReportView(get()), BorderLayout.CENTER, 0);
                    validate();
                } catch (InterruptedException e) {
                    Log.err(e.getMessage(), e);
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Log.err(e.getMessage(), e);
                }
            }
        }.execute();
    }

    @Override
    public void dispose() {
        parentWindow.setEnabled(true);
        super.dispose();
    }

}
