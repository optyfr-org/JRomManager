package jrm.server.shared.actions;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.eclipsesource.json.JsonObject.Member;

import jrm.misc.Log;
import jrm.misc.ProfileSettings;
import jrm.misc.ProfileSettingsEnum;
import jrm.security.PathAbstractor;
import jrm.server.shared.WebSession;

final class ProfileSettingsOps {
	private final ProfileActions parent;
	private final ActionsMgr ws;

	ProfileSettingsOps(ProfileActions parent) {
		this.parent = parent;
		this.ws = parent.getWs();
	}

	void importSettings(JsonObject jso) {
		WebSession session = ws.getSession();
		if (session.getCurrProfile() != null) {
			final JsonValue jsv = jso.get("params").asObject().get("path");
			if (jsv != null && !jsv.isNull()) {
				session.getCurrProfile().loadSettings(PathAbstractor.getAbsolutePath(session, jsv.asString()).toFile());
				session.getCurrProfile().loadCatVer(null);
				session.getCurrProfile().loadNPlayers(null);
				parent.loaded(session.getCurrProfile());
				new CatVerActions(ws).loaded(session.getCurrProfile());
				new NPlayersActions(ws).loaded(session.getCurrProfile());
			}
		}
	}

	void exportSettings(JsonObject jso) {
		WebSession session = ws.getSession();
		if (session.getCurrProfile() != null) {
			final JsonValue jsv = jso.get("params").asObject().get("path");
			if (jsv != null && !jsv.isNull()) {
				session.getCurrProfile().saveSettings(PathAbstractor.getAbsolutePath(session, jsv.asString()).toFile());
			}
		}
	}

	void setProperty(JsonObject jso) {
		final var profile = jso.getString("profile", null);
		ProfileSettings settings = profile != null ? new ProfileSettings() : ws.getSession().getCurrProfile().getSettings();
		JsonObject pjso = jso.get("params").asObject();
		try {
			for (Member m : pjso) {
				JsonValue value = m.getValue();
				if (value.isString())
					rejectUnwritableDestPath(m.getName(), value.asString());
			}
			for (Member m : pjso) {
				JsonValue value = m.getValue();
				if (value.isBoolean())
					settings.setProperty(m.getName(), value.asBoolean());
				else if (value.isNumber())
					settings.setProperty(m.getName(), value.asInt());
				else if (value.isString())
					settings.setProperty(m.getName(), value.asString());
				else
					settings.setProperty(m.getName(), value.toString());
			}
			if (profile != null)
				ws.getSession().getUser().getSettings().saveProfileSettings(parent.getAbsolutePath(profile).toFile(), settings);
			else
				ws.getSession().getCurrProfile().saveSettings();
		} catch (SecurityException e) {
			Log.err(() -> "Profile.setProperty rejected: " + e.getMessage());
			new GlobalActions(ws).warn("Write access denied for destination path. Settings were not saved.");
		} catch (Exception e) {
			Log.err(e.getMessage(), e);
		}
	}

	private void rejectUnwritableDestPath(final String name, final String value) {
		if (value == null || value.isEmpty())
			return;
		if (!isDestPathProperty(name))
			return;
		PathAbstractor.requireWriteable(ws.getSession(), value);
	}

	private static boolean isDestPathProperty(final String name) {
		return ProfileSettingsEnum.roms_dest_dir.toString().equals(name)
				|| ProfileSettingsEnum.disks_dest_dir.toString().equals(name)
				|| ProfileSettingsEnum.swroms_dest_dir.toString().equals(name)
				|| ProfileSettingsEnum.swdisks_dest_dir.toString().equals(name)
				|| ProfileSettingsEnum.samples_dest_dir.toString().equals(name)
				|| ProfileSettingsEnum.backup_dest_dir.toString().equals(name);
	}
}
