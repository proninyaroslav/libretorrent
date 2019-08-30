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

package org.proninyaroslav.libretorrent.core;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.proninyaroslav.libretorrent.core.filesystem.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.filesystem.FileSystemFacadeImpl;
import org.proninyaroslav.libretorrent.core.system.SystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeImpl;

public class FacadeHelper
{
    private static SystemFacade systemFacade;
    private static FileSystemFacade fileSystemFacade;

    public synchronized static SystemFacade getSystemFacade(@NonNull Context appContext)
    {
        if (systemFacade == null)
            systemFacade = new SystemFacadeImpl(appContext);

        return systemFacade;
    }

    @VisibleForTesting
    public synchronized static void setFileSystemFacade(@NonNull SystemFacade systemFacade)
    {
        FacadeHelper.systemFacade = systemFacade;
    }

    public synchronized static FileSystemFacade getFileSystemFacade(@NonNull Context appContext)
    {
        if (fileSystemFacade == null)
            fileSystemFacade = new FileSystemFacadeImpl(appContext);

        return fileSystemFacade;
    }

    @VisibleForTesting
    public synchronized static void setFileSystemFacade(@NonNull FileSystemFacade fileSystemFacade)
    {
        FacadeHelper.fileSystemFacade = fileSystemFacade;
    }
}
