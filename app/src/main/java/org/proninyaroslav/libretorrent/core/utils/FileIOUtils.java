/*
 * Copyright (C) 2016, 2017 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.content.ContentProvider;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.Log;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

/*
 * Main I/O operations on files.
 */

public class FileIOUtils
{
    @SuppressWarnings("unused")
    private static final String TAG = FileIOUtils.class.getSimpleName();

    public static final String TEMP_DIR = "temp";

    /*
     * Return path to the standard Download directory.
     * If the directory doesn't exist, the function creates it automatically.
     */

    public static String getDefaultDownloadPath()
    {
        String path = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .getAbsolutePath();

        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            return path;

        } else {
            return dir.mkdirs() ? path : "";
        }
    }

    /*
     * Return the primary shared/external storage directory.
     */

    public static String getUserDirPath()
    {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath();

        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            return path;

        } else {
            return dir.mkdirs() ? path : "";
        }
    }

    /*
     * Return path components.
     */

    public static String[] parsePath(String path)
    {
        if (path == null || TextUtils.isEmpty(path)) {
            return new String[0];
        }

        return path.split(File.separator);
    }

    public static boolean fileExist(String path)
    {
        return !(path == null || TextUtils.isEmpty(path)) && new File(path).exists();
    }

    /*
     * Checks if external storage is available for read and write.
     */

    public static boolean isStorageWritable()
    {
        String state = Environment.getExternalStorageState();

        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /*
     * Checks if external storage is available to at least read.
     */

    public static boolean isStorageReadable()
    {
        String state = Environment.getExternalStorageState();

        return Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }

    /*
     * Returns free space for the specified path in bytes.
     * If error return -1.
     */

    public static long getFreeSpace(String path)
    {
        long availableBytes = -1L;

        try {
            File file = new File(path);
            availableBytes = file.getUsableSpace();
        } catch (Exception e) {

            // this provides invalid space on some devices
            try {
                StatFs stat = new StatFs(path);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    availableBytes = stat.getAvailableBytes();
                } else {
                    availableBytes = stat.getAvailableBlocks() * stat.getBlockSize();
                }
            } catch (Exception ee) {
                /* Ignore */
            }
        }

        return availableBytes;
    }

    public static File getTempDir(Context context)
    {
        File tmpDir = new File(context.getExternalFilesDir(null), TEMP_DIR);
        if (!tmpDir.exists()) {
            if (!tmpDir.mkdirs()) {
                return null;
            }
        }

        return tmpDir;
    }

    public static void cleanTempDir(Context context) throws Exception
    {
        File tmpDir = getTempDir(context);

        if (tmpDir == null) {
            throw new FileNotFoundException("Temp dir not found");
        }

        FileUtils.cleanDirectory(tmpDir);
    }

    /*
     * Returns all shared/external storage devices where the application can place files it owns.
     * For Android below 4.4 returns only primary storage (standard download path).
     */

    public static ArrayList<String> getStorageList(Context context)
    {
        ArrayList<String> storages = new ArrayList<>();
        storages.add(Environment.getExternalStorageDirectory().getAbsolutePath());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            /*
             * First volume returned by getExternalFilesDirs is always primary storage,
             * or emulated. Further entries, if they exist, will be secondary or external SD,
             * see http://www.doubleencore.com/2014/03/android-external-storage/
             */
            File[] filesDirs = context.getExternalFilesDirs(null);

            if (filesDirs != null) {
                /* Skip primary storage */
                for (int i = 1; i < filesDirs.length; i++) {
                    if (filesDirs[i] != null) {
                        if (filesDirs[i].exists()) {
                            storages.add(filesDirs[i].getAbsolutePath());
                        } else {
                            Log.w(TAG, "Unexpected external storage: " + filesDirs[i].getAbsolutePath());
                        }
                    }
                }
            }
        }

        return storages;
    }

    public static File makeTempFile(Context context, String postfix) throws Exception
    {
        return new File(getTempDir(context), UUID.randomUUID().toString() + postfix);
    }

    public static void copyContentURIToFile(Context context, Uri uri, File file) throws Exception
    {
        FileUtils.copyInputStreamToFile(context.getContentResolver().openInputStream(uri), file);
    }
}
