package jrm.ui.profile.manager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.FileUtils;

import jrm.misc.Log;
import jrm.profile.manager.Dir;
import jrm.profile.manager.Import;
import jrm.profile.manager.ProfileNFO;
import jrm.security.Session;

/**
 * Filesystem operations for profile directories (rename, delete, import copy, drop cache).
 * <p>
 * Extracted from DirTreeModel (for tree node FS side effects) and ProfilePanel.
 */
public final class ProfileDirIo {

	private ProfileDirIo() {
	}

	/**
	 * Perform rename of a child dir when tree node changed (user edited name).
	 * @return true if renamed
	 */
	public static boolean renameDir(final DirNode node, final DirNode childNode) {
		try {
			if (childNode.getUserObject() instanceof String) {
				final File newdir = new File(node.getDir().getFile(), childNode.getUserObject().toString());
				final File olddir = childNode.getDir().getFile();
				if (olddir.renameTo(newdir)) {
					childNode.setDir(new Dir(newdir));
					childNode.setUserObject(childNode.getDir());
					return true;
				}
			}
		} catch (final NullPointerException exc) {
			Log.err(exc.getMessage(), exc);
		}
		return false;
	}

	/**
	 * Delete the directory for a removed tree node child.
	 */
	public static void deleteDir(final DirNode child) {
		try {
			if (child != null) {
				FileUtils.deleteDirectory(child.getDir().getFile());
			}
		} catch (NullPointerException | IOException exc) {
			Log.err(exc.getMessage(), exc);
		}
	}

	public static void dropCache(File file) {
		try {
			Files.deleteIfExists(Paths.get(file.getAbsolutePath() + ".cache"));
		} catch (IOException e) {
			Log.err(e.getMessage(), e);
		}
	}

	public static void copyImportFile(Import imprt, File dst) {
		try {
			FileUtils.copyFile(imprt.getFile(), dst);
		} catch (IOException e) {
			Log.err(e.getMessage(), e);
		}
	}

	public static void copyMameImport(Import imprt, File target, boolean sl, Session session) {
		try {
			final var parent = target.getParentFile();
			FileUtils.copyFile(imprt.getFile(), target);
			if (imprt.isMame()) {
				final var pnfo = ProfileNFO.load(session, target);
				pnfo.getMame().set(imprt.getOrgFile(), sl);
				if (imprt.getRomsFile() != null) {
					FileUtils.copyFileToDirectory(imprt.getRomsFile(), parent);
					pnfo.getMame().setFileroms(new File(parent, imprt.getRomsFile().getName()));
					if (imprt.getSlFile() != null) {
						FileUtils.copyFileToDirectory(imprt.getSlFile(), parent);
						pnfo.getMame().setFilesl(new File(parent, imprt.getSlFile().getName()));
					}
				}
				pnfo.save(session);
			}
		} catch (final IOException e) {
			Log.err(e.getMessage(), e);
		}
	}

	public static void copyMameUpdate(Import imprt, ProfileNFO nfo, Session session) throws IOException {
		if (imprt == null || !imprt.canApplyMameUpdate(nfo.getMame().isSL())) {
			return;
		}
		nfo.getMame().deleteAlongside(nfo.getFile());
		nfo.getMame().setFileroms(new File(nfo.getFile().getParentFile(), imprt.getRomsFile().getName()));
		Files.copy(imprt.getRomsFile().toPath(), nfo.getMame().getFileroms().toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
		if (nfo.getMame().isSL()) {
			nfo.getMame().setFilesl(new File(nfo.getFile().getParentFile(), imprt.getSlFile().getName()));
			Files.copy(imprt.getSlFile().toPath(), nfo.getMame().getFilesl().toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
		}
		nfo.getMame().setUpdated();
		nfo.getStats().reset();
		nfo.save(session);
	}
}
