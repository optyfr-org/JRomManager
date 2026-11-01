/*
 * Copyright (C) 2026 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.misc;

import java.io.File;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import jrm.aui.progress.ProgressHandler;
import jrm.profile.Profile;
import jrm.profile.scan.Scan;
import jrm.security.Session;

/**
 * AOT cache training hook (JEP 483/514). Active only when the {@value #TRAIN_PROPERTY} system property is set, which happens
 * exclusively on build-time training runs launched by the {@code aotCaches} Gradle task with {@code -XX:AOTCacheOutput}. In a
 * normal production start the property is absent and every method here is a no-op.
 */
public final class AotTrainer {

    /** System property switch enabling the training hook (build-time only, never shipped in launcher args). */
    public static final String TRAIN_PROPERTY = "jrm.aot.train";

    /** System property pointing at the fixture DAT to load during a training run. */
    public static final String FIXTURE_PROPERTY = "jrm.aot.fixture";

    private AotTrainer() {
    }

    /**
     * Returns whether an AOT training run is in progress.
     *
     * @return {@code true} when the {@value #TRAIN_PROPERTY} system property is set
     */
    public static boolean isTraining() {
        return System.getProperty(TRAIN_PROPERTY) != null;
    }

    /**
     * Loads the fixture DAT referenced by {@value #FIXTURE_PROPERTY} into the given session and runs one scan, exercising the
     * DAT parse, scan and Fory cache/report serialization paths so the AOT cache records them.
     *
     * @param session the session to load the fixture profile into
     *
     * @throws IllegalStateException if the training run fails (fails the build-time training process)
     */
    public static void train(final Session session) {
        if (!isTraining())
            return;
        final var fixture = System.getProperty(FIXTURE_PROPERTY);
        try {
            final var profile = Profile.load(session, new File(fixture), new NoopProgressHandler());
            session.setCurrProfile(profile);
            new Scan(profile, new NoopProgressHandler());
            Log.info("AOT training run completed");
        } catch (final Exception e) {
            Log.err("AOT training run failed", e);
            // Also dump to stderr so Docker buildx layers always show the cause even if JUL is reconfigured.
            e.printStackTrace(); // NOSONAR
            throw new IllegalStateException("AOT training run failed", e);
        }
    }

    /**
     * Issues one {@code GET /} against the running server so the static servlet classes are loaded before the training run
     * stops the server. Uses a raw socket to stay within {@code java.base}.
     *
     * @param bind the bind address the server listens on
     * @param port the HTTP port the server listens on
     */
    public static void pingRoot(final String bind, final int port) {
        if (!isTraining())
            return;
        try (final var socket = new Socket(bind, port)) {
            socket.setSoTimeout(30000);
            final var out = socket.getOutputStream();
            out.write(("GET / HTTP/1.1\r\nHost: " + bind + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            final var in = socket.getInputStream();
            while (in.read() != -1) {
                // drain the response
            }
        } catch (final Exception e) {
            Log.err("AOT training ping failed", e);
        }
    }

    /** Minimal no-op {@link ProgressHandler} for headless training runs. */
    private static final class NoopProgressHandler implements ProgressHandler {
        @Override
        public void setOptions(final Option first, final Option... rest) {
        }

        @Override
        public void setInfos(final int threadCnt, final Boolean multipleSubInfos) {
        }

        @Override
        public void clearInfos() {
        }

        @Override
        public void setProgress(final String msg, final Integer val, final Integer max, final String submsg) {
        }

        @Override
        public void setProgress2(final String msg, final Integer val, final Integer max) {
        }

        @Override
        public void setProgress3(final String msg, final Integer val, final Integer max) {
        }

        @Override
        public int getCurrent() {
            return 0;
        }

        @Override
        public int getCurrent2() {
            return 0;
        }

        @Override
        public int getCurrent3() {
            return 0;
        }

        @Override
        public boolean isCancel() {
            return false;
        }

        @Override
        public void doCancel() {
        }

        @Override
        public void canCancel(final boolean canCancel) {
        }

        @Override
        public boolean canCancel() {
            return false;
        }

        @Override
        public InputStream getInputStream(final InputStream in, final Integer len) {
            return in;
        }

        @Override
        public void close() {
        }

        @Override
        public void addError(final String error) {
        }

        @Override
        public void setOffsetProvider(final OffsetProvider offsetProvider) {
        }
    }
}
