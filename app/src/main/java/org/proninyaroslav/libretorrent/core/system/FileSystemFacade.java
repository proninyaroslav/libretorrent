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

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;

public interface FileSystemFacade
{
    FileDescriptorWrapper getFD(@NonNull Uri path);

    String getExtensionSeparator();

    @Nullable
    String getDefaultDownloadPath();

    @Nullable
    String getUserDirPath();

    boolean deleteFile(@NonNull Uri path) throws FileNotFoundException;

    Uri getFileUri(@NonNull Uri dir,
                   @NonNull String fileName);

    Uri getFileUri(@NonNull String relativePath,
                   @NonNull Uri dir);

    boolean fileExists(@NonNull Uri filePath);

    long lastModified(@NonNull Uri filePath);

    boolean isStorageWritable();

    boolean isStorageReadable();

    Uri createFile(@NonNull Uri dir,
                   @NonNull String fileName,
                   boolean replace) throws IOException;

    void write(@NonNull byte[] data,
               @NonNull Uri destFile) throws IOException;

    void write(@NonNull CharSequence data,
               @NonNull Charset charset,
               @NonNull Uri destFile) throws IOException;

    String makeFileSystemPath(@NonNull Uri uri);

    String makeFileSystemPath(@NonNull Uri uri,
                              String relativePath);

    long getDirAvailableBytes(@NonNull Uri dir);

    File getTempDir();

    void cleanTempDir() throws IOException;

    File makeTempFile(@NonNull String postfix);

    String getExtension(String fileName);

    boolean isValidFatFilename(String name);

    String buildValidFatFilename(String name);

    String normalizeFileSystemPath(String path);

    String getDirPath(@NonNull Uri dir);

    String getFilePath(@NonNull Uri filePath);

    Uri getParentDirUri(@NonNull Uri filePath);
}
