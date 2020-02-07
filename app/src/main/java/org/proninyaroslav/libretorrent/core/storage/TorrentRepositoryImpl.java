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

import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.core.model.data.entity.FastResume;
import org.proninyaroslav.libretorrent.core.model.data.entity.Torrent;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;

import java.io.File;
import java.io.IOException;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;

public class TorrentRepositoryImpl implements TorrentRepository
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentRepositoryImpl.class.getSimpleName();

    private static final class FileDataModel
    {
        private static final String TORRENT_SESSION_FILE = "session";
    }

    private Context appContext;
    private AppDatabase db;

    public TorrentRepositoryImpl(@NonNull Context appContext, @NonNull AppDatabase db)
    {
        this.appContext = appContext;
        this.db = db;
    }

    @Override
    public void addTorrent(@NonNull Torrent torrent)
    {
        db.torrentDao().add(torrent);
    }

    @Override
    public void updateTorrent(@NonNull Torrent torrent)
    {
        db.torrentDao().update(torrent);
    }

    @Override
    public void deleteTorrent(@NonNull Torrent torrent)
    {
        db.torrentDao().delete(torrent);
    }

    @Override
    public Torrent getTorrentById(@NonNull String id)
    {
        return db.torrentDao().getTorrentById(id);
    }

    @Override
    public Single<Torrent> getTorrentByIdSingle(@NonNull String id)
    {
        return db.torrentDao().getTorrentByIdSingle(id);
    }

    @Override
    public Flowable<Torrent> observeTorrentById(@NonNull String id)
    {
        return db.torrentDao().observeTorrentById(id);
    }

    @Override
    public List<Torrent> getAllTorrents()
    {
        return db.torrentDao().getAllTorrents();
    }

    @Override
    public void addFastResume(@NonNull FastResume fastResume)
    {
        db.fastResumeDao().add(fastResume);
    }

    @Override
    public FastResume getFastResumeById(@NonNull String torrentId)
    {
        return db.fastResumeDao().getByTorrentId(torrentId);
    }

    @Override
    public void saveSession(@NonNull byte[] data) throws IOException
    {
        String dataDir = appContext.getExternalFilesDir(null).getAbsolutePath();
        File sessionFile = new File(dataDir, TorrentRepositoryImpl.FileDataModel.TORRENT_SESSION_FILE);

        org.apache.commons.io.FileUtils.writeByteArrayToFile(sessionFile, data);
    }

    @Override
    public String getSessionFile()
    {
        if (SystemFacadeHelper.getFileSystemFacade(appContext).isStorageReadable()) {
            String dataDir = appContext.getExternalFilesDir(null).getAbsolutePath();
            File session = new File(dataDir, TorrentRepositoryImpl.FileDataModel.TORRENT_SESSION_FILE);

            if (session.exists())
                return session.getAbsolutePath();
        }

        return null;
    }

    /*
     * Search directory with data of added torrent (in standard data directory).
     * Returns path to the directory found if successful or null if the directory is not found.
     */

    private String getTorrentDataDir(Context context, String id)
    {
        if (SystemFacadeHelper.getFileSystemFacade(context).isStorageReadable()) {
            File dataDir = new File(context.getExternalFilesDir(null), id);
            if (dataDir.exists()) {
                return dataDir.getAbsolutePath();
            } else {
                return makeTorrentDataDir(context, id);
            }
        }

        return null;
    }

    /*
     * Create a directory to store data of added torrent (in standard data directory)
     * Returns path to the new directory if successful or null due to an error.
     */

    private String makeTorrentDataDir(Context context, String name)
    {
        if (!SystemFacadeHelper.getFileSystemFacade(context).isStorageWritable())
            return null;

        String dataDir = context.getExternalFilesDir(null).getAbsolutePath();
        File newDir = new File(dataDir, name);

        return (newDir.mkdir()) ? newDir.getAbsolutePath() : null;
    }
}
