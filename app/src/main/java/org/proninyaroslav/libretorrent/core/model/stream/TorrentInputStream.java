/*
 * Copyright (C) 2015-2018 Sébastiaan (github.com/se-bastiaan),
 *                         Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of LibreTorrent.
 *
 * LibreTorrent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreTorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreTorrent.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.proninyaroslav.libretorrent.core.model.stream;

import androidx.annotation.NonNull;

import com.sun.jna.Pointer;

import org.proninyaroslav.libretorrent.core.model.TorrentEngineListener;
import org.proninyaroslav.libretorrent.core.model.data.ReadPieceInfo;
import org.proninyaroslav.libretorrent.core.model.session.TorrentDownload;
import org.proninyaroslav.libretorrent.core.model.session.TorrentSession;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.ReentrantLock;

/*
 * Represent InputStream for a particular file in the torrent.
 * Abstraction implemented on top of the piece reading mechanism,
 * as if you reading a file on the disk.
 *
 * Schematic representation of a possible file:
 *
 *           ...   file0   |           file1           | file2 ...
 *                 _ _ _ _ | _ _ _ _ _ _ _ _ _ _ _ _ _ | _ _
 * pieces:   ...  |_ _ _|_ _ _|_ _ _|_ _ _|_ _ _|_ _ _|_ _ _|  ...
 * (e.g 3 bytes            *         *                 *
 *  per piece)             |         |                 |
 *                     fileStart  filePos             EOF
 */

public class TorrentInputStream extends InputStream
{
    public static final int EOF = -1;

    private TorrentSession session;
    private TorrentStream stream;
    private ReadSession readSession;
    private long filePos, fileStart, eof;
    private byte[] cacheBuf;
    private int cachePieceIndex = -1;
    private boolean stopped;
    private static ReentrantLock lock = new ReentrantLock();

    private class ReadSession
    {
        private int countLatch;
        private Piece[] piecesForReading;
        private byte[] buf;
    }

    private class Piece
    {
        int index;
        int readLength;
        int readOffset;
        int bufIndex;
        boolean cache = false;

        Piece(int index)
        {
            this.index = index;
        }

        @Override
        public int hashCode()
        {
            return index;
        }

        @Override
        public boolean equals(Object o)
        {
            return o instanceof Piece && (o == this || index == ((Piece)o).index);
        }
    }

    public TorrentInputStream(@NonNull TorrentSession session, @NonNull TorrentStream stream)
    {
        this.session = session;
        this.stream = stream;
        TorrentDownload task = session.getTask(stream.torrentId);
        if (task == null)
            throw new NullPointerException("task " + stream.torrentId + " is null");

        int firstPieceSize = (stream.firstFilePiece == stream.lastFilePiece ?
                              stream.lastFilePieceSize :
                              stream.pieceLength);
        long firstPieceEnd = (long)stream.firstFilePiece * stream.pieceLength + firstPieceSize;

        if (stream.fileOffset > firstPieceEnd)
            throw new IllegalArgumentException();

        filePos = firstPieceSize - (firstPieceEnd - stream.fileOffset);
        fileStart = filePos + 1;
        eof = filePos + stream.fileSize;

        session.addListener(listener);
        task.setInterestedPieces(stream, stream.firstFilePiece, 1);
    }

    @Override
    protected void finalize() throws Throwable
    {
        synchronized (this) {
            stopped = true;
            if (session != null)
                session.removeListener(listener);
            session = null;
            notifyAll();
        }

        super.finalize();
    }

    private synchronized boolean waitForPiece(TorrentDownload task, int pieceIndex)
    {
        while (!Thread.currentThread().isInterrupted() && !stopped) {
            try {
                if (task.havePiece(pieceIndex))
                    return true;
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return false;
    }

    private synchronized boolean waitForReadPieces()
    {
        while (!Thread.currentThread().isInterrupted() && !stopped) {
            try {
                if (readSession != null && readSession.countLatch <= 0)
                    return true;
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return false;
    }

    /*
     * Calculate global file offset to local offset inside piece
     */

    private int filePosToPiecePos(int piece, long pos)
    {
        int pieceLocalIndex = piece - stream.firstFilePiece;
        int pieceSize = (piece == stream.lastFilePiece ?
                         stream.lastFilePieceSize :
                         stream.pieceLength);
        long pieceEnd = (long)pieceLocalIndex * stream.pieceLength + pieceSize;

        return pieceSize - (int)(pieceEnd - pos);
    }

    private void readFromCache(Piece piece, byte[] b)
    {
        System.arraycopy(cacheBuf, piece.readOffset, b,
                         piece.bufIndex, piece.readLength);
    }

    /*
     * Returns byte as an int in the range 0 to 255
     */

    private int toUnsignedByte(byte b)
    {
        return 0x00 << 24 | b & 0xff;
    }

    @Override
    public int read() throws IOException
    {
        lock.lock();

        try {
            if (session == null)
                throw new IOException("Torrent session is null");

            TorrentDownload task = session.getTask(stream.torrentId);
            if (task == null)
                throw new IOException("task " + stream.torrentId + " is null");

            /* EOF check */
            if (filePos == eof) {
                cacheBuf = null;
                return EOF;
            }

            /* Pieces definition that need to be read */
            int p = stream.bytesToPieceIndex(filePos + 1);

            task.setInterestedPieces(stream, p, 1);

            readSession = new ReadSession();
            readSession.piecesForReading = new Piece[1];
            readSession.buf = new byte[1];
            readSession.countLatch = 1;

            Piece piece = new Piece(p);
            piece.readOffset = filePosToPiecePos(p, filePos);
            piece.readLength = 1;
            piece.bufIndex = 0;

            /* Check cache */
            if (p == cachePieceIndex) {
                readFromCache(piece, readSession.buf);
                filePos++;

                return toUnsignedByte(readSession.buf[0]);
            }
            piece.cache = true;
            readSession.piecesForReading[0] = piece;

            if (!waitForPiece(task, p))
                return EOF;
            /* Async piece reading */
            task.readPiece(p);

            if (!waitForReadPieces())
                return EOF;
            filePos++;

            return toUnsignedByte(readSession.buf[0]);

        } finally {
            readSession = null;
            lock.unlock();
        }
    }

    @Override
    public int read(@NonNull byte[] b, int off, int len) throws IOException
    {
        lock.lock();

        try {
            if (off < 0 || len < 0 || len > b.length - off)
                throw new IndexOutOfBoundsException();
            else if (len == 0)
                return 0;

            if (session == null)
                throw new IOException("Torrent session is null");

            TorrentDownload task = session.getTask(stream.torrentId);
            if (task == null)
                throw new IOException("Task " + stream.torrentId + " is null");

            /* EOF check */
            if (filePos == eof) {
                cacheBuf = null;
                return EOF;
            }
            if (filePos + len > eof)
                len = (int)(eof - filePos);


            /* Pieces definition that need to be read */
            int firstPiece = stream.bytesToPieceIndex(filePos + 1);
            int lastPiece = stream.bytesToPieceIndex(filePos + len);
            int numPieces = lastPiece - firstPiece + 1;

            task.setInterestedPieces(stream, firstPiece, numPieces);

            readSession = new ReadSession();
            readSession.piecesForReading = new Piece[numPieces];
            readSession.buf = b;
            readSession.countLatch = numPieces;

            int bufIndex = off;
            for (int p = firstPiece, i = 0; p <= lastPiece; p++, i++) {
                int pieceSize;
                if (p == stream.lastFilePiece)
                    pieceSize = stream.lastFilePieceSize;
                else
                    pieceSize = stream.pieceLength;

                Piece piece = new Piece(p);
                piece.bufIndex = bufIndex;
                piece.cache = p == lastPiece;

                if (p == firstPiece)
                    piece.readOffset = filePosToPiecePos(firstPiece, filePos);
                else
                    piece.readOffset = 0;

                if (p == lastPiece && firstPiece != lastPiece) {
                    piece.readLength = filePosToPiecePos(lastPiece, filePos + len);
                } else {
                    if (piece.readOffset + len > pieceSize)
                        piece.readLength = pieceSize - piece.readOffset;
                    else
                        piece.readLength = len;
                }
                bufIndex += piece.readLength;

                /* Check cache */
                if (p == cachePieceIndex) {
                    readFromCache(piece, b);
                    /* Exit if there are no other pieces except cached */
                    if (numPieces == 1) {
                        filePos += len;

                        return len;
                    }
                }

                readSession.piecesForReading[i] = piece;

                if (!waitForPiece(task, p))
                    return EOF;
                /* Async pieces reading */
                task.readPiece(p);
            }

            /* Wait for pieces reading */
            if (!waitForReadPieces())
                return EOF;
            filePos += len;

            return len;

        } finally {
            readSession = null;
            lock.unlock();
        }
    }

    @Override
    public int read(@NonNull byte[] b) throws IOException
    {
        return read(b, 0, b.length);
    }

    @Override
    public void close() throws IOException
    {
        synchronized (this) {
            stopped = true;
            if (session != null)
                session.removeListener(listener);
            session = null;
            notifyAll();
        }

        super.close();
    }

    @Override
    public long skip(long n)
    {
        lock.lock();

        try {
            if (n <= 0)
                return 0;

            /* EOF check */
            if (filePos == eof)
                return 0;
            if (filePos + n > eof)
                n = (int)(eof - filePos);

            filePos += n;

            if (session != null) {
                TorrentDownload task = session.getTask(stream.torrentId);
                if (task != null)
                    task.setInterestedPieces(stream, stream.bytesToPieceIndex(filePos + 1), 1);
            }

            return n;

        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean markSupported()
    {
        return false;
    }

    private TorrentEngineListener listener = new TorrentEngineListener()
    {
        @Override
        public void onReadPiece(@NonNull String id, ReadPieceInfo info)
        {
            if (!stream.torrentId.equals(id))
                return;

            readPiece(info);
        }

        @Override
        public void onPieceFinished(@NonNull String id, int piece)
        {
            if (!stream.torrentId.equals(id))
                return;

            pieceFinished();
        }
    };

    private synchronized void pieceFinished()
    {
        notifyAll();
    }

    private void readPiece(ReadPieceInfo info)
    {
        lock.lock();
        try {
            if (readSession == null)
                return;

            Piece piece = null;
            for (Piece p : readSession.piecesForReading) {
                if (p.index == info.piece) {
                    piece = p;
                    break;
                }
            }
            if (readSession.countLatch > 0 && piece != null && readSession.buf != null) {
                try {
                    if (info.err != null) {
                        TorrentDownload task = session.getTask(stream.torrentId);
                        if (task != null)
                            task.resume();
                        return;
                    }
                    Pointer ptr = new Pointer(info.bufferPtr);
                    if (piece.cache) {
                        cacheBuf = new byte[info.size];
                        ptr.read(0, cacheBuf, 0, info.size);
                        cachePieceIndex = piece.index;
                        readFromCache(piece, readSession.buf);
                    } else {
                        ptr.read(piece.readOffset, readSession.buf, piece.bufIndex, piece.readLength);
                    }
                } finally {
                    --readSession.countLatch;
                    notifyAll();
                }
            }

        } finally {
            lock.unlock();
        }
    }
}