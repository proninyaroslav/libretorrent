/*
 * Copyright (C) 2016-2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import org.libtorrent4j.ErrorCode;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepository;
import org.proninyaroslav.libretorrent.core.utils.old.FileIOUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    public static String torrentToDataDir(@NonNull Context context,
                                          @NonNull String torrentId,
                                          @NonNull Uri pathToTorrent) throws IOException
    {

        if (!FileUtils.fileExists(context, pathToTorrent))
            throw new FileNotFoundException();

        return torrentToDataDir(context, torrentId, pathToTorrent, null);
    }

    public static String torrentToDataDir(@NonNull Context context,
                                          @NonNull String torrentId,
                                          @NonNull byte[] bencode) throws IOException
    {
        return torrentToDataDir(context, torrentId, null, bencode);
    }

    private static String torrentToDataDir(Context context,
                                           String id,
                                           Uri pathToTorrent,
                                           byte[] bencode) throws IOException
    {
        String dataDir;
        if (torrentDataExists(context, id))
            dataDir = findTorrentDataDir(context, id);
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
     * Checking existing a directory with data of added torrent
     * (in standard data directory).
     */

    public static boolean torrentDataExists(@NonNull Context context,
                                            @NonNull String id)
    {
        return FileUtils.isStorageReadable() &&
                new File(context.getExternalFilesDir(null), id).exists();
    }

    /*
     * Search directory with data of added torrent (in standard data directory).
     * Returns path to the directory found if successful or null if the directory is not found.
     */

    public static String findTorrentDataDir(@NonNull Context context,
                                            @NonNull String id)
    {
        if (FileUtils.isStorageReadable()) {
            File dataDir = new File(context.getExternalFilesDir(null), id);
            if (dataDir.exists())
                return dataDir.getAbsolutePath();
        }

        return null;
    }

    /*
     * Create a directory to store data of added torrent (in standard data directory)
     * Returns path to the new directory if successful or null due to an error.
     */

    public static String makeTorrentDataDir(@NonNull Context context,
                                            @NonNull String name)
    {
        if (!FileUtils.isStorageWritable())
            return null;

        String dataDir = context.getExternalFilesDir(null).getAbsolutePath();
        File newDir = new File(dataDir, name);

        return (newDir.mkdir()) ? newDir.getAbsolutePath() : null;
    }

    public static boolean removeTorrentDataDir(@NonNull Context context,
                                               @NonNull String id)
    {
        if (!FileIOUtils.isStorageWritable())
            return false;

        String path = findTorrentDataDir(context, id);
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

    /*
     * Save generated fast-resume data for the torrent.
     */

    public static void saveResumeData(@NonNull Context context,
                                      @NonNull String id,
                                      @NonNull byte[] data) throws IOException
    {
        String pathToDataDir = findTorrentDataDir(context, id);
        if (pathToDataDir == null)
            return;

        File f = new File(pathToDataDir, TorrentRepository.DataModel.TORRENT_RESUME_FILE_NAME);
        org.apache.commons.io.FileUtils.writeByteArrayToFile(f, data);
    }

    /*
     * Copy torrent file from data dir to specified file path.
     * Return true if successful.
     */

    public static boolean copyTorrentFile(@NonNull Context context,
                                          @NonNull String id,
                                          @NonNull Uri destFile) throws IOException
    {
        String dataDir = findTorrentDataDir(context, id);
        if (dataDir == null)
            return false;

        File torrent = new File(dataDir, TorrentRepository.DataModel.TORRENT_FILE_NAME);
        if (!torrent.exists())
            return false;

        FileUtils.copyFile(context, Uri.fromFile(torrent), destFile);

        return true;
    }

    public static boolean copyTorrentFileToDir(@NonNull Context context,
                                               @NonNull String id,
                                               @NonNull Uri destDir,
                                               @Nullable String fileName) throws IOException
    {
        String dataDir = findTorrentDataDir(context, id);
        if (dataDir == null)
            return false;

        File torrent = new File(dataDir, TorrentRepository.DataModel.TORRENT_FILE_NAME);
        if (!torrent.exists())
            return false;

        String name = (fileName != null ? fileName : id);

        Pair<Uri, String> ret = FileUtils.createFile(context, destDir, name, Utils.MIME_TORRENT, true);
        if (ret == null || ret.first == null)
            return false;

        FileUtils.copyFile(context, Uri.fromFile(torrent), ret.first);

        return true;
    }

    public static void saveSession(@NonNull Context context,
                                   @NonNull byte[] data) throws IOException
    {
        String dataDir = context.getExternalFilesDir(null).getAbsolutePath();
        File sessionFile = new File(dataDir, TorrentRepository.DataModel.TORRENT_SESSION_FILE);

        org.apache.commons.io.FileUtils.writeByteArrayToFile(sessionFile, data);
    }

    public static String findSessionFile(@NonNull Context context)
    {
        if (FileIOUtils.isStorageReadable()) {
            String dataDir = context.getExternalFilesDir(null).getAbsolutePath();
            File session = new File(dataDir, TorrentRepository.DataModel.TORRENT_SESSION_FILE);

            if (session.exists())
                return session.getAbsolutePath();
        }

        return null;
    }

    /*
     * Return path to the current torrent download directory.
     * If the directory doesn't exist, the function creates it automatically.
     */

    public static String getTorrentDownloadPath(@NonNull Context context)
    {
        SharedPreferences pref = SettingsManager.getPreferences(context);
        String path = pref.getString(context.getString(R.string.pref_key_save_torrents_in),
                                     SettingsManager.Default.saveTorrentsIn);
        if (!TextUtils.isEmpty(path))
            return path;

        return FileIOUtils.getDefaultDownloadPath();
    }

    public static String getErrorMsg(ErrorCode error)
    {
        return (error == null ? "" : error.message() + ", code " + error.value());
    }
}
