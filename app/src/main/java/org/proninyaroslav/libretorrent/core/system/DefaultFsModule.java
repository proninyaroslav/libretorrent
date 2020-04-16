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

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.StatFs;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

class DefaultFsModule implements FsModule
{
    private Context appContext;

    public DefaultFsModule(@NonNull Context appContext)
    {
        this.appContext = appContext;
    }

    @Override
    public String getName(@NonNull Uri filePath)
    {
        return new File(filePath.getPath()).getName();
    }

    @Override
    public String getDirPath(@NonNull Uri dir)
    {
        return dir.getPath();
    }

    @Override
    public String getFilePath(@NonNull Uri filePath)
    {
        return filePath.getPath();
    }

    @Override
    public Uri getFileUri(@NonNull Uri dir, @NonNull String fileName, boolean create) throws IOException
    {
        File f = new File(dir.getPath(), fileName);
        if (create)
            f.createNewFile();

        return (f.exists() ? Uri.fromFile(f) : null);
    }

    @Override
    public Uri getFileUri(@NonNull String relativePath, @NonNull Uri dir)
    {
        if (!relativePath.startsWith(File.separator))
            relativePath = File.separator + relativePath;
        File f = new File(dir.getPath() + relativePath);

        return (f.exists() ? Uri.fromFile(f) : null);
    }

    @Override
    public boolean delete(@NonNull Uri filePath)
    {
        return new File(filePath.getPath()).delete();
    }

    @Override
    public FileDescriptorWrapper openFD(@NonNull Uri path)
    {
        return new FileDescriptorWrapperImpl(appContext, path);
    }

    @Override
    public long getDirAvailableBytes(@NonNull Uri dir)
    {
        long availableBytes;

        try {
            File file = new File(dir.getPath());
            availableBytes = file.getUsableSpace();

        } catch (Exception e) {
            /* This provides invalid space on some devices */
            StatFs stat = new StatFs(dir.getPath());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                availableBytes = stat.getAvailableBytes();
            else
                availableBytes = (long)stat.getBlockSize() * (long)stat.getAvailableBlocks();
        }

        return availableBytes;
    }

    @Override
    public boolean fileExists(@NonNull Uri filePath)
    {
        return new File(filePath.getPath()).exists();
    }

    @Override
    public long lastModified(@NonNull Uri filePath)
    {
        return new File(filePath.getPath()).lastModified();
    }

    @Override
    public String makeFileSystemPath(@NonNull Uri uri, String relativePath)
    {
        return uri.getPath();
    }

    @Override
    public Uri getParentDirUri(@NonNull Uri filePath)
    {
        File parent = new File(filePath.getPath()).getParentFile();

        return (parent.exists() ? Uri.fromFile(parent) : null);
    }
}
