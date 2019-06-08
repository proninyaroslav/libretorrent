/*
 * Copyright (C) 2016-2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.app.Application;
import android.app.NotificationManager;

import androidx.annotation.VisibleForTesting;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.greenrobot.eventbus.EventBus;
import org.proninyaroslav.libretorrent.core.TorrentEngine;
import org.proninyaroslav.libretorrent.core.TorrentStateProvider;
import org.proninyaroslav.libretorrent.core.storage.AppDatabase;
import org.proninyaroslav.libretorrent.core.storage.FeedRepository;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepository;
import org.proninyaroslav.libretorrent.core.utils.Utils;

@ReportsCrashes(mailTo = "proninyaroslav@mail.ru",
                mode = ReportingInteractionMode.DIALOG,
                reportDialogClass = ErrorReportActivity.class)

public class MainApplication extends Application
{
    @SuppressWarnings("unused")
    private static final String TAG = MainApplication.class.getSimpleName();

//    private TorrentNotifier torrentNotifier;
    private AppDatabase db;

    @Override
    public void onCreate()
    {
        super.onCreate();

        Utils.migrateTray2SharedPreferences(this);
        ACRA.init(this);
        EventBus.builder().logNoSubscriberMessages(false).installDefaultEventBus();

        db = AppDatabase.getInstance(this);
        Utils.makeNotifyChans(this, (NotificationManager)getSystemService(NOTIFICATION_SERVICE));

        TorrentEngine.getInstance(this).start();

//        torrentNotifier = new TorrentNotifier(this, getTorrentRepository());
//        torrentNotifier.startUpdate();
    }

    public AppDatabase getDatabase()
    {
        return db;
    }

    @VisibleForTesting
    public void setDatabase(AppDatabase db)
    {
        this.db = db;
    }

//    public TorrentNotifier getTorrentNotifier()
//    {
//        return torrentNotifier;
//    }

    public TorrentRepository getTorrentRepository()
    {
        return TorrentRepository.getInstance(this, db);
    }

    public TorrentStateProvider getTorrentStateProvider()
    {
        return TorrentStateProvider.getInstance(TorrentEngine.getInstance(this));
    }

    public FeedRepository getFeedRepository()
    {
        return FeedRepository.getInstance(db);
    }
}