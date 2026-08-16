package jrm.ui.batch;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;

import jrm.locale.Messages;
import jrm.security.Session;
import jrm.ui.MainFrame;

/**
 * Main batch tools panel.
 * <p>
 * Contains tabs for batch directory update, torrent check, and compression operations.
 */
public class BatchPanel extends JPanel {
    /**
     * Constructs the batch tools panel.
     *
     * @param session the user session for accessing settings
     */
    public BatchPanel(final Session session) {
        this.setLayout(new BorderLayout(0, 0));

        JTabbedPane batchToolsTabbedPane = new JTabbedPane(SwingConstants.TOP);
        this.add(batchToolsTabbedPane);

        BatchDirUpd8rPanel panelBatchToolsDat2Dir = new BatchDirUpd8rPanel(session);
        batchToolsTabbedPane.addTab(Messages.getString("MainFrame.panelBatchToolsDat2Dir.title"), MainFrame.getIcon("/jrm/resicons/icons/application_cascade.png"), //$NON-NLS-1$
                panelBatchToolsDat2Dir, null);

        BatchTrrntChkPanel panelBatchToolsDir2Torrent = new BatchTrrntChkPanel(session);
        batchToolsTabbedPane.addTab(Messages.getString("MainFrame.panelBatchToolsDir2Torrent.title"), MainFrame.getIcon("/jrm/resicons/icons/drive_web.png"), //$NON-NLS-1$
                panelBatchToolsDir2Torrent, null);

        BatchCompressorPanel panelBatchToolsCompressor = new BatchCompressorPanel(session);
        batchToolsTabbedPane.addTab(Messages.getString("BatchPanel.Compressor"), MainFrame.getIcon("/jrm/resicons/icons/compress.png"), panelBatchToolsCompressor, null); //$NON-NLS-1$
    }

}
