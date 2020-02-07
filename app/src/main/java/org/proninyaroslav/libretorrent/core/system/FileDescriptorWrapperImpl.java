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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;

class FileDescriptorWrapperImpl implements FileDescriptorWrapper
{
    private ContentResolver contentResolver;
    private Uri path;
    private ParcelFileDescriptor pfd;

    public FileDescriptorWrapperImpl(@NonNull Context appContext, @NonNull Uri path)
    {
        contentResolver = appContext.getContentResolver();
        this.path = path;
    }

    @Override
    public FileDescriptor open(@NonNull String mode) throws FileNotFoundException
    {
        pfd = contentResolver.openFileDescriptor(path, mode);

        return (pfd == null ? null : pfd.getFileDescriptor());
    }

    @Override
    public void close() throws IOException
    {
        if (pfd != null)
            pfd.close();
    }
}
