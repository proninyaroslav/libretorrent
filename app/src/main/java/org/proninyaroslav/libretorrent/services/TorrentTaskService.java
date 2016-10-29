/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
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
import org.proninyaroslav.libretorrent.core.EngineTask;
import org.proninyaroslav.libretorrent.core.ProxySettingsPack;
import org.proninyaroslav.libretorrent.core.Torrent;
import org.proninyaroslav.libretorrent.core.TorrentDownload;
import org.proninyaroslav.libretorrent.core.TorrentEngine;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.exceptions.FileAlreadyExistsException;
import org.proninyaroslav.libretorrent.core.stateparcel.PeerStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.TorrentStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.StateParcelCache;
import org.proninyaroslav.libretorrent.core.TorrentTaskServiceIPC;
import org.proninyaroslav.libretorrent.core.exceptions.DecodeException;
import org.proninyaroslav.libretorrent.core.stateparcel.TrackerStateParcel;
import org.proninyaroslav.libretorrent.core.storage.TorrentStorage;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.core.TorrentEngineCallback;
import org.proninyaroslav.libretorrent.receivers.NotificationReceiver;
import org.proninyaroslav.libretorrent.receivers.PowerReceiver;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

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
    Handler updateForegroundNotifyHandler;
    NotificationCompat.Builder foregroundNotify;
    /* TorrentEngine task */
    EngineTask engineTask;
    ExecutorService exec;
    /* Tasks list */
    private ConcurrentHashMap<String, TorrentDownload> torrentTasks = new ConcurrentHashMap<>();
    /* IPC communication */
    private Messenger messenger = new Messenger(new CallbackHandler(this));
    /* List of connected clients */
    private List<Messenger> clientCallbacks = new ArrayList<>();
    private TorrentTaskServiceIPC ipc = new TorrentTaskServiceIPC();
    private ReentrantLock sync;
    private TorrentStorage repo;
    private SettingsManager pref;
    private PowerManager.WakeLock wakeLock;
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
        exec = Executors.newSingleThreadExecutor();
        sync = new ReentrantLock();
        needsUpdateNotify = new AtomicBoolean(false);
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

        ComponentName powerReceiver = new ComponentName(getApplicationContext(), PowerReceiver.class);
        getPackageManager().setComponentEnabledSetting(powerReceiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

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

        engineTask = new EngineTask(getApplicationContext(), this);
        exec.execute(engineTask);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        setKeepCpuAwake(false);

        /* Handles must be destructed before the session is destructed */
        torrentTasks.clear();

        if (engineTask != null) {
            engineTask.cancel();
        }

        ComponentName powerReceiver = new ComponentName(getApplicationContext(), PowerReceiver.class);
        getPackageManager().setComponentEnabledSetting(powerReceiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        isAlreadyRunning = false;
        repo = null;
        pref.unregisterOnTrayPreferenceChangeListener(this);
        pref = null;

        Log.i(TAG, "Stop " + TorrentTaskService.class.getSimpleName());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if (intent != null) {
            ArrayList<String> ids =
                    intent.getStringArrayListExtra(TorrentTaskServiceIPC.TAG_TORRENT_IDS_LIST);

            if (ids != null) {
                List<Torrent> torrents = new ArrayList<>();

                for (String id: ids) {
                    torrents.add(repo.getTorrentByID(id));
                }

                addTorrents(torrents);
            }
        }

        if (isAlreadyRunning) {
            if (intent != null && intent.getAction() != null) {
                switch (intent.getAction()) {
                    case NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP:
                        ipc.sendTerminateAllClients(clientCallbacks);
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

    @Override
    public IBinder onBind(Intent intent)
    {
        return messenger.getBinder();
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
        pref.put(getString(R.string.pref_key_port), engineTask.getEngine().getPort());

        if (pref.getBoolean(getString(R.string.pref_key_proxy_changed), false)) {
            pref.put(getString(R.string.pref_key_proxy_changed), false);
            setProxy();
        }

        if (pref.getBoolean(getString(R.string.pref_key_enable_ip_filtering), false)) {
            engineTask.getEngine().enableIpFilter(
                    pref.getString(getString(R.string.pref_key_ip_filtering_file), null));
        }
    }

    @Override
    public void onEngineInterrupted()
    {
        torrentTasks.clear();

        /* Reset the notification to the default template without torrents list */
        makeForegroundNotify();
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

            torrent = repo.getTorrentByID(id);

            TorrentDownload task = torrentTasks.get(id);

            if (task != null && torrent != null) {
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

        torrent = repo.getTorrentByID(id);

        if (task != null && torrent != null) {
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

            torrent = repo.getTorrentByID(id);

            TorrentDownload task = torrentTasks.get(id);

            if (task != null && torrent != null) {
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
    public void onTrayPreferenceChanged(Collection<TrayItem> items)
    {
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
                    engineTask.getEngine().setDownloadSpeedLimit(pref.getInt(item.key(), 0));

                } else if (item.key().equals(getString(R.string.pref_key_max_upload_speed))) {
                    engineTask.getEngine().setUploadSpeedLimit(pref.getInt(item.key(), 0));

                } else if (item.key().equals(getString(R.string.pref_key_max_connections))) {
                    SettingsPack sp = engineTask.getEngine().getSettings();
                    if (sp != null) {
                        sp.setConnectionsLimit(pref.getInt(item.key(), TorrentEngine.DEFAULT_CONNECTIONS_LIMIT));
                        engineTask.getEngine().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_max_active_downloads))) {
                    SettingsPack sp = engineTask.getEngine().getSettings();
                    if (sp != null) {
                        sp.activeDownloads(pref.getInt(item.key(), TorrentEngine.DEFAULT_ACTIVE_DOWNLOADS));
                        engineTask.getEngine().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_max_active_uploads))) {
                    SettingsPack sp = engineTask.getEngine().getSettings();
                    if (sp != null) {
                        sp.activeSeeds(pref.getInt(item.key(), TorrentEngine.DEFAULT_ACTIVE_SEEDS));
                        engineTask.getEngine().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_max_active_torrents))) {
                    SettingsPack sp = engineTask.getEngine().getSettings();
                    if (sp != null) {
                        sp.activeLimit(pref.getInt(item.key(), TorrentEngine.DEFAULT_ACTIVE_LIMIT));
                        engineTask.getEngine().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_cpu_do_not_sleep))) {
                    setKeepCpuAwake(pref.getBoolean(getString(R.string.pref_key_cpu_do_not_sleep), false));

                } else if (item.key().equals(getString(R.string.pref_key_enable_dht))) {
                    SettingsPack sp = engineTask.getEngine().getSettings();
                    if (sp != null) {
                        sp.enableDht(pref.getBoolean(getString(R.string.pref_key_enable_dht),
                                TorrentEngine.DEFAULT_DHT_ENABLED));
                        engineTask.getEngine().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_enable_lsd))) {
                    SettingsPack sp = engineTask.getEngine().getSettings();
                    if (sp != null) {
                        sp.broadcastLSD(pref.getBoolean(getString(R.string.pref_key_enable_lsd),
                                TorrentEngine.DEFAULT_LSD_ENABLED));
                        engineTask.getEngine().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_enable_utp))) {
                    SettingsPack sp = engineTask.getEngine().getSettings();
                    if (sp != null) {
                        boolean enable = pref.getBoolean(getString(R.string.pref_key_enable_utp),
                                TorrentEngine.DEFAULT_UTP_ENABLED);
                        sp.setBoolean(settings_pack.bool_types.enable_incoming_utp.swigValue(), enable);
                        sp.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), enable);
                        engineTask.getEngine().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_enable_upnp))) {
                    SettingsPack sp = engineTask.getEngine().getSettings();
                    if (sp != null) {
                        sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(),
                                pref.getBoolean(getString(R.string.pref_key_enable_upnp),
                                        TorrentEngine.DEFAULT_UPNP_ENABLED));
                        engineTask.getEngine().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_enable_natpmp))) {
                    SettingsPack sp = engineTask.getEngine().getSettings();
                    if (sp != null) {
                        sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(),
                                pref.getBoolean(getString(R.string.pref_key_enable_natpmp),
                                        TorrentEngine.DEFAULT_NATPMP_ENABLED));
                        engineTask.getEngine().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_enc_mode))) {
                    SettingsPack sp = engineTask.getEngine().getSettings();
                    if (sp != null) {
                        int state = getEncryptMode();

                        sp.setInteger(settings_pack.int_types.in_enc_policy.swigValue(), state);
                        sp.setInteger(settings_pack.int_types.out_enc_policy.swigValue(), state);
                        engineTask.getEngine().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_enc_in_connections))) {
                    SettingsPack sp = engineTask.getEngine().getSettings();
                    if (sp != null) {
                        int state = settings_pack.enc_policy.pe_disabled.swigValue();

                        if (pref.getBoolean(getString(R.string.pref_key_enc_in_connections),
                                TorrentEngine.DEFAULT_ENCRYPT_IN_CONNECTIONS)) {
                            state = getEncryptMode();
                        }

                        sp.setInteger(settings_pack.int_types.in_enc_policy.swigValue(), state);
                        engineTask.getEngine().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_enc_out_connections))) {
                    SettingsPack sp = engineTask.getEngine().getSettings();
                    if (sp != null) {
                        int state = settings_pack.enc_policy.pe_disabled.swigValue();

                        if (pref.getBoolean(getString(R.string.pref_key_enc_out_connections),
                                TorrentEngine.DEFAULT_ENCRYPT_OUT_CONNECTIONS)) {
                            state = getEncryptMode();
                        }

                        sp.setInteger(settings_pack.int_types.out_enc_policy.swigValue(), state);
                        engineTask.getEngine().setSettings(sp);
                    }

                } else if (item.key().equals(getString(R.string.pref_key_port))) {
                    engineTask.getEngine().setPort(pref.getInt(getString(R.string.pref_key_port),
                            TorrentEngine.DEFAULT_PORT));
                } else if (item.key().equals(getString(R.string.pref_key_enable_ip_filtering))) {
                    if (pref.getBoolean(getString(R.string.pref_key_enable_ip_filtering), false)) {
                        engineTask.getEngine().enableIpFilter(
                                pref.getString(getString(R.string.pref_key_ip_filtering_file), null));
                    } else {
                        engineTask.getEngine().disableIpFilter();
                    }

                } else if (item.key().equals(getString(R.string.pref_key_ip_filtering_file))) {
                    engineTask.getEngine().enableIpFilter(
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
            engineTask.getEngine().setProxy(getApplicationContext(), proxy);
        }

        proxy.setAddress(pref.getString(getString(R.string.pref_key_proxy_address), ""));
        proxy.setPort(pref.getInt(getString(R.string.pref_key_proxy_port), 0));
        proxy.setProxyPeersToo(pref.getBoolean(getString(R.string.pref_key_proxy_peers_too), true));
        proxy.setForceProxy(pref.getBoolean(getString(R.string.pref_key_force_proxy), true));
        if (pref.getBoolean(getString(R.string.pref_key_proxy_requires_auth), false)) {
            proxy.setLogin(pref.getString(getString(R.string.pref_key_proxy_login), ""));
            proxy.setPassword(pref.getString(getString(R.string.pref_key_proxy_password), ""));
        }

        engineTask.getEngine().setProxy(getApplicationContext(), proxy);
    }

    private void loadTorrents(Collection<Torrent> torrents)
    {
        if (torrents == null || engineTask == null) {
            return;
        }

        for (Torrent torrent: torrents) {
            if (!TorrentUtils.torrentDataExists(getApplicationContext(), torrent.getId())) {
                Log.e(TAG, "Torrent doesn't exists: " + torrent.getName());
                repo.delete(torrent);
            }
        }

        engineTask.getEngine().asyncDownload(torrents);
    }

    private void addTorrent(Torrent torrent)
    {
        if (torrent == null || engineTask == null) {
            return;
        }

        if (!TorrentUtils.torrentDataExists(getApplicationContext(), torrent.getId())) {
            Log.e(TAG, "Torrent doesn't exists: " + torrent.getName());
            repo.delete(torrent);

            return;
        }

        if (torrentTasks.containsKey(torrent.getId())) {
            TorrentDownload task = torrentTasks.get(torrent.getId());
            if (task != null) {
                task.remove(false);
            }
            torrentTasks.remove(torrent.getId());
        }

        TorrentDownload task = engineTask.getEngine().download(torrent);
        torrentTasks.put(torrent.getId(), task);
        if (pauseTorrents.get()) {
            task.pause();
        }
    }

    private void addTorrents(Collection<Torrent> torrents)
    {
        if (torrents == null || engineTask == null) {
            return;
        }

        for (Torrent torrent : torrents) {
            if (!TorrentUtils.torrentDataExists(getApplicationContext(), torrent.getId())) {
                Log.e(TAG, "Torrent doesn't exists: " + torrent.getName());
                repo.delete(torrent);

                continue;
            }

            if (torrentTasks.containsKey(torrent.getId())) {
                TorrentDownload task = torrentTasks.get(torrent.getId());
                if (task != null) {
                    task.remove(false);
                }
                torrentTasks.remove(torrent.getId());
            }

            TorrentDownload task = engineTask.getEngine().download(torrent);

            torrentTasks.put(torrent.getId(), task);
            if (pauseTorrents.get()) {
                task.pause();
            }
        }
    }

    private void deleteTorrents(ArrayList<String> ids, boolean withFiles)
    {
        sync.lock();

        try {
            if (ids != null) {
                for (String id : ids) {
                    torrentTasks.get(id).remove(withFiles);
                    repo.delete(id);
                }
            }

            needsUpdateNotify.set(true);
            sendTorrentsStateOneShot();

        } catch (Exception e) {
            /* Ignore */
        } finally {
            sync.unlock();
        }
    }

    private void setTorrentName(String id, String name)
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
        torrent = repo.getTorrentByID(id);

        TorrentDownload task = torrentTasks.get(id);
        if (task != null && torrent != null) {
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
        torrent = repo.getTorrentByID(torrent.getId());

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

    private void setTorrentDownloadPath(ArrayList<String> ids, String path)
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
            torrent = repo.getTorrentByID(id);

            TorrentDownload task = torrentTasks.get(id);
            if (task != null && torrent != null) {
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

    private void setSequentialDownload(String id, boolean sequential)
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
        torrent = repo.getTorrentByID(id);

        TorrentDownload task = torrentTasks.get(id);
        if (task != null && torrent != null) {
            task.setTorrent(torrent);
            task.setSequentialDownload(sequential);
        }
    }

    private void changeFilesPriority(String id, ArrayList<Integer> priorities)
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

        torrent = repo.getTorrentByID(id);

        TorrentDownload task = torrentTasks.get(id);
        if (task != null && torrent != null) {
            task.setTorrent(torrent);

            Priority[] list = new Priority[priorities.size()];

            for (int i = 0; i < priorities.size(); i++) {
                list[i] = Priority.fromSwig(priorities.get(i));
            }

            task.prioritizeFiles(list);
        }
    }

    private void replaceTrackers(String id, ArrayList<String> urls)
    {
        if (id == null || urls == null) {
            return;
        }

        TorrentDownload task = torrentTasks.get(id);
        if (task != null) {
            task.replaceTrackers(new HashSet<>(urls));
        }
    }

    private void addTrackers(String id, ArrayList<String> urls)
    {
        if (id == null || urls == null) {
            return;
        }

        TorrentDownload task = torrentTasks.get(id);
        if (task != null) {
            task.addTrackers(new HashSet<>(urls));
        }
    }

    /*
     * if id is null set global limit
     */

    private void setUploadSpeedLimit(String id, int limit)
    {
        if (id == null && engineTask != null) {
            engineTask.getEngine().setUploadSpeedLimit(limit);
        } else {
            TorrentDownload task = torrentTasks.get(id);
            if (task != null) {
                task.setUploadSpeedLimit(limit);
            }
        }
    }

    /*
     * if id is null set global limit
     */

    private void setDownloadSpeedLimit(String id, int limit)
    {
        if (id == null && engineTask != null) {
            engineTask.getEngine().setDownloadSpeedLimit(limit);
        } else {
            TorrentDownload task = torrentTasks.get(id);
            if (task != null) {
                task.setDownloadSpeedLimit(limit);
            }
        }
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

            task.resume();
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

    private TorrentStateParcel makeTorrentStateParcel(TorrentDownload task)
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
                task.getShareRatio());
    }

    private ArrayList<TrackerStateParcel> makeTrackerStateParcelList(TorrentDownload task)
    {
        if (task == null || engineTask == null) {
            return null;
        }

        List<AnnounceEntry> trackers = task.getTrackers();
        ArrayList<TrackerStateParcel> states = new ArrayList<>();

        int statusDHT = TrackerStateParcel.Status.NOT_WORKING;
        int statusLSD = TrackerStateParcel.Status.NOT_WORKING;
        int statusPeX = TrackerStateParcel.Status.NOT_WORKING;

        if (engineTask.getEngine().isDHTEnabled()) {
            statusDHT = TrackerStateParcel.Status.WORKING;
        }

        if (engineTask.getEngine().isLSDEnabled()) {
            statusLSD = TrackerStateParcel.Status.WORKING;
        }

        if (engineTask.getEngine().isPeXEnabled()) {
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

            states.add(new TrackerStateParcel(entry));
        }

        return states;
    }

    private ArrayList<PeerStateParcel> makePeerStateParcellList(TorrentDownload task)
    {
        if (task == null) {
            return null;
        }

        ArrayList<PeerStateParcel> states = new ArrayList<>();
        ArrayList<PeerInfo> peers = task.getPeers();

        TorrentStatus status = task.getTorrentStatus();

        for (PeerInfo peer : peers) {
            PeerStateParcel state = new PeerStateParcel(peer.getSwig(), status);
            states.add(state);
        }

        return states;
    }

    private void sendTorrentsStateOneShot(Messenger replyTo)
    {
        if (replyTo == null) {
            return;
        }

        Bundle statesList = new Bundle();

        for (TorrentDownload task : torrentTasks.values()) {
            if (task != null) {
                TorrentStateParcel state = makeTorrentStateParcel(task);

                if (!stateCache.contains(state)) {
                    stateCache.put(state);
                }

                statesList.putParcelable(state.torrentId, state);
            }
        }

        try {
            ipc.sendTorrentStateOneShot(replyTo, statesList);

        } catch (RemoteException e) {
            /* The client is dead, ignore */
        }
    }

    private void sendTorrentsStateOneShot()
    {
        Bundle statesList = new Bundle();
        for (TorrentDownload task : torrentTasks.values()) {
            if (task != null) {
                TorrentStateParcel state = makeTorrentStateParcel(task);

                if (!stateCache.contains(state)) {
                    stateCache.put(state);
                }

                statesList.putParcelable(state.torrentId, state);
            }
        }

        for (int i = clientCallbacks.size() - 1; i >= 0; i--) {
            try {
                ipc.sendTorrentStateOneShot(clientCallbacks.get(i), statesList);

            } catch (RemoteException e) {
                /*
                 * The client is dead. Remove it from the list;
                 * we are going through the list from back to front so this is safe to do inside the loop.
                 */
                clientCallbacks.remove(i);
            }
        }
    }

    private void sendTorrentsList()
    {
        Bundle statesList = new Bundle();

        for (TorrentDownload task : torrentTasks.values()) {
            Torrent torrent = task.getTorrent();
            if (torrent != null) {
                TorrentStateParcel state =
                        new TorrentStateParcel(
                                torrent.getId(), torrent.getName());

                statesList.putParcelable(state.torrentId, state);
            }
        }

        for (int i = clientCallbacks.size() - 1; i >= 0; i--) {
            try {
                ipc.sendTorrentStateOneShot(clientCallbacks.get(i), statesList);

            } catch (RemoteException e) {
                /*
                 * The client is dead. Remove it from the list;
                 * we are going through the list from back to front so this is safe to do inside the loop.
                 */
                clientCallbacks.remove(i);
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

        for (int i = clientCallbacks.size() - 1; i >= 0; i--) {
            try {
                ipc.sendUpdateState(clientCallbacks.get(i), state);

            } catch (RemoteException e) {
                /*
                 * The client is dead. Remove it from the list;
                 * we are going through the list from back to front so this is safe to do inside the loop.
                 */
                clientCallbacks.remove(i);
            }
        }
    }

    private void sendTorrentInfo(String id, Messenger replyTo)
    {
        if (id == null || replyTo == null) {
            return;
        }

        TorrentDownload task = torrentTasks.get(id);

        if (task == null) {
            return;
        }

        TorrentInfo ti = task.getTorrentInfo();

        TorrentMetaInfo info = null;

        try {
            info = new TorrentMetaInfo(ti);

        } catch (DecodeException e) {
            Log.e(TAG, "Can't decode torrent info: ");
            Log.e(TAG, Log.getStackTraceString(e));
        }

        try {
            ipc.sendGetTorrentInfo(replyTo, info);

        } catch (RemoteException e) {
            /* The client is dead, ignore */
        }
    }

    private void sendOnAddTorrents(ArrayList<TorrentStateParcel> states, ArrayList<Throwable> exceptions)
    {
        for (int i = clientCallbacks.size() - 1; i >= 0; i--) {
            try {
                ipc.sendTorrentsAdded(clientCallbacks.get(i), states, exceptions);

            } catch (RemoteException e) {
                /*
                 * The client is dead. Remove it from the list;
                 * we are going through the list from back to front so this is safe to do inside the loop.
                 */
                clientCallbacks.remove(i);
            }
        }
    }

    private void sendActiveAndSeedingTime(String id, Messenger replyTo)
    {
        if (id == null || replyTo == null) {
            return;
        }

        TorrentDownload task = torrentTasks.get(id);

        if (task == null) {
            return;
        }

        try {
            ipc.sendGetActiveAndSeedingTime(replyTo,
                    task.getActiveTime(),
                    task.getSeedingTime());

        } catch (RemoteException e) {
            /* The client is dead, ignore */
        }
    }

    private void sendTrackerStatesList(String id, Messenger replyTo)
    {
        if (id == null || replyTo == null) {
            return;
        }

        TorrentDownload task = torrentTasks.get(id);

        if (task == null) {
            return;
        }

        try {
            ipc.sendGetTrackersStates(replyTo, makeTrackerStateParcelList(task));

        } catch (RemoteException e) {
            /* The client is dead, ignore */
        }
    }

    private void sendPeerStatesList(String id, Messenger replyTo)
    {
        if (id == null || replyTo == null) {
            return;
        }

        TorrentDownload task = torrentTasks.get(id);

        if (task == null) {
            return;
        }

        try {
            ipc.sendGetPeersStates(replyTo, makePeerStateParcellList(task));

        } catch (RemoteException e) {
            /* The client is dead, ignore */
        }
    }

    private void sendPieces(String id, Messenger replyTo)
    {
        if (id == null || replyTo == null) {
            return;
        }

        TorrentDownload task = torrentTasks.get(id);

        if (task == null) {
            return;
        }

        try {
            ipc.sendGetPieces(replyTo, task.pieces());

        } catch (RemoteException e) {
            /* The client is dead, ignore */
        }
    }

    private void sendMagnet(String id, Messenger replyTo)
    {
        if (id == null || replyTo == null) {
            return;
        }

        TorrentDownload task = torrentTasks.get(id);

        if (task == null) {
            return;
        }

        try {
            ipc.sendGetMagnet(replyTo, task.makeMagnet());

        } catch (RemoteException e) {
            /* The client is dead, ignore */
        }
    }

    /*
     * if id is null get global limit.
     */

    private void sendSpeedLimit(String id, Messenger replyTo)
    {
        if (replyTo == null) {
            return;
        }

        if (id != null) {
            TorrentDownload task = torrentTasks.get(id);

            if (task == null) {
                return;
            }

            try {
                ipc.sendGetSpeedLimit(replyTo,
                        task.getUploadSpeedLimit(),
                        task.getDownloadSpeedLimit());

            } catch (RemoteException e) {
                /* The client is dead, ignore */
            }

        } else {
            try {
                ipc.sendGetSpeedLimit(replyTo,
                        engineTask.getEngine().getUploadSpeedLimit(),
                        engineTask.getEngine().getDownloadSpeedLimit());

            } catch (RemoteException e) {
                /* The client is dead, ignore */
            }
        }
    }

    private void startUpdateForegroundNotify()
    {
        if (updateForegroundNotifyHandler != null ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return;
        }

        updateForegroundNotifyHandler = new Handler();
        Runnable r = new Runnable()
        {
            @Override
            public void run()
            {
                if (isAlreadyRunning) {
                    boolean online = engineTask.getEngine().isListening();
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
        updateForegroundNotifyHandler.postDelayed(r, SYNC_TIME);
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

    private synchronized void makeTorrentsMoveNotify()
    {
        /* TODO: Make the ability to customize the sound, LED and vibration */

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

    static class CallbackHandler extends Handler
    {
        WeakReference<TorrentTaskService> service;

        CallbackHandler(TorrentTaskService service)
        {
            this.service = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TorrentTaskServiceIPC.CLIENT_CONNECT:
                    service.get().clientCallbacks.add(msg.replyTo);
                    try {
                        service.get().sendTorrentsStateOneShot(msg.replyTo);

                    } catch (Exception e) {
                        service.get().sendTorrentsList();
                    }
                    break;
                case TorrentTaskServiceIPC.ADD_TORRENTS: {
                    Bundle b = msg.getData();
                    b.setClassLoader(Torrent.class.getClassLoader());

                    ArrayList<Torrent> torrentsList =
                            b.getParcelableArrayList(TorrentTaskServiceIPC.TAG_TORRENTS_LIST);

                    ArrayList<Torrent> addedTorrentsList = new ArrayList<>();
                    ArrayList<TorrentStateParcel> states = new ArrayList<>();
                    ArrayList<Throwable> exceptions = new ArrayList<>();

                    if (torrentsList != null) {
                        for (Torrent torrent : torrentsList) {
                            SettingsManager pref = new SettingsManager(service.get().getApplicationContext());
                            boolean deleteTorrentFile = pref.getBoolean(service.get().
                                    getString(R.string.pref_key_delete_torrent_file), false);

                            try {
                                if (service.get().repo.exists(torrent)) {
                                    service.get().repo.replace(torrent,
                                            torrent.getTorrentFilePath(),
                                            deleteTorrentFile);

                                    exceptions.add(new FileAlreadyExistsException());

                                } else {
                                    service.get().repo.add(torrent,
                                            torrent.getTorrentFilePath(),
                                            deleteTorrentFile);
                                }

                                torrent = service.get().repo.getTorrentByID(torrent.getId());

                            } catch (Throwable e) {
                                exceptions.add(e);
                            }

                            if (torrent != null) {
                                addedTorrentsList.add(torrent);
                                states.add(new TorrentStateParcel(torrent.getId(), torrent.getName()));
                            }
                        }
                    }

                    service.get().addTorrents(addedTorrentsList);

                    service.get().sendOnAddTorrents(states, exceptions);
                    break;
                }
                case TorrentTaskServiceIPC.CLIENT_DISCONNECT:
                    service.get().clientCallbacks.remove(msg.replyTo);
                    break;
                case TorrentTaskServiceIPC.UPDATE_STATES_ONESHOT:
                    try {
                        service.get().sendTorrentsStateOneShot(msg.replyTo);

                    } catch (Exception e) {
                        /* Ignore */
                    }
                    break;

                case TorrentTaskServiceIPC.UPDATE_STATE: {
                    String id = msg.getData().getString(TorrentTaskServiceIPC.TAG_TORRENT_ID);
                    try {
                        TorrentDownload task = service.get().torrentTasks.get(id);
                        service.get().sendTorrentState(task);

                    } catch (Exception e) {
                        /* Ignore */
                    }
                    break;
                }
                case TorrentTaskServiceIPC.PAUSE_RESUME_TORRENTS: {
                    ArrayList<String> ids =
                            msg.getData().getStringArrayList(TorrentTaskServiceIPC.TAG_TORRENT_IDS_LIST);
                    if (ids != null) {
                        for (String id : ids) {
                            TorrentDownload task = service.get().torrentTasks.get(id);
                            try {
                                if (task.isPaused()) {
                                    task.resume();
                                } else {
                                    task.pause();
                                }

                            } catch (Exception e) {
                                /* Ignore */
                            }
                        }
                    }
                    break;
                }
                case TorrentTaskServiceIPC.DELETE_TORRENTS:
                case TorrentTaskServiceIPC.DELETE_TORRENTS_WITH_FILES: {
                    ArrayList<String> ids =
                            msg.getData().getStringArrayList(TorrentTaskServiceIPC.TAG_TORRENT_IDS_LIST);

                    service.get().deleteTorrents(
                            ids,
                            (msg.what == TorrentTaskServiceIPC.DELETE_TORRENTS_WITH_FILES));
                    break;
                }
                case TorrentTaskServiceIPC.FORCE_RECHECK_TORRENTS: {
                    ArrayList<String> ids =
                            msg.getData().getStringArrayList(TorrentTaskServiceIPC.TAG_TORRENT_IDS_LIST);
                    if (ids != null) {
                        for (String id : ids) {
                            service.get().torrentTasks.get(id).forceRecheck();
                        }
                    }
                    break;
                }
                case TorrentTaskServiceIPC.FORCE_ANNOUNCE_TORRENTS: {
                    ArrayList<String> ids =
                            msg.getData().getStringArrayList(TorrentTaskServiceIPC.TAG_TORRENT_IDS_LIST);
                    if (ids != null) {
                        for (String id : ids) {
                            service.get().torrentTasks.get(id).requestTrackerAnnounce();
                        }
                    }
                    break;
                }
                case TorrentTaskServiceIPC.GET_TORRENT_INFO: {
                    String id = msg.getData().getString(TorrentTaskServiceIPC.TAG_TORRENT_ID);
                    service.get().sendTorrentInfo(id, msg.replyTo);
                    break;
                }
                case TorrentTaskServiceIPC.SET_TORRENT_NAME: {
                    String id = msg.getData().getString(TorrentTaskServiceIPC.TAG_TORRENT_ID);
                    String name = msg.getData().getString(TorrentTaskServiceIPC.TAG_TORRENT_NAME);
                    service.get().setTorrentName(id, name);
                    break;
                }
                case TorrentTaskServiceIPC.SET_DOWNLOAD_PATH: {
                    ArrayList<String> ids =
                            msg.getData().getStringArrayList(TorrentTaskServiceIPC.TAG_TORRENT_IDS_LIST);
                    String path = msg.getData().getString(TorrentTaskServiceIPC.TAG_DOWNLOAD_PATH);
                    service.get().setTorrentDownloadPath(ids, path);
                    break;
                }
                case TorrentTaskServiceIPC.SET_SEQUENTIAL_DOWNLOAD: {
                    String id = msg.getData().getString(TorrentTaskServiceIPC.TAG_TORRENT_ID);
                    boolean sequential =
                            msg.getData().getBoolean(TorrentTaskServiceIPC.TAG_SEQUENTIAL, false);
                    service.get().setSequentialDownload(id, sequential);
                    break;
                }
                case TorrentTaskServiceIPC.CHANGE_FILES_PRIORITY: {
                    String id = msg.getData().getString(TorrentTaskServiceIPC.TAG_TORRENT_ID);
                    ArrayList<Integer> priorities =
                            msg.getData().getIntegerArrayList(TorrentTaskServiceIPC.TAG_FILE_PRIORITIES);
                    service.get().changeFilesPriority(id, priorities);
                    break;
                }
                case TorrentTaskServiceIPC.GET_ACTIVE_AND_SEEDING_TIME: {
                    String id = msg.getData().getString(TorrentTaskServiceIPC.TAG_TORRENT_ID);
                    service.get().sendActiveAndSeedingTime(id, msg.replyTo);
                    break;
                }
                case TorrentTaskServiceIPC.GET_TRACKERS_STATES: {
                    String id = msg.getData().getString(TorrentTaskServiceIPC.TAG_TORRENT_ID);
                    service.get().sendTrackerStatesList(id, msg.replyTo);
                    break;
                }
                case TorrentTaskServiceIPC.REPLACE_TRACKERS: {
                    String id = msg.getData().getString(TorrentTaskServiceIPC.TAG_TORRENT_ID);
                    ArrayList<String> urls =
                            msg.getData().getStringArrayList(TorrentTaskServiceIPC.TAG_TRACKERS_URL_LIST);
                    service.get().replaceTrackers(id, urls);
                    break;
                }
                case TorrentTaskServiceIPC.ADD_TRACKERS: {
                    String id = msg.getData().getString(TorrentTaskServiceIPC.TAG_TORRENT_ID);
                    ArrayList<String> urls =
                            msg.getData().getStringArrayList(TorrentTaskServiceIPC.TAG_TRACKERS_URL_LIST);
                    service.get().addTrackers(id, urls);
                    break;
                }
                case TorrentTaskServiceIPC.GET_PEERS_STATES: {
                    String id = msg.getData().getString(TorrentTaskServiceIPC.TAG_TORRENT_ID);
                    service.get().sendPeerStatesList(id, msg.replyTo);
                    break;
                }
                case TorrentTaskServiceIPC.GET_PIECES: {
                    String id = msg.getData().getString(TorrentTaskServiceIPC.TAG_TORRENT_ID);
                    service.get().sendPieces(id, msg.replyTo);
                    break;
                }
                case TorrentTaskServiceIPC.GET_MAGNET: {
                    String id = msg.getData().getString(TorrentTaskServiceIPC.TAG_TORRENT_ID);
                    service.get().sendMagnet(id, msg.replyTo);
                    break;
                }
                case TorrentTaskServiceIPC.SET_UPLOAD_SPEED_LIMIT: {
                    String id = msg.getData().getString(TorrentTaskServiceIPC.TAG_TORRENT_ID);
                    int limit = msg.arg1;
                    service.get().setUploadSpeedLimit(id, limit);
                    break;
                }
                case TorrentTaskServiceIPC.SET_DOWNLOAD_SPEED_LIMIT: {
                    String id = msg.getData().getString(TorrentTaskServiceIPC.TAG_TORRENT_ID);
                    int limit = msg.arg1;
                    service.get().setDownloadSpeedLimit(id, limit);
                    break;
                }
                case TorrentTaskServiceIPC.GET_SPEED_LIMIT: {
                    String id = msg.getData().getString(TorrentTaskServiceIPC.TAG_TORRENT_ID);
                    service.get().sendSpeedLimit(id, msg.replyTo);
                    break;
                }
                default:
                    super.handleMessage(msg);
            }
        }
    }
}
