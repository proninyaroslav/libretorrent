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
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.apache.commons.io.FileUtils;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class DatabaseMigration
{
    @SuppressWarnings("unused")
    private static final String TAG = DatabaseMigration.class.getSimpleName();

    static Migration[] getMigrations(@NonNull Context appContext)
    {
        return new Migration[] {
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                new RoomDatabaseMigration(appContext),
                MIGRATION_5_6,
        };
    }

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database)
        {
            database.execSQL("ALTER TABLE torrents ADD COLUMN downloading_metadata integer ");
            database.execSQL("ALTER TABLE torrents ADD COLUMN datetime integer ");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database)
        {
            database.execSQL("CREATE TABLE feeds(_id integer primary key autoincrement, url text not null unique, name text, last_update integer, auto_download integer, filter text, is_regex_filter integer, fetch_error text);");
            database.execSQL("CREATE TABLE feed_items(_id integer primary key autoincrement, feed_url text, title text not null unique, download_url text, article_url text, pub_date integer, fetch_date integer, read integer );");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database)
        {
            database.execSQL("ALTER TABLE torrents ADD COLUMN error text ");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database)
        {
            /* Torrent.VISIBILITY_VISIBLE_NOTIFY_FINISHED */
            database.execSQL("ALTER TABLE `Torrent` ADD COLUMN `visibility` INTEGER NOT NULL DEFAULT 0");
        }
    };

    /*
     * Migration from old database (ver. 4) to Room (ver. 5).
     */

    static class RoomDatabaseMigration extends Migration
    {
        final class OldDataModel
        {
            static final String TORRENT_FILE_NAME = "torrent";
            static final String TORRENT_RESUME_FILE_NAME = "fastresume";
            static final String TORRENT_SESSION_FILE = "session";
        }

        private Context appContext;

        public RoomDatabaseMigration(@NonNull Context appContext)
        {
            super(4, 5);

            this.appContext = appContext;
        }

        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database)
        {
            database.beginTransaction();
            try {
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

                database.setTransactionSuccessful();

            } finally {
                database.endTransaction();
            }

            try {
                advancedMigration(database);

            } catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }

        void advancedMigration(SupportSQLiteDatabase database)
        {
            removeSessionFile();
            copyFastResumeData(database);
        }

        void copyFastResumeData(SupportSQLiteDatabase database)
        {
            List<String> idList = getTorrentIdList(database);

            database.beginTransaction();
            try {
                for (String id : idList) {
                    if (!torrentDataExists(id))
                        continue;

                    File dataDir = findTorrentDataDir(id);
                    if (dataDir == null)
                        continue;

                    try {
                        File fastResumeFile = new File(dataDir, OldDataModel.TORRENT_RESUME_FILE_NAME);
                        if (!fastResumeFile.exists()) {
                            backupTorrent(new File(dataDir, OldDataModel.TORRENT_FILE_NAME));
                            continue;
                        }

                        addFastResume(database,
                                id,
                                FileUtils.readFileToByteArray(fastResumeFile));

                    } catch (Exception e) {
                        backupTorrent(new File(dataDir, OldDataModel.TORRENT_FILE_NAME));

                    } finally {
                        try {
                            FileUtils.deleteDirectory(dataDir);

                        } catch (IOException e) {
                            /* Ignore */
                        }
                    }
                }
                database.setTransactionSuccessful();

            } finally {
                database.endTransaction();
            }
        }

        private void removeSessionFile()
        {
            File dataDir = appContext.getExternalFilesDir(null);
            if (dataDir != null) {
                File session = new File(dataDir, OldDataModel.TORRENT_SESSION_FILE);
                session.delete();
            }
        }

        private boolean torrentDataExists(String id)
        {
            FileSystemFacade fs = SystemFacadeHelper.getFileSystemFacade(appContext);
            File f = new File(appContext.getExternalFilesDir(null), id);

            return fs.isStorageReadable() && f.exists();
        }

        private File findTorrentDataDir(String id)
        {
            FileSystemFacade fs = SystemFacadeHelper.getFileSystemFacade(appContext);

            if (fs.isStorageReadable()) {
                File dataDir = new File(appContext.getExternalFilesDir(null), id);
                if (dataDir.exists())
                    return dataDir;
            }

            return null;
        }

        private void backupTorrent(File torrent)
        {
            if (!torrent.exists())
                return;

            String userDir = SystemFacadeHelper.getFileSystemFacade(appContext).getUserDirPath();
            if (userDir == null)
                return;

            File backupDir = new File(userDir, "LibreTorrent_backup");
            try {
                FileUtils.copyFileToDirectory(torrent, backupDir);

            } catch (Exception e) {
                /* Ignore */
            }
        }

        private List<String> getTorrentIdList(SupportSQLiteDatabase database)
        {
            ArrayList<String> idList = new ArrayList<>();

            Cursor cursor = database.query("SELECT `id` from `Torrent`");
            while (cursor.moveToNext())
                idList.add(cursor.getString(0));

            cursor.close();

            return idList;
        }

        private void addFastResume(SupportSQLiteDatabase database,
                                   String torrentId, byte[] data)
        {
            database.execSQL("INSERT INTO `FastResume` (`torrentId`, `data`) VALUES(?, ?) ",
                    new Object[] { torrentId, data });
        }
    }
}
