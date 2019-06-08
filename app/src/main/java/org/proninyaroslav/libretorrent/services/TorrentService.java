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

package org.proninyaroslav.libretorrent.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.proninyaroslav.libretorrent.MainActivity;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.TorrentEngine;
import org.proninyaroslav.libretorrent.core.TorrentEngineListener;
import org.proninyaroslav.libretorrent.core.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepository;
import org.proninyaroslav.libretorrent.core.utils.FileUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.receivers.NotificationReceiver;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TorrentService extends Service
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentService.class.getSimpleName();

    private static final int SERVICE_STARTED_NOTIFICATION_ID = 1;
    private static final int TORRENTS_MOVED_NOTIFICATION_ID = 2;
    private static final int SESSION_ERROR_NOTIFICATION_ID = 3;
    private static final int NAT_ERROR_NOTIFICATION_ID = 3;
    public static final String FOREGROUND_NOTIFY_CHAN_ID = "org.proninyaroslav.libretorrent.FOREGROUND_NOTIFY_CHAN";
    public static final String DEFAULT_CHAN_ID = "org.proninyaroslav.libretorrent.DEFAULT_CHAN";

    public static final String ACTION_SHUTDOWN = "org.proninyaroslav.libretorrent.services.TorrentService.ACTION_SHUTDOWN";

    private boolean isAlreadyRunning;
    private NotificationManager notifyManager;
    /* For the pause action button of foreground notify */
    private NotificationCompat.Builder foregroundNotify;
    private TorrentRepository repo;
    private TorrentEngine engine;
    private SharedPreferences pref;
    private PowerManager.WakeLock wakeLock;
    private boolean isNetworkOnline = false;
    private AtomicBoolean isPauseButton = new AtomicBoolean(true);
    private Thread shutdownThread;

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
        notifyManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        pref = SettingsManager.getInstance(getApplicationContext()).getPreferences();
        pref.registerOnSharedPreferenceChangeListener(sharedPreferenceListener);

        Utils.enableBootReceiverIfNeeded(getApplicationContext());
        setKeepCpuAwake(pref.getBoolean(getString(R.string.pref_key_cpu_do_not_sleep),
                                        SettingsManager.Default.cpuDoNotSleep));

        engine = TorrentEngine.getInstance(getApplicationContext());
        engine.addListener(engineListener);
        engine.start();

//        makeForegroundNotify();
//        startUpdateForegroundNotify();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        cleanTemp();
        Log.i(TAG, "Stop " + TAG);
    }

    private void cleanTemp()
    {
        try {
            FileUtils.cleanTempDir(getBaseContext());

        } catch (Exception e) {
            Log.e(TAG, "Error during setup of temp directory: ", e);
        }
    }

    private void shutdown()
    {
        if (shutdownThread != null && !shutdownThread.isAlive())
            shutdownThread.start();
    }

    private void stopService()
    {
        pref.unregisterOnSharedPreferenceChangeListener(sharedPreferenceListener);
//        stopUpdateForegroundNotify();
        engine.removeListener(engineListener);
        if (engine != null)
            engine.stop();
        isAlreadyRunning = false;
        engine = null;
        repo = null;
        pref = null;
        setKeepCpuAwake(false);

        stopForeground(true);
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        /* Clear old notifications */
        try {
            NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (manager != null)
                manager.cancelAll();
        } catch (SecurityException e) {
            /* Ignore */
        }

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
                case NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME:
                    boolean pause = isPauseButton.getAndSet(!isPauseButton.get());
//                    updateForegroundNotifyActions();
                    if (pause)
                        engine.pauseAll();
                    else
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
        if (pref.getBoolean(getString(R.string.pref_key_shutdown_downloads_complete),
                            SettingsManager.Default.shutdownDownloadsComplete) && engine.isTorrentsFinished())
            shutdown();
    }

//    private Runnable updateForegroundNotify = new Runnable()
//    {
//        @Override
//        public void run()
//        {
//            if (isAlreadyRunning) {
//                boolean online = TorrentEngine.getInstance().isConnected();
//                if (isNetworkOnline != online) {
//                    isNetworkOnline = online;
//                    needsUpdateNotify.set(true);
//                }
//
//                if (needsUpdateNotify.get()) {
//                    try {
//                        needsUpdateNotify.set(false);
//                        if (foregroundNotify != null) {
//                            foregroundNotify.setContentText((isNetworkOnline ?
//                                    getString(R.string.network_online) :
//                                    getString(R.string.network_offline)));
//                            if (!TorrentEngine.getInstance().hasTasks())
//                                foregroundNotify.setStyle(makeDetailNotifyInboxStyle());
//                            else
//                                foregroundNotify.setStyle(null);
//                            /* Disallow killing the service process by system */
//                            startForeground(SERVICE_STARTED_NOTIFICATION_ID, foregroundNotify.build());
//                        }
//
//                    } catch (Exception e) {
//                            /* Ignore */
//                    }
//                }
//            }
//            updateForegroundNotifyHandler.postDelayed(this, SYNC_TIME);
//        }
//    };
//
//    private void startUpdateForegroundNotify()
//    {
//        if (updateForegroundNotifyHandler != null ||
//                Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
//            return;
//        }
//
//        updateForegroundNotifyHandler = new Handler();
//        updateForegroundNotifyHandler.postDelayed(updateForegroundNotify, SYNC_TIME);
//    }
//
//    private void stopUpdateForegroundNotify()
//    {
//        if (updateForegroundNotifyHandler == null)
//            return;
//
//        updateForegroundNotifyHandler.removeCallbacks(updateForegroundNotify);
//    }
//
//    private void updateForegroundNotifyActions()
//    {
//        if (foregroundNotify == null)
//            return;
//
//        foregroundNotify.mActions.clear();
//        foregroundNotify.addAction(makeFuncButtonAction());
//        foregroundNotify.addAction(makeShutdownAction());
//        startForeground(SERVICE_STARTED_NOTIFICATION_ID, foregroundNotify.build());
//    }
//
//    private void makeForegroundNotify() {
//        /* For starting main activity */
//        Intent startupIntent = new Intent(getApplicationContext(), MainActivity.class);
//        startupIntent.setAction(Intent.ACTION_MAIN);
//        startupIntent.addCategory(Intent.CATEGORY_LAUNCHER);
//        startupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
//
//        PendingIntent startupPendingIntent =
//                PendingIntent.getActivity(
//                        getApplicationContext(),
//                        0,
//                        startupIntent,
//                        PendingIntent.FLAG_UPDATE_CURRENT);
//
//        foregroundNotify = new NotificationCompat.Builder(getApplicationContext(),
//                FOREGROUND_NOTIFY_CHAN_ID)
//                .setSmallIcon(R.drawable.ic_app_notification)
//                .setContentIntent(startupPendingIntent)
//                .setContentTitle(getString(R.string.app_running_in_the_background))
//                .setTicker(getString(R.string.app_running_in_the_background))
//                .setContentText((isNetworkOnline ?
//                        getString(R.string.network_online) :
//                        getString(R.string.network_offline)))
//                .setWhen(System.currentTimeMillis());
//
//        foregroundNotify.addAction(makeFuncButtonAction());
//        foregroundNotify.addAction(makeShutdownAction());
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
//            foregroundNotify.setCategory(Notification.CATEGORY_SERVICE);
//
//        /* Disallow killing the service process by system */
//        startForeground(SERVICE_STARTED_NOTIFICATION_ID, foregroundNotify.build());
//    }
//
//    /*
//     * For calling add torrent dialog or pause/resume torrents
//     */
//    private NotificationCompat.Action makeFuncButtonAction()
//    {
//        Intent funcButtonIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
//        int type = pref.getInt(getString(R.string.pref_key_foreground_notify_func_button),
//                               SettingsManager.Default.funcButton(getApplicationContext()));
//        int icon = 0;
//        String text = null;
//        if (type == Integer.parseInt(getString(R.string.pref_function_button_pause_value))) {
//            funcButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME);
//            boolean isPause = isPauseButton.get();
//            icon = (isPause ? R.drawable.ic_pause_white_24dp : R.drawable.ic_play_arrow_white_24dp);
//            text = (isPause ? getString(R.string.pause_torrent) : getString(R.string.resume_torrent));
//        } else if (type == Integer.parseInt(getString(R.string.pref_function_button_add_value))) {
//            funcButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_ADD_TORRENT);
//            icon = R.drawable.ic_add_white_36dp;
//            text = getString(R.string.add);
//        }
//        PendingIntent funcButtonPendingIntent =
//                PendingIntent.getBroadcast(
//                        getApplicationContext(),
//                        0,
//                        funcButtonIntent,
//                        PendingIntent.FLAG_UPDATE_CURRENT);
//
//        return new NotificationCompat.Action.Builder(icon, text, funcButtonPendingIntent).build();
//    }
//
//    /*
//     * For shutdown activity and service
//     */
//    private NotificationCompat.Action makeShutdownAction()
//    {
//        Intent shutdownIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
//        shutdownIntent.setAction(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP);
//        PendingIntent shutdownPendingIntent =
//                PendingIntent.getBroadcast(
//                        getApplicationContext(),
//                        0,
//                        shutdownIntent,
//                        PendingIntent.FLAG_UPDATE_CURRENT);
//
//        return new NotificationCompat.Action.Builder(
//                R.drawable.ic_power_settings_new_white_24dp,
//                getString(R.string.shutdown),
//                shutdownPendingIntent)
//                .build();
//    }
//
//    private NotificationCompat.InboxStyle makeDetailNotifyInboxStyle()
//    {
//        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
//
//        String titleTemplate = getString(R.string.torrent_count_notify_template);
//
//        int downloadingCount = 0;
//
//        for (TorrentDownload task : TorrentEngine.getInstance().getTasks()) {
//            if (task == null) {
//                continue;
//            }
//
//            String template;
//            TorrentStateCode code = task.getStateCode();
//
//            if (code == TorrentStateCode.DOWNLOADING) {
//                ++downloadingCount;
//                template =  getString(R.string.downloading_torrent_notify_template);
//                inboxStyle.addLine(
//                        String.format(
//                                template,
//                                task.getProgress(),
//                                (task.getETA() == -1) ? Utils.INFINITY_SYMBOL :
//                                        DateUtils.formatElapsedTime(task.getETA()),
//                                Formatter.formatFileSize(this, task.getDownloadSpeed()),
//                                task.getTorrent().getName()));
//
//            } else if (code == TorrentStateCode.SEEDING) {
//                template = getString(R.string.seeding_torrent_notify_template);
//                inboxStyle.addLine(
//                        String.format(
//                                template,
//                                getString(R.string.torrent_status_seeding),
//                                Formatter.formatFileSize(this, task.getUploadSpeed()),
//                                task.getTorrent().getName()));
//            } else {
//                String stateString = "";
//
//                switch (task.getStateCode()) {
//                    case PAUSED:
//                        stateString = getString(R.string.torrent_status_paused);
//                        break;
//                    case STOPPED:
//                        stateString = getString(R.string.torrent_status_stopped);
//                        break;
//                    case CHECKING:
//                        stateString = getString(R.string.torrent_status_checking);
//                        break;
//                    case DOWNLOADING_METADATA:
//                        stateString = getString(R.string.torrent_status_downloading_metadata);
//                }
//
//                template = getString(R.string.other_torrent_notify_template);
//                inboxStyle.addLine(
//                        String.format(
//                                template,
//                                stateString,
//                                task.getTorrent().getName()));
//            }
//        }
//
//        inboxStyle.setBigContentTitle(String.format(
//                titleTemplate,
//                downloadingCount,
//                TorrentEngine.getInstance().tasksCount()));
//
//        inboxStyle.setSummaryText((isNetworkOnline ?
//            getString(R.string.network_online) :
//            getString(R.string.network_offline)));
//
//        return inboxStyle;
//    }
//
//    private void makeFinishNotify(Torrent torrent)
//    {
//        if (torrent == null || !pref.getBoolean(getString(R.string.pref_key_torrent_finish_notify),
//                                                SettingsManager.Default.torrentFinishNotify))
//            return;
//
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
//                DEFAULT_CHAN_ID)
//                .setSmallIcon(R.drawable.ic_done_white_24dp)
//                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
//                .setContentTitle(getString(R.string.torrent_finished_notify))
//                .setTicker(getString(R.string.torrent_finished_notify))
//                .setContentText(torrent.getName())
//                .setWhen(System.currentTimeMillis());
//
//        if (pref.getBoolean(getString(R.string.pref_key_play_sound_notify),
//                            SettingsManager.Default.playSoundNotify)) {
//            Uri sound = Uri.parse(pref.getString(getString(R.string.pref_key_notify_sound),
//                                                 SettingsManager.Default.notifySound));
//            builder.setSound(sound);
//        }
//
//        if (pref.getBoolean(getString(R.string.pref_key_vibration_notify),
//                            SettingsManager.Default.vibrationNotify))
//            builder.setVibrate(new long[] {1000}); /* ms */
//
//        if (pref.getBoolean(getString(R.string.pref_key_led_indicator_notify),
//                            SettingsManager.Default.ledIndicatorNotify)) {
//            int color = pref.getInt(getString(R.string.pref_key_led_indicator_color_notify),
//                                    SettingsManager.Default.ledIndicatorColorNotify(getApplicationContext()));
//            builder.setLights(color, 1000, 1000); /* ms */
//        }
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
//            builder.setCategory(Notification.CATEGORY_STATUS);
//
//        notifyManager.notify(torrent.getId().hashCode(), builder.build());
//    }
//
//    private void makeTorrentErrorNotify(String name, String message)
//    {
//        if (name == null || message == null)
//            return;
//
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
//                DEFAULT_CHAN_ID)
//                .setSmallIcon(R.drawable.ic_error_white_24dp)
//                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
//                .setContentTitle(name)
//                .setTicker(getString(R.string.torrent_error_notify_title))
//                .setContentText(String.format(getString(R.string.error_template), message))
//                .setAutoCancel(true)
//                .setWhen(System.currentTimeMillis());
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
//            builder.setCategory(Notification.CATEGORY_ERROR);
//
//        notifyManager.notify(name.hashCode(), builder.build());
//    }
//
//    private void makeSessionErrorNotify(String message)
//    {
//        if (message == null)
//            return;
//
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
//                DEFAULT_CHAN_ID)
//                .setSmallIcon(R.drawable.ic_error_white_24dp)
//                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
//                .setContentTitle(getString(R.string.session_error_title))
//                .setTicker(getString(R.string.session_error_title))
//                .setContentText(String.format(getString(R.string.error_template), message))
//                .setAutoCancel(true)
//                .setWhen(System.currentTimeMillis());
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
//            builder.setCategory(Notification.CATEGORY_ERROR);
//
//        notifyManager.notify(SESSION_ERROR_NOTIFICATION_ID, builder.build());
//    }
//
//    private void makeNatErrorNotify(String message)
//    {
//        if (message == null)
//            return;
//
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
//                DEFAULT_CHAN_ID)
//                .setSmallIcon(R.drawable.ic_error_white_24dp)
//                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
//                .setContentTitle(getString(R.string.nat_error_title))
//                .setTicker(getString(R.string.nat_error_title))
//                .setContentText(String.format(getString(R.string.error_template), message))
//                .setAutoCancel(true)
//                .setWhen(System.currentTimeMillis());
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
//            builder.setCategory(Notification.CATEGORY_ERROR);
//
//        notifyManager.notify(NAT_ERROR_NOTIFICATION_ID, builder.build());
//    }
//
//    private void makeTorrentInfoNotify(String name, String message)
//    {
//        if (name == null || message == null)
//            return;
//
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
//                DEFAULT_CHAN_ID)
//                .setSmallIcon(R.drawable.ic_info_white_24dp)
//                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
//                .setContentTitle(name)
//                .setTicker(message)
//                .setContentText(message)
//                .setAutoCancel(true)
//                .setWhen(System.currentTimeMillis());
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
//            builder.setCategory(Notification.CATEGORY_STATUS);
//
//        notifyManager.notify(name.hashCode(), builder.build());
//    }
//
//    private void makeTorrentAddedNotify(String name)
//    {
//        if (name == null)
//            return;
//
//        String title = getString(R.string.torrent_added_notify);
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
//                DEFAULT_CHAN_ID)
//                .setSmallIcon(R.drawable.ic_done_white_24dp)
//                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
//                .setContentTitle(title)
//                .setTicker(title)
//                .setContentText(name)
//                .setAutoCancel(true)
//                .setWhen(System.currentTimeMillis());
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
//            builder.setCategory(Notification.CATEGORY_STATUS);
//
//        notifyManager.notify(name.hashCode(), builder.build());
//    }
//
//    private synchronized void makeTorrentsMoveNotify()
//    {
//        if (torrentsMoveTotal == null ||
//                torrentsMoveSuccess == null ||
//                torrentsMoveFailed == null) {
//            return;
//        }
//
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
//                DEFAULT_CHAN_ID);
//
//        String resultTemplate = getString(R.string.torrents_moved_content);
//        int successfully = torrentsMoveSuccess.size();
//        int failed = torrentsMoveFailed.size();
//
//        builder.setContentTitle(getString(R.string.torrents_moved_title))
//                .setTicker(getString(R.string.torrents_moved_title))
//                .setContentText(String.format(resultTemplate, successfully, failed));
//
//        builder.setSmallIcon(R.drawable.ic_folder_move_white_24dp)
//                .setAutoCancel(true)
//                .setWhen(System.currentTimeMillis())
//                .setStyle(makeTorrentsMoveInboxStyle(torrentsMoveSuccess, torrentsMoveFailed));
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            builder.setCategory(Notification.CATEGORY_STATUS);
//        }
//
//        notifyManager.notify(TORRENTS_MOVED_NOTIFICATION_ID, builder.build());
//
//        torrentsMoveTotal = null;
//        torrentsMoveSuccess = null;
//        torrentsMoveFailed = null;
//    }
//
//    private NotificationCompat.InboxStyle makeTorrentsMoveInboxStyle(List<String> torrentsMoveSuccess,
//                                                                     List<String> torrentsMoveFailed)
//    {
//        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
//
//        boolean successNotEmpty = !torrentsMoveSuccess.isEmpty();
//
//        if (successNotEmpty) {
//            inboxStyle.addLine(getString(R.string.torrents_move_inbox_successfully));
//            for (String name : torrentsMoveSuccess) {
//                inboxStyle.addLine(name);
//            }
//        }
//
//        if (!torrentsMoveFailed.isEmpty()) {
//            if (successNotEmpty) {
//                inboxStyle.addLine("\n");
//            }
//
//            inboxStyle.addLine(getString(R.string.torrents_move_inbox_failed));
//            for (String name : torrentsMoveFailed) {
//                inboxStyle.addLine(name);
//            }
//        }
//
//        return inboxStyle;
//    }

    private final TorrentEngineListener engineListener = new TorrentEngineListener() {
        @Override
        public void onTorrentFinished(String id)
        {
            checkShutdown();
        }

        @Override
        public void onParamsApplied(String id, Throwable e)
        {
            checkShutdown();
        }

        @Override
        public void onTorrentRemoved(String id)
        {
            checkShutdown();
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceListener = (sharedPreferences, key) -> {
        if (key.equals(getString(R.string.pref_key_cpu_do_not_sleep)))
            setKeepCpuAwake(sharedPreferences.getBoolean(key, SettingsManager.Default.cpuDoNotSleep));
//
//        else if (key.equals(getString(R.string.pref_key_foreground_notify_func_button)))
//            updateForegroundNotifyActions();
    };
}
