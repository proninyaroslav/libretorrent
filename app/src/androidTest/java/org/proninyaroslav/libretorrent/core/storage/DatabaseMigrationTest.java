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

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.proninyaroslav.libretorrent.core.model.data.entity.FastResume;
import org.proninyaroslav.libretorrent.core.model.data.entity.FeedChannel;
import org.proninyaroslav.libretorrent.core.model.data.entity.Torrent;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationTest
{
    private static final String TEST_DATABASE_NAME = "libretorrent_test.db";

    private Context context = ApplicationProvider.getApplicationContext();
    @Rule
    public MigrationTestHelper helper= new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class.getCanonicalName(),
            new FrameworkSQLiteOpenHelperFactory());

    private static final String torrentUrl = "http://www.pcds.fi/downloads/applications/internet/browsers/midori/current/debian-ubuntu/midori_0.5.11-0_amd64_.deb.torrent";
    private static final String torrentName = "midori_0.5.11-0_amd64_.deb";
    private static final String torrentHash = "3fe5f1a11c51cd01fd09a79621e074dda8eb36b6";
    private static final String magnet = "magnet:?xt=urn:btih:QWJC7PXG3TS6F5KJDYLLZXM6NZBHXJNK";
    private static final String magnetHash = "85922fbee6dce5e2f5491e16bcdd9e6e427ba5aa";

    private static final String feedUrl = "https://example.org";
    private static final String feedName = "Example";
    private static final String feedFilter = "Example Item 1";

    private FileSystemFacade fs;

    @Before
    public void init()
    {
        fs = SystemFacadeHelper.getFileSystemFacade(context);
    }

    @Test
    public void testMigration4to5_Torrent() throws IOException
    {
        File dataDir1 = new File(context.getExternalFilesDir(null), torrentHash);
        if (!dataDir1.exists())
            assertTrue(dataDir1.mkdir());
        File torrentFile1 = new File(dataDir1, DatabaseMigration.RoomDatabaseMigration.OldDataModel.TORRENT_FILE_NAME);
        org.apache.commons.io.FileUtils.writeByteArrayToFile(torrentFile1, downloadTorrent(torrentUrl));

        File fastResumeFile1 = new File(dataDir1, DatabaseMigration.RoomDatabaseMigration.OldDataModel.TORRENT_RESUME_FILE_NAME);
        byte[] fastResumeData1 = org.apache.commons.io.FileUtils.readFileToByteArray(torrentFile1);
        org.apache.commons.io.FileUtils.writeByteArrayToFile(fastResumeFile1, fastResumeData1);

        SQLiteDatabase sqliteDb = new OldDatabaseHelper().getWritableDatabase();

        ContentValues values1 = new ContentValues();
        values1.put("torrent_id", torrentHash);
        values1.put("name", torrentName);
        values1.put("path_to_download", fs.getDefaultDownloadPath());
        values1.put("file_priorities", 0);
        values1.put("is_paused", true); /* It's not imported from old db */
        values1.put("downloading_metadata", false);
        values1.put("path_to_torrent", torrentFile1.getAbsolutePath());
        values1.put("datetime", System.currentTimeMillis());
        addTorrent(sqliteDb, values1);

        ContentValues values2 = new ContentValues();
        values2.put("torrent_id", magnetHash);
        values2.put("name", magnetHash);
        values2.put("path_to_download", fs.getDefaultDownloadPath());
        values2.put("file_priorities", 0);
        values2.put("downloading_metadata", true);
        values2.put("path_to_torrent", magnet);
        values2.put("datetime", System.currentTimeMillis());
        addTorrent(sqliteDb, values2);

        sqliteDb.close();

        helper.runMigrationsAndValidate(TEST_DATABASE_NAME, 6, true,
                DatabaseMigration.MIGRATION_1_2,
                DatabaseMigration.MIGRATION_2_3,
                DatabaseMigration.MIGRATION_3_4,
                DatabaseMigration.MIGRATION_5_6,
                new DatabaseMigration.RoomDatabaseMigration(context));

        AppDatabase db = getMigratedRoomDatabase();

        assertFalse(dataDir1.exists());

        Torrent torrent1 = db.torrentDao().getTorrentById(torrentHash);
        FastResume fastResume1 = db.fastResumeDao().getByTorrentId(torrentHash);
        assertNotNull(torrent1);
        assertNotNull(fastResume1);

        assertEquals(torrentHash, torrent1.id);
        assertEquals(torrentName, torrent1.name);
        assertEquals("file://" + fs.getDefaultDownloadPath(), torrent1.downloadPath.toString());
        assertFalse(torrent1.manuallyPaused); /* It's not imported from old db */
        assertFalse(torrent1.downloadingMetadata);
        assertNull(torrent1.getMagnet());
        assertEquals(Torrent.VISIBILITY_VISIBLE_NOTIFY_FINISHED, torrent1.visibility);

        assertEquals(fastResume1.torrentId, torrent1.id);
        assertArrayEquals(fastResume1.data, fastResumeData1);

        Torrent torrent2 = db.torrentDao().getTorrentById(magnetHash);
        FastResume fastResume2 = db.fastResumeDao().getByTorrentId(magnetHash);
        assertNotNull(torrent2);
        assertNull(fastResume2);

        assertEquals(magnetHash, torrent2.id);
        assertEquals(magnetHash, torrent2.name);
        assertEquals("file://" + fs.getDefaultDownloadPath(), torrent2.downloadPath.toString());
        assertTrue(torrent2.downloadingMetadata);
        assertEquals(magnet, torrent2.getMagnet());
        assertEquals(Torrent.VISIBILITY_VISIBLE_NOTIFY_FINISHED, torrent2.visibility);
    }

    @Test
    public void testMigration4to5_Feed() throws IOException
    {
        SQLiteDatabase sqliteDb = new OldDatabaseHelper().getWritableDatabase();

        long currTime = System.currentTimeMillis();

        ContentValues feedValues = new ContentValues();
        feedValues.put("url", feedUrl);
        feedValues.put("name", feedName);
        feedValues.put("last_update", currTime);
        feedValues.put("auto_download", true);
        feedValues.put("filter", feedFilter);
        feedValues.put("is_regex_filter", false);
        addFeedChannel(sqliteDb, feedValues);

        sqliteDb.close();

        helper.runMigrationsAndValidate(TEST_DATABASE_NAME, 6, true,
                DatabaseMigration.MIGRATION_1_2,
                DatabaseMigration.MIGRATION_2_3,
                DatabaseMigration.MIGRATION_3_4,
                DatabaseMigration.MIGRATION_5_6,
                new DatabaseMigration.RoomDatabaseMigration(context));

        AppDatabase db = getMigratedRoomDatabase();

        List<FeedChannel> channelList = db.feedDao().getAllFeeds();
        assertEquals(1, channelList.size());

        FeedChannel channel = channelList.get(0);
        assertNotNull(channel);

        assertEquals(feedUrl, channel.url);
        assertEquals(feedName, channel.name);
        assertEquals(currTime, channel.lastUpdate);
        assertTrue(channel.autoDownload);
        assertEquals(feedFilter, channel.filter);
        assertFalse(channel.isRegexFilter);
    }

    private void addTorrent(SQLiteDatabase sqliteDb, ContentValues values)
    {
        assertNotEquals(sqliteDb.replace("torrents", null, values), -1);
    }

    private AppDatabase getMigratedRoomDatabase()
    {
        AppDatabase db = Room.databaseBuilder(context,
                AppDatabase.class, TEST_DATABASE_NAME)
                .addMigrations(DatabaseMigration.getMigrations(context))
                .build();
        /* Close the database and release any stream resources when the test finishes */
        helper.closeWhenFinished(db);

        return db;
    }

    private byte[] downloadTorrent(String url)
    {
        byte[] response = null;
        try {
            response = Utils.fetchHttpUrl(context, url);

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }

        return response;
    }

    private void addFeedChannel(SQLiteDatabase sqliteDb, ContentValues values)
    {
        assertNotEquals(sqliteDb.replace("feeds", null, values), -1);
    }

    private class OldDatabaseHelper extends SQLiteOpenHelper
    {
        public OldDatabaseHelper()
        {
            super(context, TEST_DATABASE_NAME, null, 4);
        }

        @Override
        public void onCreate(SQLiteDatabase db)
        {
            db.execSQL("create table torrents(_id integer primary key autoincrement, torrent_id text not null unique, name text not null, path_to_torrent text not null, path_to_download text not null, file_priorities text not null, is_sequential integer, is_finished integer, is_paused integer, downloading_metadata integer, datetime integer, error text);");
            db.execSQL("create table feeds(_id integer primary key autoincrement, url text not null unique, name text, last_update integer, auto_download integer, filter text, is_regex_filter integer, fetch_error text);");
            db.execSQL("create table feed_items(_id integer primary key autoincrement, feed_url text, title text not null unique, download_url text, article_url text, pub_date integer, fetch_date integer, read integer );");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    }
}