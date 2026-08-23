/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import jrm.digest.MDigest;
import jrm.digest.MDigest.Algo;
import jrm.misc.Log;

/**
 * Stateless helper for computing message digests (CRC32, MD5, SHA-1) used during directory scans.
 */
final class HashComputer {

	private HashComputer() {
		// static utility
	}

	static MDigest[] computeHash(final Path entryPath, final List<Algo> algorithm) throws NoSuchAlgorithmException {
		return computeHash(entryPath, algorithm.toArray(new Algo[0]));
	}

	static MDigest[] computeHash(final Path entryPath, final Algo[] algorithm) throws NoSuchAlgorithmException {
		var md = getMDigest(algorithm);
		try {
			MDigest.computeHash(Files.newInputStream(entryPath), md);
		} catch (final IOException e) {
			Log.err(e.getMessage(), e);
		}
		return md;
	}

	static MDigest[] getMDigest(final Algo[] algorithm) throws NoSuchAlgorithmException {
		var md = new MDigest[algorithm.length];
		for (var i = 0; i < algorithm.length; i++)
			md[i] = MDigest.getAlgorithm(algorithm[i]);
		return md;
	}

	static MDigest[] computeHash(final InputStream is, final List<Algo> algorithm) throws IOException, NoSuchAlgorithmException {
		return computeHash(is, algorithm.toArray(new Algo[0]));
	}

	static MDigest[] computeHash(final InputStream is, final Algo[] algorithm) throws IOException, NoSuchAlgorithmException {
		var md = getMDigest(algorithm);
		MDigest.computeHash(is, md);
		return md;
	}
}
