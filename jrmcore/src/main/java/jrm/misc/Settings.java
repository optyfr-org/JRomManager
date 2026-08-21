package jrm.misc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.AtomicReference;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import lombok.Getter;
import lombok.val;

/**
 * Abstract base class managing general key-value properties. Provides capabilities for reading, writing, and synchronizing
 * XML-based property files, as well as exporting configurations to JSON representations.
 * 
 * @author optyfr
 */
public abstract class Settings extends SettingsImpl {
    /**
     * Shared single-thread scheduler used to debounce automatic settings persistence.
     */
    private static final ScheduledExecutorService SAVE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        final var t = new Thread(r, "jrm-settings-save"); //$NON-NLS-1$
        t.setDaemon(true);
        return t;
    });

    /**
     * Delay in milliseconds after the last change before settings are persisted automatically.
     */
    private static final long SAVE_DELAY_MS = 1000L;

    /**
     * Backing {@link Properties} store containing key-value configurations.
     * 
     * @return the backing properties store
     */
    private final @Getter Properties properties = new Properties();

    /**
     * Optional callback responsible for persisting this settings instance when marked dirty.
     */
    private final AtomicReference<Runnable> saveHandler = new AtomicReference<>();

    /**
     * Pending debounced save task, or {@code null} when no save is scheduled.
     */
    private transient ScheduledFuture<?> saveFuture;

    /**
     * Whether there are unsaved changes since the last persistence.
     */
    private transient boolean dirty;

    /**
     * Protected default constructor.
     */
    protected Settings() {
    }

    /**
     * Registers the callback used to persist this settings instance on change.
     * 
     * @param saveHandler the persistence callback, or {@code null} to disable automatic saving
     */
    public void setSaveHandler(final Runnable saveHandler) {
        this.saveHandler.set(saveHandler);
    }

    /**
     * Schedules a debounced persistence of the settings after the next change. Consecutive changes made within the debounce window
     * are coalesced into a single save.
     */
    protected void markDirty() {
        if (saveHandler.get() == null)
            return;
        synchronized (this) {
            dirty = true;
            if (saveFuture != null)
                saveFuture.cancel(false);
            saveFuture = SAVE_EXECUTOR.schedule(this::persistNow, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Immediately persists pending changes, if any, cancelling the debounced task.
     */
    public void flush() {
        synchronized (this) {
            if (saveFuture != null) {
                saveFuture.cancel(false);
                saveFuture = null;
            }
        }
        persistNow();
    }

    /**
     * Persists the settings if marked dirty, clearing the dirty flag and pending task.
     */
    private void persistNow() {
        final Runnable handler;
        synchronized (this) {
            saveFuture = null;
            if (!dirty)
                return;
            dirty = false;
            handler = saveHandler.get();
        }
        if (handler != null)
            handler.run();
    }

    @Override
    protected boolean getProperty(final String property, final boolean def) {
        return Boolean.parseBoolean(properties.getProperty(property, Boolean.toString(def)));
    }

    @Override
    protected int getProperty(final String property, final int def) {
        return Integer.parseInt(properties.getProperty(property, Integer.toString(def)));
    }

    @Override
    public String getProperty(final String property, final String def) {
        return properties.getProperty(property, def);
    }

    @Override
    public void loadSettings(final File file) {
        if (file.exists()) {
            try (val is = new BufferedInputStream(new FileInputStream(file))) {
                properties.clear();
                properties.loadFromXML(is);
            } catch (final IOException e) {
                Log.err("IO", e); //$NON-NLS-1$
            }
        }
    }

    @Override
    public void saveSettings(final File file) {
        Log.debug(() -> "file=" + file + ", propsize=" + properties.size());
        try (val os = new BufferedOutputStream(new FileOutputStream(file))) {
            Log.debug("before store");
            properties.storeToXML(os, null);
            Log.debug("stored");
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
            // Log.err("IO", e); //$NON-NLS-1$
        }
    }

    @Override
    public void setProperty(final String property, final boolean value) {
        properties.setProperty(property, Boolean.toString(value));
        propagate(SettingsEnum.from(property), Boolean.toString(value));
        markDirty();
    }

    @Override
    public void setProperty(final String property, final int value) {
        properties.setProperty(property, Integer.toString(value));
        propagate(SettingsEnum.from(property), Integer.toString(value));
        markDirty();
    }

    @Override
    public void setProperty(Enum<?> property, String value) {
        super.setProperty(property, value);
        propagate(property, value);
    }

    @Override
    public void setProperty(final String property, final String value) {
        if (value == null)
            properties.remove(property);
        else
            properties.setProperty(property, value);
        propagate(SettingsEnum.from(property), value);
        markDirty();
    }

    @Override
    protected boolean hasProperty(String property) {
        return properties.containsKey(property);
    }

    /**
     * Propagates property adjustments to internal variables or subclass fields.
     * 
     * @param property the enum reference representing the configuration key
     * @param value the string representation of the new value
     */
    protected abstract void propagate(final Enum<?> property, final String value);

    /**
     * Exports the configured key-value properties into a {@link JsonObject}. Ignores any properties related to system, list, or
     * driver filter settings to preserve space.
     * 
     * @return a JSON object containing the filtered properties
     */
    public JsonObject asJSO() {
        final var jso = new JsonObject();
        properties.forEach((k, v) -> {
            if (((String) k).startsWith("filter.machine.") || ((String) k).startsWith("filter.swlist.") || ((String) k).startsWith("filter.cat.")
                    || ((String) k).startsWith("filter.nplayer.") || ((String) k).startsWith("filter.sources.") || ((String) k).startsWith("filter.systems."))
                return;
            try {
                JsonValue value = Json.parse((String) v);
                if (value.isObject() || value.isArray() || value.isBoolean())
                    jso.add((String) k, value);
                else
                    jso.add((String) k, (String) v);
            } catch (Exception _) {
                jso.add((String) k, (String) v);
            }
        });
        return jso;
    }
}
