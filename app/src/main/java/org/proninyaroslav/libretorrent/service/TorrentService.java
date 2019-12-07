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

package org.proninyaroslav.libretorrent.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.ui.TorrentNotifier;
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;
import org.proninyaroslav.libretorrent.core.model.TorrentEngineListener;
import org.proninyaroslav.libretorrent.core.model.TorrentInfoProvider;
import org.proninyaroslav.libretorrent.core.model.data.TorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.receiver.NotificationReceiver;
import org.proninyaroslav.libretorrent.ui.main.MainActivity;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class TorrentService extends Service
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentService.class.getSimpleName();

    private static final int SERVICE_STARTED_NOTIFICATION_ID = -1;
    private static final int FOREGROUND_NOTIFY_UPDATE_DELAY = 1000; /* ms */
    public static final String ACTION_SHUTDOWN = "org.proninyaroslav.libretorrent.services.TorrentService.ACTION_SHUTDOWN";

    private boolean isAlreadyRunning;
    /* For the pause action button of foreground notify */
    private NotificationCompat.Builder foregroundNotify;
    private Disposable foregroundDisposable;
    private boolean isNetworkOnline = false;
    private TorrentInfoProvider stateProvider;
    private TorrentEngine engine;
    private SettingsRepository pref;
    private PowerManager.WakeLock wakeLock;
    private Thread shutdownThread;
    private CompositeDisposable disposables = new CompositeDisposable();

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    private void init()
    {
        Log.i(TAG, "Start " + TAG);
        shutdownThread = new Thread() {
            @Override
            public void run()
            {
                stopService();
            }
        };
        pref = RepositoryHelper.getSettingsRepository(getApplicationContext());
        disposables.add(pref.observeSettingsChanged()
                .subscribe(this::handleSettingsChanged));

        Utils.enableBootReceiverIfNeeded(getApplicationContext());
        setKeepCpuAwake(pref.cpuDoNotSleep());

        engine = TorrentEngine.getInstance(getApplicationContext());
        engine.addListener(engineListener);
        if (engine.isRunning())
            engine.loadTorrents();
        else
            engine.start();

        stateProvider = TorrentInfoProvider.getInstance(getApplicationContext());

        makeForegroundNotify();
        startUpdateForegroundNotify();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        Log.i(TAG, "Stop " + TAG);
    }

    private void shutdown()
    {
        if (shutdownThread != null && !shutdownThread.isAlive())
            shutdownThread.start();
    }

    private void stopService()
    {
        disposables.clear();
        stopUpdateForegroundNotify();
        engine.removeListener(engineListener);
        if (engine != null)
            engine.stop();
        isAlreadyRunning = false;
        stateProvider = null;
        engine = null;
        pref = null;
        setKeepCpuAwake(false);

        stopForeground(true);
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        /* The first start */
        if (!isAlreadyRunning) {
            isAlreadyRunning = true;
            init();
        }

        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP:
                case ACTION_SHUTDOWN:
                    shutdown();
                    return START_NOT_STICKY;
                case NotificationReceiver.NOTIFY_ACTION_PAUSE_ALL:
                    engine.pauseAll();
                    break;
                case NotificationReceiver.NOTIFY_ACTION_RESUME_ALL:
                    engine.resumeAll();
                    break;
            }
        }

        return START_STICKY;
    }

    private void setKeepCpuAwake(boolean enable)
    {
        if (enable) {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            }

            if (!wakeLock.isHeld())
                wakeLock.acquire();

        } else {
            if (wakeLock == null)
                return;

            if (wakeLock.isHeld())
                wakeLock.release();
        }
    }

    private void checkShutdown()
    {
        if (pref.shutdownDownloadsComplete() && engine.isTorrentsFinished())
            shutdown();
    }

    private final TorrentEngineListener engineListener = new TorrentEngineListener() {
        @Override
        public void onSessionStarted()
        {
            engine.loadTorrents();
        }

        @Override
        public void onTorrentFinished(@NonNull String id)
        {
            checkShutdown();
        }

        @Override
        public void onParamsApplied(@NonNull String id, Throwable e)
        {
            checkShutdown();
        }

        @Override
        public void onTorrentRemoved(@NonNull String id)
        {
            checkShutdown();
        }
    };

    private void handleSettingsChanged(String key)
    {
        if (key.equals(getString(R.string.pref_key_cpu_do_not_sleep)))
            setKeepCpuAwake(pref.cpuDoNotSleep());
    };

    private void startUpdateForegroundNotify()
    {
        if (foregroundNotify == null)
            return;

        foregroundDisposable = stateProvider.observeInfoList()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .delay(FOREGROUND_NOTIFY_UPDATE_DELAY, TimeUnit.MILLISECONDS)
                .subscribe(this::updateForegroundNotify,
                        (Throwable t) -> Log.e(TAG, "Getting torrents info error: "
                                + Log.getStackTraceString(t))
                );
    }

    private void stopUpdateForegroundNotify()
    {
        if (foregroundDisposable != null)
            foregroundDisposable.dispose();
    }

    private void makeForegroundNotify()
    {
        /* For starting main activity after click */
        Intent startupIntent = new Intent(getApplicationContext(), MainActivity.class);
        startupIntent.setAction(Intent.ACTION_MAIN);
        startupIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        startupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent startupPendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, startupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        isNetworkOnline = Utils.checkConnectivity(getApplicationContext());

        foregroundNotify = new NotificationCompat.Builder(getApplicationContext(),
                TorrentNotifier.FOREGROUND_NOTIFY_CHAN_ID)
                .setSmallIcon(R.drawable.ic_app_notification)
                .setContentIntent(startupPendingIntent)
                .setContentTitle(getString(R.string.app_running_in_the_background))
                .setTicker(getString(R.string.app_running_in_the_background))
                .setContentText((isNetworkOnline ?
                        getString(R.string.network_online) :
                        getString(R.string.network_offline)))
                .setWhen(System.currentTimeMillis());

        foregroundNotify.addAction(makePauseAllAction());
        foregroundNotify.addAction(makeResumeAllAction());
        foregroundNotify.addAction(makeShutdownAction());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            foregroundNotify.setCategory(Notification.CATEGORY_SERVICE);

        /* Disallow killing the service process by system */
        startForeground(SERVICE_STARTED_NOTIFICATION_ID, foregroundNotify.build());
    }

    private void updateForegroundNotify(List<TorrentInfo> stateList)
    {
        if (foregroundNotify == null)
            return;

        isNetworkOnline = Utils.checkConnectivity(getApplicationContext());

        foregroundNotify.setContentText((isNetworkOnline ?
                getString(R.string.network_online) :
                getString(R.string.network_offline)));
        if (stateList.isEmpty())
            foregroundNotify.setStyle(null);
        else
            foregroundNotify.setStyle(makeDetailNotifyInboxStyle(stateList));
        /* Disallow killing the service process by system */
        startForeground(SERVICE_STARTED_NOTIFICATION_ID, foregroundNotify.build());
    }

    private NotificationCompat.InboxStyle makeDetailNotifyInboxStyle(List<TorrentInfo> stateList)
    {
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

        String titleTemplate = getString(R.string.torrent_count_notify_template);

        int downloadingCount = 0;

        for (TorrentInfo state : stateList) {
            if (state == null)
                continue;

            String template;
            TorrentStateCode code = state.stateCode;

            if (code == TorrentStateCode.DOWNLOADING) {
                ++downloadingCount;
                template =  getString(R.string.downloading_torrent_notify_template);
                inboxStyle.addLine(String.format(template,
                        state.progress,
                        (state.ETA == -1) ? Utils.INFINITY_SYMBOL :
                                DateUtils.formatElapsedTime(state.ETA),
                        Formatter.formatFileSize(this, state.downloadSpeed),
                        state.name));

            } else if (code == TorrentStateCode.SEEDING) {
                template = getString(R.string.seeding_torrent_notify_template);
                inboxStyle.addLine(String.format(template,
                        getString(R.string.torrent_status_seeding),
                        Formatter.formatFileSize(this, state.uploadSpeed),
                        state.name));
            } else {
                String stateString = "";

                switch (code) {
                    case PAUSED:
                        stateString = getString(R.string.torrent_status_paused);
                        break;
                    case STOPPED:
                        stateString = getString(R.string.torrent_status_stopped);
                        break;
                    case CHECKING:
                        stateString = getString(R.string.torrent_status_checking);
                        break;
                    case DOWNLOADING_METADATA:
                        stateString = getString(R.string.torrent_status_downloading_metadata);
                }

                template = getString(R.string.other_torrent_notify_template);
                inboxStyle.addLine(String.format(template, stateString, state.name));
            }
        }

        inboxStyle.setBigContentTitle(String.format(
                titleTemplate,
                downloadingCount,
                stateList.size()));

        inboxStyle.setSummaryText((isNetworkOnline ?
                getString(R.string.network_online) :
                getString(R.string.network_offline)));

        return inboxStyle;
    }

    private NotificationCompat.Action makeShutdownAction()
    {
        Intent shutdownIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        shutdownIntent.setAction(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP);
        PendingIntent shutdownPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0,
                shutdownIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_power_settings_new_white_24dp,
                getString(R.string.shutdown),
                shutdownPendingIntent)
                .build();
    }

    private NotificationCompat.Action makePauseAllAction()
    {
        Intent pauseButtonIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        pauseButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_PAUSE_ALL);
        PendingIntent pauseButtonPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0,
                pauseButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(R.drawable.ic_pause_white_24dp,
                getString(R.string.pause_all),
                pauseButtonPendingIntent)
                .build();
    }

    private NotificationCompat.Action makeResumeAllAction()
    {
        Intent resumeButtonIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        resumeButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_RESUME_ALL);
        PendingIntent resumeButtonPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0,
                resumeButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(R.drawable.ic_play_arrow_white_24dp,
                getString(R.string.resume_all),
                resumeButtonPendingIntent)
                .build();
    }
}
