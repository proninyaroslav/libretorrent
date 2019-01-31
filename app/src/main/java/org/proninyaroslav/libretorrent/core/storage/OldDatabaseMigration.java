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

package org.proninyaroslav.libretorrent.core.storage;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/*
 * Migration from old database to Room.
 */

/* TODO: maybe remove after several versions */
@Deprecated
public class OldDatabaseMigration
{
    public static Migration[] getMigrations()
    {
        return new Migration[]{MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5};
    }

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database)
        {
            database.execSQL("ALTER TABLE torrents ADD COLUMN downloading_metadata integer ");
            database.execSQL("ALTER TABLE torrents ADD COLUMN datetime integer ");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database)
        {
            database.execSQL("CREATE TABLE feeds(_id integer primary key autoincrement, url text not null unique, name text, last_update integer, auto_download integer, filter text, is_regex_filter integer, fetch_error text);");
            database.execSQL("CREATE TABLE feed_items(_id integer primary key autoincrement, feed_url text, title text not null unique, download_url text, article_url text, pub_date integer, fetch_date integer, read integer );");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database)
        {
            database.execSQL("ALTER TABLE torrents ADD COLUMN error text ");
        }
    };

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database)
        {
            /* Create new tablets */
            database.execSQL("CREATE TABLE IF NOT EXISTS `Torrent` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `source` TEXT NOT NULL, `downloadPath` TEXT NOT NULL, `filePriorities` TEXT NOT NULL, `sequentialDownload` INTEGER NOT NULL, `finished` INTEGER NOT NULL, `paused` INTEGER NOT NULL, `downloadingMetadata` INTEGER NOT NULL, `dateAdded` INTEGER NOT NULL, `error` TEXT, PRIMARY KEY(`id`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS `FeedChannel` (`url` TEXT NOT NULL, `name` TEXT, `lastUpdate` INTEGER NOT NULL, `autoDownload` INTEGER NOT NULL, `filter` TEXT, `isRegexFilter` INTEGER NOT NULL, `fetchError` TEXT, PRIMARY KEY(`url`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS `FeedItem` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `feedUrl` TEXT NOT NULL, `downloadUrl` TEXT, `articleUrl` TEXT, `pubDate` INTEGER NOT NULL, `fetchDate` INTEGER NOT NULL, `read` INTEGER NOT NULL, FOREIGN KEY(`feedUrl`) REFERENCES `FeedChannel`(`url`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            database.execSQL("CREATE  INDEX `index_FeedItem_feedUrl` ON `FeedItem` (`feedUrl`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
            database.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, \"ecece0a3ce577e3e49f6cb98c1e7d708\")");

            /* Copy from old */
            database.execSQL("ALTER TABLE torrents RENAME TO torrents_old;");
            database.execSQL("INSERT INTO `Torrent` (`id`, `name`, `source`, `downloadPath`, `filePriorities`, `sequentialDownload`, `finished`, `paused`, `downloadingMetadata`, `dateAdded`, `error`) SELECT torrent_id, name, path_to_torrent, path_to_download, file_priorities, is_sequential, is_finished, is_paused, downloading_metadata, datetime, error FROM torrents_old;");
            database.execSQL("DROP TABLE torrents_old;");

            database.execSQL("ALTER TABLE feeds RENAME TO feeds_old;");
            database.execSQL("INSERT INTO `FeedChannel` (`url`, `name`, `lastUpdate`, `autoDownload`, `filter`, `isRegexFilter`, `fetchError`) SELECT url, name, last_update, auto_download, filter, is_regex_filter, fetch_error FROM feeds_old;");
            database.execSQL("DROP TABLE feeds_old;");

            database.execSQL("ALTER TABLE feed_items RENAME TO feed_items_old;");
            database.execSQL("INSERT INTO `FeedItem` (`id`, `title`, `feedUrl`, `downloadUrl`, `articleUrl`, `pubDate`, `fetchDate`, `read`) SELECT _id, title, feed_url, download_url, article_url, pub_date, fetch_date, read FROM feed_items_old;");
            database.execSQL("DROP TABLE feed_items_old;");
        }
    };
}
