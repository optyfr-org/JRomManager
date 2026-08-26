package jrm.profile.manager;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import jrm.aui.status.StatusRendererFactory;


final class ProfileNFOHtml implements StatusRendererFactory {
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
	private static final String UNKNOWN_DATE = "????-??-?? ??:??:??";
	private static final String U = "?";
	private static final String U_OF_U = "?/?";
	private static final String N_OF_T = "%s/%d";

	private final ProfileNFO nfo;

	ProfileNFOHtml(ProfileNFO nfo) {
		this.nfo = nfo;
	}

	String getHTMLVersion() {
		return toDocument(Optional.ofNullable(nfo.getStats().getVersion()).map(this::escape).map(this::toNoBR).orElse(toGray("???")));
	}

	String getHTMLHaveSets() {
		final String have;
		final ProfileNFOStats stats = nfo.getStats();
		if (stats.getHaveSets() == null) {
			if ((stats.getTotalSets() == null))
				have = toGray(U_OF_U);
			else
				have = String.format(N_OF_T, toGray(U), stats.getTotalSets());
		} else {
			final String n;
			if (stats.getHaveSets() == 0 && stats.getTotalSets() > 0)
				n = toRed("0");
			else if (stats.getHaveSets().equals(stats.getTotalSets()))
				n = toGreen(toStr(stats.getHaveSets()));
			else
				n = toOrange(toStr(stats.getHaveSets()));
			have = String.format(N_OF_T, n, stats.getTotalSets());
		}
		return toDocument(have);
	}

	String getHTMLHaveRoms() {
		final String have;
		final ProfileNFOStats stats = nfo.getStats();
		if (stats.getHaveRoms() == null) {
			if (stats.getTotalRoms() == null)
				have = toGray(U_OF_U);
			else
				have = String.format(N_OF_T, toGray(U), stats.getTotalRoms());
		} else {
			final String n;
			if (stats.getHaveRoms() == 0 && stats.getTotalRoms() > 0)
				n = toRed("0");
			else if (stats.getHaveRoms().equals(stats.getTotalRoms()))
				n = toGreen(toStr(stats.getHaveRoms()));
			else
				n = toOrange(toStr(stats.getHaveRoms()));
			have = String.format(N_OF_T, n, stats.getTotalRoms());
		}
		return toDocument(have);
	}

	String getHTMLHaveDisks() {
		final String have;
		final ProfileNFOStats stats = nfo.getStats();
		if (stats.getHaveDisks() == null) {
			if (stats.getTotalDisks() == null)
				have = toGray(U_OF_U);
			else
				have = String.format(N_OF_T, toGray(U), stats.getTotalDisks());
		} else {
			final String n;
			if (stats.getHaveDisks() == 0 && stats.getTotalDisks() > 0)
				n = toRed("0");
			else if (stats.getHaveDisks().equals(stats.getTotalDisks()))
				n = toGreen(toStr(stats.getHaveDisks()));
			else
				n = toOrange(toStr(stats.getHaveDisks()));
			have = String.format(N_OF_T, n, stats.getTotalDisks());
		}
		return toDocument(have);
	}

	String getHTMLCreated() {
		final var s = nfo.getStats();
		return toDocument(s.getCreated() == null ? toGray(UNKNOWN_DATE) : DATE_FORMAT.format(s.getCreated()));
	}

	String getHTMLScanned() {
		final var s = nfo.getStats();
		return toDocument(s.getScanned() == null ? toGray(UNKNOWN_DATE) : DATE_FORMAT.format(s.getScanned()));
	}

	String getHTMLFixed() {
		final var s = nfo.getStats();
		return toDocument(s.getFixed() == null ? toGray(UNKNOWN_DATE) : DATE_FORMAT.format(s.getFixed()));
	}
}
