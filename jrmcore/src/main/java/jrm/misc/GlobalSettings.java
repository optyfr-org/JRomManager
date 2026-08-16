/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.misc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.CRC32;

import org.apache.commons.io.FilenameUtils;

import jrm.profile.scan.options.HashCollisionOptions;
import jrm.profile.scan.options.MergeOptions;
import jrm.security.User;
import lombok.Getter;

/**
 * The settings backend and environment configuration implementation.
 * <p>
 * This class extends {@link Settings} and implements {@link SystemSettings} to provide system paths, temporary file creation, and
 * user-specific workspace/global preference managers.
 * </p>
 * 
 * @author optyfr
 */
public class GlobalSettings extends Settings implements SystemSettings {
    /**
     * The user context associated with these global settings.
     * 
     * @return the user context
     */
    private final @Getter User user;

    /**
     * Cached base path to avoid repetitive file system or system property evaluation.
     */
    private Path cachedBasePath = null;

    /**
     * Cached working directory path to avoid repetitive resolution.
     */
    private Path cachedWorkPath = null;

    /**
     * Constructs a new {@code GlobalSettings} instance linked to the specified user and loads the user's properties from their
     * properties file.
     * 
     * @param user the user whose settings should be loaded and managed
     */
    public GlobalSettings(User user) {
        super();
        this.user = user;
        loadSettings();
    }

    /**
     * Returns the base directory path where user settings and system configurations are saved.
     * <p>
     * Depending on configuration flags:
     * <ul>
     * <li>If server mode is active, it uses the path in system property {@code jrommanager.dir}, falling back to
     * {@code user.dir}.</li>
     * <li>If multi-user mode is active, it uses {@code .jrommanager} under the user's home folder.</li>
     * <li>Otherwise, it defaults to the current working directory {@code "."}.</li>
     * </ul>
     * 
     * @return the resolved base directory path
     */
    @Override
    public Path getBasePath() {
        if (cachedBasePath != null)
            return cachedBasePath;
        if (user.getSession().isServer()) {
            final String prop = System.getProperty("jrommanager.dir");
            final Path work = (prop != null ? Paths.get(prop) : Paths.get(System.getProperty("user.dir"))).toAbsolutePath().normalize(); //$NON-NLS-1$ //$NON-NLS-2$
            if (!Files.exists(work)) {
                try {
                    Files.createDirectories(work);
                } catch (IOException e) {
                    Log.err(e.getMessage(), e);
                }
            }
            cachedBasePath = work;
            Log.debug(() -> "GlobalSettings.getBasePath (server) -> " + cachedBasePath);
            return cachedBasePath;
        } else if (user.getSession().isMultiuser()) {
            final Path work = Paths.get(System.getProperty("user.home"), ".jrommanager").toAbsolutePath().normalize(); //$NON-NLS-1$ //$NON-NLS-2$
            if (!Files.exists(work)) {
                try {
                    Files.createDirectories(work);
                } catch (IOException e) {
                    Log.err(e.getMessage(), e);
                }
            }
            cachedBasePath = work;
            Log.debug(() -> "GlobalSettings.getBasePath (multiuser) -> " + cachedBasePath);
            return cachedBasePath;
        }
        cachedBasePath = Paths.get(".").toAbsolutePath().normalize(); //$NON-NLS-1$
        Log.debug(() -> "GlobalSettings.getBasePath (default) -> " + cachedBasePath);
        return cachedBasePath;
    }

    /**
     * Returns the actual working path where user caches, presets, backups, and logs are saved.
     * <p>
     * In server and multi-user setups, this resolves to a {@code users/$login} subdirectory inside the base path.
     * </p>
     * 
     * @return the working directory path
     */
    @Override
    public Path getWorkPath() {
        if (cachedWorkPath != null)
            return cachedWorkPath;
        final var base = getBasePath();
        if (user.getSession().isServer() && user.getSession().isMultiuser()) {
            if (user.getName().equals("server")) {
                cachedWorkPath = base;
                Log.debug(() -> "GlobalSettings.getWorkPath (server multiuser, server user) -> " + cachedWorkPath);
                return cachedWorkPath;
            }
            final var work = base.resolve("users").resolve(user.getName());
            if (!Files.exists(work)) {
                try {
                    Files.createDirectories(work);
                } catch (IOException e) {
                    Log.err(e.getMessage(), e);
                }
            }
            cachedWorkPath = work;
            Log.debug(() -> "GlobalSettings.getWorkPath (server multiuser) -> " + cachedWorkPath);
            return cachedWorkPath;
        }
        cachedWorkPath = base;
        Log.debug(() -> "GlobalSettings.getWorkPath (default) -> " + cachedWorkPath);
        return cachedWorkPath;
    }

    /**
     * Resolves a safe working file location given a parent directory, file name, and extension.
     * <p>
     * If the requested parent folder is outside the user's workspace sandbox, a unique virtual filename is generated inside the
     * internal {@code work} subdirectory using the CRC32 checksum of the original path to prevent directory traversal or
     * file-system poisoning.
     * </p>
     * 
     * @param parent the requested parent directory
     * @param name the requested file name
     * @param ext the requested file extension
     * 
     * @return the resolved safe file location
     */
    public File getWorkFile(final File parent, final String name, final String ext) {
        if (!parent.getAbsoluteFile().toPath().startsWith(getWorkPath().toAbsolutePath())) {
            final var crc = new CRC32();
            crc.update(new File(parent, name).getAbsolutePath().getBytes());
            final var work = getWorkPath().resolve("work").toFile(); //$NON-NLS-1$
            work.mkdirs();
            return new File(work, String.format("%08x", crc.getValue()) + ext); //$NON-NLS-1$

        }
        if (FilenameUtils.getExtension(name).equalsIgnoreCase(ext.substring(1)))
            return new File(parent, name);
        return new File(parent, name + ext);
    }

    /**
     * Returns the absolute directory path where log files are stored. Creates the directories if they do not exist.
     * 
     * @return the directory path for log files as a string
     */
    public String getLogPath() {
        final var path = getWorkPath().resolve("logs");
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                Log.err(e.getMessage(), e);
            }
        }
        return path.toString();
    }

    /**
     * Retrieves a safe temporary directory path.
     * 
     * @param local if {@code true}, a local {@code tmp} subdirectory is created inside the active work path; if {@code false}, the
     *        standard system-wide temporary directory is returned
     * 
     * @return the resolved temporary directory path
     */
    public Path getTmpPath(boolean local) {
        if (local) {
            if (user.getSession().isMultiuser()) {
                try {
                    return Files.createDirectories(getWorkPath().resolve("tmp")); //$NON-NLS-1$
                } catch (IOException e) {
                    Log.err(e.getMessage(), e);
                }
            }
            try {
                return IOUtils.createTempDirectory("JRM"); //$NON-NLS-1$
            } catch (IOException e) {
                Log.err(e.getMessage(), e);
            }
        }
        return Paths.get(System.getProperty("java.io.tmpdir")); //$NON-NLS-1$
    }

    /**
     * Returns the file mapping to the current user's global settings properties file. Creates any missing parent directories.
     * 
     * @return the user-specific settings file
     */
    private File getSettingsFile() {
        final var workdir = getWorkPath().toAbsolutePath().normalize().toFile(); // $NON-NLS-1$
        final var cachedir = new File(workdir, "settings"); //$NON-NLS-1$
        final var settingsfile = new File(cachedir, user.getName() + ".properties"); //$NON-NLS-1$
        settingsfile.getParentFile().mkdirs();
        return settingsfile;

    }

    /**
     * Saves the current global settings properties to the user-specific settings file.
     */
    public void saveSettings() {
        saveSettings(getSettingsFile());
    }

    /**
     * Loads the global settings from the user-specific settings file. Configures thread pool defaults based on the number of
     * available system CPU cores.
     */
    public void loadSettings() {
        loadSettings(getSettingsFile());
        setProperty("MaxThreadCount", Runtime.getRuntime().availableProcessors());
    }

    /**
     * Returns the derived `.cache` file located in the same directory as the provided profile file.
     * 
     * @param file the source profile file
     * 
     * @return the derived cache file
     */
    public File getCacheFile(final File file) {
        return getWorkFile(file.getParentFile(), file.getName(), ".cache"); //$NON-NLS-1$
    }

    /**
     * Returns the derived `.properties` settings file associated with a profile file.
     * 
     * @param file the profile file
     * 
     * @return the derived profile settings properties file
     */
    public File getProfileSettingsFile(final File file) {
        return getWorkFile(file.getParentFile(), file.getName(), ".properties"); //$NON-NLS-1$
    }

    /**
     * Saves the provided profile settings to the safe derived profile settings file.
     * 
     * @param file the source profile file from which to derive the destination filename
     * @param settings the profile settings to save, can be null (which creates default settings)
     * 
     * @return the saved profile settings
     */
    public ProfileSettings saveProfileSettings(final File file, ProfileSettings settings) {
        if (settings == null)
            settings = new ProfileSettings();
        settings.saveSettings(getProfileSettingsFile(file));
        return settings;
    }

    /**
     * Loads the profile settings from the safe derived profile settings file. Fallbacks to standard scan, merge, and hash collision
     * options if properties are missing.
     * 
     * @param file the source profile file from which to derive the settings filename
     * @param settings the profile settings container to populate, can be null
     * 
     * @return the loaded profile settings
     */
    public ProfileSettings loadProfileSettings(File file, ProfileSettings settings) {
        if (settings == null)
            settings = new ProfileSettings();
        if (getProfileSettingsFile(file).exists())
            settings.loadSettings(getProfileSettingsFile(file));
        settings.setMergeMode(MergeOptions.valueOf(settings.getProperty("merge_mode", MergeOptions.SPLIT.toString())));
        settings.setHashCollisionMode(HashCollisionOptions.valueOf(settings.getProperty("hash_collision_mode", HashCollisionOptions.SINGLEFILE.toString())));
        settings.setImplicitMerge(settings.getProperty("implicit_merge", false));
        return settings;
    }

    @Override
    protected void propagate(Enum<?> property, String value) {
        // unused
    }
}
