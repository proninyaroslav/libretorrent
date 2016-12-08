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
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/*
 * Main I/O operations on files.
 */

public class FileIOUtils
{
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

    public static File createTempFile(String prefix, String suffix, File saveDir) throws IOException
    {
        if (saveDir == null || !saveDir.exists() || saveDir.isFile()) {
            return null;
        }

        return File.createTempFile(prefix, suffix, saveDir);
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
     * If error return -1;
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
        File tmpDir = new File(context.getCacheDir(), TEMP_DIR);
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
}
