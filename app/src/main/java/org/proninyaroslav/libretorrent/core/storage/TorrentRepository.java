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
import android.util.Pair;

import org.proninyaroslav.libretorrent.core.entity.Torrent;
import org.proninyaroslav.libretorrent.core.utils.FileUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import androidx.annotation.NonNull;

import io.reactivex.Flowable;
import io.reactivex.Single;

public class TorrentRepository
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentRepository.class.getSimpleName();

    private static class DataModel
    {
        private static final String TORRENT_FILE_NAME = "torrent";
        private static final String TORRENT_RESUME_FILE_NAME = "fastresume";
        private static final String TORRENT_SESSION_FILE = "session";
        private static final String FILE_LIST_SEPARATOR = ",";
    }

    private static TorrentRepository INSTANCE;
    private AppDatabase db;

    public static TorrentRepository getInstance(@NonNull AppDatabase db)
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
        String newPath = torrentToDataDir(context, torrent.id, pathToTorrent);
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
        String newPath = torrentToDataDir(context, torrent.id, bencode);
        if (newPath == null)
            throw new IOException("Unable to create torrent file");
        torrent.setFilesystemPath(newPath);

        db.torrentDao().add(torrent);
    }

    public void addTorrent(@NonNull Context context,
                           @NonNull Torrent torrent) throws IOException
    {
        if (!torrentDataExists(context, torrent.id))
            if (makeTorrentDataDir(context, torrent.id) == null)
                throw new IOException("Unable to create dir");

        db.torrentDao().add(torrent);
    }

    public void replaceTorrent(@NonNull Context context,
                               @NonNull Torrent torrent,
                               @NonNull byte[] bencode) throws IOException
    {
        String newPath = torrentToDataDir(context, torrent.id, bencode);
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
        String newPath = torrentToDataDir(context, torrent.id, pathToTorrent);
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
        Torrent oldTorrent = getTorrentById(torrent.id);
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

        if (!deleteTorrentDataDir(context, torrent.id))
            Log.e(TAG, "Can't delete torrent data " + torrent);
    }

    public Torrent getTorrentById(@NonNull String id)
    {
        return db.torrentDao().getTorrentById(id);
    }

    public Single<Torrent> getTorrentByIdSingle(@NonNull String id)
    {
        return db.torrentDao().getTorrentByIdSingle(id);
    }

    public Flowable<Torrent> observeTorrentById(@NonNull String id)
    {
        return db.torrentDao().observeTorrentById(id);
    }

    public Flowable<List<Torrent>> observeAllTorrents()
    {
        return db.torrentDao().observeAllTorrents();
    }

    public Single<List<Torrent>> getAllTorrentsSingle()
    {
        return db.torrentDao().getAllTorrentsSingle();
    }

    public List<Torrent> getAllTorrents()
    {
        return db.torrentDao().getAllTorrents();
    }

    /*
     * Checking existing a directory with data of added torrent
     * (in standard data directory).
     */

    public boolean torrentDataExists(@NonNull Context context,
                                     @NonNull String id)
    {
        return FileUtils.isStorageReadable() &&
                new File(context.getExternalFilesDir(null), id).exists();
    }

    public boolean torrentFileExists(@NonNull Context context,
                                     @NonNull String id)
    {
        if (FileUtils.isStorageReadable()) {
            File dataDir = new File(context.getExternalFilesDir(null),  id);

            if (dataDir.exists())
                return new File(dataDir, TorrentRepository.DataModel.TORRENT_FILE_NAME).exists();
        }

        return false;
    }

    public String getTorrentFile(@NonNull Context context,
                                 @NonNull String id)
    {
        if (FileUtils.isStorageReadable()) {
            File dataDir = new File(context.getExternalFilesDir(null),  id);

            if (dataDir.exists()) {
                File f = new File(dataDir, TorrentRepository.DataModel.TORRENT_FILE_NAME);
                return f.getAbsolutePath();
            }
        }

        return null;
    }

    /*
     * Search directory with data of added torrent (in standard data directory).
     * Returns path to the directory found if successful or null if the directory is not found.
     */

    public String getTorrentDataDir(@NonNull Context context,
                                    @NonNull String id)
    {
        if (FileUtils.isStorageReadable()) {
            File dataDir = new File(context.getExternalFilesDir(null), id);
            if (dataDir.exists())
                return dataDir.getAbsolutePath();
        }

        return null;
    }

    public String getResumeFile(@NonNull Context context,
                                @NonNull String id)
    {
        String dataDir = getTorrentDataDir(context, id);
        if (dataDir == null)
            return null;

        File file = new File(dataDir, TorrentRepository.DataModel.TORRENT_RESUME_FILE_NAME);

        return (file.exists() ? file.getAbsolutePath() : null);
    }

    /*
     * Save generated fast-resume data for the torrent.
     */

    public void saveResumeData(@NonNull Context context,
                               @NonNull String id,
                               @NonNull byte[] data) throws IOException
    {
        String pathToDataDir = getTorrentDataDir(context, id);
        if (pathToDataDir == null)
            return;

        File f = new File(pathToDataDir, TorrentRepository.DataModel.TORRENT_RESUME_FILE_NAME);
        org.apache.commons.io.FileUtils.writeByteArrayToFile(f, data);
    }

    public void saveSession(@NonNull Context context,
                            @NonNull byte[] data) throws IOException
    {
        String dataDir = context.getExternalFilesDir(null).getAbsolutePath();
        File sessionFile = new File(dataDir, TorrentRepository.DataModel.TORRENT_SESSION_FILE);

        org.apache.commons.io.FileUtils.writeByteArrayToFile(sessionFile, data);
    }

    public String getSessionFile(@NonNull Context context)
    {
        if (FileUtils.isStorageReadable()) {
            String dataDir = context.getExternalFilesDir(null).getAbsolutePath();
            File session = new File(dataDir, TorrentRepository.DataModel.TORRENT_SESSION_FILE);

            if (session.exists())
                return session.getAbsolutePath();
        }

        return null;
    }

    /*
     * Copy torrent file to the data directory.
     *
     * The process consists of the following stages:
     *  0) Checking the existence of the torrent file
     *     and lack of already saved torrent in the data directory
     *  1) Creating a directory for save the torrent file and the service data
     *  2) Copying a torrent file in the directory
     *
     *  Returns the path of the copied file torrent.
     *  In case of error throws exception.
     */

    private String torrentToDataDir(Context context, String torrentId,
                                    Uri pathToTorrent) throws IOException
    {

        if (!FileUtils.fileExists(context, pathToTorrent))
            throw new FileNotFoundException();

        return torrentToDataDir(context, torrentId, pathToTorrent, null);
    }

    private String torrentToDataDir(Context context, String torrentId,
                                    byte[] bencode) throws IOException
    {
        return torrentToDataDir(context, torrentId, null, bencode);
    }

    private String torrentToDataDir(Context context,
                                    String id,
                                    Uri pathToTorrent,
                                    byte[] bencode) throws IOException
    {
        String dataDir;
        if (torrentDataExists(context, id))
            dataDir = getTorrentDataDir(context, id);
        else
            dataDir = makeTorrentDataDir(context, id);
        if (dataDir == null)
            throw new IOException("Unable to create dir");

        File torrent = new File(dataDir, TorrentRepository.DataModel.TORRENT_FILE_NAME);
        if (torrent.exists())
            torrent.delete();
        /* We are sure that one of them is not null */
        if (pathToTorrent != null)
            FileUtils.copyFile(context, pathToTorrent, Uri.fromFile(torrent));
        else
            org.apache.commons.io.FileUtils.writeByteArrayToFile(torrent, bencode);

        return (torrent.exists() ? torrent.getAbsolutePath() : null);
    }

    /*
     * Create a directory to store data of added torrent (in standard data directory)
     * Returns path to the new directory if successful or null due to an error.
     */

    private String makeTorrentDataDir(Context context, String name)
    {
        if (!FileUtils.isStorageWritable())
            return null;

        String dataDir = context.getExternalFilesDir(null).getAbsolutePath();
        File newDir = new File(dataDir, name);

        return (newDir.mkdir()) ? newDir.getAbsolutePath() : null;
    }

    private boolean deleteTorrentDataDir(Context context, String id)
    {
        if (!FileUtils.isStorageWritable())
            return false;

        String path = getTorrentDataDir(context, id);
        if (path != null) {
            try {
                org.apache.commons.io.FileUtils.deleteDirectory(new File(path));

                return true;

            } catch (IOException e) {
                Log.e(TAG, Log.getStackTraceString(e));

                return false;
            }
        }

        return false;
    }
}
