/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile;

import jrm.misc.Log;
import jrm.misc.ProfileSettings;
import jrm.misc.ProfileSettingsEnum;

/**
 * Helper extracted from Profile to handle settings property get/set and change detection.
 * Reduces size of the main Profile class.
 */
class ProfileProperties {

	private final Profile profile;
	private int propsHashCode = 0;

	ProfileProperties(Profile profile) {
		this.profile = profile;
	}

	void setProperty(final ProfileSettingsEnum property, final boolean value) {
		Log.info(() -> "%s : %b".formatted(property, value));
		getSettings().setProperty(property, Boolean.toString(value));
	}

	void setProperty(final String property, final boolean value) {
		Log.info(() -> "%s : %b".formatted(property, value));
		getSettings().setProperty(property, Boolean.toString(value));
	}

	void setProperty(final ProfileSettingsEnum property, final String value) {
		getSettings().setProperty(property, value);
	}

	void setProperty(final String property, final String value) {
		getSettings().setProperty(property, value);
	}

	boolean getProperty(final String property, final boolean def) {
		return Boolean.parseBoolean(getSettings().getProperty(property, Boolean.toString(def)));
	}

	int getProperty(final String property, final int def) {
		return Integer.parseInt(getSettings().getProperty(property, Integer.toString(def)));
	}

	String getProperty(final String property, final String def) {
		return getSettings().getProperty(property, def);
	}

	<T> T getProperty(final ProfileSettingsEnum property, Class<T> cls) {
		return getSettings().getProperty(property, cls);
	}

	String getProperty(final ProfileSettingsEnum property) {
		return getSettings().getProperty(property, String.class);
	}

	void setPropsCheckPoint() {
		propsHashCode = getSettings().hashCode();
	}

	boolean hasPropsChanged() {
		return propsHashCode != getSettings().hashCode();
	}

	private ProfileSettings getSettings() {
		return profile.getSettings();
	}
}
