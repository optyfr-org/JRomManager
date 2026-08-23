/**
 * 
 */
package jrm.batch;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.commons.compress.utils.Sets;
import org.apache.commons.io.FilenameUtils;

import jrm.aui.basic.AbstractSrcDstResult;
import jrm.aui.basic.ResultColUpdater;
import jrm.aui.progress.ProgressHandler;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.Profile;
import jrm.profile.fix.Fix;
import jrm.profile.report.Report;
import jrm.profile.scan.DirScan;
import jrm.profile.scan.Scan;
import jrm.profile.scan.ScanException;
import jrm.security.PathAbstractor;
import jrm.security.Session;

/**
 * Updates destination directories based on DAT files, scanning and fixing ROM files from source directories according to configured
 * profiles.
 */
public class DirUpdater {
    /** the active user session */
    private final Session session;
    /** the handler for reporting progress during the update process */
    private final ProgressHandler progress;
    /** the list of source directories containing new ROM files to be processed */
    private final List<File> srcdirs;
    /** the interface for updating results in the user interface */
    private final ResultColUpdater result;
    /**
     * flag indicating whether to perform a dry run (no actual changes) or to apply fixes
     */
    private final boolean dryrun;
    /** map from result to its row index to avoid repeated indexOf scans */
    private final Map<AbstractSrcDstResult, Integer> rowMap;

    /**
     * Constructs a DirUpdater with the specified parameters.
     *
     * @param session the active user session
     * @param sdrl the list of source-destination results to process
     * @param progress the handler for reporting progress during the update process
     * @param srcdirs the list of source directories containing new ROM files to be processed
     * @param result the interface for updating results in the user interface
     * @param dryrun flag indicating whether to perform a dry run (no actual changes) or to apply fixes
     */
    public DirUpdater(final Session session, final List<? extends AbstractSrcDstResult> sdrl, final ProgressHandler progress, final List<File> srcdirs,
            final ResultColUpdater result, final boolean dryrun) {
        this.session = session;
        this.progress = progress;
        this.srcdirs = srcdirs;
        this.result = result;
        this.dryrun = dryrun;

        rowMap = new HashMap<>();
        for (int i = 0; i < sdrl.size(); i++)
            rowMap.put(sdrl.get(i), i);

        final Map<String, DirScan> scancache = new HashMap<>();
        sdrl.stream().filter(AbstractSrcDstResult::isSelected).forEach(sdr -> result.updateResult(rowMap.get(sdr), "") //$NON-NLS-1$
        );
        sdrl.stream().filter(AbstractSrcDstResult::isSelected).takeWhile(_ -> !progress.isCancel()).forEach(sdr -> update(scancache, sdr));
    }

    /**
     * Processes the specified DAT file or directory of DAT files, updating the status and results.
     * 
     * @param scancache the cache of previous scans
     * @param sdr the source-destination result to update
     * 
     * @throws SecurityException if a security manager denies access
     * @throws BreakException if the process was cancelled
     */
    private void update(final Map<String, DirScan> scancache, AbstractSrcDstResult sdr) throws SecurityException, BreakException {
        final var row = rowMap.get(sdr);
        result.updateResult(row, "In progress..."); //$NON-NLS-1$
        final var dat = PathAbstractor.getAbsolutePath(session, sdr.getSrc()).toFile();
        final var dst = PathAbstractor.getAbsolutePath(session, sdr.getDst()).toFile();
        final var dur = new DirUpdaterResults();
        dur.setDat(dat);
        try {
            var datlist = new File[] { dat };
            var dstlist = new File[] { dst };
            if (dat.isDirectory()) {
                datlist = dat.listFiles((_, sfilename) -> Sets.newHashSet("xml", "dat").contains(FilenameUtils.getExtension(sfilename).toLowerCase())); //$NON-NLS-1$ //$NON-NLS-2$
                Arrays.sort(datlist, (a, b) -> a.getAbsolutePath().compareTo(b.getAbsolutePath()));
                for (File d : datlist)
                    Files.copy(session.getUser().getSettings().getProfileSettingsFile(dat).toPath(), session.getUser().getSettings().getProfileSettingsFile(d).toPath(),
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                dstlist = Stream.of(datlist).map(datfile -> new File(dst, FilenameUtils.removeExtension(datfile.getName()))).toArray(File[]::new);
                for (File d : dstlist)
                    d.mkdir();
            }
            update(scancache, row, dat, dur, datlist, dstlist);
            progress.setProgress3(null, null);
            dur.save(session);
        } catch (final BreakException e) {
            throw e;
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Processes a list of DAT files and their corresponding destination directories, performing scans and optionally fixing
     * mismatches for each entry.
     *
     * @param scancache the cache of directory scans to optimize search operations
     * @param row the index of the row being updated in the results table
     * @param dat the source DAT file or directory containing DAT files
     * @param dur the accumulator for collecting update results
     * @param datlist the array of DAT files to process
     * @param dstlist the array of corresponding destination directories
     * 
     * @throws BreakException if the update process is aborted or canceled
     * @throws ScanException if an error occurs while scanning the directories
     */
    private void update(final Map<String, DirScan> scancache, final int row, final File dat, final DirUpdaterResults dur, File[] datlist, File[] dstlist)
            throws BreakException, ScanException {
        final var total = new AtomicLong();
        final var ok = new AtomicLong();
        for (var j = 0; j < datlist.length; j++) {
            if (dat.isDirectory())
                progress.setProgress3(String.format("%s/%s (%d/%d)", dat.getName(), FilenameUtils.getBaseName(datlist[j].getName()), j, datlist.length), j, datlist.length);
            else
                progress.setProgress3(String.format("%s (%d/%d)", FilenameUtils.getBaseName(datlist[j].getName()), j, datlist.length), j, datlist.length);
            session.getReport().setProfile(Profile.load(session, datlist[j], progress));
            if (session.getCurrProfile().getSoftwaresListCnt() > 0 && dat.isDirectory())
                session.getCurrProfile().setProperty(ProfileSettingsEnum.roms_dest_dir, dstlist[j].getParentFile().getAbsolutePath()); // $NON-NLS-1$
            else
                session.getCurrProfile().setProperty(ProfileSettingsEnum.roms_dest_dir, dstlist[j].getAbsolutePath()); // $NON-NLS-1$
            session.getCurrProfile().setProperty(ProfileSettingsEnum.src_dir, String.join("|", srcdirs.stream().map(File::getAbsolutePath).toList())); //$NON-NLS-1$ //$NON-NLS-2$
            final var scan = new Scan(session.getCurrProfile(), progress, scancache);
            if (!dryrun && !scan.actions.isEmpty()) {
                new Fix(session.getCurrProfile(), scan, progress);
                new Scan(session.getCurrProfile(), progress, scancache);
            }
            final var lTotal = total.addAndGet(
                    (long) session.getReport().getStats().getSetCreate() + session.getReport().getStats().getSetFound() + session.getReport().getStats().getSetMissing());
            final var lOk = ok.addAndGet((long) session.getReport().getStats().getSetCreateComplete() + session.getReport().getStats().getSetFoundFixComplete()
                    + session.getReport().getStats().getSetFoundOk());
            dur.add(datlist[j], new Report.Stats(session.getReport().getStats()));
            session.getReport().save(session);
            result.updateResult(row, String.format(session.getMsgs().getString("DirUpdater.Result"), lOk * 100.0 / lTotal, lTotal - lOk, lTotal)); //$NON-NLS-1$
        }
    }
}
