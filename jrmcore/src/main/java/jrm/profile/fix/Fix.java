/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.fix;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.time.DurationFormatUtils;

import jrm.aui.progress.ProgressHandler;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.misc.MultiThreadingVirtual;
import jrm.misc.OffsetProvider;
import jrm.misc.ProfileSettingsEnum;
import jrm.misc.SettingsEnum;
import jrm.profile.Profile;
import jrm.profile.fix.actions.BackupContainer;
import jrm.profile.fix.actions.ContainerAction;
import jrm.profile.scan.Scan;

/**
 * Orchestrates the application of fixes, repairs, and container actions determined by a prior scan across the user's ROM and game
 * sets.
 * <p>
 * This class coordinates virtual multi-threaded execution pools to perform parallel processing of queued actions, updates visual
 * progress bars, backups altered data, and stores session timing statistics.
 * </p>
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public class Fix {
    /**
     * Retain the scan result from which this class will apply fixes from defined actions.
     */
    private final Scan currScan;

    /**
     * Constructs a new {@code Fix} coordinator and immediately launches the fixing pipeline.
     * 
     * @param currProfile the active {@link Profile} from which settings are read and updated
     * @param currScan the active {@link Scan} containing action definitions to process
     * @param progress the UI progress feedback visual handler
     */
    public Fix(final Profile currProfile, final Scan currScan, final ProgressHandler progress) {
        this.currScan = currScan;

        final boolean useParallelism = Boolean.TRUE.equals(currProfile.getProperty(ProfileSettingsEnum.use_parallelism, Boolean.class)); // $NON-NLS-1$
        final var nThreads = useParallelism ? currProfile.getSession().getUser().getSettings().getProperty(SettingsEnum.thread_count, Integer.class) : 1;

        final long start = System.currentTimeMillis();

        /*
         * Initialize global progression
         */
        final var i = new AtomicInteger(0);
        final var max = new AtomicInteger(0);
        currScan.actions.forEach(actions -> {
            max.addAndGet(actions.size());
            actions.forEach(action -> max.addAndGet(action.count() + (int) (action.estimatedSize() >> 20)));
        });
        progress.setProgress(currProfile.getSession().getMsgs().getString("Fix.Fixing"), i.get(), max.get()); //$NON-NLS-1$

        // foreach ordered action groups
        currScan.actions.forEach(actions -> {
            if (!actions.isEmpty()) {
                final List<ContainerAction> done = Collections.synchronizedList(new ArrayList<ContainerAction>());
                // resets progression parallelism (needed since thread IDs may change between
                // two parallel streaming)
                progress.setInfos(nThreads, useParallelism);
                try (final var mt = new MultiThreadingVirtual<ContainerAction>("fix", progress, nThreads, action -> doAction(currProfile, progress, i, done, action))) {
                    mt.start(actions.stream().sorted(ContainerAction.rcomparator()));
                }
                // close all open FS from backup (if the last actions was backup)
                if (!done.isEmpty() && done.get(0) instanceof BackupContainer)
                    BackupContainer.closeAllFS();
                // remove all done actions
                actions.removeAll(done);
                // this actions group is finished, clear progression status
                progress.clearInfos();
                if (!actions.isEmpty())
                    Log.warn(() -> "Missed " + actions.size() + " actions"); //$NON-NLS-1$
            }
        });

        // reset progression to normal before leaving
        progress.setInfos(1, false);
        // set stats last fixed date to 'now'
        currProfile.getNfo().getStats().setFixed(Instant.now());

        // output to console timing information
        Log.debug(() -> "Fix total duration for " + currProfile.getNfo().getName() + " : " + DurationFormatUtils.formatDurationHMS(System.currentTimeMillis() - start)); //$NON-NLS-1$
    }

    /**
     * Internal worker method executing a single container action in the multi-threading pool context.
     * <p>
     * A thread-safe wrapper around the shared progress handler is passed to the action so concurrent progress updates are
     * serialized while the actual container work still runs in parallel.
     * </p>
     * 
     * @param currProfile the active {@link Profile} context
     * @param progress the active UI progress status tracker
     * @param i global task progression counter
     * @param done thread-safe list storing successfully processed actions
     * @param action the actual container repair task to apply
     */
    private void doAction(final Profile currProfile, final ProgressHandler progress, final AtomicInteger i, final List<ContainerAction> done, ContainerAction action) {
        final var safeProgress = new SynchronizedProgressHandler(progress);
        if (safeProgress.isCancel())
            return;
        try {
            if (!action.doAction(currProfile.getSession(), safeProgress)) // do action...
            {
                Log.warn(() -> "Action " + action.toString() + " has failed, remaining actions processing will be cancelled");
                safeProgress.doCancel(); // ... and cancel all if it failed
            } else
                done.add(action); // add to "done" list successful action
            safeProgress.setProgress("", i.addAndGet(1 + action.count() + (int) (action.estimatedSize() >> 20))); // update progression
        } catch (final BreakException _) { // special catch case from BreakException thrown from underlying streams
            safeProgress.doCancel();
        } catch (final Exception e) { // oups! something unexpected happened
            safeProgress.setProgress("");
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Thread-safe wrapper that serializes all calls to a delegate {@link ProgressHandler}. This allows parallel actions to share a
     * single progress handler without corrupting its internal state.
     */
    private static final class SynchronizedProgressHandler implements ProgressHandler {
        private final ProgressHandler delegate;

        SynchronizedProgressHandler(ProgressHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized void setOptions(ProgressHandler.Option first, ProgressHandler.Option... rest) {
            delegate.setOptions(first, rest);
        }

        @Override
        public synchronized void setInfos(int threadCnt, Boolean multipleSubInfos) {
            delegate.setInfos(threadCnt, multipleSubInfos);
        }

        @Override
        public synchronized void clearInfos() {
            delegate.clearInfos();
        }

        @Override
        public synchronized void setProgress(String msg, Integer val, Integer max, String submsg) {
            delegate.setProgress(msg, val, max, submsg);
        }

        @Override
        public synchronized void setProgress2(String msg, Integer val, Integer max) {
            delegate.setProgress2(msg, val, max);
        }

        @Override
        public synchronized void setProgress3(String msg, Integer val, Integer max) {
            delegate.setProgress3(msg, val, max);
        }

        @Override
        public synchronized int getCurrent() {
            return delegate.getCurrent();
        }

        @Override
        public synchronized int getCurrent2() {
            return delegate.getCurrent2();
        }

        @Override
        public synchronized int getCurrent3() {
            return delegate.getCurrent3();
        }

        @Override
        public synchronized boolean isCancel() {
            return delegate.isCancel();
        }

        @Override
        public synchronized void doCancel() {
            delegate.doCancel();
        }

        @Override
        public synchronized void canCancel(boolean canCancel) {
            delegate.canCancel(canCancel);
        }

        @Override
        public synchronized boolean canCancel() {
            return delegate.canCancel();
        }

        @Override
        public synchronized InputStream getInputStream(InputStream in, Integer len) {
            return delegate.getInputStream(in, len);
        }

        @Override
        public synchronized void close() {
            delegate.close();
        }

        @Override
        public synchronized void addError(String error) {
            delegate.addError(error);
        }

        @Override
        public synchronized void setOffsetProvider(OffsetProvider offsetProvider) {
            delegate.setOffsetProvider(offsetProvider);
        }
    }

    /**
     * Returns the count of remaining actions that have not yet been executed.
     * 
     * @return the number of pending actions, or 0 if all were processed successfully
     */
    public int getActionsRemain() {
        final var actionsRemain = new AtomicInteger(0);
        currScan.actions.forEach(actions -> actionsRemain.addAndGet(actions.size()));
        return actionsRemain.get();
    }

}
