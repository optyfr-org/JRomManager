package jrm.batch;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.codec.binary.Hex;

import jrm.aui.basic.AbstractSrcDstResult;
import jrm.aui.progress.ProgressHandler;
import jrm.aui.status.StatusRendererFactory;
import jrm.batch.TrntChkReport.Child;
import jrm.batch.TrntChkReport.Status;
import jrm.io.torrent.Torrent;
import jrm.io.torrent.TorrentFile;
import jrm.misc.Log;
import jrm.misc.UnitRenderer;

final class TorrentPieceHasher<T extends AbstractSrcDstResult> implements UnitRenderer, StatusRendererFactory {
	private final TorrentChecker<T> checker;

	TorrentPieceHasher(TorrentChecker<T> checker) {
		this.checker = checker;
	}

	String checkBlocks(final ProgressHandler progress, final T sdr, final File src, final File dst, final TrntChkReport report, final Torrent torrent, final List<TorrentFile> tfiles) {
		String result;
		try {
			final var data = new CheckBlocksData(torrent);

			data.toGo = data.pieceLength;
			checker.processing.addAndGet(data.pieces.size());
			progress.setProgress(src.getAbsolutePath(), -1, null, "");
			progress.setProgress2(String.format(checker.session.getMsgs().getString("TorrentChecker.PieceProgression"), checker.current.get(), checker.processing.get()), -1, checker.processing.get());
			data.pieceCnt++;
			data.block = report.add(String.format("Piece %d", data.pieceCnt));
			data.block.getData().setLength(data.pieceLength);
			for (TorrentFile tfile : tfiles) {
				checkBlocksFile(data, src, dst, tfile, report, progress);
				if (progress.isCancel())
					return "cancelled...";
			}
			progress.setProgress2(String.format(checker.session.getMsgs().getString("TorrentChecker.PieceProgression"), checker.current.get(), checker.processing.get()), checker.current.get(), checker.processing.get());

			final long lastPieceLen = data.pieceLength - data.toGo;
			if (lastPieceLen > 0) {
				applyPieceHash(data, lastPieceLen);
				data.block.getData().setLength(lastPieceLen);
			}
			Log.info(String.format("piece counted %d, given %d, valid %d, completion=%.02f%%%n", data.pieceCnt, data.pieces.size(), data.pieceValid,
					data.pieceValid * 100.0 / data.pieceCnt));
			Log.info(String.format("piece len : %d%n", data.pieceLength));
			Log.info(String.format("last piece len : %d%n", data.pieceLength - data.toGo));
			int removedFiles = checker.removeUnknownFiles(report, data.paths, sdr, checker.options.contains(TorrentChecker.Options.REMOVEUNKNOWNFILES) && !progress.isCancel());
			if (data.pieceValid == data.pieceCnt) {
				result = checker.formatCompleteResult(removedFiles);
			} else {
				result = String.format(checker.session.getMsgs().getString("TorrentChecker.ResultSHA1"), data.pieceValid * 100.0 / data.pieceCnt,
						humanReadableByteCount(data.missingBytes, false), data.wrongSizedFiles.get(), removedFiles);
			}
		} catch (Exception ex) {
			result = ex.getMessage();
		}
		return result;
	}

	private class CheckBlocksData {
		Child block;
		Child node = null;
		long missingBytes = 0L;
		long toGo;
		int pieceCnt = 0;
		int pieceValid = 0;
		final byte[] buffer = new byte[8192];
		final MessageDigest md;
		final AtomicBoolean valid = new AtomicBoolean(true);
		final AtomicInteger wrongSizedFiles = new AtomicInteger();
		final Set<Path> paths = new java.util.HashSet<>();
		final long pieceLength;
		final List<String> pieces;

		public CheckBlocksData(Torrent torrent) throws NoSuchAlgorithmException {
			md = MessageDigest.getInstance("SHA-1");
			pieceLength = torrent.getPieceLength();
			pieces = torrent.getPieces();
		}
	}

	private void checkBlocksFile(final CheckBlocksData data, final File src, final File dst, TorrentFile tfile, final TrntChkReport report, final ProgressHandler progress) throws IOException {
		final Path destRoot = dst.toPath().toAbsolutePath().normalize();
		final Path resolved = resolveBlocksFile(data, destRoot, tfile);
		try (BufferedInputStream in = resolved == null ? null : getFileStram(checker.options, data.wrongSizedFiles, data.node, data.valid, tfile, resolved)) {
			progress.setProgress(toDocument(toPurple(src.getAbsolutePath())), -1, null, resolved != null ? resolved.toString() : data.node.getData().getTitle());
			long flen = data.node.getData().setLength(tfile.getFileLength()).getLength();
			while (flen >= data.toGo) {
				hashStream(data.md, data.buffer, in, data.toGo);
				flen -= data.toGo;
				data.toGo = data.pieceLength;
				progress.setProgress2(String.format(checker.session.getMsgs().getString("TorrentChecker.PieceProgression"), checker.current.get(), checker.processing.get()), checker.current.get(), checker.processing.get());
				finalizePiece(data, report, data.pieceLength);
				if (flen > 0) {
					revalidateRemainingFile(data, resolved, tfile);
				}
			}
			hashStream(data.md, data.buffer, in, flen);
			data.toGo -= flen;
		}
	}

	private Path resolveBlocksFile(final CheckBlocksData data, final Path destRoot, final TorrentFile tfile) {
		try {
			final Path file = TorrentChecker.resolveTorrentEntry(destRoot, tfile.getFileDirs());
			data.paths.add(file);
			data.node = data.block.add(destRoot.relativize(file).toString());
			return file;
		} catch (IOException _) {
			data.node = data.block.add(String.join("/", tfile.getFileDirs()));
			data.node.setStatus(Status.MISSING);
			data.valid.set(false);
			return null;
		}
	}

	private void applyPieceHash(final CheckBlocksData data, final long completedLength) {
		if (data.valid.get()) {
			if (data.pieceCnt > 0 && data.pieceCnt <= data.pieces.size() && Hex.encodeHexString(data.md.digest()).equalsIgnoreCase(data.pieces.get(data.pieceCnt - 1))) {
				data.pieceValid++;
				data.block.setStatus(Status.OK);
			} else {
				data.block.setStatus(Status.SHA1);
			}
		} else {
			data.missingBytes += completedLength;
			data.block.setStatus(Status.SKIPPED);
		}
	}

	private void finalizePiece(final CheckBlocksData data, final TrntChkReport report, final long completedLength) {
		applyPieceHash(data, completedLength);
		data.md.reset();
		checker.current.incrementAndGet();
		data.valid.set(true);
		if (data.pieceCnt < data.pieces.size()) {
			data.pieceCnt++;
			data.block = report.add(String.format("Piece %d", data.pieceCnt));
			data.block.getData().setLength(data.pieceLength);
			data.node = data.block.add(data.node);
		}
	}

	private void revalidateRemainingFile(final CheckBlocksData data, final Path resolved, final TorrentFile tfile) throws IOException {
		if (resolved == null || !Files.exists(resolved)) {
			data.valid.set(false);
			data.node.setStatus(Status.MISSING);
		} else if (Files.size(resolved) != tfile.getFileLength()) {
			data.valid.set(false);
			data.node.setStatus(Status.SIZE);
		}
	}

	private void hashStream(final MessageDigest md, final byte[] buffer, BufferedInputStream in, long toRead) throws IOException {
		if (in == null || toRead <= 0) {
			return;
		}
		while (toRead > 0) {
			int len = in.read(buffer, 0, (int) Math.min(toRead, buffer.length));
			if (len < 0) {
				break;
			}
			md.update(buffer, 0, len);
			toRead -= len;
		}
	}

	private BufferedInputStream getFileStram(Set<TorrentChecker.Options> options, AtomicInteger wrongSizedFiles, Child node, AtomicBoolean valid, TorrentFile tfile, Path file) throws IOException {
		if (!Files.exists(file)) {
			valid.set(false);
			node.setStatus(Status.MISSING);
		} else if (Files.size(file) != (node.getData().setLength(tfile.getFileLength())).getLength()) {
			if (options.contains(TorrentChecker.Options.REMOVEWRONGSIZEDFILES))
				Files.delete(file);
			wrongSizedFiles.incrementAndGet();
			node.setStatus(Status.SIZE);
			valid.set(false);
		} else
			return new BufferedInputStream(new FileInputStream(file.toFile()));
		return null;
	}
}
