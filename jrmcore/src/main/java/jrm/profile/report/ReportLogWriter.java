package jrm.profile.report;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.eclipsesource.json.Json;

import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.misc.SettingsEnum;
import jrm.security.Session;

final class ReportLogWriter {
	enum ReportMode {
		SETTINGS,
		STATS,
		OK,
		FIXABLE,
		MISSING,
		OTHERS,
		COMPACT,
		NO_ENTRIES,
		GROUP_BY_TYPE_AND_STATUS
	}

	private final Report report;

	ReportLogWriter(Report report) {
		this.report = report;
	}

	public void write(final Session session) {
		final var modes = parseReportModes(session);
		final File reportdir = createReportDirectory(session);
		report.reportFile = createReportFile(reportdir);

		try (PrintWriter reportWriter = new PrintWriter(report.reportFile)) {
			writeReportHeader(reportWriter);
			writeReportSettings(reportWriter, modes);
			writeReportStatistics(reportWriter, modes);
			writeReportBody(reportWriter, modes);
			reportWriter.println();
		} catch (final IOException e) {
			Log.err(e.getMessage(), e);
		}
	}

	private EnumSet<ReportMode> parseReportModes(final Session session) {
		final var jsondata = session.getUser().getSettings().getProperty(SettingsEnum.report_settings);
		final var jsonarray = Json.parse(jsondata);
		final var modes = EnumSet.noneOf(ReportMode.class);
		if (jsonarray.isArray())
			for (final var jsonvalue : jsonarray.asArray())
				if (jsonvalue.isString())
					modes.add(ReportMode.valueOf(jsonvalue.asString()));
		return modes;
	}

	private File createReportDirectory(final Session session) {
		final File workdir = session.getUser().getSettings().getWorkPath().toFile();
		final File reportdir = new File(workdir, "reports");
		reportdir.mkdirs();
		return reportdir;
	}

	private File createReportFile(final File reportdir) {
		return new File(reportdir, "report-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now(ZoneId.systemDefault())) + ".log");
	}

	private void writeReportHeader(PrintWriter reportWriter) {
		reportWriter.println("=== Scanned Profile ===");
		reportWriter.println(report.profile.getNfo().getFile());
		reportWriter.println();
	}

	private void writeReportSettings(PrintWriter reportWriter, EnumSet<ReportMode> modes) throws IOException {
		if (!modes.contains(ReportMode.SETTINGS))
			return;
		reportWriter.println("=== Used Profile Properties ===");
		report.profile.getSettings().getProperties().store(reportWriter, null);
		reportWriter.println();
	}

	private void writeReportStatistics(PrintWriter reportWriter, EnumSet<ReportMode> modes) {
		if (!modes.contains(ReportMode.STATS))
			return;
		reportWriter.println("=== Statistics ===");
		reportWriter.println(String.format(Messages.getString("Report.MissingRoms"),
				report.stats.getMissingRomsCnt(), report.stats.getMissingRomsCnt() - report.stats.getFixableRomsCnt(), report.profile.getRomsCnt()));
		reportWriter.println(String.format(Messages.getString("Report.MissingDisks"),
				report.stats.getMissingDisksCnt(), report.stats.getMissingDisksCnt() - report.stats.getFixableDisksCnt(), report.profile.getDisksCnt()));
		reportWriter.println(String.format(Messages.getString("Report.MissingSets"),
				report.profile.getMachinesCnt() - report.stats.getSetFoundOk(),
				report.profile.getMachinesCnt() - report.stats.getSetFoundOk() - report.stats.getSetFoundFixComplete(),
				report.profile.getMachinesCnt()));
		reportWriter.println();
	}

	private void writeReportBody(PrintWriter reportWriter, EnumSet<ReportMode> modes) {
		reportWriter.println("=== Scanner Report ===");
		if (modes.contains(ReportMode.GROUP_BY_TYPE_AND_STATUS))
			writeGroupedReport(reportWriter, modes);
		else
			report.subjects.stream().filter(new ReportSubjectFilter(modes)).sorted(Subject.getComparator())
					.forEachOrdered(subject -> writeReport(reportWriter, subject, modes));
	}

	private void writeGroupedReport(PrintWriter reportWriter, EnumSet<ReportMode> modes) {
		if (modes.contains(ReportMode.NO_ENTRIES))
			writeGroupedSubjects(reportWriter, modes);
		else
			writeGroupedNotes(reportWriter, modes);
	}

	private void writeGroupedSubjects(PrintWriter reportWriter, EnumSet<ReportMode> modes) {
		final Map<ReportMode, List<Subject>> grouped = report.subjects.stream()
				.collect(Collectors.groupingBy(this::classifySubject));
		grouped.forEach((mode, list) -> {
			reportWriter.println();
			reportWriter.println("== %s ==".formatted(mode));
			list.stream().sorted(Subject.getComparator()).forEachOrdered(n -> writeReport(reportWriter, n, modes));
			reportWriter.println();
		});
	}

	private ReportMode classifySubject(Subject s) {
		if (s instanceof SubjectSet ss) {
			if (ss.isFixable())
				return ReportMode.FIXABLE;
			if (ss.isMissing())
				return ReportMode.MISSING;
			return ReportMode.OK;
		}
		return ReportMode.OTHERS;
	}

	private void writeGroupedNotes(PrintWriter reportWriter, EnumSet<ReportMode> modes) {
		final Map<String, List<Note>> grouped = report.subjects.stream().flatMap(s -> s.stream())
				.collect(Collectors.groupingBy(Note::getAbbrv));
		grouped.forEach((abbrv, list) -> {
			reportWriter.println();
			reportWriter.println("== %s ==".formatted(abbrv));
			list.stream().sorted(this::compareNotes).forEachOrdered(n -> writeReport(reportWriter, n, modes));
			reportWriter.println();
		});
	}

	private int compareNotes(Note n1, Note n2) {
		int ret = n1.parent != null && n2.parent != null ? Subject.getComparator().compare(n1.parent, n2.parent) : 0;
		if (ret == 0)
			return n1.getName().compareToIgnoreCase(n2.getName());
		return ret;
	}

	class ReportSubjectFilter implements Predicate<Subject> {
		private final Set<ReportMode> modes;

		public ReportSubjectFilter(Set<ReportMode> modes) {
			this.modes = modes;
		}

		@Override
		public boolean test(Subject subject) {
			if (subject instanceof SubjectSet ss) {
				if (ss.isOK() && modes.contains(ReportMode.OK))
					return true;
				if (ss.isFixable() && modes.contains(ReportMode.FIXABLE))
					return true;
				if (ss.isMissing()) //NOSONAR
					return true;
				return false;
			} else if (modes.contains(ReportMode.OTHERS))
				return true;
			return false;
		}

	}

	private void writeReport(PrintWriter reportWriter, Subject subject, final EnumSet<ReportMode> modes) {
		if (modes.contains(ReportMode.NO_ENTRIES))
			reportWriter.println(subject);
		else {
			if (!modes.contains(ReportMode.COMPACT))
				reportWriter.println(subject);
			subject.getNotes().stream().filter(new ReportNoteFilter(modes)).forEach(note -> writeReport(reportWriter, note, modes));
		}
	}

	class ReportNoteFilter implements Predicate<Note> {
		private final Set<ReportMode> modes;

		public ReportNoteFilter(Set<ReportMode> modes) {
			this.modes = modes;
		}

		@Override
		public boolean test(Note note) {
			if (modes.contains(ReportMode.OK) && note instanceof EntryOK)
				return true;
			if (modes.contains(ReportMode.FIXABLE)
					&& (note instanceof EntryAdd || note instanceof EntryMissingDuplicate || note instanceof EntryUnneeded || note instanceof EntryWrongName))
				return true;
			if (note instanceof EntryWrongHash || note instanceof EntryMissing) //NOSONAR
				return true;
			return false;
		}

	}

	private void writeReport(PrintWriter reportWriter, Note note, final EnumSet<ReportMode> modes) {
		if (modes.contains(ReportMode.COMPACT)) {
			if (note.parent != null) {
				if (modes.contains(ReportMode.GROUP_BY_TYPE_AND_STATUS))
					reportWriter.println("[" + note.parent.getWare().getBaseName() + "]\t" + note.getName() + "\t(" + note.getHash() + ")");
				else
					reportWriter.println(note.getAbbrv() + " :\t[" + note.parent.getWare().getBaseName() + "]\t" + note.getName() + "\t(" + note.getHash() + ")");
			} else
				reportWriter.println(note.getName() + " (" + note.getHash() + ")");
		} else
			reportWriter.println("\t" + note);
	}
}
