package jrm.server.shared.actions;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.apache.commons.io.FileUtils;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import jrm.misc.BreakException;
import jrm.misc.FindCmd;
import jrm.misc.Log;
import jrm.profile.manager.Import;
import jrm.profile.manager.ProfileNFO;
import jrm.security.PathAbstractor;
import jrm.server.shared.WebSession;

final class ProfileImportOps {
	private final ProfileActions parent;
	private final ActionsMgr ws;

	ProfileImportOps(ProfileActions parent) {
		this.parent = parent;
		this.ws = parent.getWs();
	}

	void performMameImport(JsonObject jso) {
		WebSession session = ws.getSession();
		session.getWorker().setProgress(new ProgressActions(ws));
		session.getWorker().getProgress().canCancel(false);
		session.getWorker().getProgress().setProgress(session.getMsgs().getString("MainFrame.ImportingFromMame"), -1);
		try {
			JsonObject jsobj = jso.get("params").asObject();
			String filename = FindCmd.findMame();
			if (filename != null) {
				final var sl = jsobj.getBoolean("sl", false);
				final var imprt = new Import(session, new File(filename), sl, session.getWorker().getProgress());
				if (imprt.getFile() != null)
					doImport(session, jsobj, sl, imprt);
				else
					new GlobalActions(ws).warn("Could not import anything from Mame");
			} else
				new GlobalActions(ws).warn("Mame not found in system's search path");
		} catch (BreakException _) {
		} catch (IOException e) {
			Log.err(e.getMessage(), e);
			new GlobalActions(ws).warn(e.getMessage());
		} finally {
			session.getWorker().getProgress().close();
			session.getWorker().setProgress(null);
			session.setLastAction(java.time.Instant.now());
		}
	}

	void doImport(WebSession session, JsonObject jsobj, final boolean sl, final Import imprt) throws SecurityException, IOException {
		final var parentDir = PathAbstractor.getAbsolutePath(session, Optional.ofNullable(jsobj.get("parent")).filter(JsonValue::isString).map(JsonValue::asString).orElse(session.getUser().getSettings().getWorkPath().toString())).toFile();
		final var file = new File(parentDir, imprt.getFile().getName());
		FileUtils.copyFile(imprt.getFile(), file);
		final var pnfo = ProfileNFO.load(session, file);
		pnfo.getMame().set(imprt.getOrgFile(), sl);
		if (imprt.getRomsFile() != null) {
			FileUtils.copyFileToDirectory(imprt.getRomsFile(), parentDir);
			pnfo.getMame().setFileroms(new File(parentDir, imprt.getRomsFile().getName()));
			if (sl) {
				if (imprt.getSlFile() != null) {
					FileUtils.copyFileToDirectory(imprt.getSlFile(), parentDir);
					pnfo.getMame().setFilesl(new File(parentDir, imprt.getSlFile().getName()));
				} else
					new GlobalActions(ws).warn("Could not import softwares list");
			}
			pnfo.save(session);
			parent.imported(pnfo.getFile());
		} else {
			new GlobalActions(ws).warn("Could not import roms list");
			java.nio.file.Files.delete(file.toPath());
		}
	}
}
