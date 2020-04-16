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

package org.proninyaroslav.libretorrent.core.system;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.UUID;

class FileSystemFacadeImpl implements FileSystemFacade
{
    @SuppressWarnings("unused")
    private static final String TAG = FileSystemFacadeImpl.class.getSimpleName();

    private static final String EXTENSION_SEPARATOR = ".";
    private static final String TEMP_DIR = "temp";

    private Context appContext;
    private FsModuleResolver fsResolver;

    public FileSystemFacadeImpl(@NonNull Context appContext,
                                @NonNull FsModuleResolver fsResolver)
    {
        this.appContext = appContext;
        this.fsResolver = fsResolver;
    }

    @Override
    public FileDescriptorWrapper getFD(@NonNull Uri path)
    {
        FsModule fsModule = fsResolver.resolveFsByUri(path);

        return fsModule.openFD(path);
    }

    @Override
    public String getExtensionSeparator()
    {
        return EXTENSION_SEPARATOR;
    }

    /*
     * Return path to the standard Download directory.
     * If the directory doesn't exist, the function creates it automatically.
     */

    @Override
    @Nullable
    public String getDefaultDownloadPath()
    {
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .getAbsolutePath();

        File dir = new File(path);
        if (dir.exists() && dir.isDirectory())
            return path;
        else
            return dir.mkdirs() ? path : null;
    }

    /*
     * Return the primary shared/external storage directory.
     */

    @Override
    @Nullable
    public String getUserDirPath()
    {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath();

        File dir = new File(path);
        if (dir.exists() && dir.isDirectory())
            return path;
        else
            return dir.mkdirs() ? path : null;
    }

    @Override
    public boolean deleteFile(@NonNull Uri path) throws FileNotFoundException
    {
        FsModule fsModule = fsResolver.resolveFsByUri(path);

        return fsModule.delete(path);
    }

    /*
     * Returns a file (if exists) Uri by name from the pointed directory
     */

    @Override
    public Uri getFileUri(@NonNull Uri dir,
                          @NonNull String fileName)
    {
        FsModule fsModule = fsResolver.resolveFsByUri(dir);

        Uri path = null;
        try {
            path = fsModule.getFileUri(dir, fileName, false);

        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }

        return path;
    }

    /*
     * Returns a file (if exists) Uri by relative path (e.g foo/bar.txt)
     * from the pointed directory
     */

    @Override
    public Uri getFileUri(@NonNull String relativePath,
                          @NonNull Uri dir)
    {
        FsModule fsModule = fsResolver.resolveFsByUri(dir);

        return fsModule.getFileUri(relativePath, dir);
    }

    @Override
    public boolean fileExists(@NonNull Uri filePath)
    {
        FsModule fsModule = fsResolver.resolveFsByUri(filePath);

        return fsModule.fileExists(filePath);
    }

    @Override
    public long lastModified(@NonNull Uri filePath)
    {
        FsModule fsModule = fsResolver.resolveFsByUri(filePath);

        return fsModule.lastModified(filePath);
    }

    /*
     * Checks if main storage is available for read and write.
     */

    @Override
    public boolean isStorageWritable()
    {
        String state = Environment.getExternalStorageState();

        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /*
     * Checks if main storage is available to at least read.
     */

    @Override
    public boolean isStorageReadable()
    {
        String state = Environment.getExternalStorageState();

        return Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }

    /*
     * Returns Uri of created file.
     * Note: if replace == false, doesn't replace file if it exists and returns its Uri
     */

    @Override
    public Uri createFile(@NonNull Uri dir,
                          @NonNull String fileName,
                          boolean replace) throws IOException
    {
        FsModule fsModule = fsResolver.resolveFsByUri(dir);
        try {
            Uri path = fsModule.getFileUri(dir, fileName, false);
            if (path != null) {
                if (!replace)
                    return path;
                else if (!fsModule.delete(path))
                    return null;
            }

            return fsModule.getFileUri(dir, fileName, true);

        } catch (SecurityException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void write(@NonNull byte[] data,
                      @NonNull Uri destFile) throws IOException
    {
        try (FileDescriptorWrapper w = getFD(destFile)) {
            try (FileOutputStream fout = new FileOutputStream(w.open("rw"))) {
                IOUtils.write(data, fout);
            }
        }
    }

    @Override
    public void write(@NonNull CharSequence data,
                      @NonNull Charset charset,
                      @NonNull Uri destFile) throws IOException
    {
        try (FileDescriptorWrapper w = getFD(destFile)) {
            try (FileOutputStream fout = new FileOutputStream(w.open("rw"))) {
                IOUtils.write(data, fout, charset);
            }
        }
    }

    /*
     * If the uri is a file system path, returns the path as is,
     * otherwise returns the path in `SafFileSystem` format
     */

    @Override
    public String makeFileSystemPath(@NonNull Uri uri)
    {
        return makeFileSystemPath(uri, null);
    }

    @Override
    public String makeFileSystemPath(@NonNull Uri uri,
                                     String relativePath)
    {
        FsModule fsModule = fsResolver.resolveFsByUri(uri);

        return fsModule.makeFileSystemPath(uri, relativePath);
    }

    /*
     * Return the number of bytes that are free on the file system
     * backing the given Uri
     */

    @Override
    public long getDirAvailableBytes(@NonNull Uri dir)
    {
        long availableBytes = -1;

        FsModule fsModule = fsResolver.resolveFsByUri(dir);
        try {
            availableBytes = fsModule.getDirAvailableBytes(dir);

        } catch (IllegalArgumentException | IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            return availableBytes;
        }

        return availableBytes;
    }

    @Override
    public File getTempDir()
    {
        File tmpDir = new File(appContext.getExternalFilesDir(null), TEMP_DIR);
        if (!tmpDir.exists())
            if (!tmpDir.mkdirs())
                return null;

        return tmpDir;
    }

    @Override
    public void cleanTempDir() throws IOException
    {
        File tmpDir = getTempDir();
        if (tmpDir == null)
            throw new FileNotFoundException("Temp dir not found");

        org.apache.commons.io.FileUtils.cleanDirectory(tmpDir);
    }

    @Override
    public File makeTempFile(@NonNull String postfix)
    {
        return new File(getTempDir(), UUID.randomUUID().toString() + postfix);
    }

    @Override
    public String getExtension(String fileName)
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

    @Override
    public boolean isValidFatFilename(String name)
    {
        return name != null && name.equals(buildValidFatFilename(name));
    }

    /*
     * Mutate the given filename to make it valid for a FAT filesystem,
     * replacing any invalid characters with "_"
     */

    @Override
    public String buildValidFatFilename(String name)
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

    private boolean isValidFatFilenameChar(char c)
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

    private void trimFilename(StringBuilder res, int maxBytes)
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

    @Override
    public String normalizeFileSystemPath(String path)
    {
        return (TextUtils.isEmpty(path) ||
                path.startsWith("file://") ||
                path.startsWith("content://") ?
                path :
                "file://" + path);
    }

    /*
     * Returns path if the directory belongs to the filesystem,
     * otherwise returns SAF name
     */

    @Override
    public String getDirPath(@NonNull Uri dir)
    {
        FsModule fsModule = fsResolver.resolveFsByUri(dir);

        return fsModule.getDirPath(dir);
    }

    /*
     * Returns path if the file belongs to the filesystem,
     * otherwise returns SAF name
     */

    @Override
    public String getFilePath(@NonNull Uri filePath)
    {
        FsModule fsModule = fsResolver.resolveFsByUri(filePath);

        return fsModule.getFilePath(filePath);
    }

    @Override
    public Uri getParentDirUri(@NonNull Uri filePath)
    {
        FsModule fsModule = fsResolver.resolveFsByUri(filePath);

        return fsModule.getParentDirUri(filePath);
    }
}
