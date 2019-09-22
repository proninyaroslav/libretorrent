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

package org.proninyaroslav.libretorrent.core.system.filesystem;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.system.Os;
import android.system.StructStatVfs;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

public class FileSystemFacadeImpl implements FileSystemFacade
{
    @SuppressWarnings("unused")
    private static final String TAG = FileSystemFacadeImpl.class.getSimpleName();

    private static final String EXTENSION_SEPARATOR = ".";
    private static final String TEMP_DIR = "temp";

    private Context appContext;

    public FileSystemFacadeImpl(@NonNull Context appContext)
    {
        this.appContext = appContext;
    }

    @Override
    public FileDescriptorWrapper getFD(@NonNull Uri path)
    {
        return new FileDescriptorWrapperImpl(appContext, path);
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

    @Override
    public String altExtStoragePath()
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

    /*
     * Return true if the uri is a SAF path
     */

    @Override
    public boolean isSafPath(@NonNull Uri path)
    {
        return SafFileSystem.getInstance(appContext).isSafPath(path);
    }

    /*
     * Return true if the uri is a simple filesystem path
     */

    @Override
    public boolean isFileSystemPath(@NonNull Uri path)
    {
        String scheme = path.getScheme();
        if (scheme == null)
            throw new IllegalArgumentException("Scheme of " + path.getPath() + " is null");

        return scheme.equals("file");
    }

    @Override
    public boolean deleteFile(@NonNull Uri path) throws FileNotFoundException
    {
        if (isFileSystemPath(path)) {
            String fileSystemPath = path.getPath();
            if (fileSystemPath == null)
                return false;
            return new File(fileSystemPath).delete();

        } else {
            SafFileSystem fs = SafFileSystem.getInstance(appContext);
            return fs.delete(path);
        }
    }

    /*
     * Returns a file (if exists) Uri by name from the pointed directory
     */

    @Override
    public Uri getFileUri(@NonNull Uri dir,
                          @NonNull String fileName)
    {
        if (isFileSystemPath(dir)) {
            File f = new File(dir.getPath(), fileName);

            return (f.exists() ? Uri.fromFile(f) : null);

        } else {
            return SafFileSystem.getInstance(appContext)
                    .getFileUri(dir, fileName, false);
        }
    }

    /*
     * Returns a file (if exists) Uri by relative path (e.g foo/bar.txt)
     * from the pointed directory
     */

    @Override
    public Uri getFileUri(@NonNull String relativePath,
                          @NonNull Uri dir)
    {
        if (isFileSystemPath(dir)) {
            File f = new File(dir.getPath() + File.separator + relativePath);

            return (f.exists() ? Uri.fromFile(f) : null);

        } else {
            return SafFileSystem.getInstance(appContext)
                    .getFileUri(new SafFileSystem.FakePath(dir, relativePath), false);
        }
    }

    @Override
    public boolean fileExists(@NonNull Uri filePath)
    {
        if (isFileSystemPath(filePath)) {
            return new File(filePath.getPath()).exists();

        } else {
            return SafFileSystem.getInstance(appContext).exists(filePath);
        }
    }

    @Override
    public boolean fileExists(@NonNull Uri dir,
                              @NonNull String relativePath)
    {
        if (isFileSystemPath(dir)) {
            return new File(dir.getPath(), relativePath).exists();

        } else {
            return SafFileSystem.getInstance(appContext)
                    .exists(new SafFileSystem.FakePath(dir, relativePath));
        }
    }

    @Override
    public long lastModified(@NonNull Uri filePath)
    {
        if (isFileSystemPath(filePath)) {
            return new File(filePath.getPath()).lastModified();

        } else {
            SafFileSystem.Stat stat = SafFileSystem.getInstance(appContext)
                    .stat(filePath);

            return (stat == null ? -1 : stat.lastModified);
        }
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
     * src - an existing file to copy
     * dest - the new file
     */

    @Override
    public void copyFile(@NonNull Uri src,
                         @NonNull Uri dest) throws IOException
    {

        try (FileDescriptorWrapper wSrc = getFD(src);
             FileDescriptorWrapper wDest = getFD(dest)) {

            try (FileInputStream fin = new FileInputStream(wSrc.open("r"));
                 FileOutputStream fout = new FileOutputStream(wDest.open("rw"))) {
                IOUtils.copy(fin, fout);
            }
        }
    }

    /*
     * Returns Uri of created file.
     * Note: if replace == false, doesn't replace file if it exists and returns its Uri.
     *       Storage Access Framework can change the name after creating the file
     *       (e.g. extension), please check it after returning.
     */

    @Override
    public Uri createFile(@NonNull Uri dir,
                          @NonNull String fileName,
                          boolean replace) throws IOException
    {
        if (isFileSystemPath(dir)) {
            File f = new File(dir.getPath(), fileName);
            try {
                if (f.exists()) {
                    if (!replace)
                        return Uri.fromFile(f);
                    else if (!f.delete())
                        return null;
                }
                if (!f.createNewFile())
                    return null;

            } catch (IOException | SecurityException e) {
                throw new IOException(e);
            }

            return Uri.fromFile(f);

        } else {
            SafFileSystem fs = SafFileSystem.getInstance(appContext);
            Uri path = fs.getFileUri(dir, fileName, false);
            if (replace && path != null) {
                if (!fs.delete(path))
                    return null;
                path = fs.getFileUri(dir, fileName, true);
            }

            if (path == null)
                throw new IOException("Unable to create file {name=" + fileName + ", dir=" + dir + "}");

            return path;
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
        if (isSafPath(uri))
            return new SafFileSystem.FakePath(uri, (relativePath == null ? "" : relativePath))
                    .toString();
        else
            return uri.getPath();
    }

    /*
     * Return the number of bytes that are free on the file system
     * backing the given FileDescriptor
     *
     * TODO: maybe there is analog for KitKat?
     */

    @Override
    @TargetApi(21)
    public long getAvailableBytes(@NonNull FileDescriptor fd) throws IOException
    {
        try {
            StructStatVfs stat = Os.fstatvfs(fd);

            return stat.f_bavail * stat.f_bsize;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public long getDirAvailableBytes(@NonNull Uri dir)
    {
        long availableBytes = -1;

        if (isSafPath(dir)) {
            SafFileSystem fs = SafFileSystem.getInstance(appContext);
            Uri dirPath = fs.makeSafRootDir(dir);
            try (FileDescriptorWrapper w = getFD(dirPath)) {
                availableBytes = getAvailableBytes(w.open("r"));

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

    /*
     * Returns all shared/external storage devices where the application can place files it owns.
     * For Android below 4.4 returns only primary storage (standard download path).
     */

    @Override
    public ArrayList<String> getStorageList()
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
            File[] filesDirs = appContext.getExternalFilesDirs(null);

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
    public String getDirName(@NonNull Uri dir)
    {
        if (isFileSystemPath(dir))
            return dir.getPath();

        SafFileSystem.Stat stat = SafFileSystem.getInstance(appContext).statSafRoot(dir);

        return (stat == null || stat.name == null ? dir.getPath() : stat.name);
    }
}
