package jrm.aui.progress;

import java.io.InputStream;

import jrm.misc.OffsetProvider;

/**
 * Serializes all calls to a shared {@link ProgressHandler} so concurrent workers can reuse one instance.
 */
public final class SynchronizedProgressHandler implements ProgressHandler {
    private final ProgressHandler delegate;

    public SynchronizedProgressHandler(final ProgressHandler delegate) {
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
