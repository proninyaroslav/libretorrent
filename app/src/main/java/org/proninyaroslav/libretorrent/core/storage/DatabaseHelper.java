/*
 * Copyright (C) 2016-2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.util.Log;

/*
 * A database model for store torrents.
 */

public class DatabaseHelper extends SQLiteOpenHelper
{
    @SuppressWarnings("unused")
    private static final String TAG = DatabaseHelper.class.getSimpleName();

    private static final String DATABASE_NAME = "libretorrent.db";
    private static final int DATABASE_VERSION = 3;
    public static final String COLUMN_ID = "_id";

    /* Torrents storage */
    public static final String TORRENTS_TABLE = "torrents";
    public static final String COLUMN_TORRENT_ID = "torrent_id";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_PATH_TO_TORRENT = "path_to_torrent";
    public static final String COLUMN_PATH_TO_DOWNLOAD = "path_to_download";
    public static final String COLUMN_FILE_PRIORITIES = "file_priorities";
    public static final String COLUMN_IS_SEQUENTIAL = "is_sequential";
    public static final String COLUMN_IS_FINISHED = "is_finished";
    public static final String COLUMN_IS_PAUSED = "is_paused";
    public static final String COLUMN_DOWNLOADING_METADATA = "downloading_metadata";
    public static final String COLUMN_DATETIME = "datetime";

    /* Feed storage */
    public static final String FEEDS_TABLE = "feeds";
    public static final String COLUMN_FEED_NAME = "name";
    public static final String COLUMN_FEED_URL = "url";
    public static final String COLUMN_FEED_LAST_UPDATE = "last_update";
    public static final String COLUMN_FEED_AUTO_DOWNLOAD = "auto_download";
    public static final String COLUMN_FEED_FILTER = "filter";
    public static final String COLUMN_FEED_IS_REGEX_FILTER = "is_regex_filter";
    public static final String COLUMN_FETCH_ERROR = "fetch_error";

    /* Feed item storage */
    public static final String FEED_ITEMS_TABLE = "feed_items";
    public static final String COLUMN_FEED_ITEM_FEED_URL = "feed_url";
    public static final String COLUMN_FEED_ITEM_TITLE = "title";
    public static final String COLUMN_FEED_ITEM_DOWNLOAD_URL = "download_url";
    public static final String COLUMN_FEED_ITEM_ARTICLE_URL = "article_url";
    public static final String COLUMN_FEED_ITEM_PUB_DATE = "pub_date";
    public static final String COLUMN_FEED_ITEM_FETCH_DATE = "fetch_date";
    public static final String COLUMN_FEED_ITEM_READ = "read";

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
            + COLUMN_IS_PAUSED + " integer, "
            + COLUMN_DOWNLOADING_METADATA + " integer, "
            + COLUMN_DATETIME + " integer );";

    private static final String CREATE_FEEDS_TABLE = "create table "
            + FEEDS_TABLE +
            "(" + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_FEED_URL + " text not null unique, "
            + COLUMN_FEED_NAME + " text, "
            + COLUMN_FEED_LAST_UPDATE + " integer, "
            + COLUMN_FEED_AUTO_DOWNLOAD + " integer, "
            + COLUMN_FEED_FILTER + " text, "
            + COLUMN_FEED_IS_REGEX_FILTER + " integer, "
            + COLUMN_FETCH_ERROR + " text);";

    private static final String CREATE_FEED_ITEMS_TABLE = "create table "
            + FEED_ITEMS_TABLE +
            "(" + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_FEED_ITEM_FEED_URL + " text, "
            + COLUMN_FEED_ITEM_TITLE + " text not null unique, "
            + COLUMN_FEED_ITEM_DOWNLOAD_URL + " text, "
            + COLUMN_FEED_ITEM_ARTICLE_URL + " text, "
            + COLUMN_FEED_ITEM_PUB_DATE + " integer, "
            + COLUMN_FEED_ITEM_FETCH_DATE + " integer, "
            + COLUMN_FEED_ITEM_READ + " integer );";

    public DatabaseHelper(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase)
    {
        sqLiteDatabase.execSQL(CREATE_TORRENTS_TABLE);
        sqLiteDatabase.execSQL(CREATE_FEED_ITEMS_TABLE);
        sqLiteDatabase.execSQL(CREATE_FEEDS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion)
    {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to version "+ newVersion);

        if (oldVersion < 2) {
            sqLiteDatabase.beginTransaction();
            try {
                sqLiteDatabase.execSQL("ALTER TABLE " + TORRENTS_TABLE + " ADD COLUMN "
                        + COLUMN_DOWNLOADING_METADATA + " integer ");
                sqLiteDatabase.execSQL("ALTER TABLE " + TORRENTS_TABLE + " ADD COLUMN "
                        + COLUMN_DATETIME + " integer ");

                sqLiteDatabase.setTransactionSuccessful();

            } finally {
                sqLiteDatabase.endTransaction();
            }
        }
        if (oldVersion < 3) {
            sqLiteDatabase.execSQL(CREATE_FEED_ITEMS_TABLE);
            sqLiteDatabase.execSQL(CREATE_FEEDS_TABLE);
        }
    }
}
