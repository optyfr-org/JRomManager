/*
 * Copyright (C) 2015 Christophe De Troyer Copyright (C) 2018 Optyfr This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public
 * License for more details. You should have received a copy of the GNU General Public License along with this program; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.io.torrent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import jrm.io.torrent.bencoding.Reader;
import jrm.io.torrent.bencoding.Utils;
import jrm.io.torrent.bencoding.types.BByteString;
import jrm.io.torrent.bencoding.types.BDictionary;
import jrm.io.torrent.bencoding.types.BInt;
import jrm.io.torrent.bencoding.types.BList;
import jrm.io.torrent.bencoding.types.IBencodable;

/**
 * Utility parser class that decodes BitTorrent metainfo (.torrent) files. Reads raw bencoded byte streams, extracts standard keys,
 * calculates info-hashes, and generates structured {@link Torrent} representations.
 * 
 * @author Christophe De Troyer
 * @author Optyfr
 */
public class TorrentParser {
    /**
     * Private constructor to prevent instantiation of utility parser class.
     */
    private TorrentParser() {
    }

    /**
     * Parses a bencoded torrent file from a file path.
     *
     * @param file the target .torrent file to read and parse
     * 
     * @return a fully populated {@link Torrent} metainfo model
     * 
     * @throws TorrentException if the bencoded structure is invalid or keys are missing
     * @throws IOException if an I/O error occurs while reading the file
     */
    public static Torrent parseTorrent(File file) throws TorrentException, IOException {
        return parseTorrent(Files.readAllBytes(file.toPath()));
    }

    /**
     * Parses a bencoded torrent file from a file path string.
     *
     * @param filePath the path to the target .torrent file
     * 
     * @return a fully populated {@link Torrent} metainfo model
     * 
     * @throws TorrentException if the bencoded structure is invalid or keys are missing
     * @throws IOException if an I/O error occurs while reading the file
     */
    public static Torrent parseTorrent(String filePath) throws TorrentException, IOException {
        return parseTorrent(new File(filePath));
    }

    /**
     * Parses a bencoded torrent file from a raw byte array.
     *
     * @param torrentData the raw byte content of a .torrent file
     * 
     * @return a fully populated {@link Torrent} metainfo model
     * 
     * @throws TorrentException if the bencoded structure is invalid or key-constraints are violated
     */
    public static Torrent parseTorrent(byte[] torrentData) throws TorrentException {
        final var t = new Torrent();

        final var parser = new Reader(torrentData);
        BDictionary torrent;
        try {
            torrent = (BDictionary) parser.read();
        } catch (Exception e) {
            throw new TorrentException(e.getMessage());
        }

        BByteString announceKey = new BByteString("announce"); //$NON-NLS-1$
        BByteString infoKey = new BByteString("info"); //$NON-NLS-1$
        BByteString commentKey = new BByteString("comment"); //$NON-NLS-1$
        BByteString createdByKey = new BByteString("created by"); //$NON-NLS-1$
        BByteString creationDateKey = new BByteString("creation date"); //$NON-NLS-1$

        // These fields are optional
        if (null != torrent.find(commentKey))
            t.setComment(torrent.find(commentKey).toString());
        if (null != torrent.find(createdByKey))
            t.setCreatedBy(torrent.find(createdByKey).toString());
        if (null != torrent.find(creationDateKey)) {
            BInt creationTimeSeconds = (BInt) torrent.find(creationDateKey);
            t.setCreationDate(Instant.ofEpochSecond(creationTimeSeconds.getValue()));
        }
        if (null != torrent.find(announceKey))
            t.setAnnounce(torrent.find(announceKey).toString());

        t.setAnnounceList(parseAnnounceList(torrent));

        BDictionary info = (BDictionary) torrent.find(infoKey);

        if (null == info)
            throw new TorrentException("Torrent has no info dictionary."); //$NON-NLS-1$

        // Calculate info-hash
        final var infoBytes = info.bencode();
        t.setInfoHash(Utils.sumSHA(infoBytes));

        BByteString nameKey = new BByteString("name"); //$NON-NLS-1$
        BByteString pieceLengthKey = new BByteString("piece length"); //$NON-NLS-1$
        BByteString piecesKey = new BByteString("pieces"); //$NON-NLS-1$

        t.setName(info.find(nameKey).toString());
        t.setPieceLength(((BInt) info.find(pieceLengthKey)).getValue());

        BByteString pieces = (BByteString) info.find(piecesKey);
        t.setPiecesBlob(pieces.getData());
        t.setPieces(parsePieces(pieces));

        BByteString lengthKey = new BByteString("length"); //$NON-NLS-1$
        BByteString filesKey = new BByteString("files"); //$NON-NLS-1$

        // If the "files" key exists, this is a multi-file torrent.
        // If "files" is not present, it's a single file torrent and "length" should be
        // there.
        if (null != info.find(filesKey)) {
            t.setSingleFileTorrent(false);
            t.setFileList(parseFiles((BList) info.find(filesKey)));
            t.setTotalSize(calculateTotalSize(t.getFileList()));
        } else {
            t.setSingleFileTorrent(true);
            t.setTotalSize(((BInt) info.find(lengthKey)).getValue());
        }

        return t;
    }

    /**
     * Helper method to sum up the length of all files in a multi-file torrent.
     *
     * @param files the list of torrent files
     * 
     * @return the aggregated total size of all files in bytes
     */
    private static Long calculateTotalSize(List<TorrentFile> files) {
        var total = 0L;
        for (TorrentFile f : files)
            total += f.getFileLength();
        return total;
    }

    /**
     * Parses the raw pieces byte blob into individual SHA-1 piece hashes.
     *
     * @param piecesBlob the raw pieces byte string containing concatenated 20-byte SHA-1 hashes
     * 
     * @return a list of 20-byte piece hashes formatted as lowercase hexadecimal strings
     */
    private static List<String> parsePieces(BByteString piecesBlob) {
        final var piecesList = new ArrayList<String>();
        final byte[] blob = piecesBlob.getData();

        // Each piece is represented by a 20-byte SHA-1 hash.
        // Total amount of hashes is blob.length / 20.
        for (var offset = 0; offset < blob.length; offset += 20) {
            final var hash = new byte[20];
            System.arraycopy(blob, offset, hash, 0, 20);
            piecesList.add(Utils.bytesToHex(hash));
        }

        return piecesList;
    }

    /**
     * Parses a BList of file dictionary representations inside a multi-file torrent.
     *
     * @param filesBList the list of bencoded file dictionaries
     * 
     * @return a list of decoded {@link TorrentFile} descriptors
     */
    private static List<TorrentFile> parseFiles(BList filesBList) {
        final var fileList = new LinkedList<TorrentFile>();
        Iterator<IBencodable> fileDicts = filesBList.getIterator();
        while (fileDicts.hasNext()) {
            BDictionary fileDict = (BDictionary) fileDicts.next();
            BByteString lengthKey = new BByteString("length"); //$NON-NLS-1$
            BByteString pathKey = new BByteString("path"); //$NON-NLS-1$

            Long length = ((BInt) fileDict.find(lengthKey)).getValue();
            BList pathBList = (BList) fileDict.find(pathKey);

            final var pathList = new LinkedList<String>();
            Iterator<IBencodable> pathElements = pathBList.getIterator();
            while (pathElements.hasNext()) {
                BByteString pathElement = (BByteString) pathElements.next();
                pathList.add(pathElement.toString());
            }

            fileList.add(new TorrentFile(length, pathList));
        }
        return fileList;
    }

    /**
     * Parses the optional multi-tracker announce list (announce-list) of lists from the metainfo dictionary.
     *
     * @param dictionary the root torrent metainfo dictionary
     * 
     * @return a flat list of tracker announce URLs
     */
    private static List<String> parseAnnounceList(BDictionary dictionary) {
        final var announceUrls = new LinkedList<String>();
        final var announceListValue = dictionary.find(new BByteString("announce-list")); //$NON-NLS-1$
        if (announceListValue != null) {
            BList announceList = (BList) announceListValue;
            Iterator<IBencodable> subLists = announceList.getIterator();
            while (subLists.hasNext()) {
                final var subList = (BList) subLists.next();
                Iterator<IBencodable> elements = subList.getIterator();
                while (elements.hasNext()) {
                    // Assume that each element is a BByteString
                    BByteString tracker = (BByteString) elements.next();
                    announceUrls.add(tracker.toString());
                }
            }
        }
        return announceUrls;
    }
}
