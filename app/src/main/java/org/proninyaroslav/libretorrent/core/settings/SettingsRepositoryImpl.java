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

package org.proninyaroslav.libretorrent.core.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposables;

public class SettingsRepositoryImpl implements SettingsRepository
{
    private static class Default
    {
        /* Appearance settings */
        static final String notifySound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString();
        static final boolean torrentFinishNotify = true;
        static final boolean playSoundNotify = true;
        static final boolean ledIndicatorNotify = true;
        static final boolean vibrationNotify = true;
        static int theme(@NonNull Context context) { return Integer.parseInt(context.getString(R.string.pref_theme_light_value)); }
        static int ledIndicatorColorNotify(@NonNull Context context)
        {
            return ContextCompat.getColor(context, R.color.primary);
        }
        /* Behavior settings */
        static final boolean autostart = false;
        static final boolean keepAlive = true;
        static final boolean shutdownDownloadsComplete = false;
        static final boolean cpuDoNotSleep = false;
        static final boolean onlyCharging = false;
        static final boolean batteryControl = false;
        static final boolean customBatteryControl = false;
        static final int customBatteryControlValue = Utils.getDefaultBatteryLowLevel();
        static final boolean unmeteredConnectionsOnly = false;
        static final boolean enableRoaming = true;
        /* Network settings */
        static final int portRangeFirst = SessionSettings.DEFAULT_PORT_RANGE_FIRST;
        static final int portRangeSecond = SessionSettings.DEFAULT_PORT_RANGE_SECOND;
        static final boolean enableDht = true;
        static final boolean enableLsd = true;
        static final boolean enableUtp = true;
        static final boolean enableUpnp = true;
        static final boolean enableNatPmp = true;
        static final boolean useRandomPort = true;
        static final boolean encryptInConnections = true;
        static final boolean encryptOutConnections = true;
        static final boolean enableIpFiltering = false;
        static final String ipFilteringFile = null;
        static int encryptMode(@NonNull Context context)
        {
            return Integer.parseInt(context.getString(R.string.pref_enc_mode_prefer_value));
        }
        static final boolean showNatErrors = false;
        /* Storage settings */
        static String saveTorrentsIn(@NonNull Context context)
        {
            return "file://" + SystemFacadeHelper.getFileSystemFacade(context).getDefaultDownloadPath();
        }
        static final boolean moveAfterDownload = false;
        static String moveAfterDownloadIn(@NonNull Context context)
        {
            return "file://" + SystemFacadeHelper.getFileSystemFacade(context).getDefaultDownloadPath();
        }
        static final boolean saveTorrentFiles = false;
        static String saveTorrentFilesIn(@NonNull Context context)
        {
            return "file://" + SystemFacadeHelper.getFileSystemFacade(context).getDefaultDownloadPath();
        }
        static final boolean watchDir = false;
        static String dirToWatch(@NonNull Context context)
        {
            return "file://" + SystemFacadeHelper.getFileSystemFacade(context).getDefaultDownloadPath();
        }
        static final boolean anonymousMode = SessionSettings.DEFAULT_ANONYMOUS_MODE;
        static final boolean seedingOutgoingConnections = SessionSettings.DEFAULT_SEEDING_OUTGOING_CONNECTIONS;
        /* Limitations settings */
        static final int maxDownloadSpeedLimit = SessionSettings.DEFAULT_DOWNLOAD_RATE_LIMIT;
        static final int maxUploadSpeedLimit = SessionSettings.DEFAULT_UPLOAD_RATE_LIMIT;
        static final int maxConnections = SessionSettings.DEFAULT_CONNECTIONS_LIMIT;
        static final int maxConnectionsPerTorrent = SessionSettings.DEFAULT_CONNECTIONS_LIMIT_PER_TORRENT;
        static final int maxUploadsPerTorrent = SessionSettings.DEFAULT_UPLOADS_LIMIT_PER_TORRENT;
        static final int maxActiveUploads = SessionSettings.DEFAULT_ACTIVE_SEEDS;
        static final int maxActiveDownloads = SessionSettings.DEFAULT_ACTIVE_DOWNLOADS;
        static final int maxActiveTorrents = SessionSettings.DEFAULT_ACTIVE_LIMIT;
        static final boolean autoManage = false;
        /* Proxy settings */
        static final int proxyType = SessionSettings.DEFAULT_PROXY_TYPE.value();
        static final String proxyAddress = SessionSettings.DEFAULT_PROXY_ADDRESS;
        static final int proxyPort = SessionSettings.DEFAULT_PROXY_PORT;
        static final boolean proxyPeersToo = SessionSettings.DEFAULT_PROXY_PEERS_TOO;
        static final boolean proxyRequiresAuth = SessionSettings.DEFAULT_PROXY_REQUIRES_AUTH;
        static final String proxyLogin = SessionSettings.DEFAULT_PROXY_LOGIN;
        static final String proxyPassword = SessionSettings.DEFAULT_PROXY_PASSWORD;
        static final boolean proxyChanged = false;
        static final boolean applyProxy = false;
        /* Scheduling settings */
        static final boolean enableSchedulingStart = false;
        static final boolean enableSchedulingShutdown = false;
        static final int schedulingStartTime = 540; /* 9:00 am in minutes*/
        static final int schedulingShutdownTime = 1260; /* 9:00 pm in minutes */
        static final boolean schedulingRunOnlyOnce = false;
        static final boolean schedulingSwitchWiFi = false;
        /* Feed settings */
        static final long feedItemKeepTime = 4 * 86400000L; /* 4 days */
        static final boolean autoRefreshFeeds = false;
        static final long refreshFeedsInterval = 2 * 3600000L; /* 2 hours */
        static final boolean autoRefreshFeedsUnmeteredConnectionsOnly = false;
        static final boolean autoRefreshFeedsEnableRoaming = true;
        static final boolean feedStartTorrents = true;
        static final boolean feedRemoveDuplicates = true;
        /* Streaming settings */
        static final boolean enableStreaming = true;
        static final String streamingHostname = "127.0.0.1";
        static final int streamingPort = 8800;
        /* Logging settings */
        static final boolean logging = SessionSettings.DEFAULT_LOGGING;
        static final int maxLogSize = SessionSettings.DEFAULT_MAX_LOG_SIZE;
        static final boolean logSessionFilter = SessionSettings.DEFAULT_LOG_SESSION_FILTER;
        static final boolean logDhtFilter = SessionSettings.DEFAULT_LOG_DHT_FILTER;
        static final boolean logPeerFilter = SessionSettings.DEFAULT_LOG_PEER_FILTER;
        static final boolean logPortmapFilter = SessionSettings.DEFAULT_LOG_PORTMAP_FILTER;
        static final boolean logTorrentFilter = SessionSettings.DEFAULT_LOG_TORRENT_FILTER;
    }

    private Context appContext;
    private SharedPreferences pref;
    private FileSystemFacade fs;

    public SettingsRepositoryImpl(@NonNull Context appContext)
    {
        this.appContext = appContext;
        fs = SystemFacadeHelper.getFileSystemFacade(appContext);
        pref = PreferenceManager.getDefaultSharedPreferences(appContext);
    }

    /*
     * Returns Flowable with key
     */

    @Override
    public Flowable<String> observeSettingsChanged()
    {
        return Flowable.create((emitter) -> {
            SharedPreferences.OnSharedPreferenceChangeListener listener = (sharedPreferences, key) -> {
                if (!emitter.isCancelled())
                    emitter.onNext(key);
            };

            if (!emitter.isCancelled()) {
                pref.registerOnSharedPreferenceChangeListener(listener);
                emitter.setDisposable(Disposables.fromAction(() ->
                        pref.unregisterOnSharedPreferenceChangeListener(listener)));
            }

        }, BackpressureStrategy.LATEST);
    }

    @Override
    public SessionSettings readSessionSettings()
    {
        SessionSettings settings = new SessionSettings();
        settings.uploadRateLimit = maxUploadSpeedLimit();
        settings.connectionsLimit = maxConnections();
        settings.connectionsLimitPerTorrent = maxConnectionsPerTorrent();
        settings.uploadsLimitPerTorrent = maxUploadsPerTorrent();
        settings.activeDownloads = maxActiveDownloads();
        settings.activeSeeds = maxActiveUploads();
        settings.activeLimit = maxActiveTorrents();
        settings.portRangeFirst = portRangeFirst();
        settings.portRangeSecond = portRangeSecond();
        settings.dhtEnabled = enableDht();
        settings.lsdEnabled = enableLsd();
        settings.utpEnabled = enableUtp();
        settings.upnpEnabled = enableUpnp();
        settings.natPmpEnabled = enableNatPmp();
        settings.encryptInConnections = encryptInConnections();
        settings.encryptOutConnections = encryptOutConnections();
        settings.encryptMode = SessionSettings.EncryptMode.fromValue(encryptMode());
        settings.autoManaged = autoManage();
        settings.anonymousMode = anonymousMode();
        settings.seedingOutgoingConnections = seedingOutgoingConnections();

        settings.proxyType = SessionSettings.ProxyType.fromValue(proxyType());
        settings.proxyAddress = proxyAddress();
        settings.proxyPort = proxyPort();
        settings.proxyPeersToo = proxyPeersToo();
        settings.proxyRequiresAuth = proxyRequiresAuth();
        settings.proxyLogin = proxyLogin();
        settings.proxyPassword = proxyPassword();

        settings.logging = logging();
        settings.maxLogSize = maxLogSize();
        settings.logSessionFilter = logSessionFilter();
        settings.logDhtFilter = logDhtFilter();
        settings.logPeerFilter = logPeerFilter();
        settings.logPortmapFilter = logPortmapFilter();
        settings.logTorrentFilter = logTorrentFilter();

        return settings;
    }


    @Override
    public String notifySound()
    {
        return pref.getString(appContext.getString(R.string.pref_key_notify_sound),
                Default.notifySound);
    }

    @Override
    public void notifySound(String val)
    {
        pref.edit()
                .putString(appContext.getString(R.string.pref_key_notify_sound), val)
                .apply();
    }

    @Override
    public boolean torrentFinishNotify()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_torrent_finish_notify),
                Default.torrentFinishNotify);
    }

    @Override
    public void torrentFinishNotify(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_torrent_finish_notify), val)
                .apply();
    }

    @Override
    public boolean playSoundNotify()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_play_sound_notify),
                Default.playSoundNotify);
    }

    @Override
    public void playSoundNotify(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_play_sound_notify), val)
                .apply();
    }

    @Override
    public boolean ledIndicatorNotify()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_led_indicator_notify),
                Default.ledIndicatorNotify);
    }

    @Override
    public void ledIndicatorNotify(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_led_indicator_notify), val)
                .apply();
    }

    @Override
    public boolean vibrationNotify()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_vibration_notify),
                Default.vibrationNotify);
    }

    @Override
    public void vibrationNotify(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_vibration_notify), val)
                .apply();
    }

    @Override
    public int theme()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_theme),
                Default.theme(appContext));
    }

    @Override
    public void theme(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_theme), val)
                .apply();
    }

    @Override
    public int ledIndicatorColorNotify()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_led_indicator_color_notify),
            Default.ledIndicatorColorNotify(appContext));
    }

    @Override
    public void ledIndicatorColorNotify(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_led_indicator_color_notify), val)
                .apply();
    }

    @Override
    public boolean autostart()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_autostart),
                Default.autostart);
    }

    @Override
    public void autostart(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_autostart), val)
                .apply();
    }

    @Override
    public boolean keepAlive()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_keep_alive),
                Default.keepAlive);
    }

    @Override
    public void keepAlive(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_keep_alive), val)
                .apply();
    }

    @Override
    public boolean shutdownDownloadsComplete()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_shutdown_downloads_complete),
                Default.shutdownDownloadsComplete);
    }

    @Override
    public void shutdownDownloadsComplete(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_shutdown_downloads_complete), val)
                .apply();
    }

    @Override
    public boolean cpuDoNotSleep()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_cpu_do_not_sleep),
                Default.cpuDoNotSleep);
    }

    @Override
    public void cpuDoNotSleep(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_cpu_do_not_sleep), val)
                .apply();
    }

    @Override
    public boolean onlyCharging()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_download_and_upload_only_when_charging),
                Default.onlyCharging);
    }

    @Override
    public void onlyCharging(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_download_and_upload_only_when_charging), val)
                .apply();
    }

    @Override
    public boolean batteryControl()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_battery_control),
                Default.batteryControl);
    }

    @Override
    public void batteryControl(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_battery_control), val)
                .apply();
    }

    @Override
    public boolean customBatteryControl()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_custom_battery_control),
                Default.customBatteryControl);
    }

    @Override
    public void customBatteryControl(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_custom_battery_control), val)
                .apply();
    }

    @Override
    public int customBatteryControlValue()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_custom_battery_control_value),
                Default.customBatteryControlValue);
    }

    @Override
    public void customBatteryControlValue(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_custom_battery_control_value), val)
                .apply();
    }

    @Override
    public boolean unmeteredConnectionsOnly()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_unmetered_connections_only),
                Default.unmeteredConnectionsOnly);
    }

    @Override
    public void unmeteredConnectionsOnly(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_unmetered_connections_only), val)
                .apply();
    }

    @Override
    public boolean enableRoaming()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_enable_roaming),
                Default.enableRoaming);
    }

    @Override
    public void enableRoaming(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_enable_roaming), val)
                .apply();
    }

    @Override
    public int portRangeFirst()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_port_range_first),
                Default.portRangeFirst);
    }

    @Override
    public void portRangeFirst(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_port_range_first), val)
                .apply();
    }

    @Override
    public int portRangeSecond()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_port_range_second),
                Default.portRangeSecond);
    }

    @Override
    public void portRangeSecond(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_port_range_second), val)
                .apply();
    }

    @Override
    public boolean enableDht()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_enable_dht),
                Default.enableDht);
    }

    @Override
    public void enableDht(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_enable_dht), val)
                .apply();
    }

    @Override
    public boolean enableLsd()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_enable_lsd),
                Default.enableLsd);
    }

    @Override
    public void enableLsd(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_enable_lsd), val)
                .apply();
    }

    @Override
    public boolean enableUtp()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_enable_utp),
                Default.enableUtp);
    }

    @Override
    public void enableUtp(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_enable_utp), val)
                .apply();
    }

    @Override
    public boolean enableUpnp()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_enable_upnp),
                Default.enableUpnp);
    }

    @Override
    public void enableUpnp(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_enable_upnp), val)
                .apply();
    }

    @Override
    public boolean enableNatPmp()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_enable_natpmp),
                Default.enableNatPmp);
    }

    @Override
    public void enableNatPmp(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_enable_natpmp), val)
                .apply();
    }

    @Override
    public boolean useRandomPort()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_use_random_port),
                Default.useRandomPort);
    }

    @Override
    public void useRandomPort(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_use_random_port), val)
                .apply();
    }

    @Override
    public boolean encryptInConnections()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_enc_in_connections),
                Default.encryptInConnections);
    }

    @Override
    public void encryptInConnections(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_enc_in_connections), val)
                .apply();
    }

    @Override
    public boolean encryptOutConnections()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_enc_out_connections),
                Default.encryptOutConnections);
    }

    @Override
    public void encryptOutConnections(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_enc_out_connections), val)
                .apply();
    }

    @Override
    public boolean enableIpFiltering()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_enable_ip_filtering),
                Default.enableIpFiltering);
    }

    @Override
    public void enableIpFiltering(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_enable_ip_filtering), val)
                .apply();
    }

    @Override
    public String ipFilteringFile()
    {
        return fs.normalizeFileSystemPath(pref.getString(appContext.getString(R.string.pref_key_ip_filtering_file),
                Default.ipFilteringFile));
    }

    @Override
    public void ipFilteringFile(String val)
    {
        pref.edit()
                .putString(appContext.getString(R.string.pref_key_ip_filtering_file), val)
                .apply();
    }

    @Override
    public int encryptMode()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_enc_mode),
                Default.encryptMode(appContext));
    }

    @Override
    public void encryptMode(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_enc_mode), val)
                .apply();
    }

    @Override
    public boolean showNatErrors()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_show_nat_errors),
                Default.showNatErrors);
    }

    @Override
    public void showNatErrors(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_show_nat_errors), val)
                .apply();
    }

    @Override
    public String saveTorrentsIn()
    {
        return fs.normalizeFileSystemPath(pref.getString(appContext.getString(R.string.pref_key_save_torrents_in),
                Default.saveTorrentsIn(appContext)));
    }

    @Override
    public void saveTorrentsIn(String val)
    {
        pref.edit()
                .putString(appContext.getString(R.string.pref_key_save_torrents_in), val)
                .apply();
    }

    @Override
    public boolean moveAfterDownload()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_move_after_download),
                Default.moveAfterDownload);
    }

    @Override
    public void moveAfterDownload(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_move_after_download), val)
                .apply();
    }

    @Override
    public String moveAfterDownloadIn()
    {
        return fs.normalizeFileSystemPath(pref.getString(appContext.getString(R.string.pref_key_move_after_download_in),
                Default.moveAfterDownloadIn(appContext)));
    }

    @Override
    public void moveAfterDownloadIn(String val)
    {
        pref.edit()
                .putString(appContext.getString(R.string.pref_key_move_after_download_in), val)
                .apply();
    }

    @Override
    public boolean saveTorrentFiles()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_save_torrent_files),
                Default.saveTorrentFiles);
    }

    @Override
    public void saveTorrentFiles(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_save_torrent_files), val)
                .apply();
    }

    @Override
    public String saveTorrentFilesIn()
    {
        return fs.normalizeFileSystemPath(pref.getString(appContext.getString(R.string.pref_key_save_torrent_files_in),
                Default.saveTorrentFilesIn(appContext)));
    }

    @Override
    public void saveTorrentFilesIn(String val)
    {
        pref.edit()
                .putString(appContext.getString(R.string.pref_key_save_torrent_files_in), val)
                .apply();
    }

    @Override
    public boolean watchDir()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_watch_dir),
                Default.watchDir);
    }

    @Override
    public void watchDir(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_watch_dir), val)
                .apply();
    }

    @Override
    public String dirToWatch()
    {
        return fs.normalizeFileSystemPath(pref.getString(appContext.getString(R.string.pref_key_dir_to_watch),
                Default.dirToWatch(appContext)));
    }

    @Override
    public void dirToWatch(String val)
    {
        pref.edit()
                .putString(appContext.getString(R.string.pref_key_dir_to_watch), val)
                .apply();
    }

    @Override
    public int maxDownloadSpeedLimit()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_max_download_speed),
                Default.maxDownloadSpeedLimit);
    }

    @Override
    public void maxDownloadSpeedLimit(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_max_download_speed), val)
                .apply();
    }

    @Override
    public int maxUploadSpeedLimit()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_max_upload_speed),
                Default.maxUploadSpeedLimit);
    }

    @Override
    public void maxUploadSpeedLimit(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_max_upload_speed), val)
                .apply();
    }

    @Override
    public int maxConnections()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_max_connections),
                Default.maxConnections);
    }

    @Override
    public void maxConnections(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_max_connections), val)
                .apply();
    }

    @Override
    public int maxConnectionsPerTorrent()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_max_connections_per_torrent),
                Default.maxConnectionsPerTorrent);
    }

    @Override
    public void maxConnectionsPerTorrent(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_max_connections_per_torrent), val)
                .apply();
    }

    @Override
    public int maxUploadsPerTorrent()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_max_uploads_per_torrent),
                Default.maxUploadsPerTorrent);
    }

    @Override
    public void maxUploadsPerTorrent(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_max_uploads_per_torrent), val)
                .apply();
    }

    @Override
    public int maxActiveUploads()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_max_active_uploads),
                Default.maxActiveUploads);
    }

    @Override
    public void maxActiveUploads(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_max_active_uploads), val)
                .apply();
    }

    @Override
    public int maxActiveDownloads()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_max_active_downloads),
                Default.maxActiveDownloads);
    }

    @Override
    public void maxActiveDownloads(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_max_active_downloads), val)
                .apply();
    }

    @Override
    public int maxActiveTorrents()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_max_active_torrents),
                Default.maxActiveTorrents);
    }

    @Override
    public void maxActiveTorrents(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_max_active_torrents), val)
                .apply();
    }

    @Override
    public boolean anonymousMode()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_anonymous_mode),
                Default.anonymousMode);
    }

    @Override
    public void anonymousMode(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_anonymous_mode), val)
                .apply();
    }

    @Override
    public boolean seedingOutgoingConnections()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_seeding_outgoing_connections),
                Default.seedingOutgoingConnections);
    }

    @Override
    public void seedingOutgoingConnections(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_seeding_outgoing_connections), val)
                .apply();
    }

    @Override
    public boolean autoManage()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_auto_manage),
                Default.autoManage);
    }

    @Override
    public void autoManage(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_auto_manage), val)
                .apply();
    }

    @Override
    public int proxyType()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_proxy_type),
                Default.proxyType);
    }

    @Override
    public void proxyType(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_proxy_type), val)
                .apply();
    }

    @Override
    public String proxyAddress()
    {
        return pref.getString(appContext.getString(R.string.pref_key_proxy_address),
                Default.proxyAddress);
    }

    @Override
    public void proxyAddress(String val)
    {
        pref.edit()
                .putString(appContext.getString(R.string.pref_key_proxy_address), val)
                .apply();
    }

    @Override
    public int proxyPort()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_proxy_port),
                Default.proxyPort);
    }

    @Override
    public void proxyPort(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_proxy_port), val)
                .apply();
    }

    @Override
    public boolean proxyPeersToo()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_proxy_peers_too),
                Default.proxyPeersToo);
    }

    @Override
    public void proxyPeersToo(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_proxy_peers_too), val)
                .apply();
    }

    @Override
    public boolean proxyRequiresAuth()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_proxy_requires_auth),
                Default.proxyRequiresAuth);
    }

    @Override
    public void proxyRequiresAuth(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_proxy_requires_auth), val)
                .apply();
    }

    @Override
    public String proxyLogin()
    {
        return pref.getString(appContext.getString(R.string.pref_key_proxy_login),
                Default.proxyLogin);
    }

    @Override
    public void proxyLogin(String val)
    {
        pref.edit()
                .putString(appContext.getString(R.string.pref_key_proxy_login), val)
                .apply();
    }

    @Override
    public String proxyPassword()
    {
        return pref.getString(appContext.getString(R.string.pref_key_proxy_password),
                Default.proxyPassword);
    }

    @Override
    public void proxyPassword(String val)
    {
        pref.edit()
                .putString(appContext.getString(R.string.pref_key_proxy_password), val)
                .apply();
    }

    @Override
    public boolean proxyChanged()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_proxy_changed),
                Default.proxyChanged);
    }

    @Override
    public void proxyChanged(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_proxy_changed), val)
                .apply();
    }

    @Override
    public boolean applyProxy()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_apply_proxy),
                Default.applyProxy);
    }

    @Override
    public void applyProxy(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_apply_proxy), val)
                .apply();
    }

    @Override
    public boolean enableSchedulingStart()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_enable_scheduling_start),
                Default.enableSchedulingStart);
    }

    @Override
    public void enableSchedulingStart(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_enable_scheduling_start), val)
                .apply();
    }

    @Override
    public boolean enableSchedulingShutdown()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_enable_scheduling_shutdown),
                Default.enableSchedulingShutdown);
    }

    @Override
    public void enableSchedulingShutdown(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_enable_scheduling_shutdown), val)
                .apply();
    }

    @Override
    public int schedulingStartTime()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_scheduling_start_time),
                Default.schedulingStartTime);
    }

    @Override
    public void schedulingStartTime(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_scheduling_start_time), val)
                .apply();
    }

    @Override
    public int schedulingShutdownTime()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_scheduling_shutdown_time),
                Default.schedulingShutdownTime);
    }

    @Override
    public void schedulingShutdownTime(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_scheduling_shutdown_time), val)
                .apply();
    }

    @Override
    public boolean schedulingRunOnlyOnce()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_scheduling_run_only_once),
                Default.schedulingRunOnlyOnce);
    }

    @Override
    public void schedulingRunOnlyOnce(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_scheduling_run_only_once), val)
                .apply();
    }

    @Override
    public boolean schedulingSwitchWiFi()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_scheduling_switch_wifi),
                Default.schedulingSwitchWiFi);
    }

    @Override
    public void schedulingSwitchWiFi(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_scheduling_switch_wifi), val)
                .apply();
    }

    @Override
    public long feedItemKeepTime()
    {
        return pref.getLong(appContext.getString(R.string.pref_key_feed_keep_items_time),
                Default.feedItemKeepTime);
    }

    @Override
    public void feedItemKeepTime(long val)
    {
        pref.edit()
                .putLong(appContext.getString(R.string.pref_key_feed_keep_items_time), val)
                .apply();
    }

    @Override
    public boolean autoRefreshFeeds()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_feed_auto_refresh),
                Default.autoRefreshFeeds);
    }

    @Override
    public void autoRefreshFeeds(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_feed_auto_refresh), val)
                .apply();
    }

    @Override
    public long refreshFeedsInterval()
    {
        return pref.getLong(appContext.getString(R.string.pref_key_feed_refresh_interval),
                Default.refreshFeedsInterval);
    }

    @Override
    public void refreshFeedsInterval(long val)
    {
        pref.edit()
                .putLong(appContext.getString(R.string.pref_key_feed_refresh_interval), val)
                .apply();
    }

    @Override
    public boolean autoRefreshFeedsUnmeteredConnectionsOnly()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_feed_auto_refresh_unmetered_connections_only),
                Default.autoRefreshFeedsUnmeteredConnectionsOnly);
    }

    @Override
    public void autoRefreshFeedsUnmeteredConnectionsOnly(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_feed_auto_refresh_unmetered_connections_only), val)
                .apply();
    }

    @Override
    public boolean autoRefreshFeedsEnableRoaming()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_feed_auto_refresh_enable_roaming),
                Default.autoRefreshFeedsEnableRoaming);
    }

    @Override
    public void autoRefreshFeedsEnableRoaming(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_feed_auto_refresh_enable_roaming), val)
                .apply();
    }

    @Override
    public boolean feedStartTorrents()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_feed_start_torrents),
                Default.feedStartTorrents);
    }

    @Override
    public void feedStartTorrents(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_feed_start_torrents), val)
                .apply();
    }

    @Override
    public boolean feedRemoveDuplicates()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_feed_remove_duplicates),
                Default.feedRemoveDuplicates);
    }

    @Override
    public void feedRemoveDuplicates(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_feed_remove_duplicates), val)
                .apply();
    }

    @Override
    public boolean enableStreaming()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_streaming_enable),
                Default.enableStreaming);
    }

    @Override
    public void enableStreaming(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_streaming_enable), val)
                .apply();
    }

    @Override
    public String streamingHostname()
    {
        return pref.getString(appContext.getString(R.string.pref_key_streaming_hostname),
                Default.streamingHostname);
    }

    @Override
    public void streamingHostname(String val)
    {
        pref.edit()
                .putString(appContext.getString(R.string.pref_key_streaming_hostname), val)
                .apply();
    }

    @Override
    public int streamingPort()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_streaming_port),
                Default.streamingPort);
    }

    @Override
    public void streamingPort(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_streaming_port), val)
                .apply();
    }

    @Override
    public boolean logging()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_enable_logging),
                Default.logging);
    }

    @Override
    public void logging(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_enable_logging), val)
                .apply();
    }

    @Override
    public int maxLogSize()
    {
        return pref.getInt(appContext.getString(R.string.pref_key_max_log_size),
                Default.maxLogSize);
    }

    @Override
    public void maxLogSize(int val)
    {
        pref.edit()
                .putInt(appContext.getString(R.string.pref_key_max_log_size), val)
                .apply();
    }

    @Override
    public boolean logSessionFilter()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_log_session_filter),
                Default.logSessionFilter);
    }

    @Override
    public void logSessionFilter(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_log_session_filter), val)
                .apply();
    }

    @Override
    public boolean logDhtFilter()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_log_dht_filter),
                Default.logDhtFilter);
    }

    @Override
    public void logDhtFilter(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_log_dht_filter), val)
                .apply();
    }

    @Override
    public boolean logPeerFilter()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_log_peer_filter),
                Default.logPeerFilter);
    }

    @Override
    public void logPeerFilter(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_log_peer_filter), val)
                .apply();
    }

    @Override
    public boolean logPortmapFilter()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_log_portmap_filter),
                Default.logPortmapFilter);
    }

    @Override
    public void logPortmapFilter(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_log_portmap_filter), val)
                .apply();
    }

    @Override
    public boolean logTorrentFilter()
    {
        return pref.getBoolean(appContext.getString(R.string.pref_key_log_torrent_filter),
                Default.logTorrentFilter);
    }

    @Override
    public void logTorrentFilter(boolean val)
    {
        pref.edit()
                .putBoolean(appContext.getString(R.string.pref_key_log_torrent_filter), val)
                .apply();
    }
}