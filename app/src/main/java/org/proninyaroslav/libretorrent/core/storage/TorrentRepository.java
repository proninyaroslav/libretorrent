/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.storage;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.proninyaroslav.libretorrent.core.entity.Torrent;
import org.proninyaroslav.libretorrent.core.utils.FileUtils;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import androidx.annotation.NonNull;
import io.reactivex.Flowable;

public class TorrentRepository
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentRepository.class.getSimpleName();

    public static class DataModel
    {
        public static final String TORRENT_FILE_NAME = "torrent";
        public static final String TORRENT_RESUME_FILE_NAME = "fastresume";
        public static final String TORRENT_SESSION_FILE = "session";
        public static final String FILE_LIST_SEPARATOR = ",";
    }

    private static TorrentRepository INSTANCE;
    private AppDatabase db;

    public static TorrentRepository getInstance(AppDatabase db)
    {
        if (INSTANCE == null) {
            synchronized (TorrentRepository.class) {
                if (INSTANCE == null)
                    INSTANCE = new TorrentRepository(db);
            }
        }
        return INSTANCE;
    }

    private TorrentRepository(AppDatabase db)
    {
        this.db = db;
    }

    public void addTorrent(@NonNull Context context,
                           @NonNull Torrent torrent,
                           @NonNull Uri pathToTorrent,
                           boolean deleteFile) throws IOException
    {
        String newPath = TorrentUtils.torrentToDataDir(context, torrent.id, pathToTorrent);
        if (deleteFile) {
            try {
                FileUtils.deleteFile(context, pathToTorrent);

            } catch (FileNotFoundException | SecurityException e) {
                Log.w(TAG, "Could not delete torrent file: ", e);
            }
        }
        if (newPath == null)
            throw new IOException("Unable to create torrent file");
        torrent.setFilesystemPath(newPath);

        db.torrentDao().add(torrent);
    }

    public void addTorrent(@NonNull Context context,
                           @NonNull Torrent torrent,
                           @NonNull byte[] bencode) throws IOException
    {
        String newPath = TorrentUtils.torrentToDataDir(context, torrent.id, bencode);
        if (newPath == null)
            throw new IOException("Unable to create torrent file");
        torrent.setFilesystemPath(newPath);

        db.torrentDao().add(torrent);
    }

    public void addTorrent(@NonNull Context context,
                           @NonNull Torrent torrent) throws IOException
    {
        if (!TorrentUtils.torrentDataExists(context, torrent.id))
            if (TorrentUtils.makeTorrentDataDir(context, torrent.id) == null)
                throw new IOException("Unable to create dir");

        db.torrentDao().add(torrent);
    }

    public void replaceTorrent(@NonNull Context context,
                               @NonNull Torrent torrent,
                               @NonNull byte[] bencode) throws IOException
    {
        String newPath = TorrentUtils.torrentToDataDir(context, torrent.id, bencode);
        if (newPath == null)
            throw new IOException("Unable to create torrent file");

        torrent.setFilesystemPath(newPath);
        copyStatusToNew(torrent);

        db.torrentDao().update(torrent);
    }

    public void replaceTorrent(@NonNull Context context,
                               @NonNull Torrent torrent,
                               @NonNull Uri pathToTorrent,
                               boolean deleteFile) throws IOException
    {
        String newPath = TorrentUtils.torrentToDataDir(context, torrent.id, pathToTorrent);
        if (deleteFile && !torrent.isDownloadingMetadata()) {
            try {
                FileUtils.deleteFile(context, Uri.fromFile(new File(torrent.getSource())));

            } catch (Exception e) {
                Log.w(TAG, "Could not delete torrent file: ", e);
            }
        }
        if (newPath == null)
            throw new IOException("Unable to create torrent file");
        torrent.setFilesystemPath(newPath);
        copyStatusToNew(torrent);

        db.torrentDao().update(torrent);
    }

    private void copyStatusToNew(Torrent torrent)
    {
        Torrent oldTorrent = db.torrentDao().getTorrentById(torrent.id);
        if (oldTorrent != null) {
            torrent.paused = oldTorrent.paused;
            torrent.finished = oldTorrent.finished;
        }
    }

    public void updateTorrent(@NonNull Torrent torrent)
    {
        db.torrentDao().update(torrent);
    }

    public void deleteTorrent(@NonNull Context context,
                              @NonNull Torrent torrent)
    {
        db.torrentDao().delete(torrent);

        if (!TorrentUtils.removeTorrentDataDir(context, torrent.id))
            Log.e(TAG, "Can't delete torrent data " + torrent);
    }

    public Flowable<Torrent> observeTorrentById(@NonNull String id)
    {
        return db.torrentDao().observeTorrentById(id);
    }

    public Flowable<List<Torrent>> observeAllTorrents()
    {
        return db.torrentDao().observeAllTorrents();
    }
}
