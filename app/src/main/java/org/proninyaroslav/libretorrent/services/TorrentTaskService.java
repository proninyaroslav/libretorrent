/*
 * Copyright (C) 2016, 2017 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import com.frostwire.jlibtorrent.AnnounceEntry;
import com.frostwire.jlibtorrent.PeerInfo;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.jlibtorrent.swig.settings_pack;

import net.grandcentrix.tray.core.OnTrayPreferenceChangeListener;
import net.grandcentrix.tray.core.TrayItem;

import org.proninyaroslav.libretorrent.MainActivity;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.FetchMagnetCallback;
import org.proninyaroslav.libretorrent.core.ProxySettingsPack;
import org.proninyaroslav.libretorrent.core.Torrent;
import org.proninyaroslav.libretorrent.core.TorrentDownload;
import org.proninyaroslav.libretorrent.core.TorrentEngine;
import org.proninyaroslav.libretorrent.core.TorrentEngineCallback;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.TorrentServiceCallback;
import org.proninyaroslav.libretorrent.core.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.exceptions.DecodeException;
import org.proninyaroslav.libretorrent.core.exceptions.FileAlreadyExistsException;
import org.proninyaroslav.libretorrent.core.stateparcel.PeerStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.StateParcelCache;
import org.proninyaroslav.libretorrent.core.stateparcel.TorrentStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.TrackerStateParcel;
import org.proninyaroslav.libretorrent.core.storage.TorrentStorage;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.receivers.NotificationReceiver;
import org.proninyaroslav.libretorrent.receivers.PowerReceiver;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class TorrentTaskService extends Service
        implements
        TorrentEngineCallback,
        OnTrayPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentTaskService.class.getSimpleName();

    private static final int SERVICE_STARTED_NOTIFICATION_ID = 1;
    private static final int TORRENTS_MOVED_NOTIFICATION_ID = 2;
    private static final int SYNC_TIME = 1000; /* ms */

    private boolean isAlreadyRunning;
    private NotificationManager notifyManager;
    /* For the pause action button of foreground notify */
    private Handler updateForegroundNotifyHandler;
    private NotificationCompat.Builder foregroundNotify;
    /* Tasks list */
    private ConcurrentHashMap<String, TorrentDownload> torrentTasks = new ConcurrentHashMap<>();
    /* Fetch magnet (non added magnets) */
    private ConcurrentHashMap<String, TorrentDownload> magnetList = new ConcurrentHashMap<>();
    /* List of connected clients */
    private ArrayList<TorrentServiceCallback> clientCallbacks = new ArrayList<>();
    private ArrayList<FetchMagnetCallback> magnetCallbacks = new ArrayList<>();
    private final IBinder binder = new LocalBinder();
    private TorrentStorage repo;
    private SettingsManager pref;
    private PowerManager.WakeLock wakeLock;
    private PowerReceiver powerReceiver = new PowerReceiver();
    /* Pause torrents (including new added) when in power settings are set power save flags */
    private AtomicBoolean pauseTorrents = new AtomicBoolean(false);
    /* Reduces sending packets due skip cache duplicates */
    private StateParcelCache<TorrentStateParcel> stateCache = new StateParcelCache<>();
    private AtomicBoolean needsUpdateNotify;
    private Integer torrentsMoveTotal;
    private List<String> torrentsMoveSuccess;
    private List<String> torrentsMoveFailed;
    private boolean shutdownAfterMove = false;
    private boolean isNetworkOnline = false;

    public TorrentTaskService()
    {
        needsUpdateNotify = new AtomicBoolean(false);
    }

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

    @Override
    public void onCreate()
    {
        super.onCreate();

        Log.i(TAG, "Start " + TorrentTaskService.class.getSimpleName());
        notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        repo = new TorrentStorage(getApplicationContext());

        pref = new SettingsManager(getApplicationContext());
        pref.registerOnTrayPreferenceChangeListener(this);

        registerReceiver(powerReceiver, PowerReceiver.getFilter());

        if (pref.getBoolean(getString(R.string.pref_key_battery_control), false) &&
                (Utils.getBatteryLevel(getApplicationContext()) <= Utils.getDefaultBatteryLowLevel())) {
            pauseTorrents.set(true);
        }

        if (pref.getBoolean(getString(R.string.pref_key_download_and_upload_only_when_charging), false) &&
                !Utils.isBatteryCharging(getApplicationContext())) {
            pauseTorrents.set(true);
        }

        if (pref.getBoolean(getString(R.string.pref_key_cpu_do_not_sleep), false)) {
            setKeepCpuAwake(true);
        }

        TorrentEngine.getInstance().setContext(getApplicationContext());
        TorrentEngine.getInstance().setCallback(this);
        TorrentEngine.getInstance().start();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        setKeepCpuAwake(false);
        stopUpdateForegroundNotify();

        /* Handles must be destructed before the session is destructed */
        torrentTasks.clear();
        TorrentEngine.getInstance().stop();

        unregisterReceiver(powerReceiver);

        isAlreadyRunning = false;
        repo = null;
        pref.unregisterOnTrayPreferenceChangeListener(this);
        pref = null;

        Log.i(TAG, "Stop " + TorrentTaskService.class.getSimpleName());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if (isAlreadyRunning) {
            if (intent != null && intent.getAction() != null) {
                switch (intent.getAction()) {
                    case NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP:
                        clientCallbacks.clear();
                        stopForeground(true);
                        stopSelf(startId);
                        break;
                    case Intent.ACTION_BATTERY_LOW:
                        if (pref.getBoolean(getString(R.string.pref_key_battery_control), false)) {
                            pauseTorrents.set(true);
                            pauseAll();
                        }
                        break;
                    case Intent.ACTION_BATTERY_OKAY:
                        if (pref.getBoolean(getString(R.string.pref_key_battery_control), false) &&
                                !pref.getBoolean(getString(R.string.pref_key_download_and_upload_only_when_charging), false)) {
                            pauseTorrents.set(false);
                            resumeAll();
                        }
                        break;
                    case Intent.ACTION_POWER_CONNECTED:
                        if (pref.getBoolean(getString(R.string.pref_key_download_and_upload_only_when_charging), false)) {
                            pauseTorrents.set(false);
                            resumeAll();
                        }
                        break;
                    case Intent.ACTION_POWER_DISCONNECTED:
                        if (pref.getBoolean(getString(R.string.pref_key_download_and_upload_only_when_charging), false)) {
                            pauseTorrents.set(true);
                            pauseAll();
                        }
                        break;
                }
            }

            return START_STICKY;
        }

        /* The first start */
        isAlreadyRunning = true;

        makeForegroundNotify();
        startUpdateForegroundNotify();

        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
            restartServiceIntent.setPackage(getPackageName());

            PendingIntent restartServicePendingIntent =
                    PendingIntent.getService(
                            getApplicationContext(),
                            1, restartServiceIntent,
                            PendingIntent.FLAG_ONE_SHOT);

            AlarmManager alarmService =
                    (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
            alarmService.set(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 1000,
                    restartServicePendingIntent);
        }
    }

    public void addListener(TorrentServiceCallback callback)
    {
        if (callback != null && !clientCallbacks.contains(callback)) {
            clientCallbacks.add(callback);
            sendTorrentsStateOneShot(callback);
        }
    }

    public void removeListener(TorrentServiceCallback callback)
    {
        clientCallbacks.remove(callback);
    }

    @Override
    public void onTorrentAdded(String id, TorrentDownload task)
    {
        if (pauseTorrents.get()) {
            task.pause();
        }
        torrentTasks.put(id, task);
    }

    @Override
    public void onEngineStarted()
    {
        if (torrentTasks.isEmpty()) {
            List<Torrent> torrents = repo.getAll();
            loadTorrents(torrents);
        }

        /* Set current port */
        pref.put(getString(R.string.pref_key_port), TorrentEngine.getInstance().getPort());

        if (pref.getBoolean(getString(R.string.pref_key_proxy_changed), false)) {
            pref.put(getString(R.string.pref_key_proxy_changed), false);
            setProxy();
        }

        if (pref.getBoolean(getString(R.string.pref_key_enable_ip_filtering), false)) {
            TorrentEngine.getInstance().enableIpFilter(
                    pref.getString(getString(R.string.pref_key_ip_filtering_file), null));
        }
    }

    @Override
    public void onTorrentStateChanged(String id)
    {
        sendTorrentState(torrentTasks.get(id));
    }

    @Override
    public void onTorrentRemoved(String id)
    {
        torrentTasks.remove(id);
        if (stateCache.contains(id)) {
            stateCache.remove(id);
        }

        for (TorrentServiceCallback callback : clientCallbacks) {
            TorrentStateParcel state = new TorrentStateParcel();
            state.torrentId = id;
            if (callback != null) {
                callback.onTorrentRemoved(state);
            }
        }
    }

    @Override
    public void onTorrentPaused(String id)
    {
        sendTorrentState(torrentTasks.get(id));

        Torrent torrent = repo.getTorrentByID(id);

        if (torrent == null) {
            return;
        }

        if (!torrent.isPaused()) {
            torrent.setPaused(true);
            repo.update(torrent);

            TorrentDownload task = torrentTasks.get(id);

            if (task != null) {
                task.setTorrent(torrent);
            }
        }
    }

    @Override
    public void onTorrentResumed(String id)
    {
        TorrentDownload task = torrentTasks.get(id);
        if (task != null && !task.getTorrent().isPaused()) {
            return;
        }

        Torrent torrent = repo.getTorrentByID(id);

        if (torrent == null) {
            return;
        }

        torrent.setPaused(false);
        repo.update(torrent);

        if (task != null) {
            task.setTorrent(torrent);
        }
    }

    @Override
    public void onTorrentFinished(String id)
    {
        Torrent torrent = repo.getTorrentByID(id);

        if (torrent == null) {
            return;
        }

        if (!torrent.isFinished()) {
            torrent.setFinished(true);
            makeFinishNotify(torrent);

            repo.update(torrent);

            TorrentDownload task = torrentTasks.get(id);

            if (task != null) {
                task.setTorrent(torrent);
            }

            if (pref.getBoolean(getString(R.string.pref_key_move_after_download), false)) {
                String path = pref.getString(
                        getString(R.string.pref_key_move_after_download_in), torrent.getDownloadPath());

                if (!torrent.getDownloadPath().equals(path)) {
                    torrent.setDownloadPath(path);
                }

                moveTorrent(torrent);
            }

            if (pref.getBoolean(getString(R.string.pref_key_shutdown_downloads_complete), false)) {
                if (torrentsMoveTotal != null) {
                    shutdownAfterMove = true;
                } else {
                    Intent shutdownIntent =
                            new Intent(getApplicationContext(), NotificationReceiver.class);
                    shutdownIntent.setAction(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP);
                    sendBroadcast(shutdownIntent);
                }
            }
        }
    }

    @Override
    public void onTorrentMoved(String id, boolean success)
    {
        TorrentDownload task = torrentTasks.get(id);
        String name = null;

        if (task != null) {
            name = task.getTorrent().getName();
        }

        if (success) {
            if (torrentsMoveSuccess != null && name != null) {
                torrentsMoveSuccess.add(name);
            }

        } else {
            if (torrentsMoveFailed != null && name != null) {
                torrentsMoveFailed.add(name);
            }
        }

        if (torrentsMoveSuccess != null && torrentsMoveFailed != null) {
            if ((torrentsMoveSuccess.size() + torrentsMoveFailed.size()) == torrentsMoveTotal) {
                makeTorrentsMoveNotify();
                if (shutdownAfterMove) {
                    Intent shutdownIntent =
                            new Intent(getApplicationContext(), NotificationReceiver.class);
                    shutdownIntent.setAction(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP);
                    sendBroadcast(shutdownIntent);
                }
            }
        }
    }

    @Override
    public void onIpFilterParsed(boolean success)
    {
        if (!success) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.ip_filter_add_error),
                    Toast.LENGTH_LONG)
                    .show();
        }
    }

    @Override
    public void onMetadataReceived(String hash, byte[] data)
    {
        File torrentFile;
        String pathToTorrent = null;
        Torrent torrent = null;
        Exception err = null;
        boolean nonAddedMagnet = true;

        if (!magnetList.containsKey(hash)) {
            nonAddedMagnet = false;

            torrent = repo.getTorrentByID(hash);
            if (torrent == null) {
                return;
            }
        }

        try {
            File dir;
            String name;

            if (nonAddedMagnet) {
                dir = FileIOUtils.getTempDir(getApplicationContext());
                name = hash;

            } else {
                String pathToDir = TorrentUtils.findTorrentDataDir(getApplicationContext(), hash);
                if (pathToDir == null) {
                    throw new FileNotFoundException("Data dir not found");
                }
                dir = new File(pathToDir);
                name = TorrentStorage.Model.DATA_TORRENT_FILE_NAME;
            }

            torrentFile = TorrentUtils.createTorrentFile(name, data, dir);
            if (torrentFile != null && torrentFile.exists()) {
                pathToTorrent = torrentFile.getAbsolutePath();
            }

            if (pathToTorrent != null) {
                TorrentDownload task;
                TorrentMetaInfo info = new TorrentMetaInfo(pathToTorrent);
                ArrayList<Integer> priorities =
                        new ArrayList<>(Collections.nCopies(info.fileList.size(), Priority.NORMAL.swig()));

                if (nonAddedMagnet) {
                    task = magnetList.get(hash);

                } else {
                    task = torrentTasks.get(hash);

                    if (FileIOUtils.getFreeSpace(torrent.getDownloadPath()) < info.torrentSize) {
                        if (task != null) {
                            task.remove(true);
                        }
                        repo.delete(hash);

                        makeTorrentErrorNotify(torrent, getString(R.string.error_free_space));
                        needsUpdateNotify.set(true);
                        sendTorrentsStateOneShot();

                        return;
                    }

                    torrent.setTorrentFilePath(pathToTorrent);
                    torrent.setFilePriorities(priorities);
                    torrent.setDownloadingMetadata(false);
                    repo.update(torrent);
                }

                if (task != null) {
                    task.getTorrent().setTorrentFilePath(pathToTorrent);
                    task.getTorrent().setFilePriorities(priorities);
                    task.getTorrent().setDownloadingMetadata(false);

                    if (!nonAddedMagnet) {
                        task.setSequentialDownload(task.getTorrent().isSequentialDownload());
                        if (task.getTorrent().isPaused()) {
                            task.pause();
                        } else {
                            task.resume(true);
                        }
                        task.setDownloadPath(task.getTorrent().getDownloadPath());
                    }
                }
            }

        } catch (Exception e) {
            err = e;
        }

        for (FetchMagnetCallback callback : magnetCallbacks) {
            callback.onMagnetFetched(hash, pathToTorrent, err);
        }
    }

    @Override
    public void onMetadataExist(String hash)
    {
        File torrent = null;
        Exception err = null;

        try {
            torrent = new File(FileIOUtils.getTempDir(getApplicationContext()), hash);
            /* Torrent already added */
            if (!torrent.exists()) {
                torrent = new File(TorrentUtils.findTorrentDataDir(getApplicationContext(), hash),
                        TorrentStorage.Model.DATA_TORRENT_FILE_NAME);
            }

        } catch (Exception e) {
            err = e;
        }

        for (FetchMagnetCallback callback : magnetCallbacks) {
            String pathToTorrent = (torrent != null && torrent.exists() ? torrent.getAbsolutePath() : null);
            callback.onMagnetFetched(hash, pathToTorrent, err);
        }
    }

    @Override
    public void onRestoreSessionError(String id)
    {
        if (id == null) {
            return;
        }

        try {
            Torrent torrent = repo.getTorrentByID(id);

            if (torrent != null) {
                makeTorrentErrorNotify(torrent, getString(R.string.restore_torrent_error));
                repo.delete(torrent);
            }

        } catch (Exception e) {
            /* Ignore */
        }
    }

    @Override
    public void onTrayPreferenceChanged(Collection<TrayItem> items)
    {
        if (pref == null) {
            return;
        }

        for (TrayItem item : items) {
            if (item.module().equals(SettingsManager.MODULE_NAME)) {
                if (item.key().equals(getString(R.string.pref_key_battery_control))) {
                    if (pref.getBoolean(item.key(), false) &&
                            (Utils.getBatteryLevel(getApplicationContext()) <= Utils.getDefaultBatteryLowLevel())) {
                        pauseTorrents.set(true);
                        pauseAll();
                    } else if (!pref.getBoolean(getString(R.string.pref_key_download_and_upload_only_when_charging), false)) {
                        pauseTorrents.set(false);
                        resumeAll();
                    }

                } else if (item.key().equals(getString(R.string.pref_key_download_and_upload_only_when_charging))) {
                    if (pref.getBoolean(item.key(), false) && !Utils.isBatteryCharging(getApplicationContext())) {
                        pauseTorrents.set(true);
                        pauseAll();
                    } else {
                        pauseTorrents.set(false);
                        resumeAll();
                    }

                } else if (item.key().equals(getString(R.string.pref_key_max_download_speed))) {
                    TorrentEngine.getInstance().setDownloadSpeedLimit(pref.getInt(item.key(), 0));

                } else if (item.key().equals(getString(R.string.pref_key_max_upload_speed))) {
                    TorrentEngine.getInstance().setUploadSpeedLimit(pref.getInt(item.key(), 0));

                } else if (item.key().equals(getString(R.string.pref_key_max_connections))) {
                    SettingsPack sp = TorrentEngine.getInstance().getSettings();
                    if (sp != null) {
                        sp.connectionsLimit(pref.getInt(item.key(), TorrentEngine.DEFAULT_CONNECTIONS_LIMIT));
                        TorrentEngine.getInstance().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_max_active_downloads))) {
                    SettingsPack sp = TorrentEngine.getInstance().getSettings();
                    if (sp != null) {
                        sp.activeDownloads(pref.getInt(item.key(), TorrentEngine.DEFAULT_ACTIVE_DOWNLOADS));
                        TorrentEngine.getInstance().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_max_active_uploads))) {
                    SettingsPack sp = TorrentEngine.getInstance().getSettings();
                    if (sp != null) {
                        sp.activeSeeds(pref.getInt(item.key(), TorrentEngine.DEFAULT_ACTIVE_SEEDS));
                        TorrentEngine.getInstance().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_max_active_torrents))) {
                    SettingsPack sp = TorrentEngine.getInstance().getSettings();
                    if (sp != null) {
                        sp.activeLimit(pref.getInt(item.key(), TorrentEngine.DEFAULT_ACTIVE_LIMIT));
                        TorrentEngine.getInstance().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_cpu_do_not_sleep))) {
                    setKeepCpuAwake(pref.getBoolean(getString(R.string.pref_key_cpu_do_not_sleep), false));

                } else if (item.key().equals(getString(R.string.pref_key_enable_dht))) {
                    SettingsPack sp = TorrentEngine.getInstance().getSettings();
                    if (sp != null) {
                        sp.enableDht(pref.getBoolean(getString(R.string.pref_key_enable_dht),
                                TorrentEngine.DEFAULT_DHT_ENABLED));
                        TorrentEngine.getInstance().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_enable_lsd))) {
                    SettingsPack sp = TorrentEngine.getInstance().getSettings();
                    if (sp != null) {
                        sp.broadcastLSD(pref.getBoolean(getString(R.string.pref_key_enable_lsd),
                                TorrentEngine.DEFAULT_LSD_ENABLED));
                        TorrentEngine.getInstance().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_enable_utp))) {
                    SettingsPack sp = TorrentEngine.getInstance().getSettings();
                    if (sp != null) {
                        boolean enable = pref.getBoolean(getString(R.string.pref_key_enable_utp),
                                TorrentEngine.DEFAULT_UTP_ENABLED);
                        sp.setBoolean(settings_pack.bool_types.enable_incoming_utp.swigValue(), enable);
                        sp.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), enable);
                        TorrentEngine.getInstance().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_enable_upnp))) {
                    SettingsPack sp = TorrentEngine.getInstance().getSettings();
                    if (sp != null) {
                        sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(),
                                pref.getBoolean(getString(R.string.pref_key_enable_upnp),
                                        TorrentEngine.DEFAULT_UPNP_ENABLED));
                        TorrentEngine.getInstance().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_enable_natpmp))) {
                    SettingsPack sp = TorrentEngine.getInstance().getSettings();
                    if (sp != null) {
                        sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(),
                                pref.getBoolean(getString(R.string.pref_key_enable_natpmp),
                                        TorrentEngine.DEFAULT_NATPMP_ENABLED));
                        TorrentEngine.getInstance().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_enc_mode))) {
                    SettingsPack sp = TorrentEngine.getInstance().getSettings();
                    if (sp != null) {
                        int state = getEncryptMode();

                        sp.setInteger(settings_pack.int_types.in_enc_policy.swigValue(), state);
                        sp.setInteger(settings_pack.int_types.out_enc_policy.swigValue(), state);
                        TorrentEngine.getInstance().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_enc_in_connections))) {
                    SettingsPack sp = TorrentEngine.getInstance().getSettings();
                    if (sp != null) {
                        int state = settings_pack.enc_policy.pe_disabled.swigValue();

                        if (pref.getBoolean(getString(R.string.pref_key_enc_in_connections),
                                TorrentEngine.DEFAULT_ENCRYPT_IN_CONNECTIONS)) {
                            state = getEncryptMode();
                        }

                        sp.setInteger(settings_pack.int_types.in_enc_policy.swigValue(), state);
                        TorrentEngine.getInstance().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_enc_out_connections))) {
                    SettingsPack sp = TorrentEngine.getInstance().getSettings();
                    if (sp != null) {
                        int state = settings_pack.enc_policy.pe_disabled.swigValue();

                        if (pref.getBoolean(getString(R.string.pref_key_enc_out_connections),
                                TorrentEngine.DEFAULT_ENCRYPT_OUT_CONNECTIONS)) {
                            state = getEncryptMode();
                        }

                        sp.setInteger(settings_pack.int_types.out_enc_policy.swigValue(), state);
                        TorrentEngine.getInstance().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_use_random_port))) {
                    if (pref.getBoolean(getString(R.string.pref_key_use_random_port), false)) {
                        TorrentEngine.getInstance().setRandomPort();

                    } else {
                        TorrentEngine.getInstance().setPort(pref.getInt(getString(R.string.pref_key_port),
                                TorrentEngine.DEFAULT_PORT));
                    }

                } else if (item.key().equals(getString(R.string.pref_key_port))) {
                    TorrentEngine.getInstance().setPort(pref.getInt(getString(R.string.pref_key_port),
                            TorrentEngine.DEFAULT_PORT));
                } else if (item.key().equals(getString(R.string.pref_key_enable_ip_filtering))) {
                    if (pref.getBoolean(getString(R.string.pref_key_enable_ip_filtering), false)) {
                        TorrentEngine.getInstance().enableIpFilter(
                                pref.getString(getString(R.string.pref_key_ip_filtering_file), null));
                    } else {
                        TorrentEngine.getInstance().disableIpFilter();
                    }

                } else if (item.key().equals(getString(R.string.pref_key_ip_filtering_file))) {
                    TorrentEngine.getInstance().enableIpFilter(
                            pref.getString(getString(R.string.pref_key_ip_filtering_file), null));

                } else if (item.key().equals(getString(R.string.pref_key_apply_proxy))) {
                    pref.put(getString(R.string.pref_key_proxy_changed), false);
                    setProxy();
                    Toast.makeText(getApplicationContext(),
                            R.string.proxy_settings_applied,
                            Toast.LENGTH_SHORT)
                            .show();
                }
            }
        }
    }

    private int getEncryptMode()
    {
        int mode = pref.getInt(getString(R.string.pref_key_enc_mode),
                Integer.parseInt(getString(R.string.pref_enc_mode_prefer_value)));

        if (mode == Integer.parseInt(getString(R.string.pref_enc_mode_prefer_value))) {
            return settings_pack.enc_policy.pe_enabled.swigValue();
        } else if (mode == Integer.parseInt(getString(R.string.pref_enc_mode_require_value))) {
            return settings_pack.enc_policy.pe_forced.swigValue();
        } else {
            return settings_pack.enc_policy.pe_disabled.swigValue();
        }
    }

    private void setProxy()
    {
        ProxySettingsPack proxy = new ProxySettingsPack();

        ProxySettingsPack.ProxyType type = ProxySettingsPack.ProxyType.fromValue(
                pref.getInt(getString(R.string.pref_key_proxy_type),
                        ProxySettingsPack.ProxyType.NONE.value()));

        proxy.setType(type);
        if (type == ProxySettingsPack.ProxyType.NONE) {
            TorrentEngine.getInstance().setProxy(getApplicationContext(), proxy);
        }

        proxy.setAddress(pref.getString(getString(R.string.pref_key_proxy_address), ""));
        proxy.setPort(pref.getInt(getString(R.string.pref_key_proxy_port), 0));
        proxy.setProxyPeersToo(pref.getBoolean(getString(R.string.pref_key_proxy_peers_too), true));
        proxy.setForceProxy(pref.getBoolean(getString(R.string.pref_key_force_proxy), true));
        if (pref.getBoolean(getString(R.string.pref_key_proxy_requires_auth), false)) {
            proxy.setLogin(pref.getString(getString(R.string.pref_key_proxy_login), ""));
            proxy.setPassword(pref.getString(getString(R.string.pref_key_proxy_password), ""));
        }

        TorrentEngine.getInstance().setProxy(getApplicationContext(), proxy);
    }

    private void loadTorrents(Collection<Torrent> torrents)
    {
        if (torrents == null) {
            return;
        }

        for (Torrent torrent : torrents) {
            if (!torrent.isDownloadingMetadata() &&
                    !TorrentUtils.torrentFileExists(getApplicationContext(), torrent.getId()))
            {
                Log.e(TAG, "Torrent doesn't exists: " + torrent);
                makeTorrentErrorNotify(torrent, getString(R.string.torrent_does_not_exists_error));
                repo.delete(torrent);
                torrents.remove(torrent);
            }
        }

        TorrentEngine.getInstance().restoreDownloads(torrents);
    }


    public synchronized void addTorrent(Torrent torrent)
    {
        if (torrent == null) {
            return;
        }

        Throwable exception = null;
        boolean deleteTorrentFile = pref.getBoolean(getString(R.string.pref_key_delete_torrent_file), false);

        if (torrent.isDownloadingMetadata()) {
            if (!repo.exists(torrent)) {
                try {
                    repo.add(torrent,
                            torrent.getTorrentFilePath(),
                            false);

                } catch (Throwable e) {
                    exception = e;
                }
            }

        } else if (new File(torrent.getTorrentFilePath()).exists()) {
            try {
                if (repo.exists(torrent)) {
                    repo.replace(torrent,
                            torrent.getTorrentFilePath(),
                            deleteTorrentFile);

                    exception = new FileAlreadyExistsException();

                } else {
                    repo.add(torrent,
                            torrent.getTorrentFilePath(),
                            deleteTorrentFile);
                }

            } catch (Throwable e) {
                exception = e;
            }

        } else {
            exception = new FileNotFoundException();
        }

        torrent = repo.getTorrentByID(torrent.getId());
        if (torrent != null) {
            sendOnAddTorrent(new TorrentStateParcel(torrent.getId(), torrent.getName()), exception);

        } else {
            sendOnAddTorrent(null, exception);

            return;
        }

        if (!torrent.isDownloadingMetadata() && !TorrentUtils.torrentDataExists(getApplicationContext(), torrent.getId()))
        {
            Log.e(TAG, "Torrent doesn't exists: " + torrent.getName());
            repo.delete(torrent);

            return;
        }

        if (magnetList.containsKey(torrent.getId())) {
            TorrentDownload task = magnetList.remove(torrent.getId());
            task.setTorrent(torrent);
            torrentTasks.put(torrent.getId(), task);

            if (!torrent.isDownloadingMetadata()) {
                task.setSequentialDownload(torrent.isSequentialDownload());
                if (torrent.isPaused()) {
                    task.pause();
                } else {
                    task.resume(true);
                }
                task.setDownloadPath(torrent.getDownloadPath());
            }

        } else {
            if (torrentTasks.containsKey(torrent.getId())) {
                TorrentDownload task = torrentTasks.get(torrent.getId());
                if (task != null) {
                    task.remove(false);
                }
                torrentTasks.remove(torrent.getId());
            }

            TorrentEngine.getInstance().download(torrent);
        }
    }

    public synchronized void pauseResumeTorrents(List<String> ids)
    {
        for (String id : ids) {
            if (id == null) {
                continue;
            }

            pauseResumeTorrent(id);
        }
    }

    public synchronized void pauseResumeTorrent(String id)
    {
        if (id == null) {
            return;
        }

        TorrentDownload task = torrentTasks.get(id);
        try {
            if (task.isPaused()) {
                task.resume(false);
            } else {
                task.pause();
            }

        } catch (Exception e) {
            /* Ignore */
        }
    }

    public synchronized void forceRecheckTorrents(List<String> ids)
    {
        if (ids == null) {
            return;
        }

        for (String id : ids) {
            if (id == null) {
                continue;
            }

            torrentTasks.get(id).forceRecheck();
        }
    }

    public synchronized void forceAnnounceTorrents(List<String> ids)
    {
        if (ids == null) {
            return;
        }

        for (String id : ids) {
            if (id == null) {
                continue;
            }

            torrentTasks.get(id).requestTrackerAnnounce();
        }
    }

    public synchronized void deleteTorrents(List<String> ids, boolean withFiles)
    {
        if (ids != null) {
            for (String id : ids) {
                TorrentDownload task = torrentTasks.get(id);
                if (task != null) {
                    task.remove(withFiles);
                }
                repo.delete(id);
            }
        }

        needsUpdateNotify.set(true);
        sendTorrentsStateOneShot();
    }

    public synchronized void setTorrentName(String id, String name)
    {
        if (id == null || name == null) {
            return;
        }

        Torrent torrent = repo.getTorrentByID(id);

        if (torrent == null) {
            return;
        }

        torrent.setName(name);
        repo.update(torrent);

        TorrentDownload task = torrentTasks.get(id);
        if (task != null) {
            task.setTorrent(torrent);

            needsUpdateNotify.set(true);
            sendTorrentsStateOneShot();
        }
    }

    private void moveTorrent(Torrent torrent)
    {
        if (torrent == null) {
            return;
        }

        repo.update(torrent);

        TorrentDownload task = torrentTasks.get(torrent.getId());
        if (task != null) {
            if (torrentsMoveSuccess == null) {
                torrentsMoveSuccess = new ArrayList<>();
            }

            if (torrentsMoveFailed == null) {
                torrentsMoveFailed = new ArrayList<>();
            }

            if (torrentsMoveTotal == null) {
                torrentsMoveTotal = 0;
            }

            ++torrentsMoveTotal;

            task.setTorrent(torrent);
            task.setDownloadPath(torrent.getDownloadPath());
        }
    }

    public synchronized void setTorrentDownloadPath(ArrayList<String> ids, String path)
    {
        if (ids == null || path == null || TextUtils.isEmpty(path)) {
            return;
        }

        for (String id : ids) {
            Torrent torrent = repo.getTorrentByID(id);

            if (torrent == null) {
                return;
            }

            torrent.setDownloadPath(path);
            repo.update(torrent);

            TorrentDownload task = torrentTasks.get(id);
            if (task != null) {
                if (torrentsMoveSuccess == null) {
                    torrentsMoveSuccess = new ArrayList<>();
                }

                if (torrentsMoveFailed == null) {
                    torrentsMoveFailed = new ArrayList<>();
                }

                if (torrentsMoveTotal == null) {
                    torrentsMoveTotal = 0;
                }

                ++torrentsMoveTotal;

                task.setTorrent(torrent);
                task.setDownloadPath(path);
            }
        }
    }

    public synchronized void setSequentialDownload(String id, boolean sequential)
    {
        if (id == null) {
            return;
        }

        Torrent torrent = repo.getTorrentByID(id);

        if (torrent == null) {
            return;
        }

        torrent.setSequentialDownload(sequential);
        repo.update(torrent);

        TorrentDownload task = torrentTasks.get(id);
        if (task != null) {
            task.setTorrent(torrent);
            task.setSequentialDownload(sequential);
        }
    }

    public synchronized void changeFilesPriority(String id, ArrayList<Integer> priorities)
    {
        if (id == null || (priorities == null || priorities.size() == 0)) {
            return;
        }

        Torrent torrent = repo.getTorrentByID(id);

        if (torrent == null) {
            return;
        }

        torrent.setFilePriorities(priorities);
        repo.update(torrent);

        TorrentDownload task = torrentTasks.get(id);
        if (task != null) {
            task.setTorrent(torrent);

            Priority[] list = new Priority[priorities.size()];

            for (int i = 0; i < priorities.size(); i++) {
                list[i] = Priority.fromSwig(priorities.get(i));
            }

            task.prioritizeFiles(list);
        }
    }

    public synchronized void replaceTrackers(String id, ArrayList<String> urls)
    {
        if (id == null || urls == null) {
            return;
        }

        TorrentDownload task = torrentTasks.get(id);
        if (task != null) {
            task.replaceTrackers(new HashSet<>(urls));
        }
    }

    public synchronized void addTrackers(String id, ArrayList<String> urls)
    {
        if (id == null || urls == null) {
            return;
        }

        TorrentDownload task = torrentTasks.get(id);
        if (task != null) {
            task.addTrackers(new HashSet<>(urls));
        }
    }

    public String getMagnet(String id)
    {
        if (id == null) {
            return null;
        }

        TorrentDownload task = torrentTasks.get(id);

        if (task == null) {
            return null;
        }

        return task.makeMagnet();
    }

    public void setUploadSpeedLimit(String id, int limit)
    {
        if (id == null) {
            return;
        }

        TorrentDownload task = torrentTasks.get(id);
        if (task != null) {
            task.setUploadSpeedLimit(limit);
        }
    }

    public void setDownloadSpeedLimit(String id, int limit)
    {
        if (id == null) {
            return;
        }

        TorrentDownload task = torrentTasks.get(id);
        if (task != null) {
            task.setDownloadSpeedLimit(limit);
        }
    }

    public void setGlobalUploadSpeedLimit(int limit)
    {
        TorrentEngine.getInstance().setUploadSpeedLimit(limit);
    }

    public void setGlobalDownloadSpeedLimit(int limit)
    {
        TorrentEngine.getInstance().setDownloadSpeedLimit(limit);
    }

    private void pauseAll()
    {
        for (TorrentDownload task : torrentTasks.values()) {
            if (task == null) {
                continue;
            }

            task.pause();
        }
    }

    private void resumeAll()
    {
        for (TorrentDownload task : torrentTasks.values()) {
            if (task == null) {
                continue;
            }

            task.resume(false);
        }
    }

    private void setKeepCpuAwake(boolean enable)
    {
        if (enable) {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            }

            if (!wakeLock.isHeld()) {
                wakeLock.acquire();
            }

        } else {
            if (wakeLock == null) {
                return;
            }

            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    public TorrentStateParcel makeTorrentStateParcel(TorrentDownload task)
    {
        if (task == null) {
            return null;
        }

        Torrent torrent = task.getTorrent();

        return new TorrentStateParcel(
                torrent.getId(),
                torrent.getName(),
                task.getStateCode(),
                task.getProgress(),
                task.getTotalReceivedBytes(),
                task.getTotalSentBytes(),
                task.getTotalWanted(),
                task.getDownloadSpeed(),
                task.getUploadSpeed(),
                task.getETA(),
                task.getFilesReceivedBytes(),
                task.getTotalSeeds(),
                task.getConnectedSeeds(),
                task.getTotalPeers(),
                task.getConnectedPeers(),
                task.getNumDownloadedPieces(),
                task.getShareRatio(),
                torrent.getDateAdded());
    }

    private ArrayList<TrackerStateParcel> makeTrackerStateParcelList(TorrentDownload task)
    {
        if (task == null) {
            return null;
        }

        List<AnnounceEntry> trackers = task.getTrackers();
        ArrayList<TrackerStateParcel> states = new ArrayList<>();

        int statusDHT = TrackerStateParcel.Status.NOT_WORKING;
        int statusLSD = TrackerStateParcel.Status.NOT_WORKING;
        int statusPeX = TrackerStateParcel.Status.NOT_WORKING;

        if (TorrentEngine.getInstance().isDHTEnabled()) {
            statusDHT = TrackerStateParcel.Status.WORKING;
        }

        if (TorrentEngine.getInstance().isLSDEnabled()) {
            statusLSD = TrackerStateParcel.Status.WORKING;
        }

        if (TorrentEngine.getInstance().isPeXEnabled()) {
            statusPeX = TrackerStateParcel.Status.WORKING;
        }

        states.add(new TrackerStateParcel(TrackerStateParcel.DHT_ENTRY_NAME, "", -1, statusDHT));
        states.add(new TrackerStateParcel(TrackerStateParcel.LSD_ENTRY_NAME, "", -1, statusLSD));
        states.add(new TrackerStateParcel(TrackerStateParcel.PEX_ENTRY_NAME, "", -1, statusPeX));

        for (AnnounceEntry entry : trackers) {
            String url = entry.url();
            /* Prevent duplicate */
            if (url.equals(TrackerStateParcel.DHT_ENTRY_NAME) ||
                    url.equals(TrackerStateParcel.LSD_ENTRY_NAME) ||
                    url.equals(TrackerStateParcel.PEX_ENTRY_NAME)) {
                continue;
            }

            states.add(new TrackerStateParcel(entry.swig()));
        }

        return states;
    }

    private ArrayList<PeerStateParcel> makePeerStateParcelList(TorrentDownload task)
    {
        if (task == null) {
            return null;
        }

        ArrayList<PeerStateParcel> states = new ArrayList<>();
        ArrayList<PeerInfo> peers = task.getPeers();

        TorrentStatus status = task.getTorrentStatus();

        for (PeerInfo peer : peers) {
            PeerStateParcel state = new PeerStateParcel(peer.swig(), status);
            states.add(state);
        }

        return states;
    }

    public Bundle makeTorrentsStateList()
    {
        Bundle states = new Bundle();
        for (TorrentDownload task : torrentTasks.values()) {
            if (task != null) {
                TorrentStateParcel state = makeTorrentStateParcel(task);

                if (!stateCache.contains(state)) {
                    stateCache.put(state);
                }

                states.putParcelable(state.torrentId, state);
            }
        }

        return states;
    }

    public void sendTorrentsStateOneShot(TorrentServiceCallback callback)
    {
        if (callback == null) {
            return;
        }

        callback.onTorrentsStateChanged(makeTorrentsStateList());
    }

    private void sendTorrentsStateOneShot()
    {
        Bundle states = makeTorrentsStateList();

        for (TorrentServiceCallback callback : clientCallbacks) {
            if (callback != null) {
                callback.onTorrentsStateChanged(states);
            }
        }
    }

    private void sendTorrentState(TorrentDownload task)
    {
        if (task == null) {
            return;
        }

        Torrent torrent = task.getTorrent();
        if (torrent == null) {
            return;
        }

        TorrentStateParcel state = makeTorrentStateParcel(task);

        if (stateCache.contains(state)) {
            return;

        } else {
            stateCache.put(state);
            /* Update foreground notification only if added a new package to the cache */
            needsUpdateNotify.set(true);
        }

        for (TorrentServiceCallback callback : clientCallbacks) {
            if (callback != null) {
                callback.onTorrentStateChanged(state);
            }
        }
    }

    public TorrentMetaInfo getTorrentInfo(String id)
    {
        if (id == null) {
            return null;
        }

        TorrentDownload task = torrentTasks.get(id);

        if (task == null) {
            return null;
        }

        TorrentInfo ti = task.getTorrentInfo();

        TorrentMetaInfo info = null;

        try {
            if (ti != null) {
                info = new TorrentMetaInfo(ti);

            } else {
                info = new TorrentMetaInfo(task.getTorrent().getName(), task.getInfoHash());
            }

        } catch (DecodeException e) {
            Log.e(TAG, "Can't decode torrent info: ");
            Log.e(TAG, Log.getStackTraceString(e));
        }

        return info;
    }

    public long getActiveTime(String id)
    {
        if (id == null) {
            return -1;
        }

        TorrentDownload task = torrentTasks.get(id);

        if (task == null) {
            return -1;
        }

        return task.getActiveTime();
    }

    public long getSeedingTime(String id)
    {
        if (id == null) {
            return -1;
        }

        TorrentDownload task = torrentTasks.get(id);

        if (task == null) {
            return -1;
        }

        return task.getSeedingTime();
    }

    public ArrayList<TrackerStateParcel> getTrackerStatesList(String id)
    {
        if (id == null) {
            return null;
        }

        TorrentDownload task = torrentTasks.get(id);

        if (task == null) {
            return null;
        }

        return makeTrackerStateParcelList(task);
    }

    public ArrayList<PeerStateParcel> getPeerStatesList(String id)
    {
        if (id == null) {
            return null;
        }

        TorrentDownload task = torrentTasks.get(id);

        if (task == null) {
            return null;
        }

        return makePeerStateParcelList(task);
    }

    public boolean[] getPieces(String id)
    {
        if (id == null) {
            return null;
        }

        TorrentDownload task = torrentTasks.get(id);

        if (task == null) {
            return null;
        }

        return task.pieces();
    }

    private void sendOnAddTorrent(TorrentStateParcel state, Throwable exception)
    {
        for (TorrentServiceCallback callback : clientCallbacks) {
            callback.onTorrentAdded(state, exception);
        }
    }

    public int getUploadSpeedLimit(String id)
    {
        if (id == null) {
            return -1;
        }

        TorrentDownload task = torrentTasks.get(id);

        if (task == null) {
            return -1;
        }

        return task.getUploadSpeedLimit();
    }

    public int getDownloadSpeedLimit(String id)
    {
        if (id == null) {
            return -1;
        }

        TorrentDownload task = torrentTasks.get(id);

        if (task == null) {
            return -1;
        }

        return task.getDownloadSpeedLimit();
    }

    public int getGlobalUploadSpeedLimit()
    {
        return TorrentEngine.getInstance().getUploadSpeedLimit();
    }

    public int getGlobalDownloadSpeedLimit()
    {
        return TorrentEngine.getInstance().getDownloadSpeedLimit();
    }

    public String fetchMagnet(String uri, FetchMagnetCallback callback) throws Exception
    {
        if (callback != null && !magnetCallbacks.contains(callback)) {
            magnetCallbacks.add(callback);
        }

        TorrentDownload task = TorrentEngine.getInstance().fetchMagnet(uri);
        if (task != null) {
            String hash = task.getInfoHash();
            magnetList.put(hash, task);

            return hash;
        }

        return null;
    }

    /*
     * Used only for magnets from the magnetList (non added magnets)
     */

    public void removeMagnet(String infoHash)
    {
        if (infoHash == null || torrentTasks.containsKey(infoHash)) {
            return;
        }

        magnetList.remove(infoHash);
        TorrentEngine.getInstance().removeMagnet(infoHash);
    }

    public void addFetchMagnetCallback(FetchMagnetCallback callback)
    {
        if (callback != null) {
            magnetCallbacks.add(callback);
        }
    }

    public void removeFetchMagnetCallback(FetchMagnetCallback callback)
    {
        if (callback != null && magnetCallbacks.contains(callback)) {
            magnetCallbacks.remove(callback);
        }
    }

    private Runnable updateForegroundNotify = new Runnable()
    {
        @Override
        public void run()
        {
            if (isAlreadyRunning) {
                boolean online = TorrentEngine.getInstance().isListening();
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

                            if (!torrentTasks.isEmpty()) {
                                foregroundNotify.setStyle(makeDetailNotifyInboxStyle());
                            } else {
                                foregroundNotify.setStyle(null);
                            }

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
        if (updateForegroundNotifyHandler != null) {
            return;
        }

        updateForegroundNotifyHandler.removeCallbacks(updateForegroundNotify);
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

        foregroundNotify = new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.ic_app_notification)
                .setContentIntent(startupPendingIntent)
                .setContentTitle(getString(R.string.app_running_in_the_background))
                .setTicker(getString(R.string.app_running_in_the_background))
                .setContentText((isNetworkOnline ?
                        getString(R.string.network_online) :
                        getString(R.string.network_offline)))
                .setWhen(System.currentTimeMillis());

        /* For calling add torrent dialog */
        Intent addTorrentIntent =
                new Intent(getApplicationContext(), NotificationReceiver.class);
        addTorrentIntent.setAction(NotificationReceiver.NOTIFY_ACTION_ADD_TORRENT);

        PendingIntent addTorrentPendingIntent =
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        0,
                        addTorrentIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Action addTorrentAction =
                new NotificationCompat.Action.Builder(
                        R.drawable.ic_add_white_36dp,
                        getString(R.string.add),
                        addTorrentPendingIntent)
                        .build();

        foregroundNotify.addAction(addTorrentAction);

        /* For shutdown activity and service */
        Intent shutdownIntent =
                new Intent(getApplicationContext(), NotificationReceiver.class);
        shutdownIntent.setAction(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP);

        PendingIntent shutdownPendingIntent =
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        0,
                        shutdownIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Action shutdownAction =
                new NotificationCompat.Action.Builder(
                        R.drawable.ic_power_settings_new_white_24dp,
                        getString(R.string.shutdown),
                        shutdownPendingIntent)
                        .build();

        foregroundNotify.addAction(shutdownAction);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            foregroundNotify.setCategory(Notification.CATEGORY_SERVICE);
        }

        /* Disallow killing the service process by system */
        startForeground(SERVICE_STARTED_NOTIFICATION_ID, foregroundNotify.build());
    }

    private NotificationCompat.InboxStyle makeDetailNotifyInboxStyle()
    {
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

        String titleTemplate = getString(R.string.torrent_count_notify_template);

        int downloadingCount = 0;

        for (TorrentDownload task : torrentTasks.values()) {
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
                torrentTasks.size()));

        inboxStyle.setSummaryText((isNetworkOnline ?
            getString(R.string.network_online) :
            getString(R.string.network_offline)));

        return inboxStyle;
    }

    private void makeFinishNotify(Torrent torrent)
    {
        if (torrent == null || notifyManager == null ||
                !pref.getBoolean(getString(R.string.pref_key_torrent_finish_notify), true)) {
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.ic_done_white_24dp)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
                .setContentTitle(getString(R.string.torrent_finished_notify))
                .setTicker(getString(R.string.torrent_finished_notify))
                .setContentText(torrent.getName())
                .setWhen(System.currentTimeMillis());

        if (pref.getBoolean(getString(R.string.pref_key_play_sound_notify), true)) {
            Uri sound = Uri.parse(pref.getString(getString(R.string.pref_key_notify_sound),
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString()));

            builder.setSound(sound);
        }

        if (pref.getBoolean(getString(R.string.pref_key_vibration_notify), true)) {
            /* TODO: Make the ability to customize vibration */
            long[] vibration = new long[] {1000}; /* ms */

            builder.setVibrate(vibration);
        }

        if (pref.getBoolean(getString(R.string.pref_key_led_indicator_notify), true)) {
            int color = pref.getInt(getString(R.string.pref_key_led_indicator_color_notify),
                    ContextCompat.getColor(getApplicationContext(), R.color.primary));

            builder.setLights(color, 1000, 1000); /* ms */
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_STATUS);
        }

        notifyManager.notify(torrent.getId().hashCode(), builder.build());
    }

    private void makeTorrentErrorNotify(Torrent torrent, String message)
    {
        if (torrent == null || message == null) {
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.ic_error_white_24dp)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
                .setContentTitle(torrent.getName())
                .setTicker(getString(R.string.torrent_error_notify_title))
                .setContentText(String.format(getString(R.string.torrent_error_notify_template), message))
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_ERROR);
        }

        notifyManager.notify(torrent.getId().hashCode(), builder.build());
    }

    private synchronized void makeTorrentsMoveNotify()
    {
        if (torrentsMoveTotal == null ||
                torrentsMoveSuccess == null ||
                torrentsMoveFailed == null ||
                notifyManager == null) {
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());

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
}
