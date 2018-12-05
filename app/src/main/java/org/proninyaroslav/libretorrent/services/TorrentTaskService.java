/*
 * Copyright (C) 2016-2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.libtorrent4j.Priority;
import org.libtorrent4j.TorrentInfo;
import org.libtorrent4j.swig.settings_pack;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.proninyaroslav.libretorrent.MainActivity;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.AddTorrentParams;
import org.proninyaroslav.libretorrent.core.ProxySettingsPack;
import org.proninyaroslav.libretorrent.core.Torrent;
import org.proninyaroslav.libretorrent.core.TorrentDownload;
import org.proninyaroslav.libretorrent.core.TorrentEngine;
import org.proninyaroslav.libretorrent.core.TorrentEngineCallback;
import org.proninyaroslav.libretorrent.core.TorrentFileObserver;
import org.proninyaroslav.libretorrent.core.TorrentHelper;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.exceptions.DecodeException;
import org.proninyaroslav.libretorrent.core.exceptions.FileAlreadyExistsException;
import org.proninyaroslav.libretorrent.core.exceptions.FreeSpaceException;
import org.proninyaroslav.libretorrent.core.server.TorrentStreamServer;
import org.proninyaroslav.libretorrent.core.stateparcel.BasicStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.StateParcelCache;
import org.proninyaroslav.libretorrent.core.TorrentStateMsg;
import org.proninyaroslav.libretorrent.receivers.TorrentTaskServiceReceiver;
import org.proninyaroslav.libretorrent.core.storage.TorrentStorage;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.receivers.NotificationReceiver;
import org.proninyaroslav.libretorrent.receivers.PowerReceiver;
import org.proninyaroslav.libretorrent.receivers.WifiReceiver;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TorrentTaskService extends Service
        implements
        TorrentEngineCallback,
        SharedPreferences.OnSharedPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentTaskService.class.getSimpleName();

    private static final int SERVICE_STARTED_NOTIFICATION_ID = 1;
    private static final int TORRENTS_MOVED_NOTIFICATION_ID = 2;
    private static final int SESSION_ERROR_NOTIFICATION_ID = 3;
    private static final int NAT_ERROR_NOTIFICATION_ID = 3;
    public static final String FOREGROUND_NOTIFY_CHAN_ID = "org.proninyaroslav.libretorrent.FOREGROUND_NOTIFY_CHAN";
    public static final String DEFAULT_CHAN_ID = "org.proninyaroslav.libretorrent.DEFAULT_CHAN";
    public static final String ACTION_SHUTDOWN = "org.proninyaroslav.libretorrent.services.TorrentTaskService.ACTION_SHUTDOWN";
    public static final String ACTION_ADD_TORRENT = "org.proninyaroslav.libretorrent.services.TorrentTaskService.ACTION_ADD_TORRENT";
    public static final String ACTION_ADD_TORRENT_LIST = "org.proninyaroslav.libretorrent.services.TorrentTaskService.ACTION_ADD_TORRENT_LIST";
    public static final String ACTION_MOVE_TORRENT = "org.proninyaroslav.libretorrent.services.TorrentTaskService.ACTION_MOVE_TORRENT";
    public static final String TAG_ADD_TORRENT_PARAMS = "add_torrent_params";
    public static final String TAG_ADD_TORRENT_PARAMS_LIST = "add_torrent_params_list";
    public static final String TAG_SAVE_TORRENT_FILE = "save_torrnet_file";
    public static final String TAG_ID_LIST = "id_list";
    public static final String TAG_DOWNLOAD_PATH = "download_path";
    private static final int SYNC_TIME = 1000; /* ms */

    private boolean isAlreadyRunning;
    private NotificationManager notifyManager;
    /* For the pause action button of foreground notify */
    private Handler updateForegroundNotifyHandler;
    private NotificationCompat.Builder foregroundNotify;
    private final IBinder binder = new LocalBinder();
    private TorrentStorage repo;
    private SharedPreferences pref;
    private PowerManager.WakeLock wakeLock;
    private PowerReceiver powerReceiver = new PowerReceiver();
    private WifiReceiver wifiReceiver = new WifiReceiver();
    /* Pause torrents (including new added) when in power settings are set power save flags */
    private AtomicBoolean pauseTorrents = new AtomicBoolean(false);
    /* Reduces sending packets due skip cache duplicates */
    private StateParcelCache<BasicStateParcel> basicStateCache = new StateParcelCache<>();
    private AtomicBoolean needsUpdateNotify = new AtomicBoolean(false);
    private Integer torrentsMoveTotal;
    private List<String> torrentsMoveSuccess;
    private List<String> torrentsMoveFailed;
    private boolean shutdownAfterMove = false;
    private boolean isNetworkOnline = false;
    private AtomicBoolean isPauseButton = new AtomicBoolean(true);
    private TorrentFileObserver fileObserver;
    private Thread shutdownThread;
    private TorrentStreamServer torrentStreamServer;

    public class LocalBinder extends Binder
    {
        public TorrentTaskService getService()
        {
            return TorrentTaskService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return binder;
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
        notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Context context = getApplicationContext();
        repo = new TorrentStorage(context);
        pref = SettingsManager.getPreferences(context);
        pref.registerOnSharedPreferenceChangeListener(this);

        makeNotifyChans(notifyManager);
        Utils.enableBootReceiverIfNeeded(getApplicationContext());
        checkPauseControl();

        if (pref.getBoolean(getString(R.string.pref_key_cpu_do_not_sleep),
                            SettingsManager.Default.cpuDoNotSleep))
            setKeepCpuAwake(true);

        TorrentEngine.getInstance().setContext(context);
        TorrentEngine.getInstance().setCallback(this);
        TorrentEngine.getInstance().setSettings(SettingsManager.readEngineSettings(context));
        TorrentEngine.getInstance().start();

        makeForegroundNotify();
        startUpdateForegroundNotify();
        if (!TorrentTaskServiceReceiver.getInstance().isRegistered(msgReceiver))
            TorrentTaskServiceReceiver.getInstance().register(msgReceiver);
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
            FileIOUtils.cleanTempDir(getBaseContext());

        } catch (Exception e) {
            Log.e(TAG, "Error during setup of temp directory: ", e);
        }
    }

    private void shutdown()
    {
        Intent shutdownIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        shutdownIntent.setAction(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP);
        sendBroadcast(shutdownIntent);
    }

    private void stopService()
    {
        pref.unregisterOnSharedPreferenceChangeListener(this);
        try {
            unregisterReceiver(powerReceiver);
        } catch (IllegalArgumentException e) {
            /* Ignore non-registered receiver */
        }
        try {
            unregisterReceiver(wifiReceiver);
        } catch (IllegalArgumentException e) {
            /* Ignore non-registered receiver */
        }
        TorrentTaskServiceReceiver.getInstance().unregister(msgReceiver);
        stopWatchDir();
        setKeepCpuAwake(false);
        stopUpdateForegroundNotify();
        TorrentEngine.getInstance().stop();
        stopStreamingServer();
        isAlreadyRunning = false;
        repo = null;
        pref = null;

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
            Context context = getApplicationContext();
            boolean batteryControl = pref.getBoolean(getString(R.string.pref_key_battery_control),
                                                     SettingsManager.Default.batteryControl);
            boolean customBatteryControl = pref.getBoolean(getString(R.string.pref_key_custom_battery_control),
                                                           SettingsManager.Default.customBatteryControl);
            int customBatteryControlValue = pref.getInt(getString(R.string.pref_key_custom_battery_control_value),
                                                        Utils.getDefaultBatteryLowLevel());
            boolean onlyCharging = pref.getBoolean(getString(R.string.pref_key_download_and_upload_only_when_charging),
                                                   SettingsManager.Default.onlyCharging);
            boolean wifiOnly = pref.getBoolean(getString(R.string.pref_key_wifi_only),
                                               SettingsManager.Default.wifiOnly);

            switch (intent.getAction()) {
                case NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP:
                case ACTION_SHUTDOWN:
                    if (shutdownThread != null && !shutdownThread.isAlive())
                        shutdownThread.start();

                    return START_NOT_STICKY;
                case Intent.ACTION_BATTERY_LOW:
                    if (batteryControl) {
                        boolean pause = true;
                        if (onlyCharging)
                            pause &= !Utils.isBatteryCharging(context);
                        if (wifiOnly)
                            pause &= !Utils.isWifiEnabled(context);
                        if (pause) {
                            pauseTorrents.set(true);
                            TorrentEngine.getInstance().pauseAll();
                        }
                    }
                    break;
                case Intent.ACTION_BATTERY_OKAY:
                    if (batteryControl) {
                        boolean resume = true;
                        if (onlyCharging)
                            resume &= Utils.isBatteryCharging(context);
                        if (wifiOnly)
                            resume &= Utils.isWifiEnabled(context);
                        if (resume) {
                            pauseTorrents.set(false);
                            TorrentEngine.getInstance().resumeAll();
                        }
                    }
                    break;
                case Intent.ACTION_BATTERY_CHANGED:
                    if (customBatteryControl) {
                        boolean pause = Utils.isBatteryBelowThreshold(context, customBatteryControlValue);
                        if(onlyCharging)
                            pause &= !Utils.isBatteryCharging(context);
                        if (wifiOnly)
                            pause &= !Utils.isWifiEnabled(context);
                        if (pause) {
                            pauseTorrents.set(true);
                            TorrentEngine.getInstance().pauseAll();
                        } else {
                            pauseTorrents.set(false);
                            TorrentEngine.getInstance().resumeAll();
                        }
                    }
                case Intent.ACTION_POWER_CONNECTED:
                    if (onlyCharging) {
                        boolean resume = true;
                        if (wifiOnly)
                            resume &= Utils.isWifiEnabled(context);
                        if (customBatteryControl)
                            resume &= !Utils.isBatteryBelowThreshold(context, customBatteryControlValue);
                        else if (batteryControl)
                            resume &= !Utils.isBatteryLow(context);
                        if (resume) {
                            pauseTorrents.set(false);
                            TorrentEngine.getInstance().resumeAll();
                        }
                    }
                    break;
                case Intent.ACTION_POWER_DISCONNECTED:
                    if (onlyCharging) {
                        boolean pause = true;
                        if (wifiOnly)
                            pause &= !Utils.isWifiEnabled(context);
                        if (customBatteryControl)
                            pause &= Utils.isBatteryBelowThreshold(context, customBatteryControlValue);
                        else if (batteryControl)
                            pause &= Utils.isBatteryLow(context);
                        if (pause) {
                            pauseTorrents.set(true);
                            TorrentEngine.getInstance().pauseAll();
                        }
                    }
                    break;
                case WifiReceiver.ACTION_WIFI_ENABLED:
                    if (wifiOnly) {
                        boolean resume = true;
                        if (onlyCharging)
                            resume &= Utils.isBatteryCharging(context);
                        if (customBatteryControl)
                            resume &= !Utils.isBatteryBelowThreshold(context, customBatteryControlValue);
                        else if (batteryControl)
                            resume &= !Utils.isBatteryLow(context);
                        if (resume) {
                            pauseTorrents.set(false);
                            TorrentEngine.getInstance().resumeAll();
                        }
                    }
                    break;
                case WifiReceiver.ACTION_WIFI_DISABLED:
                    if (wifiOnly) {
                        boolean pause = true;
                        if (onlyCharging)
                            pause &= !Utils.isBatteryCharging(context);
                        if (batteryControl)
                            pause &= Utils.isBatteryLow(context);
                        if (pause) {
                            pauseTorrents.set(true);
                            TorrentEngine.getInstance().pauseAll();
                        }
                    }
                    break;
                case NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME:
                    boolean pause = isPauseButton.getAndSet(!isPauseButton.get());
                    updateForegroundNotifyActions();
                    if (pause)
                        TorrentEngine.getInstance().pauseAll();
                    else
                        TorrentEngine.getInstance().resumeAll();
                    break;
                case ACTION_ADD_TORRENT: {
                    AddTorrentParams params = intent.getParcelableExtra(TAG_ADD_TORRENT_PARAMS);
                    boolean saveFile = intent.getBooleanExtra(TAG_SAVE_TORRENT_FILE, false);
                    try {
                        TorrentHelper.addTorrent(getApplicationContext(), params, !saveFile);

                    } catch (Throwable e) {
                        handleAddTorrentError(params, e);
                    }
                    break;
                } case ACTION_ADD_TORRENT_LIST: {
                    ArrayList<AddTorrentParams> paramsList =
                            intent.getParcelableArrayListExtra(TAG_ADD_TORRENT_PARAMS_LIST);
                    boolean saveFile = intent.getBooleanExtra(TAG_SAVE_TORRENT_FILE, false);
                    if (paramsList != null) {
                        for (AddTorrentParams params : paramsList) {
                            try {
                                TorrentHelper.addTorrent(getApplicationContext(), params, !saveFile);

                            } catch (Throwable e) {
                                handleAddTorrentError(params, e);
                            }
                        }
                    }
                    break;
                } case ACTION_MOVE_TORRENT: {
                    ArrayList<String> ids = intent.getStringArrayListExtra(TAG_ID_LIST);
                    String path = intent.getStringExtra(TAG_DOWNLOAD_PATH);

                    if (torrentsMoveSuccess == null)
                        torrentsMoveSuccess = new ArrayList<>();
                    if (torrentsMoveFailed == null)
                        torrentsMoveFailed = new ArrayList<>();
                    if (torrentsMoveTotal == null)
                        torrentsMoveTotal = 0;
                    ++torrentsMoveTotal;

                    TorrentHelper.setTorrentDownloadPath(getApplicationContext(), ids, path);
                }
            }
        }

        return START_STICKY;
    }

    @Override
    public void onEngineStarted()
    {
        loadTorrents(repo.getAll());

        if (pref.getBoolean(getString(R.string.pref_key_use_random_port),
                            SettingsManager.Default.useRandomPort)) {
            TorrentEngine.getInstance().setRandomPort();
            /* Update port */
            pref.edit().putInt(getString(R.string.pref_key_port), TorrentEngine.getInstance().getPort()).apply();
        }

        if (pref.getBoolean(getString(R.string.pref_key_proxy_changed),
                            SettingsManager.Default.proxyChanged)) {
            pref.edit().putBoolean(getString(R.string.pref_key_proxy_changed), false).apply();
            setProxy();
        }

        if (pref.getBoolean(getString(R.string.pref_key_enable_ip_filtering),
                            SettingsManager.Default.enableIpFiltering))
            TorrentEngine.getInstance().enableIpFilter(
                    pref.getString(getString(R.string.pref_key_ip_filtering_file),
                                   SettingsManager.Default.ipFilteringFile));

        if (pref.getBoolean(getString(R.string.pref_key_watch_dir), SettingsManager.Default.watchDir))
            startWatchDir();

        boolean enableStreaming = pref.getBoolean(getString(R.string.pref_key_streaming_enable),
                                                  SettingsManager.Default.enableStreaming);
        if (enableStreaming)
            startStreamingServer();
    }

    private void startStreamingServer()
    {
        stopStreamingServer();

        String hostname = pref.getString(getString(R.string.pref_key_streaming_hostname),
                                         SettingsManager.Default.streamingHostname);
        int port = pref.getInt(getString(R.string.pref_key_streaming_port),
                               SettingsManager.Default.streamingPort);

        torrentStreamServer = new TorrentStreamServer(hostname, port);
        try {
            torrentStreamServer.start();
        } catch (IOException e) {
            makeTorrentErrorNotify(null, getString(R.string.pref_streaming_error));
        }
    }

    private void stopStreamingServer()
    {
        if (torrentStreamServer != null)
            torrentStreamServer.stop();
        torrentStreamServer = null;
    }

    @Override
    public void onTorrentAdded(String id)
    {
        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null && pauseTorrents.get())
            task.pause();
    }

    @Override
    public void onTorrentStateChanged(String id)
    {
        sendBasicState(TorrentEngine.getInstance().getTask(id));
    }

    @Override
    public void onTorrentRemoved(String id)
    {
        if (basicStateCache.contains(id))
            basicStateCache.remove(id);
        TorrentTaskServiceReceiver.getInstance().post(TorrentStateMsg.makeTorrentRemovedBundle(id));
    }

    @Override
    public void onTorrentPaused(String id)
    {
        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        sendBasicState(task);

        Torrent torrent = repo.getTorrentByID(id);
        if (torrent == null)
            return;

        if (!torrent.isPaused()) {
            torrent.setPaused(true);
            repo.update(torrent);

            if (task != null)
                task.setTorrent(torrent);
        }
    }

    @Override
    public void onTorrentResumed(String id)
    {
        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null && !task.getTorrent().isPaused())
            return;

        Torrent torrent = repo.getTorrentByID(id);
        if (torrent == null)
            return;

        torrent.setPaused(false);
        torrent.setError(null);
        repo.update(torrent);

        if (task != null)
            task.setTorrent(torrent);
    }

    @Override
    public void onTorrentError(String id, String errorMsg)
    {
        if (errorMsg != null)
            Log.e(TAG, "Torrent " + id + ": " + errorMsg);

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        Torrent torrent = repo.getTorrentByID(id);
        if (torrent == null)
            return;

        torrent.setError(errorMsg);
        repo.update(torrent);

        if (task != null) {
            task.setTorrent(torrent);
            task.pause();
        }
    }

    @Override
    public void onTorrentFinished(String id)
    {
        Torrent torrent = repo.getTorrentByID(id);
        if (torrent == null)
            return;

        if (!torrent.isFinished()) {
            torrent.setFinished(true);
            makeFinishNotify(torrent);

            repo.update(torrent);

            TorrentDownload task = TorrentEngine.getInstance().getTask(id);
            if (task != null)
                task.setTorrent(torrent);

            if (pref.getBoolean(getString(R.string.pref_key_move_after_download),
                                SettingsManager.Default.moveAfterDownload)) {
                String path = pref.getString(getString(R.string.pref_key_move_after_download_in),
                                             torrent.getDownloadPath());

                if (!torrent.getDownloadPath().equals(path))
                    torrent.setDownloadPath(path);

                moveTorrent(torrent);
            }

            if (pref.getBoolean(getString(R.string.pref_key_shutdown_downloads_complete),
                                SettingsManager.Default.shutdownDownloadsComplete) && torrentsFinished()) {
                if (torrentsMoveTotal != null)
                    shutdownAfterMove = true;
                else
                    shutdown();
            }
        }
    }

    private boolean torrentsFinished() {
        List<TorrentStateCode> inProgressStates = Arrays.asList(
                TorrentStateCode.DOWNLOADING, TorrentStateCode.PAUSED, TorrentStateCode.CHECKING,
                TorrentStateCode.DOWNLOADING_METADATA, TorrentStateCode.ALLOCATING);

        for (TorrentDownload torrentDownload : TorrentEngine.getInstance().getTasks()) {
            if (inProgressStates.contains(torrentDownload.getStateCode())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onTorrentMoved(String id, boolean success)
    {
        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        String name = null;

        if (task != null)
            name = task.getTorrent().getName();

        if (success) {
            if (torrentsMoveSuccess != null && name != null)
                torrentsMoveSuccess.add(name);
        } else {
            if (torrentsMoveFailed != null && name != null)
                torrentsMoveFailed.add(name);
        }

        if (torrentsMoveSuccess != null && torrentsMoveFailed != null) {
            if ((torrentsMoveSuccess.size() + torrentsMoveFailed.size()) == torrentsMoveTotal) {
                makeTorrentsMoveNotify();
                if (shutdownAfterMove)
                    shutdown();
            }
        }
    }

    @Override
    public void onIpFilterParsed(boolean success)
    {
        Toast.makeText(getApplicationContext(),
                (success ? getString(R.string.ip_filter_add_success) :
                           getString(R.string.ip_filter_add_error)),
                Toast.LENGTH_LONG)
                .show();
    }

    @Override
    public void onTorrentMetadataLoaded(String id, Exception err)
    {
        if (err != null) {
            Log.e(TAG, "Load metadata error: ");
            Log.e(TAG, Log.getStackTraceString(err));
            if (err instanceof FreeSpaceException) {
                makeTorrentErrorNotify(repo.getTorrentByID(id).getName(), getString(R.string.error_free_space));
                TorrentTaskServiceReceiver.getInstance().post(TorrentStateMsg.makeTorrentRemovedBundle(id));
            }
            repo.delete(id);
        } else {
            TorrentDownload task = TorrentEngine.getInstance().getTask(id);
            if (task != null) {
                Torrent torrent = task.getTorrent();
                repo.update(torrent);
                if (pref.getBoolean(getString(R.string.pref_key_save_torrent_files),
                                    SettingsManager.Default.saveTorrentFiles))
                    TorrentHelper.saveTorrentFileIn(
                            getApplicationContext(),
                            torrent,
                            pref.getString(getString(R.string.pref_key_save_torrent_files_in),
                            torrent.getDownloadPath()));
            }
        }
    }

    @Override
    public void onMagnetLoaded(String hash, byte[] bencode)
    {
        TorrentMetaInfo info = null;
        try {
            info = new TorrentMetaInfo(bencode);

        } catch (DecodeException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        TorrentTaskServiceReceiver.getInstance().post(TorrentStateMsg.makeMagnetFetchedBundle(info));
    }

    @Override
    public void onRestoreSessionError(String id)
    {
        if (id == null)
            return;

        try {
            Torrent torrent = repo.getTorrentByID(id);
            if (torrent != null) {
                makeTorrentErrorNotify(torrent.getName(), getString(R.string.restore_torrent_error));
                repo.delete(torrent);
            }

        } catch (Exception e) {
            /* Ignore */
        }
    }

    @Override
    public void onSessionError(String errorMsg)
    {
        Log.e(TAG, errorMsg);
        makeSessionErrorNotify(errorMsg);
    }

    @Override
    public void onNatError(String errorMsg)
    {
        Log.e(TAG, "NAT error: " + errorMsg);
        if (pref.getBoolean(getString(R.string.pref_key_show_nat_errors),
                            SettingsManager.Default.showNatErrors))
            makeNatErrorNotify(errorMsg);
    }

    private TorrentTaskServiceReceiver.Callback msgReceiver = new TorrentTaskServiceReceiver.Callback()
    {
        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onReceive(Bundle b)
        {
            if (b != null) {
                Object type = b.getSerializable(TorrentStateMsg.TYPE);
                if (type == null)
                    return;
                switch ((TorrentStateMsg.Type)type) {
                    case UPDATE_TORRENT:
                    case UPDATE_TORRENTS: {
                        needsUpdateNotify.set(true);
                        break;
                    }
                    case TORRENT_REMOVED: {
                        String id = b.getString(TorrentStateMsg.TORRENT_ID);
                        if (id != null)
                            needsUpdateNotify.set(true);
                        break;
                    }
                }
            }
        }
    };

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        if (pref == null)
            pref = sharedPreferences;

        if (key.equals(getString(R.string.pref_key_battery_control)) ||
                key.equals(getString(R.string.pref_key_custom_battery_control)) ||
                key.equals(getString(R.string.pref_key_custom_battery_control_value)) ||
                key.equals(getString(R.string.pref_key_download_and_upload_only_when_charging)) ||
                key.equals(getString(R.string.pref_key_wifi_only))) {
            if (checkPauseControl())
                TorrentEngine.getInstance().pauseAll();
            else
                TorrentEngine.getInstance().resumeAll();
        } else if (key.equals(getString(R.string.pref_key_max_download_speed))) {
            TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
            s.downloadRateLimit = pref.getInt(key, SettingsManager.Default.maxDownloadSpeedLimit);
            TorrentEngine.getInstance().setSettings(s);
        } else if (key.equals(getString(R.string.pref_key_max_upload_speed))) {
            TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
            s.uploadRateLimit = pref.getInt(key, SettingsManager.Default.maxUploadSpeedLimit);
            TorrentEngine.getInstance().setSettings(s);
        } else if (key.equals(getString(R.string.pref_key_max_connections))) {
            TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
            s.connectionsLimit = pref.getInt(key, SettingsManager.Default.maxConnections);
            s.maxPeerListSize = s.connectionsLimit;
            TorrentEngine.getInstance().setSettings(s);
        } else if (key.equals(getString(R.string.pref_key_max_connections_per_torrent))) {
            TorrentEngine.getInstance().setMaxConnectionsPerTorrent(pref.getInt(key,
                    SettingsManager.Default.maxConnectionsPerTorrent));
        } else if (key.equals(getString(R.string.pref_key_max_uploads_per_torrent))) {
            TorrentEngine.getInstance().setMaxUploadsPerTorrent(pref.getInt(key,
                    SettingsManager.Default.maxUploadsPerTorrent));
        } else if (key.equals(getString(R.string.pref_key_max_active_downloads))) {
            TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
            s.activeDownloads = pref.getInt(key, SettingsManager.Default.maxActiveDownloads);
            TorrentEngine.getInstance().setSettings(s);
        } else if (key.equals(getString(R.string.pref_key_max_active_uploads))) {
            TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
            s.activeSeeds = pref.getInt(key, SettingsManager.Default.maxActiveUploads);
            TorrentEngine.getInstance().setSettings(s);
        } else if (key.equals(getString(R.string.pref_key_max_active_torrents))) {
            TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
            s.activeLimit = pref.getInt(key, SettingsManager.Default.maxActiveTorrents);
            TorrentEngine.getInstance().setSettings(s);
        } else if (key.equals(getString(R.string.pref_key_cpu_do_not_sleep))) {
            setKeepCpuAwake(pref.getBoolean(getString(R.string.pref_key_cpu_do_not_sleep),
                    SettingsManager.Default.cpuDoNotSleep));
        } else if (key.equals(getString(R.string.pref_key_enable_dht))) {
            TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
            s.dhtEnabled = pref.getBoolean(getString(R.string.pref_key_enable_dht),
                    SettingsManager.Default.enableDht);
            TorrentEngine.getInstance().setSettings(s);
        } else if (key.equals(getString(R.string.pref_key_enable_lsd))) {
            TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
            s.lsdEnabled = pref.getBoolean(getString(R.string.pref_key_enable_lsd),
                    SettingsManager.Default.enableLsd);
            TorrentEngine.getInstance().setSettings(s);
        } else if (key.equals(getString(R.string.pref_key_enable_utp))) {
            TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
            s.utpEnabled = pref.getBoolean(getString(R.string.pref_key_enable_utp),
                    SettingsManager.Default.enableUtp);
            TorrentEngine.getInstance().setSettings(s);
        } else if (key.equals(getString(R.string.pref_key_enable_upnp))) {
            TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
            s.upnpEnabled = pref.getBoolean(getString(R.string.pref_key_enable_upnp),
                    SettingsManager.Default.enableUpnp);
            TorrentEngine.getInstance().setSettings(s);
        } else if (key.equals(getString(R.string.pref_key_enable_natpmp))) {
            TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
            s.natPmpEnabled = pref.getBoolean(getString(R.string.pref_key_enable_natpmp),
                    SettingsManager.Default.enableNatPmp);
            TorrentEngine.getInstance().setSettings(s);
        } else if (key.equals(getString(R.string.pref_key_enc_mode))) {
            TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
            s.encryptMode = getEncryptMode();
            TorrentEngine.getInstance().setSettings(s);
        } else if (key.equals(getString(R.string.pref_key_enc_in_connections))) {
            TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
            int state = settings_pack.enc_policy.pe_disabled.swigValue();
            s.encryptInConnections = pref.getBoolean(getString(R.string.pref_key_enc_in_connections),
                    SettingsManager.Default.encryptInConnections);
            if (s.encryptInConnections) {
                state = getEncryptMode();
            }
            s.encryptMode = state;
            TorrentEngine.getInstance().setSettings(s);
        } else if (key.equals(getString(R.string.pref_key_enc_out_connections))) {
            TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
            int state = settings_pack.enc_policy.pe_disabled.swigValue();
            s.encryptOutConnections = pref.getBoolean(getString(R.string.pref_key_enc_out_connections),
                    SettingsManager.Default.encryptOutConnections);
            if (s.encryptOutConnections) {
                state = getEncryptMode();
            }
            s.encryptMode = state;
            TorrentEngine.getInstance().setSettings(s);
        } else if (key.equals(getString(R.string.pref_key_use_random_port))) {
            if (pref.getBoolean(getString(R.string.pref_key_use_random_port),
                    SettingsManager.Default.useRandomPort))
                TorrentEngine.getInstance().setRandomPort();
            else
                TorrentEngine.getInstance().setPort(pref.getInt(getString(R.string.pref_key_port),
                        SettingsManager.Default.port));
        } else if (key.equals(getString(R.string.pref_key_port))) {
            TorrentEngine.getInstance().setPort(pref.getInt(getString(R.string.pref_key_port),
                    SettingsManager.Default.port));
        } else if (key.equals(getString(R.string.pref_key_enable_ip_filtering))) {
            if (pref.getBoolean(getString(R.string.pref_key_enable_ip_filtering),
                    SettingsManager.Default.enableIpFiltering))
                TorrentEngine.getInstance().enableIpFilter(
                        pref.getString(getString(R.string.pref_key_ip_filtering_file),
                                SettingsManager.Default.ipFilteringFile));
            else
                TorrentEngine.getInstance().disableIpFilter();
        } else if (key.equals(getString(R.string.pref_key_ip_filtering_file))) {
            TorrentEngine.getInstance().enableIpFilter(
                    pref.getString(getString(R.string.pref_key_ip_filtering_file),
                            SettingsManager.Default.ipFilteringFile));
        } else if (key.equals(getString(R.string.pref_key_apply_proxy))) {
            pref.edit().putBoolean(getString(R.string.pref_key_proxy_changed), false).apply();
            setProxy();
            Toast.makeText(getApplicationContext(),
                    R.string.proxy_settings_applied,
                    Toast.LENGTH_SHORT)
                    .show();
        } else if (key.equals(getString(R.string.pref_key_auto_manage))) {
            TorrentEngine.getInstance().setAutoManaged(pref.getBoolean(key,
                    SettingsManager.Default.autoManage));
        } else if (key.equals(getString(R.string.pref_key_foreground_notify_func_button))) {
            updateForegroundNotifyActions();
        } else if (key.equals(getString(R.string.pref_key_watch_dir))) {
            if (pref.getBoolean(getString(R.string.pref_key_watch_dir), SettingsManager.Default.watchDir))
                startWatchDir();
            else
                stopWatchDir();
        } else if (key.equals(getString(R.string.pref_key_dir_to_watch))) {
            stopWatchDir();
            startWatchDir();
        } else if (key.equals(getString(R.string.pref_key_streaming_enable))) {
            if (pref.getBoolean(key, SettingsManager.Default.enableStreaming))
                startStreamingServer();
            else
                stopStreamingServer();
        } else if (key.equals(getString(R.string.pref_key_streaming_port)) ||
                   key.equals(getString(R.string.pref_key_streaming_hostname))) {
            startStreamingServer();
        }
    }

    /*
     * Return pause state
     */
    private boolean checkPauseControl()
    {
        Context context = getApplicationContext();
        boolean batteryControl = pref.getBoolean(getString(R.string.pref_key_battery_control),
                SettingsManager.Default.batteryControl);
        boolean customBatteryControl = pref.getBoolean(getString(R.string.pref_key_custom_battery_control),
                SettingsManager.Default.customBatteryControl);
        int customBatteryControlValue = pref.getInt(getString(
                R.string.pref_key_custom_battery_control_value), Utils.getDefaultBatteryLowLevel());
        boolean onlyCharging = pref.getBoolean(getString(R.string.pref_key_download_and_upload_only_when_charging),
                SettingsManager.Default.onlyCharging);
        boolean wifiOnly = pref.getBoolean(getString(R.string.pref_key_wifi_only),
                SettingsManager.Default.wifiOnly);

        try {
            unregisterReceiver(powerReceiver);
        } catch (IllegalArgumentException e) {
            /* Ignore non-registered receiver */
        }
        if (customBatteryControl)
            registerReceiver(powerReceiver, PowerReceiver.getCustomFilter());
        else if (batteryControl || onlyCharging)
            registerReceiver(powerReceiver, PowerReceiver.getFilter());

        try {
            unregisterReceiver(wifiReceiver);
        } catch (IllegalArgumentException e) {
            /* Ignore non-registered receiver */
        }
        if (wifiOnly)
            registerReceiver(wifiReceiver, WifiReceiver.getFilter());

        boolean pause = false;
        if (wifiOnly)
            pause = !Utils.isWifiEnabled(context);
        if (onlyCharging)
            pause |= !Utils.isBatteryCharging(context);
        if (customBatteryControl)
            pause |= Utils.isBatteryBelowThreshold(context, customBatteryControlValue);
        else if (batteryControl)
            pause |= Utils.isBatteryLow(context);
        pauseTorrents.set(pause);

        return pause;
    }

    private int getEncryptMode()
    {
        int mode = pref.getInt(getString(R.string.pref_key_enc_mode),
                               SettingsManager.Default.encryptMode(getApplicationContext()));

        if (mode == Integer.parseInt(getString(R.string.pref_enc_mode_prefer_value)))
            return settings_pack.enc_policy.pe_enabled.swigValue();
        else if (mode == Integer.parseInt(getString(R.string.pref_enc_mode_require_value)))
            return settings_pack.enc_policy.pe_forced.swigValue();
        else
            return settings_pack.enc_policy.pe_disabled.swigValue();
    }

    private void setProxy()
    {
        ProxySettingsPack proxy = new ProxySettingsPack();
        ProxySettingsPack.ProxyType type = ProxySettingsPack.ProxyType.fromValue(
                pref.getInt(getString(R.string.pref_key_proxy_type),
                            SettingsManager.Default.proxyType));
        proxy.setType(type);
        if (type == ProxySettingsPack.ProxyType.NONE)
            TorrentEngine.getInstance().setProxy(getApplicationContext(), proxy);
        proxy.setAddress(pref.getString(getString(R.string.pref_key_proxy_address),
                                        SettingsManager.Default.proxyAddress));
        proxy.setPort(pref.getInt(getString(R.string.pref_key_proxy_port),
                                  SettingsManager.Default.proxyPort));
        proxy.setProxyPeersToo(pref.getBoolean(getString(R.string.pref_key_proxy_peers_too),
                                               SettingsManager.Default.proxyPeersToo));
        if (pref.getBoolean(getString(R.string.pref_key_proxy_requires_auth),
                            SettingsManager.Default.proxyRequiresAuth)) {
            proxy.setLogin(pref.getString(getString(R.string.pref_key_proxy_login),
                                          SettingsManager.Default.proxyLogin));
            proxy.setPassword(pref.getString(getString(R.string.pref_key_proxy_password),
                                             SettingsManager.Default.proxyPassword));
        }
        TorrentEngine.getInstance().setProxy(getApplicationContext(), proxy);
    }

    private void loadTorrents(Collection<Torrent> torrents)
    {
        if (torrents == null)
            return;

        ArrayList<Torrent> loadList = new ArrayList<>();
        for (Torrent torrent : torrents) {
            if (!torrent.isDownloadingMetadata() &&
                    !TorrentUtils.torrentFileExists(getApplicationContext(), torrent.getId())) {
                Log.e(TAG, "Torrent doesn't exists: " + torrent);
                makeTorrentErrorNotify(torrent.getName(), getString(R.string.torrent_does_not_exists_error));
                repo.delete(torrent);
            } else {
                loadList.add(torrent);
            }
        }

        TorrentEngine.getInstance().restoreDownloads(loadList);
    }

    private void moveTorrent(Torrent torrent)
    {
        if (torrent == null)
            return;

        repo.update(torrent);

        TorrentDownload task = TorrentEngine.getInstance().getTask(torrent.getId());
        if (task != null) {
            if (torrentsMoveSuccess == null)
                torrentsMoveSuccess = new ArrayList<>();

            if (torrentsMoveFailed == null)
                torrentsMoveFailed = new ArrayList<>();

            if (torrentsMoveTotal == null)
                torrentsMoveTotal = 0;

            ++torrentsMoveTotal;

            task.setTorrent(torrent);
            task.setDownloadPath(torrent.getDownloadPath());
        }
    }

    private void setKeepCpuAwake(boolean enable)
    {
        if (enable) {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
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

    private void sendBasicState(TorrentDownload task)
    {
        if (task == null)
            return;
        Torrent torrent = task.getTorrent();
        if (torrent == null)
            return;

        BasicStateParcel state = TorrentHelper.makeBasicStateParcel(task);
        if (basicStateCache.contains(state))
            return;
        basicStateCache.put(state);
        TorrentTaskServiceReceiver.getInstance().post(TorrentStateMsg.makeUpdateTorrentBundle(state));
    }

    private Runnable updateForegroundNotify = new Runnable()
    {
        @Override
        public void run()
        {
            if (isAlreadyRunning) {
                boolean online = TorrentEngine.getInstance().isConnected();
                if (isNetworkOnline != online) {
                    isNetworkOnline = online;
                    needsUpdateNotify.set(true);
                }

                if (needsUpdateNotify.get()) {
                    try {
                        needsUpdateNotify.set(false);
                        if (foregroundNotify != null) {
                            foregroundNotify.setContentText((isNetworkOnline ?
                                    getString(R.string.network_online) :
                                    getString(R.string.network_offline)));
                            if (!TorrentEngine.getInstance().hasTasks())
                                foregroundNotify.setStyle(makeDetailNotifyInboxStyle());
                            else
                                foregroundNotify.setStyle(null);
                            /* Disallow killing the service process by system */
                            startForeground(SERVICE_STARTED_NOTIFICATION_ID, foregroundNotify.build());
                        }

                    } catch (Exception e) {
                            /* Ignore */
                    }
                }
            }
            updateForegroundNotifyHandler.postDelayed(this, SYNC_TIME);
        }
    };

    private void startUpdateForegroundNotify()
    {
        if (updateForegroundNotifyHandler != null ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return;
        }

        updateForegroundNotifyHandler = new Handler();
        updateForegroundNotifyHandler.postDelayed(updateForegroundNotify, SYNC_TIME);
    }

    private void stopUpdateForegroundNotify()
    {
        if (updateForegroundNotifyHandler == null)
            return;

        updateForegroundNotifyHandler.removeCallbacks(updateForegroundNotify);
    }

    private void updateForegroundNotifyActions()
    {
        if (foregroundNotify == null)
            return;

        foregroundNotify.mActions.clear();
        foregroundNotify.addAction(makeFuncButtonAction());
        foregroundNotify.addAction(makeShutdownAction());
        startForeground(SERVICE_STARTED_NOTIFICATION_ID, foregroundNotify.build());
    }

    private void makeForegroundNotify() {
        /* For starting main activity */
        Intent startupIntent = new Intent(getApplicationContext(), MainActivity.class);
        startupIntent.setAction(Intent.ACTION_MAIN);
        startupIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        startupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent startupPendingIntent =
                PendingIntent.getActivity(
                        getApplicationContext(),
                        0,
                        startupIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        foregroundNotify = new NotificationCompat.Builder(getApplicationContext(),
                FOREGROUND_NOTIFY_CHAN_ID)
                .setSmallIcon(R.drawable.ic_app_notification)
                .setContentIntent(startupPendingIntent)
                .setContentTitle(getString(R.string.app_running_in_the_background))
                .setTicker(getString(R.string.app_running_in_the_background))
                .setContentText((isNetworkOnline ?
                        getString(R.string.network_online) :
                        getString(R.string.network_offline)))
                .setWhen(System.currentTimeMillis());

        foregroundNotify.addAction(makeFuncButtonAction());
        foregroundNotify.addAction(makeShutdownAction());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            foregroundNotify.setCategory(Notification.CATEGORY_SERVICE);

        /* Disallow killing the service process by system */
        startForeground(SERVICE_STARTED_NOTIFICATION_ID, foregroundNotify.build());
    }

    /*
     * For calling add torrent dialog or pause/resume torrents
     */
    private NotificationCompat.Action makeFuncButtonAction()
    {
        Intent funcButtonIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        int type = pref.getInt(getString(R.string.pref_key_foreground_notify_func_button),
                               SettingsManager.Default.funcButton(getApplicationContext()));
        int icon = 0;
        String text = null;
        if (type == Integer.parseInt(getString(R.string.pref_function_button_pause_value))) {
            funcButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME);
            boolean isPause = isPauseButton.get();
            icon = (isPause ? R.drawable.ic_pause_white_24dp : R.drawable.ic_play_arrow_white_24dp);
            text = (isPause ? getString(R.string.pause_torrent) : getString(R.string.resume_torrent));
        } else if (type == Integer.parseInt(getString(R.string.pref_function_button_add_value))) {
            funcButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_ADD_TORRENT);
            icon = R.drawable.ic_add_white_36dp;
            text = getString(R.string.add);
        }
        PendingIntent funcButtonPendingIntent =
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        0,
                        funcButtonIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(icon, text, funcButtonPendingIntent).build();
    }

    /*
     * For shutdown activity and service
     */
    private NotificationCompat.Action makeShutdownAction()
    {
        Intent shutdownIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        shutdownIntent.setAction(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP);
        PendingIntent shutdownPendingIntent =
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        0,
                        shutdownIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_power_settings_new_white_24dp,
                getString(R.string.shutdown),
                shutdownPendingIntent)
                .build();
    }

    private NotificationCompat.InboxStyle makeDetailNotifyInboxStyle()
    {
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

        String titleTemplate = getString(R.string.torrent_count_notify_template);

        int downloadingCount = 0;

        for (TorrentDownload task : TorrentEngine.getInstance().getTasks()) {
            if (task == null) {
                continue;
            }

            String template;
            TorrentStateCode code = task.getStateCode();
            
            if (code == TorrentStateCode.DOWNLOADING) {
                ++downloadingCount;
                template =  getString(R.string.downloading_torrent_notify_template);
                inboxStyle.addLine(
                        String.format(
                                template,
                                task.getProgress(),
                                (task.getETA() == -1) ? Utils.INFINITY_SYMBOL :
                                        DateUtils.formatElapsedTime(task.getETA()),
                                Formatter.formatFileSize(this, task.getDownloadSpeed()),
                                task.getTorrent().getName()));

            } else if (code == TorrentStateCode.SEEDING) {
                template = getString(R.string.seeding_torrent_notify_template);
                inboxStyle.addLine(
                        String.format(
                                template,
                                getString(R.string.torrent_status_seeding),
                                Formatter.formatFileSize(this, task.getUploadSpeed()),
                                task.getTorrent().getName()));
            } else {
                String stateString = "";
                
                switch (task.getStateCode()) {
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
                inboxStyle.addLine(
                        String.format(
                                template,
                                stateString,
                                task.getTorrent().getName()));
            }
        }

        inboxStyle.setBigContentTitle(String.format(
                titleTemplate,
                downloadingCount,
                TorrentEngine.getInstance().tasksCount()));

        inboxStyle.setSummaryText((isNetworkOnline ?
            getString(R.string.network_online) :
            getString(R.string.network_offline)));

        return inboxStyle;
    }

    private void makeFinishNotify(Torrent torrent)
    {
        if (torrent == null || !pref.getBoolean(getString(R.string.pref_key_torrent_finish_notify),
                                                SettingsManager.Default.torrentFinishNotify))
            return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                DEFAULT_CHAN_ID)
                .setSmallIcon(R.drawable.ic_done_white_24dp)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
                .setContentTitle(getString(R.string.torrent_finished_notify))
                .setTicker(getString(R.string.torrent_finished_notify))
                .setContentText(torrent.getName())
                .setWhen(System.currentTimeMillis());

        if (pref.getBoolean(getString(R.string.pref_key_play_sound_notify),
                            SettingsManager.Default.playSoundNotify)) {
            Uri sound = Uri.parse(pref.getString(getString(R.string.pref_key_notify_sound),
                                                 SettingsManager.Default.notifySound));
            builder.setSound(sound);
        }

        if (pref.getBoolean(getString(R.string.pref_key_vibration_notify),
                            SettingsManager.Default.vibrationNotify))
            builder.setVibrate(new long[] {1000}); /* ms */

        if (pref.getBoolean(getString(R.string.pref_key_led_indicator_notify),
                            SettingsManager.Default.ledIndicatorNotify)) {
            int color = pref.getInt(getString(R.string.pref_key_led_indicator_color_notify),
                                    SettingsManager.Default.ledIndicatorColorNotify(getApplicationContext()));
            builder.setLights(color, 1000, 1000); /* ms */
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_STATUS);

        notifyManager.notify(torrent.getId().hashCode(), builder.build());
    }

    private void makeTorrentErrorNotify(String name, String message)
    {
        if (name == null || message == null)
            return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                DEFAULT_CHAN_ID)
                .setSmallIcon(R.drawable.ic_error_white_24dp)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
                .setContentTitle(name)
                .setTicker(getString(R.string.torrent_error_notify_title))
                .setContentText(String.format(getString(R.string.error_template), message))
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_ERROR);

        notifyManager.notify(name.hashCode(), builder.build());
    }

    private void makeSessionErrorNotify(String message)
    {
        if (message == null)
            return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                DEFAULT_CHAN_ID)
                .setSmallIcon(R.drawable.ic_error_white_24dp)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
                .setContentTitle(getString(R.string.session_error_title))
                .setTicker(getString(R.string.session_error_title))
                .setContentText(String.format(getString(R.string.error_template), message))
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_ERROR);

        notifyManager.notify(SESSION_ERROR_NOTIFICATION_ID, builder.build());
    }

    private void makeNatErrorNotify(String message)
    {
        if (message == null)
            return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                DEFAULT_CHAN_ID)
                .setSmallIcon(R.drawable.ic_error_white_24dp)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
                .setContentTitle(getString(R.string.nat_error_title))
                .setTicker(getString(R.string.nat_error_title))
                .setContentText(String.format(getString(R.string.error_template), message))
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_ERROR);

        notifyManager.notify(NAT_ERROR_NOTIFICATION_ID, builder.build());
    }

    private void makeTorrentInfoNotify(String name, String message)
    {
        if (name == null || message == null)
            return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                DEFAULT_CHAN_ID)
                .setSmallIcon(R.drawable.ic_info_white_24dp)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
                .setContentTitle(name)
                .setTicker(message)
                .setContentText(message)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_STATUS);

        notifyManager.notify(name.hashCode(), builder.build());
    }

    private void makeTorrentAddedNotify(String name)
    {
        if (name == null)
            return;

        String title = getString(R.string.torrent_added_notify);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                DEFAULT_CHAN_ID)
                .setSmallIcon(R.drawable.ic_done_white_24dp)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(name)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_STATUS);

        notifyManager.notify(name.hashCode(), builder.build());
    }

    private synchronized void makeTorrentsMoveNotify()
    {
        if (torrentsMoveTotal == null ||
                torrentsMoveSuccess == null ||
                torrentsMoveFailed == null) {
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                DEFAULT_CHAN_ID);

        String resultTemplate = getString(R.string.torrents_moved_content);
        int successfully = torrentsMoveSuccess.size();
        int failed = torrentsMoveFailed.size();

        builder.setContentTitle(getString(R.string.torrents_moved_title))
                .setTicker(getString(R.string.torrents_moved_title))
                .setContentText(String.format(resultTemplate, successfully, failed));

        builder.setSmallIcon(R.drawable.ic_folder_move_white_24dp)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setStyle(makeTorrentsMoveInboxStyle(torrentsMoveSuccess, torrentsMoveFailed));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_STATUS);
        }

        notifyManager.notify(TORRENTS_MOVED_NOTIFICATION_ID, builder.build());

        torrentsMoveTotal = null;
        torrentsMoveSuccess = null;
        torrentsMoveFailed = null;
    }

    private NotificationCompat.InboxStyle makeTorrentsMoveInboxStyle(List<String> torrentsMoveSuccess,
                                                                     List<String> torrentsMoveFailed)
    {
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

        boolean successNotEmpty = !torrentsMoveSuccess.isEmpty();

        if (successNotEmpty) {
            inboxStyle.addLine(getString(R.string.torrents_move_inbox_successfully));
            for (String name : torrentsMoveSuccess) {
                inboxStyle.addLine(name);
            }
        }

        if (!torrentsMoveFailed.isEmpty()) {
            if (successNotEmpty) {
                inboxStyle.addLine("\n");
            }

            inboxStyle.addLine(getString(R.string.torrents_move_inbox_failed));
            for (String name : torrentsMoveFailed) {
                inboxStyle.addLine(name);
            }
        }

        return inboxStyle;
    }

    private TorrentFileObserver makeTorrentFileObserver(final String pathToDir)
    {
        return new TorrentFileObserver(pathToDir) {
            @Override
            public void onEvent(int event, @Nullable String name)
            {
                if (name == null)
                    return;

                File f = new File(pathToDir, name);
                if (!f.exists())
                    return;
                if (f.isDirectory() || !f.getName().endsWith(".torrent"))
                    return;

                addTorrent(f);
            }
        };
    }

    private void addTorrent(File file)
    {
        if (file == null)
            return;

        TorrentInfo ti;
        try {
            ti = new TorrentInfo(file);
        } catch (IllegalArgumentException e) {
            makeTorrentErrorNotify(null, getString(R.string.error_decode_torrent));
            return;
        }
        ArrayList<Priority> priorities = new ArrayList<>(Collections.nCopies(ti.files().numFiles(),Priority.DEFAULT));
        String downloadPath = pref.getString(getString(R.string.pref_key_save_torrents_in),
                                             SettingsManager.Default.saveTorrentsIn);
        AddTorrentParams params = new AddTorrentParams(file.getAbsolutePath(), false, ti.infoHash().toHex(),
                                                       ti.name(), priorities, downloadPath, false, false);
        if (isAllFilesTooBig(downloadPath, ti)) {
            makeTorrentErrorNotify(file.getName(), getString(R.string.error_free_space));
            return;
        }
        try {
            TorrentHelper.addTorrent(getApplicationContext(), params, true);

        } catch (Throwable e) {
            handleAddTorrentError(params, e);
        }

        makeTorrentAddedNotify(params.getName());
    }

    private void handleAddTorrentError(AddTorrentParams params, Throwable e)
    {
        if (e instanceof FileAlreadyExistsException) {
            makeTorrentInfoNotify(params.getName(), getString(R.string.torrent_exist));
            return;
        }
        Log.e(TAG, Log.getStackTraceString(e));
        String message;
        if (e instanceof FileNotFoundException)
            message = getString(R.string.error_file_not_found_add_torrent);
        else if (e instanceof IOException)
            message = getString(R.string.error_io_add_torrent);
        else
            message = getString(R.string.error_add_torrent);
        makeTorrentErrorNotify(params.getName(), message);
    }

    private void startWatchDir()
    {
        String dir = pref.getString(getString(R.string.pref_key_dir_to_watch),
                                    SettingsManager.Default.dirToWatch);
        scanTorrentsInDir(dir);
        fileObserver = makeTorrentFileObserver(dir);
        fileObserver.startWatching();
    }

    private void stopWatchDir()
    {
        if (fileObserver == null)
            return;
        fileObserver.stopWatching();
        fileObserver = null;
    }

    private void scanTorrentsInDir(String pathToDir)
    {
        if (pathToDir == null)
            return;

        File dir = new File(pathToDir);
        if (!dir.exists())
            return;
        for (File file : FileUtils.listFiles(dir, FileFilterUtils.suffixFileFilter(".torrent"), null)) {
            if (!file.exists())
                continue;
            addTorrent(file);
        }
    }

    private boolean isAllFilesTooBig(String downloadPath, TorrentInfo ti)
    {
        return FileIOUtils.getFreeSpace(downloadPath) < ti.totalSize();
    }

    private void makeNotifyChans(NotificationManager notifyManager)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;

        ArrayList<NotificationChannel> chans = new ArrayList<>();
        NotificationChannel defaultChan = new NotificationChannel(DEFAULT_CHAN_ID, getString(R.string.def),
                                                           NotificationManager.IMPORTANCE_DEFAULT);
        Uri sound = Uri.parse(pref.getString(getString(R.string.pref_key_notify_sound),
                                             SettingsManager.Default.notifySound));
        defaultChan.setSound(sound, getNotifyAudioAttributes());

        if (pref.getBoolean(getString(R.string.pref_key_vibration_notify),
                            SettingsManager.Default.vibrationNotify)) {
            defaultChan.enableVibration(true);
            defaultChan.setVibrationPattern(new long[]{1000}); /* ms */
        } else {
            defaultChan.enableVibration(false);
        }

        if (pref.getBoolean(getString(R.string.pref_key_led_indicator_notify),
                            SettingsManager.Default.ledIndicatorNotify)) {
            int color = pref.getInt(getString(R.string.pref_key_led_indicator_color_notify),
                                    SettingsManager.Default.ledIndicatorColorNotify(getApplicationContext()));
            defaultChan.enableLights(true);
            defaultChan.setLightColor(color);
        } else {
            defaultChan.enableLights(false);
        }
        chans.add(defaultChan);
        chans.add(new NotificationChannel(FOREGROUND_NOTIFY_CHAN_ID, getString(R.string.foreground_notification),
                                          NotificationManager.IMPORTANCE_LOW));
        notifyManager.createNotificationChannels(chans);
    }

    private AudioAttributes getNotifyAudioAttributes()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return null;

        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }
}
