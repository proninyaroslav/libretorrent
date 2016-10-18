/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.storage;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

/*
 * The class provides concurrent access to torrents storage.
 */

public class ConnectionManager
{
    private final static ConnectionManager INSTANCE = new ConnectionManager();
    private DatabaseHelper helper;

    private ConnectionManager()
    {
        /* Nothing */
    }

    public static synchronized SQLiteDatabase getDatabase(Context context)
    {
        if (INSTANCE.helper == null){
            INSTANCE.helper = new DatabaseHelper(context);
        }

        return INSTANCE.helper.getWritableDatabase();
    }
}
