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

package org.proninyaroslav.libretorrent.core.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import org.proninyaroslav.libretorrent.core.model.data.entity.FastResume;
import org.proninyaroslav.libretorrent.core.model.data.entity.FeedChannel;
import org.proninyaroslav.libretorrent.core.model.data.entity.FeedItem;
import org.proninyaroslav.libretorrent.core.model.data.entity.Torrent;
import org.proninyaroslav.libretorrent.core.storage.converter.UriConverter;
import org.proninyaroslav.libretorrent.core.storage.dao.FastResumeDao;
import org.proninyaroslav.libretorrent.core.storage.dao.FeedDao;
import org.proninyaroslav.libretorrent.core.storage.dao.TorrentDao;

@Database(entities = {Torrent.class,
        FastResume.class,
        FeedChannel.class,
        FeedItem.class},
        version = 6)
@TypeConverters({UriConverter.class})

public abstract class AppDatabase extends RoomDatabase
{
    private static final String DATABASE_NAME = "libretorrent.db";

    private static volatile AppDatabase INSTANCE;

    public abstract TorrentDao torrentDao();

    public abstract FastResumeDao fastResumeDao();

    public abstract FeedDao feedDao();

    public static AppDatabase getInstance(@NonNull Context appContext)
    {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null)
                    INSTANCE = buildDatabase(appContext);
            }
        }

        return INSTANCE;
    }

    private static AppDatabase buildDatabase(Context appContext)
    {
        return Room.databaseBuilder(appContext, AppDatabase.class, DATABASE_NAME)
                .addMigrations(DatabaseMigration.getMigrations(appContext))
                .build();
    }
}