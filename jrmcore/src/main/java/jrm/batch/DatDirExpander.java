package jrm.batch;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.stream.Stream;

import org.apache.commons.compress.utils.Sets;
import org.apache.commons.io.FilenameUtils;

import jrm.security.Session;

final class DatDirExpander {
	record Lists(File[] datlist, File[] dstlist) {
	}

	static Lists expand(final Session session, final File dat, final File dst) throws java.io.IOException {
		var datlist = new File[] { dat };
		var dstlist = new File[] { dst };
		if (dat.isDirectory()) {
			datlist = dat.listFiles((_, sfilename) -> Sets.newHashSet("xml", "dat").contains(FilenameUtils.getExtension(sfilename).toLowerCase()));
			Arrays.sort(datlist, (a, b) -> a.getAbsolutePath().compareTo(b.getAbsolutePath()));
			for (File d : datlist)
				Files.copy(session.getUser().getSettings().getProfileSettingsFile(dat).toPath(), session.getUser().getSettings().getProfileSettingsFile(d).toPath(),
						StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			dstlist = Stream.of(datlist).map(datfile -> new File(dst, FilenameUtils.removeExtension(datfile.getName()))).toArray(File[]::new);
			for (File d : dstlist)
				d.mkdir();
		}
		return new Lists(datlist, dstlist);
	}
}
