/*
 * Copyright (C) 2019-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import androidx.annotation.NonNull;

import java.io.FileDescriptor;

public class FakeFileDescriptorWrapper implements FileDescriptorWrapper {
    private final FileDescriptor fd;

    public FakeFileDescriptorWrapper(FileDescriptor fd) {
        this.fd = fd;
    }

    @Override
    public FileDescriptor open(@NonNull String mode) {
        return fd;
    }

    @Override
    public void close() {
    }
}
