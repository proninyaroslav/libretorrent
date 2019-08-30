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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.proninyaroslav.libretorrent.core.FacadeHelper;
import org.proninyaroslav.libretorrent.core.filesystem.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.model.data.entity.FastResume;
import org.proninyaroslav.libretorrent.core.model.data.entity.Torrent;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class DatabaseMigration
{

    public static final class DataModelBefore5
    {
        public static final String TORRENT_FILE_NAME = "torrent";
        public static final String TORRENT_RESUME_FILE_NAME = "fastresume";
        public static final String TORRENT_SESSION_FILE = "session";
    }

    public static Migration[] getMigrations()
    {
        return new Migration[]{MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5};
    }

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database)
        {
            database.execSQL("ALTER TABLE torrents ADD COLUMN downloading_metadata integer ");
            database.execSQL("ALTER TABLE torrents ADD COLUMN datetime integer ");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database)
        {
            database.execSQL("CREATE TABLE feeds(_id integer primary key autoincrement, url text not null unique, name text, last_update integer, auto_download integer, filter text, is_regex_filter integer, fetch_error text);");
            database.execSQL("CREATE TABLE feed_items(_id integer primary key autoincrement, feed_url text, title text not null unique, download_url text, article_url text, pub_date integer, fetch_date integer, read integer );");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database)
        {
            database.execSQL("ALTER TABLE torrents ADD COLUMN error text ");
        }
    };

    /*
     * Migration from old database to Room.
     */

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database)
        {
            /* Create new tablets */
            database.execSQL("CREATE TABLE IF NOT EXISTS `Torrent` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `downloadPath` TEXT NOT NULL, `dateAdded` INTEGER NOT NULL, `error` TEXT, `manuallyPaused` INTEGER NOT NULL, `magnet` TEXT, `downloadingMetadata` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS `FastResume` (`torrentId` TEXT NOT NULL, `data` BLOB NOT NULL, PRIMARY KEY(`torrentId`), FOREIGN KEY(`torrentId`) REFERENCES `Torrent`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            database.execSQL("CREATE TABLE IF NOT EXISTS `FeedChannel` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, `name` TEXT, `lastUpdate` INTEGER NOT NULL, `autoDownload` INTEGER NOT NULL, `filter` TEXT, `isRegexFilter` INTEGER NOT NULL, `fetchError` TEXT)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `FeedItem` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `feedId` INTEGER NOT NULL, `downloadUrl` TEXT, `articleUrl` TEXT, `pubDate` INTEGER NOT NULL, `fetchDate` INTEGER NOT NULL, `read` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`feedId`) REFERENCES `FeedChannel`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            database.execSQL("CREATE INDEX `index_FeedItem_feedId` ON `FeedItem` (`feedId`)");
            database.execSQL("CREATE INDEX `index_FastResume_torrentId` ON `FastResume` (`torrentId`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
            database.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '9a928af8203fc8a72546bb3719135e08')");

            /* Copy from old */
            database.execSQL("ALTER TABLE torrents RENAME TO torrents_old;");
            database.execSQL("INSERT INTO `Torrent` (`id`, `name`, `downloadPath`, `manuallyPaused`, `downloadingMetadata`, `magnet`, `dateAdded`, `error`) SELECT torrent_id, name, 'file://' || path_to_download, 0, downloading_metadata, CASE WHEN downloading_metadata THEN path_to_torrent ELSE NULL END path_to_torrent, datetime, error FROM torrents_old;");
            database.execSQL("DROP TABLE torrents_old;");

            database.execSQL("ALTER TABLE feeds RENAME TO feeds_old;");
            database.execSQL("INSERT INTO `FeedChannel` (`url`, `name`, `lastUpdate`, `autoDownload`, `filter`, `isRegexFilter`, `fetchError`) SELECT url, name, last_update, auto_download, filter, is_regex_filter, fetch_error FROM feeds_old;");
            database.execSQL("DROP TABLE feeds_old;");

            /* Don't import feed items */
            database.execSQL("DROP TABLE feed_items;");
        }
    };

    public static void advancedMigrationTo5(@NonNull Context appContext, @NonNull AppDatabase db)
    {
        copyFastResumeData(appContext, db);
        removeSessionFile(appContext);
    }

    public static void copyFastResumeData(Context appContext, AppDatabase db)
    {
        List<Torrent> torrents = db.torrentDao().getAllTorrents();
        for (Torrent torrent : torrents) {
            if (!torrentDataExists(appContext, torrent.id))
                continue;

            File dataDir = findTorrentDataDir(appContext, torrent.id);
            if (dataDir == null)
                continue;

            try {
                File fastResumeFile = new File(dataDir, DataModelBefore5.TORRENT_RESUME_FILE_NAME);
                if (!fastResumeFile.exists()) {
                    backupTorrent(appContext, new File(dataDir, DataModelBefore5.TORRENT_FILE_NAME));
                    continue;
                }

                FastResume fastResume = new FastResume(torrent.id,
                        org.apache.commons.io.FileUtils.readFileToByteArray(fastResumeFile));

                db.fastResumeDao().add(fastResume);

            } catch (Exception e) {
                backupTorrent(appContext, new File(dataDir, DataModelBefore5.TORRENT_FILE_NAME));

            } finally {
                try {
                    org.apache.commons.io.FileUtils.deleteDirectory(dataDir);

                } catch (IOException e) {
                    /* Ignore */
                }
            }
        }
    }

    private static void removeSessionFile(Context context)
    {
        File dataDir = context.getExternalFilesDir(null);
        if (dataDir != null) {
            File session = new File(dataDir, DataModelBefore5.TORRENT_SESSION_FILE);
            session.delete();
        }
    }

    private static boolean torrentDataExists(Context context, String id)
    {
        FileSystemFacade fs = FacadeHelper.getFileSystemFacade(context);
        File f = new File(context.getExternalFilesDir(null), id);

        return fs.isStorageReadable() && f.exists();
    }

    private static File findTorrentDataDir(Context context, String id)
    {
        FileSystemFacade fs = FacadeHelper.getFileSystemFacade(context);

        if (fs.isStorageReadable()) {
            File dataDir = new File(context.getExternalFilesDir(null), id);
            if (dataDir.exists())
                return dataDir;
        }

        return null;
    }

    private static void backupTorrent(Context context, File torrent)
    {
        if (!torrent.exists())
            return;

        String userDir = FacadeHelper.getFileSystemFacade(context).getUserDirPath();
        if (userDir == null)
            return;

        File backupDir = new File(userDir, "LibreTorrent_backup");
        try {
            org.apache.commons.io.FileUtils.copyFileToDirectory(torrent, backupDir);

        } catch (IOException e) {
            /* Ignore */
        }
    }
}
