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

import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.FileNotFoundException;
import java.io.IOException;

/*
 * A platform dependent filesystem interface, that uses in FileSystemFacade.
 */

interface FsModule
{
    String getName(@NonNull Uri filePath);

    /*
     * Returns path (if present) or directory name
     */

    String getDirPath(@NonNull Uri dir);

    /*
     * Returns path (if present) or file name
     */

    String getFilePath(@NonNull Uri filePath);

    /*
     * Returns Uri of the file by the given file name or
     * null if the file doesn't exists
     */

    Uri getFileUri(@NonNull Uri dir, @NonNull String fileName, boolean create) throws IOException;

    /*
     * Returns a file (if exists) Uri by relative path (e.g foo/bar.txt)
     * from the pointed directory
     */

    Uri getFileUri(@NonNull String relativePath, @NonNull Uri dir);

    boolean delete(@NonNull Uri filePath) throws FileNotFoundException;

    FileDescriptorWrapper openFD(@NonNull Uri path);

    /*
     * Return the number of bytes that are free on the file system
     * backing the given Uri
     */

    long getDirAvailableBytes(@NonNull Uri dir) throws IOException;

    boolean fileExists(@NonNull Uri filePath);

    long lastModified(@NonNull Uri filePath);

    /*
     * If the uri is a file system path, returns the path as is,
     * otherwise returns the path in `SafFileSystem` format
     */

    String makeFileSystemPath(@NonNull Uri uri, String relativePath);

    Uri getParentDirUri(@NonNull Uri filePath);
}
