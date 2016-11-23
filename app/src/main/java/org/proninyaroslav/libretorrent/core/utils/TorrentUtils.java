/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.utils;

import android.content.Context;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import org.apache.commons.io.FileUtils;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.storage.TorrentStorage;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.UUID;

public class TorrentUtils
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentUtils.class.getSimpleName();

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

    @Nullable
    public static String copyTorrent(Context context,
                                     String dataDirId,
                                     String pathToTorrent) throws Throwable
    {

        if ((pathToTorrent == null ||
                TextUtils.isEmpty(pathToTorrent)) ||
                !FileIOUtils.fileExist(pathToTorrent)) {
            throw new FileNotFoundException();
        }

        if (torrentDataExists(context, dataDirId)) {
            removeTorrentDataDir(context, dataDirId);
        }

        String dataDir = makeTorrentDataDir(context, dataDirId);

        if (dataDir == null) {
            throw new IOException("Unable to create dir");
        }

        File torrent = new File(dataDir, TorrentStorage.Model.DATA_TORRENT_FILE_NAME);

        FileUtils.copyFile(new File(pathToTorrent), torrent);

        return (torrent.exists() ? torrent.getAbsolutePath() : "");
    }

    /*
     * Search directory with data of added torrent (in standard external data directory).
     * Returns path to the directory found if successful or null if the directory is not found.
     */

    @Nullable
    public static String findTorrentDataDir(Context context, String id)
    {
        if (FileIOUtils.isStorageReadable()) {
            File dataDir = new File(context.getExternalFilesDir(null), id);
            if (dataDir.exists()) {
                return dataDir.getAbsolutePath();
            }
        }

        return null;
    }

    /*
     * Checking existing a directory with data of added torrent
     * (in standard external data directory).
     */

    public static boolean torrentDataExists(Context context, String id)
    {
        if (FileIOUtils.isStorageReadable()) {
            File dataDir = new File(context.getExternalFilesDir(null),  id);

            if (dataDir.exists()) {
                File torrentFile = new File(dataDir, TorrentStorage.Model.DATA_TORRENT_FILE_NAME);

                if (torrentFile.exists()) {
                    return true;
                }
            }
        }

        return false;
    }

    /*
     * Create a directory to store data of added torrent (in standard external data directory)
     * Returns path to the new directory if successful or null due to an error.
     */

    @Nullable
    public static String makeTorrentDataDir(Context context, String name)
    {
        if (!FileIOUtils.isStorageWritable()) {
            return null;
        }

        String dataDir = context.getExternalFilesDir(null).getAbsolutePath();

        File newDir = new File(dataDir, name);

        return (newDir.mkdir()) ? newDir.getAbsolutePath() : null;
    }

    public static boolean removeTorrentDataDir(Context context, String id)
    {
        if (!FileIOUtils.isStorageWritable()) {
            return false;
        }

        String path = findTorrentDataDir(context, id);

        if (path != null) {
            try {
                FileUtils.deleteDirectory(new File(path));

                return true;

            } catch (IOException e) {
                Log.e(TAG, Log.getStackTraceString(e));

                return false;
            }
        }

        return false;
    }

    /*
     * Save generated fast-resume data for the torrent.
     */

    public static void saveResumeData(Context context, String id, byte[] data) throws Exception
    {
        String pathToDataDir = findTorrentDataDir(context, id);

        if (pathToDataDir == null) {
            return;
        }

        FileUtils.writeByteArrayToFile(
                new File(pathToDataDir, TorrentStorage.Model.DATA_TORRENT_RESUME_FILE_NAME),
                data);
    }

    /*
     * Returns a torrent file as "sha1-random_number.torrent".
     */

    public static File createTempTorrentFile(byte[] data, File saveDir) throws Exception
    {
        return createTorrentFile(UUID.randomUUID().toString(), data, saveDir);
    }

    /*
     * Returns empty torrent file as "random_number.torrent".
     */

    public static File createTempTorrentFile(File saveDir)
    {
        return new File(saveDir, UUID.randomUUID().toString() + ".torrent");
    }


    public static File createTorrentFile(String name, byte[] data, File saveDir) throws Exception
    {
        if (name == null || data == null || saveDir == null) {
            return null;
        }

        File torrent = FileIOUtils.createTempFile(name, ".torrent", saveDir);

        if (torrent != null) {
            FileUtils.writeByteArrayToFile(torrent, data);
        }

        return torrent;
    }

    /*
     * Copy torrent file from data dir to specified dir.
     * Return true if successful.
     */

    public static boolean copyTorrentFile(Context context, String id, String pathToDir) throws IOException
    {
        return copyTorrentFile(context, id, pathToDir, null);
    }

    public static boolean copyTorrentFile(Context context, String id, String pathToDir, String fileName) throws IOException
    {
        if (pathToDir == null || id == null ||
                TextUtils.isEmpty(pathToDir) ||
                !FileIOUtils.fileExist(pathToDir)) {
            return false;
        }

        String dataDir = findTorrentDataDir(context, id);
        if (dataDir == null) {
            return false;
        }

        File torrent = new File(dataDir, TorrentStorage.Model.DATA_TORRENT_FILE_NAME);
        if (!torrent.exists()) {
            return false;
        }

        String newTorrent = (fileName != null ? fileName : id);

        FileUtils.copyFile(torrent, new File(pathToDir, newTorrent));

        return true;
    }

    public static void saveSession(Context context, byte[] data) throws Exception
    {
        String dataDir = context.getExternalFilesDir(null).getAbsolutePath();
        File sessionFile = new File(dataDir, TorrentStorage.Model.DATA_TORRENT_SESSION_FILE);

        FileUtils.writeByteArrayToFile(sessionFile, data);
    }

    @Nullable
    public static String findSessionFile(Context context)
    {
        if (FileIOUtils.isStorageReadable()) {
            String dataDir = context.getExternalFilesDir(null).getAbsolutePath();
            File session = new File(dataDir, TorrentStorage.Model.DATA_TORRENT_SESSION_FILE);

            if (session.exists()) {
                return session.getAbsolutePath();
            }
        }

        return null;
    }

    /*
     * Return path to the current torrent download directory.
     * If the directory doesn't exist, the function creates it automatically.
     */

    public static String getTorrentDownloadPath(Context context)
    {
        SettingsManager pref = new SettingsManager(context);
        String path = pref.getString(context.getString(R.string.pref_key_save_torrents_in), "");

        if (!TextUtils.isEmpty(path)) {
            return path;
        }

        return FileIOUtils.getDefaultDownloadPath();
    }
}
