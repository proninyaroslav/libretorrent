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

package org.proninyaroslav.libretorrent.core.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.filesystem.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.sorting.TorrentSorting;

public class SettingsManager
{
    public static class Default
    {
        /* Appearance settings */
        public static final String notifySound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString();
        public static final boolean torrentFinishNotify = true;
        public static final boolean playSoundNotify = true;
        public static final boolean ledIndicatorNotify = true;
        public static final boolean vibrationNotify = true;
        public static int theme(Context context) { return Integer.parseInt(context.getString(R.string.pref_theme_light_value)); }
        public static int ledIndicatorColorNotify(Context context) { return ContextCompat.getColor(context, R.color.primary); }
        public static int funcButton(Context context) { return Integer.parseInt(context.getString(R.string.pref_function_button_pause_value)); }
        /* Behavior settings */
        public static final boolean autostart = false;
        public static final boolean keepAlive = true;
        public static final boolean shutdownDownloadsComplete = false;
        public static final boolean cpuDoNotSleep = false;
        public static final boolean onlyCharging = false;
        public static final boolean batteryControl = false;
        public static final boolean customBatteryControl = false;
        public static final boolean unmeteredConnectionsOnly = false;
        public static final boolean enableRoaming = true;
        /* Network settings */
        public static final int portRangeFirst = SessionSettings.DEFAULT_PORT_RANGE_FIRST;
        public static final int portRangeSecond = SessionSettings.DEFAULT_PORT_RANGE_SECOND;
        public static final boolean enableDht = true;
        public static final boolean enableLsd = true;
        public static final boolean enableUtp = true;
        public static final boolean enableUpnp = true;
        public static final boolean enableNatPmp = true;
        public static final boolean useRandomPort = true;
        public static final boolean encryptInConnections = true;
        public static final boolean encryptOutConnections = true;
        public static final boolean enableIpFiltering = false;
        public static final String ipFilteringFile = null;
        public static int encryptMode(Context context) { return Integer.parseInt(context.getString(R.string.pref_enc_mode_prefer_value)); }
        public static final boolean showNatErrors = false;
        /* Storage settings */
        public static final String saveTorrentsIn = "file://" + FileSystemFacade.getDefaultDownloadPath();
        public static final boolean moveAfterDownload = false;
        public static final String moveAfterDownloadIn = "file://" + FileSystemFacade.getDefaultDownloadPath();
        public static final boolean saveTorrentFiles = false;
        public static final String saveTorrentFilesIn = "file://" + FileSystemFacade.getDefaultDownloadPath();
        public static final boolean watchDir = false;
        public static final String dirToWatch = "file://" + FileSystemFacade.getDefaultDownloadPath();
        /* Limitations settings */
        public static final int maxDownloadSpeedLimit = SessionSettings.DEFAULT_DOWNLOAD_RATE_LIMIT;
        public static final int maxUploadSpeedLimit = SessionSettings.DEFAULT_UPLOAD_RATE_LIMIT;
        public static final int maxConnections = SessionSettings.DEFAULT_CONNECTIONS_LIMIT;
        public static final int maxConnectionsPerTorrent = SessionSettings.DEFAULT_CONNECTIONS_LIMIT_PER_TORRENT;
        public static final int maxUploadsPerTorrent = SessionSettings.DEFAULT_UPLOADS_LIMIT_PER_TORRENT;
        public static final int maxActiveUploads = SessionSettings.DEFAULT_ACTIVE_SEEDS;
        public static final int maxActiveDownloads = SessionSettings.DEFAULT_ACTIVE_DOWNLOADS;
        public static final int maxActiveTorrents = SessionSettings.DEFAULT_ACTIVE_LIMIT;
        public static final boolean autoManage = false;
        /* Proxy settings */
        public static final int proxyType = ProxySettingsPack.ProxyType.NONE.value();
        public static final String proxyAddress = "";
        public static final int proxyPort = ProxySettingsPack.DEFAULT_PROXY_PORT;
        public static final boolean proxyPeersToo = true;
        public static final boolean proxyRequiresAuth = false;
        public static final String proxyLogin = "";
        public static final String proxyPassword = "";
        public static final boolean proxyChanged = false;
        /* Sorting settings */
        public static final String sortTorrentBy = TorrentSorting.SortingColumns.name.name();
        public static final String sortTorrentDirection = TorrentSorting.Direction.ASC.name();
        /* Filemanager settings */
        public static final String fileManagerLastDir = FileSystemFacade.getDefaultDownloadPath();
        /* Scheduling settings */
        public static final boolean enableSchedulingStart = false;
        public static final boolean enableSchedulingShutdown = false;
        public static final int schedulingStartTime = 540; /* 9:00 am in minutes*/
        public static final int schedulingShutdownTime = 1260; /* 9:00 pm in minutes */
        public static final boolean schedulingRunOnlyOnce = false;
        public static final boolean schedulingSwitchWiFi = false;
        /* Feed settings */
        public static final long feedItemKeepTime = 4 * 86400000L; /* 4 days */
        public static final boolean autoRefreshFeeds = false;
        public static final long refreshFeedsInterval = 2 * 3600000L; /* 2 hours */
        public static final boolean autoRefreshUnmeteredConnectionsOnly = false;
        public static final boolean autoRefreshEnableRoaming = true;
        public static final boolean feedStartTorrents = true;
        public static final boolean feedRemoveDuplicates = true;
        /* Streaming settings */
        public static final boolean enableStreaming = true;
        public static final String streamingHostname = "127.0.0.1";
        public static final int streamingPort = 8800;
    }

    private static SettingsManager INSTANCE;
    private SharedPreferences pref;

    public static SettingsManager getInstance(@NonNull Context appContext)
    {
        if (INSTANCE == null) {
            synchronized (SettingsManager.class) {
                if (INSTANCE == null)
                    INSTANCE = new SettingsManager(appContext);
            }
        }
        return INSTANCE;
    }

    private SettingsManager(@NonNull Context appContext)
    {
        pref = PreferenceManager.getDefaultSharedPreferences(appContext);
    }

    public SharedPreferences getPreferences()
    {
        return pref;
    }

    public SessionSettings readSessionSettings(Context context)
    {
        SessionSettings settings = new SessionSettings();
        settings.downloadRateLimit = pref.getInt(context.getString(R.string.pref_key_max_download_speed),
                                                 Default.maxDownloadSpeedLimit);
        settings.uploadRateLimit = pref.getInt(context.getString(R.string.pref_key_max_upload_speed),
                                               Default.maxUploadSpeedLimit);
        settings.connectionsLimit = pref.getInt(context.getString(R.string.pref_key_max_connections),
                                                Default.maxConnections);
        settings.connectionsLimitPerTorrent = pref.getInt(context.getString(R.string.pref_key_max_connections_per_torrent),
                                                          Default.maxConnectionsPerTorrent);
        settings.uploadsLimitPerTorrent = pref.getInt(context.getString(R.string.pref_key_max_uploads_per_torrent),
                                                      Default.maxUploadsPerTorrent);
        settings.activeDownloads = pref.getInt(context.getString(R.string.pref_key_max_active_downloads),
                                               Default.maxActiveDownloads);
        settings.activeSeeds = pref.getInt(context.getString(R.string.pref_key_max_active_uploads),
                                           Default.maxActiveUploads);
        settings.activeLimit = pref.getInt(context.getString(R.string.pref_key_max_active_torrents),
                                           Default.maxActiveTorrents);
        settings.portRangeFirst = pref.getInt(context.getString(R.string.pref_key_port_range_first),
                                              Default.portRangeFirst);
        settings.portRangeSecond = pref.getInt(context.getString(R.string.pref_key_port_range_second),
                                               Default.portRangeSecond);
        settings.dhtEnabled = pref.getBoolean(context.getString(R.string.pref_key_enable_dht), Default.enableDht);
        settings.lsdEnabled = pref.getBoolean(context.getString(R.string.pref_key_enable_lsd), Default.enableLsd);
        settings.utpEnabled = pref.getBoolean(context.getString(R.string.pref_key_enable_utp), Default.enableUtp);
        settings.upnpEnabled = pref.getBoolean(context.getString(R.string.pref_key_enable_upnp), Default.enableUpnp);
        settings.natPmpEnabled = pref.getBoolean(context.getString(R.string.pref_key_enable_natpmp), Default.enableNatPmp);
        settings.encryptInConnections = pref.getBoolean(context.getString(R.string.pref_key_enc_in_connections),
                                                        Default.encryptInConnections);
        settings.encryptOutConnections = pref.getBoolean(context.getString(R.string.pref_key_enc_out_connections),
                                                         Default.encryptOutConnections);
        int modeVal = pref.getInt(context.getString(R.string.pref_key_enc_mode), Default.encryptMode(context));
        settings.encryptMode = SessionSettings.EncryptMode.fromValue(modeVal);
        settings.autoManaged = pref.getBoolean(context.getString(R.string.pref_key_auto_manage), Default.autoManage);

        return settings;
    }
}