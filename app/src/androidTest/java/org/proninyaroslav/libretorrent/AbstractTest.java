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

package org.proninyaroslav.libretorrent;

import android.Manifest;
import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;
import org.proninyaroslav.libretorrent.core.model.TorrentInfoProvider;
import org.proninyaroslav.libretorrent.core.storage.AppDatabase;
import org.proninyaroslav.libretorrent.core.storage.FeedRepository;
import org.proninyaroslav.libretorrent.core.storage.FeedRepositoryImpl;
import org.proninyaroslav.libretorrent.core.storage.TagRepository;
import org.proninyaroslav.libretorrent.core.storage.TagRepositoryImpl;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepository;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepositoryImpl;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.GrantPermissionRule;

public class AbstractTest
{
    @Rule
    public GrantPermissionRule runtimePermissionRule = GrantPermissionRule.grant(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE);

    protected Context context;
    protected AppDatabase db;
    protected TorrentEngine engine;
    protected TorrentInfoProvider stateProvider;
    protected TorrentRepository torrentRepo;
    protected FeedRepository feedRepo;
    protected FileSystemFacade fs;
    protected TagRepository tagRepo;

    @Before
    public void init()
    {
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context,
                AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        torrentRepo = new TorrentRepositoryImpl(context, db);
        feedRepo = new FeedRepositoryImpl(context, db);
        engine = TorrentEngine.getInstance(context);
        tagRepo = new TagRepositoryImpl(db);
        stateProvider = TorrentInfoProvider.getInstance(engine, tagRepo);
        fs = SystemFacadeHelper.getFileSystemFacade(context);
    }

    @After
    public void finish()
    {
        db.close();
    }
}
