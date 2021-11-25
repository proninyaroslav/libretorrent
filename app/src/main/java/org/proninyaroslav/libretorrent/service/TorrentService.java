/*
 * Copyright (C) 2016-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.os.IBinder;
import android.os.PowerManager;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.filter.TorrentFilter;
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;
import org.proninyaroslav.libretorrent.core.model.TorrentEngineListener;
import org.proninyaroslav.libretorrent.core.model.TorrentInfoProvider;
import org.proninyaroslav.libretorrent.core.model.data.TorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.sorting.TorrentSortingComparator;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.receiver.NotificationReceiver;
import org.proninyaroslav.libretorrent.ui.TorrentNotifier;
import org.proninyaroslav.libretorrent.ui.main.MainActivity;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;

public class TorrentService extends Service
{
    private static final String TAG = TorrentService.class.getSimpleName();

    private static final int SERVICE_STARTED_NOTIFICATION_ID = -1;
    private static final int FOREGROUND_NOTIFY_UPDATE_DELAY = 1000; /* ms */
    public static final String ACTION_SHUTDOWN = "org.proninyaroslav.libretorrent.services.TorrentService.ACTION_SHUTDOWN";

    private AtomicBoolean isAlreadyRunning = new AtomicBoolean();
    /* For the pause action button of foreground notify */
    private NotificationCompat.Builder foregroundNotify;
    private Disposable foregroundDisposable;
    private boolean isNetworkOnline = false;
    private TorrentInfoProvider stateProvider;
    private TorrentEngine engine;
    private SettingsRepository pref;
    private PowerManager.WakeLock wakeLock;
    private CompositeDisposable disposables = new CompositeDisposable();
    private AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private AtomicBoolean shutDownNotifyShow = new AtomicBoolean(false);
    private PublishSubject<Boolean> forceSortAndFilter = PublishSubject.create();
    private TorrentFilter itemsFilter;
    private TorrentSortingComparator itemsSorting;
    private BehaviorSubject<Boolean> combinedPauseButtonState =
            BehaviorSubject.createDefault(false);

    PendingIntent shutdownPendingIntent;
    PendingIntent pauseButtonPendingIntent;
    PendingIntent resumeButtonPendingIntent;
    PendingIntent pauseResumePendingIntent;

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public void onCreate()
    {
        pref = RepositoryHelper.getSettingsRepository(getApplicationContext());
        engine = TorrentEngine.getInstance(getApplicationContext());
        stateProvider = TorrentInfoProvider.getInstance(getApplicationContext());

        initPendingIntents();
        makeForegroundNotify();
    }

    private void init()
    {
        Log.i(TAG, "Start " + TAG);

        makeForegroundNotify();
        subscribeCombinedPauseButtonState();
        setFilterAndSorting();
        subscribeForceSortAndFilter();

        disposables.add(pref.observeSettingsChanged()
                .subscribe(this::handleSettingsChanged));

        Utils.enableBootReceiverIfNeeded(getApplicationContext());
        setKeepCpuAwake(pref.cpuDoNotSleep());

        engine.doStart();
        engine.addListener(engineListener);

        startUpdateForegroundNotify();
    }

    private void setFilterAndSorting() {
        itemsFilter = Utils.getForegroundNotifyFilter(this, pref);
        itemsSorting = Utils.getForegroundNotifySorting(this, pref);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        Log.i(TAG, "Stop " + TAG);
    }

    private void shutdown()
    {
        disposables.add(Completable.fromRunnable(this::stopEngine)
                .subscribeOn(Schedulers.computation())
                .subscribe());
    }

    private void stopEngine()
    {
        shuttingDown.set(true);
        forceClearForeground();
        engine.doStop();
    }

    private void stopService()
    {
        disposables.clear();
        engine.removeListener(engineListener);
        setKeepCpuAwake(false);

        isAlreadyRunning.set(false);
        stopForeground(true);
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        String action = null;
        if (intent != null)
            action = intent.getAction();

        /* Handle shutdown actions before init service */
        if (action != null) {
            int ret = handleShutdownActions(action);
            if (ret >= 0)
                return ret;
        }

        /* The first start */
        if (isAlreadyRunning.compareAndSet(false, true))
            init();

        if (action != null)
            handleActions(action);

        return START_STICKY;
    }

    private int handleShutdownActions(String action)
    {
        switch (action) {
            case NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP:
            case ACTION_SHUTDOWN:
                shutdown();
                return START_NOT_STICKY;
        }

        return -1;
    }

    private void handleActions(String action)
    {
        switch (action) {
            case NotificationReceiver.NOTIFY_ACTION_PAUSE_ALL:
                engine.pauseAll();
                break;
            case NotificationReceiver.NOTIFY_ACTION_RESUME_ALL:
                engine.resumeAll();
                break;
            case NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME_ALL:
                boolean paused = combinedPauseButtonState.getValue();
                if (paused) {
                    engine.resumeAll();
                } else {
                    engine.pauseAll();
                }
                combinedPauseButtonState.onNext(!paused);
                break;
        }
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

    private final TorrentEngineListener engineListener = new TorrentEngineListener() {
        @Override
        public void onSessionStopped()
        {
            Log.i(TAG, "Session stopped");
            stopService();
        }
    };

    private void handleSettingsChanged(String key)
    {
        if (key.equals(getString(R.string.pref_key_cpu_do_not_sleep))) {
            setKeepCpuAwake(pref.cpuDoNotSleep());
        } else if (key.equals(getString(R.string.pref_key_foreground_notify_status_filter)) ||
                key.equals(getString(R.string.pref_key_foreground_notify_sorting))) {
            setFilterAndSorting();
            forceSortAndFilter.onNext(true);
        } else if (key.equals(getString(R.string.pref_key_foreground_notify_combined_pause_button))) {
            updateForegroundNotifyActions(pref.foregroundNotifyCombinedPauseButton());
        }
    }

    private void startUpdateForegroundNotify()
    {
        if (shuttingDown.get() || foregroundNotify == null)
            return;

        foregroundDisposable = stateProvider.observeInfoList()
                .subscribeOn(Schedulers.io())
                .flatMapSingle((infoList) ->
                        Flowable.fromIterable(infoList)
                                .filter(itemsFilter)
                                .sorted(itemsSorting)
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .delay(FOREGROUND_NOTIFY_UPDATE_DELAY, TimeUnit.MILLISECONDS)
                .subscribe(this::updateForegroundNotify,
                        (Throwable t) -> Log.e(TAG, "Getting torrents info error: "
                                + Log.getStackTraceString(t))
                );
    }

    private Disposable getInfoListSingle() {
        return stateProvider.getInfoListSingle()
                .subscribeOn(Schedulers.io())
                .flatMap((infoList) ->
                        Observable.fromIterable(infoList)
                                .filter(itemsFilter)
                                .sorted(itemsSorting)
                                .toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::updateForegroundNotify,
                        (Throwable t) -> Log.e(TAG, "Getting torrents info error: "
                                + Log.getStackTraceString(t))
                );
    }

    private void subscribeForceSortAndFilter() {
        disposables.add(forceSortAndFilter
                .filter((force) -> force)
                .observeOn(Schedulers.io())
                .subscribe((force) -> disposables.add(getInfoListSingle())));
    }

    private void subscribeCombinedPauseButtonState() {
        disposables.add(combinedPauseButtonState
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((paused) -> updateForegroundNotifyActions(
                        pref.foregroundNotifyCombinedPauseButton()
                )));
    }

    private void forceClearForeground()
    {
        disposables.add(Completable.fromRunnable(() -> updateForegroundNotify(Collections.emptyList()))
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe());
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
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        foregroundNotify = new NotificationCompat.Builder(getApplicationContext(),
                TorrentNotifier.FOREGROUND_NOTIFY_CHAN_ID)
                .setSmallIcon(R.drawable.ic_app_notification)
                .setContentIntent(startupPendingIntent)
                .setContentTitle(getString(R.string.app_running_in_the_background))
                .setTicker(getString(R.string.app_running_in_the_background))
                .setWhen(System.currentTimeMillis());

        setForegroundNotifyActions(pref.foregroundNotifyCombinedPauseButton());
        foregroundNotify.setCategory(Notification.CATEGORY_SERVICE);

        /* Disallow killing the service process by system */
        startForeground(SERVICE_STARTED_NOTIFICATION_ID, foregroundNotify.build());
    }

    private void updateForegroundNotify(List<TorrentInfo> stateList)
    {
        if ((shuttingDown.get() && shutDownNotifyShow.get()) || foregroundNotify == null)
            return;

        isNetworkOnline = Utils.checkConnectivity(getApplicationContext());

        if (shuttingDown.get()) {
            shutdownPendingIntent.cancel();
            stopUpdateForegroundNotify();

            shutDownNotifyShow.set(true);

            String shuttingDownStr = getString(R.string.notify_shutting_down);
            foregroundNotify.setStyle(null);
            foregroundNotify.setTicker(shuttingDownStr);
            foregroundNotify.setContentTitle(shuttingDownStr);
        } else {
            foregroundNotify.setContentText((isNetworkOnline ?
                    getString(R.string.network_online) :
                    getString(R.string.network_offline)));
            if (stateList.isEmpty())
                foregroundNotify.setStyle(null);
            else
                foregroundNotify.setStyle(makeDetailNotifyInboxStyle(stateList));
        }
        /* Disallow killing the service process by system */
        startForeground(SERVICE_STARTED_NOTIFICATION_ID, foregroundNotify.build());
    }

    private NotificationCompat.InboxStyle makeDetailNotifyInboxStyle(List<TorrentInfo> stateList)
    {
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

        int downloadingCount = 0;

        for (TorrentInfo state : stateList) {
            if (state == null)
                continue;

            String template;
            TorrentStateCode code = state.stateCode;

            if (code == TorrentStateCode.DOWNLOADING) {
                ++downloadingCount;
                inboxStyle.addLine(getString(R.string.downloading_torrent_notify_template,
                        state.progress,
                        (state.ETA == -1) ? Utils.INFINITY_SYMBOL :
                                DateUtils.formatElapsedTime(state.ETA),
                        Formatter.formatFileSize(this, state.downloadSpeed),
                        state.name));

            } else if (code == TorrentStateCode.SEEDING) {
                inboxStyle.addLine(getString(R.string.seeding_torrent_notify_template,
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
                inboxStyle.addLine(getString(R.string.other_torrent_notify_template, stateString, state.name));
            }
        }

        inboxStyle.setBigContentTitle(getString(R.string.torrent_count_notify_template,
                downloadingCount,
                stateList.size()));

        inboxStyle.setSummaryText((isNetworkOnline ?
                getString(R.string.network_online) :
                getString(R.string.network_offline)));

        return inboxStyle;
    }

    private void setForegroundNotifyActions(boolean combinedPauseButton) {
        foregroundNotify.clearActions();

        if (combinedPauseButton) {
            boolean paused = combinedPauseButtonState.getValue();
            foregroundNotify.addAction(
                    new NotificationCompat.Action.Builder(
                            paused ? R.drawable.ic_play_arrow_white_24dp : R.drawable.ic_pause_white_24dp,
                            paused ? getString(R.string.resume_all) : getString(R.string.pause_all),
                            pauseResumePendingIntent
                    ).build()
            );
        } else {
            foregroundNotify.addAction(
                    new NotificationCompat.Action.Builder(
                            R.drawable.ic_pause_white_24dp,
                            getString(R.string.pause_all),
                            pauseButtonPendingIntent
                    ).build()
            );
            foregroundNotify.addAction(
                    new NotificationCompat.Action.Builder(
                            R.drawable.ic_play_arrow_white_24dp,
                            getString(R.string.resume_all),
                            resumeButtonPendingIntent
                    ).build()
            );
        }
        foregroundNotify.addAction(
                new NotificationCompat.Action.Builder(
                        R.drawable.ic_power_settings_new_white_24dp,
                        getString(R.string.shutdown),
                        shutdownPendingIntent
                ).build()
        );
    }

    void updateForegroundNotifyActions(boolean combinedPauseButton) {
        setForegroundNotifyActions(combinedPauseButton);
        startForeground(SERVICE_STARTED_NOTIFICATION_ID, foregroundNotify.build());
    }

    private void initPendingIntents() {
        Intent shutdownIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        shutdownIntent.setAction(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP);
        shutdownPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0,
                shutdownIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent pauseButtonIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        pauseButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_PAUSE_ALL);
        pauseButtonPendingIntent = PendingIntent.getBroadcast(
                getApplicationContext(), 0, pauseButtonIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent resumeButtonIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        resumeButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_RESUME_ALL);
        resumeButtonPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0,
                resumeButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent pauseResumeButtonIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        pauseResumeButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME_ALL);
        pauseResumePendingIntent = PendingIntent.getBroadcast(
                getApplicationContext(), 0, pauseResumeButtonIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

    }
}
