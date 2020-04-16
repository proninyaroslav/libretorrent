/*
 * Copyright (C) 2019, 2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.StructStatVfs;

import androidx.annotation.NonNull;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;

class SafFsModule implements FsModule
{
    private Context appContext;

    public SafFsModule(@NonNull Context appContext)
    {
        this.appContext = appContext;
    }

    @Override
    public String getName(@NonNull Uri filePath)
    {
        SafFileSystem fs = SafFileSystem.getInstance(appContext);
        SafFileSystem.Stat stat = fs.stat(filePath);

        return (stat == null ? null : stat.name);
    }

    @Override
    public String getDirPath(@NonNull Uri dir)
    {
        SafFileSystem.Stat stat = SafFileSystem.getInstance(appContext).statSafRoot(dir);

        return (stat == null || stat.name == null ? dir.getPath() : stat.name);
    }

    @Override
    public String getFilePath(@NonNull Uri filePath)
    {
        SafFileSystem.Stat stat = SafFileSystem.getInstance(appContext).stat(filePath);

        return (stat == null || stat.name == null ? filePath.getPath() : stat.name);
    }

    @Override
    public Uri getFileUri(@NonNull Uri dir, @NonNull String fileName, boolean create)
    {
        return SafFileSystem.getInstance(appContext).getFileUri(dir, fileName, create);
    }

    @Override
    public Uri getFileUri(@NonNull String relativePath, @NonNull Uri dir)
    {
        return SafFileSystem.getInstance(appContext)
                .getFileUri(new SafFileSystem.FakePath(dir, relativePath), false);
    }

    @Override
    public boolean delete(@NonNull Uri filePath) throws FileNotFoundException
    {
        SafFileSystem fs = SafFileSystem.getInstance(appContext);

        return fs.delete(filePath);
    }

    @Override
    public FileDescriptorWrapper openFD(@NonNull Uri path)
    {
        return new FileDescriptorWrapperImpl(appContext, path);
    }

    @Override
    public long getDirAvailableBytes(@NonNull Uri dir) throws IOException
    {
        long availableBytes = -1;
        ContentResolver contentResolver = appContext.getContentResolver();
        SafFileSystem fs = SafFileSystem.getInstance(appContext);
        Uri dirPath = fs.makeSafRootDir(dir);

        try (ParcelFileDescriptor pfd = contentResolver.openFileDescriptor(dirPath, "r")) {
            if (pfd == null)
                return availableBytes;

            availableBytes = getAvailableBytes(pfd.getFileDescriptor());

        }

        return availableBytes;
    }

    /*
     * Return the number of bytes that are free on the file system
     * backing the given FileDescriptor
     *
     * TODO: maybe there is analog for KitKat?
     */

    @TargetApi(21)
    private long getAvailableBytes(@NonNull FileDescriptor fd) throws IOException
    {
        try {
            StructStatVfs stat = Os.fstatvfs(fd);

            return stat.f_bavail * stat.f_bsize;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean fileExists(@NonNull Uri filePath)
    {
        return SafFileSystem.getInstance(appContext).exists(filePath);
    }

    @Override
    public long lastModified(@NonNull Uri filePath)
    {
        SafFileSystem.Stat stat = SafFileSystem.getInstance(appContext)
                .stat(filePath);

        return (stat == null ? -1 : stat.lastModified);
    }

    @Override
    public String makeFileSystemPath(@NonNull Uri uri, String relativePath)
    {
        return new SafFileSystem.FakePath(uri, (relativePath == null ? "" : relativePath))
                .toString();
    }

    @Override
    public Uri getParentDirUri(@NonNull Uri filePath)
    {
        return SafFileSystem.getInstance(appContext).getParentDirUri(filePath);
    }
}
