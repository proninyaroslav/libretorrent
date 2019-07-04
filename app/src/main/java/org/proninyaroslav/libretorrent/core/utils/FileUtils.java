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

package org.proninyaroslav.libretorrent.core.utils;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.system.Os;
import android.system.StructStatVfs;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

public class FileUtils
{
    @SuppressWarnings("unused")
    private static final String TAG = FileUtils.class.getSimpleName();

    public static final char EXTENSION_SEPARATOR = '.';

    public static final String TEMP_DIR = "temp";

    /*
     * Return path to the standard Download directory.
     * If the directory doesn't exist, the function creates it automatically.
     */

    @Nullable
    public static String getDefaultDownloadPath()
    {
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .getAbsolutePath();

        File dir = new File(path);
        if (dir.exists() && dir.isDirectory())
            return path;
        else
            return dir.mkdirs() ? path : null;
    }

    public static String altExtStoragePath()
    {
        File extdir = new File("/storage/sdcard1");
        String path = "";
        if (extdir.exists() && extdir.isDirectory()) {
            File[] contents = extdir.listFiles();
            if (contents != null && contents.length > 0) {
                path = extdir.toString();
            }
        }
        return path;
    }

    /*
     * Return the primary shared/external storage directory.
     */

    @Nullable
    public static String getUserDirPath()
    {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath();

        File dir = new File(path);
        if (dir.exists() && dir.isDirectory())
            return path;
        else
            return dir.mkdirs() ? path : null;
    }

    /*
     * Return path components.
     */

    public static String[] parsePath(@NonNull String path)
    {
        return path.split(File.separator);
    }

    /*
     * Return true if the uri is a SAF path
     */

    public static boolean isSAFPath(@NonNull Uri uri)
    {
        String scheme = uri.getScheme();
        if (scheme == null)
            throw new IllegalArgumentException("Scheme of " + uri + " is null");

        return scheme.equals("content");
    }

    /*
     * Return true if the uri is a simple filesystem path
     */

    public static boolean isFileSystemPath(@NonNull Uri uri)
    {
        String scheme = uri.getScheme();
        if (scheme == null)
            throw new IllegalArgumentException("Scheme of " + uri.getPath() + " is null");

        return scheme.equals("file");
    }

    public static boolean deleteFile(@NonNull Context context,
                                     @NonNull Uri path) throws FileNotFoundException
    {
        if (isSAFPath(path)) {
            return DocumentsContract.deleteDocument(context.getContentResolver(), path);

        } else {
            String fileSystemPath = path.getPath();
            if (fileSystemPath == null)
                return false;
            return new File(fileSystemPath).delete();
        }
    }

    /*
     * Returns a file (if exists) Uri by name from the pointed directory
     */

    public static Uri getFileUri(@NonNull Context context,
                                 @NonNull Uri dir,
                                 @NonNull String fileName)
    {
        if (isSAFPath(dir)) {
            return getSAFFileUri(context, dir, fileName);

        } else {
            File f = new File(dir.getPath(), fileName);

            return (f.exists() ? Uri.fromFile(f) : null);
        }
    }

    /*
     * Returns a file (if exists) Uri by relative path (e.g foo/bar.txt)
     * from the pointed directory
     */

    public static Uri getFileUri(@NonNull Context context,
                                 @NonNull String relativePath,
                                 @NonNull Uri dir)
    {
        if (isFileSystemPath(dir)) {
            File f = new File(dir.getPath() + File.separator + relativePath);

            return (f.exists() ? Uri.fromFile(f) : null);

        } else {
            Uri currNode = dir;

            for (String nodeName : relativePath.split(File.separator)) {
                if (currNode == null)
                    break;
                currNode = getSAFFileUri(context, currNode, nodeName);
            }

            return currNode;
        }
    }

    private static Uri getSAFFileUri(Context context, Uri dir, String fileName)
    {
        DocumentFile tree = DocumentFile.fromTreeUri(context, dir);
        DocumentFile f;
        try {
            f = tree.findFile(fileName);

        } catch (UnsupportedOperationException e) {
            return null;
        }

        return (f != null ? f.getUri() : null);
    }

    public static boolean fileExists(@NonNull Context context,
                                     @NonNull Uri filePath)
    {
        if (isSAFPath(filePath)) {
            DocumentFile f = DocumentFile.fromSingleUri(context, filePath);
            return f != null && f.exists();

        } else {
            return new File(filePath.getPath()).exists();
        }
    }

    /*
     * Checks if main storage is available for read and write.
     */

    public static boolean isStorageWritable()
    {
        String state = Environment.getExternalStorageState();

        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /*
     * Checks if main storage is available to at least read.
     */

    public static boolean isStorageReadable()
    {
        String state = Environment.getExternalStorageState();

        return Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }

    /*
     * src - an existing file to copy
     * dest - the new file
     */

    public static void copyFile(@NonNull Context context,
                                @NonNull Uri src,
                                @NonNull Uri dest) throws IOException
    {

        ContentResolver resolver = context.getContentResolver();
        FileOutputStream fout = null;
        FileInputStream fin = null;
        try {
            ParcelFileDescriptor fd = resolver.openFileDescriptor(src, "r");
            fin = new FileInputStream(fd.getFileDescriptor());

            fd = resolver.openFileDescriptor(dest, "rw");
            fout = new FileOutputStream(fd.getFileDescriptor());

            IOUtils.copy(fin, fout);

        } finally {
            IOUtils.closeQuietly(fout);
            IOUtils.closeQuietly(fin);
        }
    }

    /*
     * Returns Uri and name of created file.
     * Note: if replace == false, doesn't replace file if it exists and returns its Uri.
     *       Storage Access Framework can change the name after creating the file
     *       (e.g. extension), please check it after returning.
     */

    public static Pair<Uri, String> createFile(@NonNull Context context,
                                               @NonNull Uri dir,
                                               @NonNull String desiredFileName,
                                               @NonNull String mimeType,
                                               boolean replace) throws IOException
    {
        if (isSAFPath(dir)) {
            DocumentFile tree = DocumentFile.fromTreeUri(context, dir);
            DocumentFile f;
            try {
                f = tree.findFile(desiredFileName);
                if (f != null) {
                    if (!replace)
                        return Pair.create(f.getUri(), desiredFileName);
                    else if (!DocumentsContract.deleteDocument(context.getContentResolver(), f.getUri()))
                        return null;
                }
                f = tree.createFile(mimeType, desiredFileName);

            } catch (UnsupportedOperationException e) {
                throw new IOException(e);
            }
            if (f == null)
                throw new IOException("Unable to create file {name=" + desiredFileName + ", dir=" + dir + "}");

            /* Maybe an extension was added to the file name */
            String newName = f.getName();

            return Pair.create(f.getUri(), (newName == null ? desiredFileName : newName));
        } else {
            File f = new File(dir.getPath(), desiredFileName);
            try {
                if (f.exists()) {
                    if (!replace)
                        return Pair.create(Uri.fromFile(f), desiredFileName);
                    else if (!f.delete())
                        return null;
                }
                if (!f.createNewFile())
                    return null;

            } catch (IOException | SecurityException e) {
                throw new IOException(e);
            }

            return Pair.create(Uri.fromFile(f), desiredFileName);
        }
    }

    /*
     * Return the number of bytes that are free on the file system
     * backing the given FileDescriptor
     *
     * TODO: maybe there is analog for KitKat?
     */

    @TargetApi(21)
    public static long getAvailableBytes(@NonNull FileDescriptor fd) throws IOException
    {
        try {
            StructStatVfs stat = Os.fstatvfs(fd);

            return stat.f_bavail * stat.f_bsize;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public static long getDirAvailableBytes(@NonNull Context context,
                                            @NonNull Uri dir)
    {
        long availableBytes = -1;

        if (isSAFPath(dir)) {
            Uri pseudoDirPath = DocumentsContract.buildDocumentUriUsingTree(dir,
                    DocumentsContract.getTreeDocumentId(dir));
            try {
                ParcelFileDescriptor pfd = context.getContentResolver()
                        .openFileDescriptor(pseudoDirPath, "r");
                availableBytes = getAvailableBytes(pfd.getFileDescriptor());

            } catch (IllegalArgumentException | IOException e) {
                Log.e(TAG, Log.getStackTraceString(e));
                return availableBytes;
            }

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                File file = new File(dir.getPath());
                availableBytes = file.getUsableSpace();

            } catch (Exception e) {
                /* This provides invalid space on some devices */
                try {
                    StatFs stat = new StatFs(dir.getPath());

                    availableBytes = stat.getAvailableBytes();
                } catch (Exception ee) {
                    Log.e(TAG, Log.getStackTraceString(e));
                    return availableBytes;
                }
            }
        }

        return availableBytes;
    }

    public static File getTempDir(@NonNull Context context)
    {
        File tmpDir = new File(context.getExternalFilesDir(null), TEMP_DIR);
        if (!tmpDir.exists())
            if (!tmpDir.mkdirs())
                return null;

        return tmpDir;
    }

    public static void cleanTempDir(@NonNull Context context) throws IOException
    {
        File tmpDir = getTempDir(context);
        if (tmpDir == null)
            throw new FileNotFoundException("Temp dir not found");

        org.apache.commons.io.FileUtils.cleanDirectory(tmpDir);
    }

    /*
     * Returns all shared/external storage devices where the application can place files it owns.
     * For Android below 4.4 returns only primary storage (standard download path).
     */

    public static ArrayList<String> getStorageList(@NonNull Context context)
    {
        ArrayList<String> storages = new ArrayList<>();
        storages.add(Environment.getExternalStorageDirectory().getAbsolutePath());

        String altPath = altExtStoragePath();
        if (TextUtils.isEmpty(altPath))
            storages.add(altPath);

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
                        if (filesDirs[i].exists())
                            storages.add(filesDirs[i].getAbsolutePath());
                        else
                            Log.w(TAG, "Unexpected external storage: " + filesDirs[i].getAbsolutePath());
                    }
                }
            }
        }

        return storages;
    }

    public static File makeTempFile(@NonNull Context context,
                                    @NonNull String postfix)
    {
        return new File(getTempDir(context), UUID.randomUUID().toString() + postfix);
    }

    public static String getExtension(String fileName)
    {
        if (fileName == null)
            return null;

        int extensionPos = fileName.lastIndexOf(EXTENSION_SEPARATOR);
        int lastSeparator = fileName.lastIndexOf(File.separator);
        int index = (lastSeparator > extensionPos ? -1 : extensionPos);

        if (index == -1)
            return "";
        else
            return fileName.substring(index + 1);
    }

    /*
     * Check if given filename is valid for a FAT filesystem
     */

    public static boolean isValidFatFilename(String name)
    {
        return name != null && name.equals(buildValidFatFilename(name));
    }

    /*
     * Mutate the given filename to make it valid for a FAT filesystem,
     * replacing any invalid characters with "_"
     */

    public static String buildValidFatFilename(String name)
    {
        if (TextUtils.isEmpty(name) || ".".equals(name) || "..".equals(name))
            return "(invalid)";

        final StringBuilder res = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (isValidFatFilenameChar(c))
                res.append(c);
            else
                res.append('_');
        }
        /*
         * Even though vfat allows 255 UCS-2 chars, we might eventually write to
         * ext4 through a FUSE layer, so use that limit
         */
        trimFilename(res, 255);

        return res.toString();
    }

    private static boolean isValidFatFilenameChar(char c)
    {
        if ((0x00 <= c && c <= 0x1f))
            return false;
        switch (c) {
            case '"':
            case '*':
            case '/':
            case ':':
            case '<':
            case '>':
            case '?':
            case '\\':
            case '|':
            case 0x7F:
                return false;
            default:
                return true;
        }
    }

    private static void trimFilename(StringBuilder res, int maxBytes)
    {
        byte[] raw = res.toString().getBytes(Charset.forName("UTF-8"));
        if (raw.length > maxBytes) {
            maxBytes -= 3;
            while (raw.length > maxBytes) {
                res.deleteCharAt(res.length() / 2);
                raw = res.toString().getBytes(Charset.forName("UTF-8"));
            }
            res.insert(res.length() / 2, "...");
        }
    }

    /*
     * Append file:// scheme for Uri
     */

    public static String normalizeFilesystemPath(@NonNull String path)
    {
        return (path.startsWith("file://") ? path : "file://" + path);
    }

    /*
     * Returns path if the directory belongs to the filesystem,
     * otherwise returns SAF name
     */

    public static String getDirName(@NonNull Context context,
                                    @NonNull Uri dirPath)
    {
        if (FileUtils.isFileSystemPath(dirPath))
            return dirPath.getPath();

        DocumentFile dir = DocumentFile.fromTreeUri(context, dirPath);
        if (dir == null)
            return dirPath.getPath();

        String name = dir.getName();

        return (name == null ? dirPath.getPath() : name);
    }
}
