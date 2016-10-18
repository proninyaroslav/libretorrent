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
import android.database.sqlite.SQLiteOpenHelper;

/*
 * A database model for store torrents.
 */

public class DatabaseHelper extends SQLiteOpenHelper
{
    @SuppressWarnings("unused")
    private static final String TAG = DatabaseHelper.class.getSimpleName();

    private static final String DATABASE_NAME = "libretorrent.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TORRENTS_TABLE = "torrents";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TORRENT_ID = "torrent_id";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_PATH_TO_TORRENT = "path_to_torrent";
    public static final String COLUMN_PATH_TO_DOWNLOAD = "path_to_download";
    public static final String COLUMN_FILE_PRIORITIES = "file_priorities";
    public static final String COLUMN_IS_SEQUENTIAL = "is_sequential";
    public static final String COLUMN_IS_FINISHED = "is_finished";
    public static final String COLUMN_IS_PAUSED = "is_paused";

    private static final String CREATE_TORRENTS_TABLE = "create table "
            + TORRENTS_TABLE +
            "(" + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_TORRENT_ID + " text not null unique, "
            + COLUMN_NAME + " text not null, "
            + COLUMN_PATH_TO_TORRENT + " text not null, "
            + COLUMN_PATH_TO_DOWNLOAD + " text not null, "
            + COLUMN_FILE_PRIORITIES + " text not null, "
            + COLUMN_IS_SEQUENTIAL + " integer, "
            + COLUMN_IS_FINISHED + " integer, "
            + COLUMN_IS_PAUSED + " integer );";

    public DatabaseHelper(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase)
    {
        sqLiteDatabase.execSQL(CREATE_TORRENTS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1)
    {
        /* Nothing */
    }
}
