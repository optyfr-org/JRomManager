package jrm.ui.batch;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import jrm.batch.TrntChkReport;

/**
 * Dialog displaying batch torrent check results.
 */
public class BatchTrrntChkResultsDialog extends JDialog {
    /** The parent window that opened this dialog. */
    Window parentWindow;

    /**
     * Constructs the results dialog.
     *
     * @param parent the parent window
     * @param results the torrent check results to display
     */
    public BatchTrrntChkResultsDialog(Window parent, TrntChkReport results) {
        super(parent);
        this.parentWindow = parent;
        parent.setEnabled(false);
        setBounds(100, 100, 455, 410);
        getContentPane().setLayout(new BorderLayout());
        JScrollPane contentPanel = new BatchTrrntChkReportView(results);
        contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        getContentPane().add(contentPanel, BorderLayout.CENTER);
        JPanel buttonPane = new JPanel();
        buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
        getContentPane().add(buttonPane, BorderLayout.SOUTH);
        JButton okButton = new JButton("OK");
        okButton.addActionListener(_ -> BatchTrrntChkResultsDialog.this.dispose());
        buttonPane.add(okButton);
        getRootPane().setDefaultButton(okButton);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    @Override
    public void dispose() {
        parentWindow.setEnabled(true);
        super.dispose();
    }
}
