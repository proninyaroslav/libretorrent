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

package org.proninyaroslav.libretorrent.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.support.v4.content.ContextCompat;

import net.grandcentrix.tray.TrayPreferences;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.TorrentEngine;
import org.proninyaroslav.libretorrent.core.sorting.TorrentSorting;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.receivers.BootReceiver;

public class SettingsManager extends TrayPreferences
{
    public static final String MODULE_NAME = "settings";

    public SettingsManager(Context context)
    {
        super(context, MODULE_NAME, 1);
    }

    public static void initPreferences(Context context)
    {
        SettingsManager pref = new SettingsManager(context);

        String keyTheme = context.getString(R.string.pref_key_theme);
        if (pref.getInt(keyTheme, -1) == -1) {
            pref.put(keyTheme, Integer.parseInt(context.getString(R.string.pref_theme_light_value)));
        }

        String keyAutostart = context.getString(R.string.pref_key_autostart);
        int flag = (pref.getBoolean(keyAutostart, false) ?
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        ComponentName bootReceiver = new ComponentName(context, BootReceiver.class);
        context.getPackageManager()
                .setComponentEnabledSetting(bootReceiver, flag, PackageManager.DONT_KILL_APP);

        String keyPort = context.getString(R.string.pref_key_port);
        if (pref.getInt(keyPort, -1) == -1) {
            pref.put(keyPort, TorrentEngine.DEFAULT_PORT);
        }

        String keySaveTorrentIn = context.getString(R.string.pref_key_save_torrents_in);
        if (pref.getString(keySaveTorrentIn, null) == null) {
            pref.put(keySaveTorrentIn, FileIOUtils.getDefaultDownloadPath());
        }

        String keyFileManagerLastDir = context.getString(R.string.pref_key_filemanager_last_dir);
        if (pref.getString(keyFileManagerLastDir, null) == null) {
            pref.put(keyFileManagerLastDir, FileIOUtils.getDefaultDownloadPath());
        }

        String keyMoveAfterDownloadIn = context.getString(R.string.pref_key_move_after_download_in);
        if (pref.getString(keyMoveAfterDownloadIn, null) == null) {
            pref.put(keyMoveAfterDownloadIn, FileIOUtils.getDefaultDownloadPath());
        }

        String keyMaxDownloadSpeedLimit = context.getString(R.string.pref_key_max_download_speed);
        if (pref.getInt(keyMaxDownloadSpeedLimit, -1) == -1) {
            pref.put(keyMaxDownloadSpeedLimit, 0);
        }

        String keyMaxUploadSpeedLimit = context.getString(R.string.pref_key_max_upload_speed);
        if (pref.getInt(keyMaxUploadSpeedLimit, -1) == -1) {
            pref.put(keyMaxUploadSpeedLimit, 0);
        }

        String keyMaxConnections = context.getString(R.string.pref_key_max_connections);
        if (pref.getInt(keyMaxConnections, -1) == -1) {
            pref.put(keyMaxConnections, TorrentEngine.DEFAULT_CONNECTIONS_LIMIT);
        }

        String keyMaxActiveUploads = context.getString(R.string.pref_key_max_active_uploads);
        if (pref.getInt(keyMaxActiveUploads, -1) == -1) {
            pref.put(keyMaxActiveUploads, TorrentEngine.DEFAULT_ACTIVE_SEEDS);
        }

        String keyMaxActiveDownloads = context.getString(R.string.pref_key_max_active_downloads);
        if (pref.getInt(keyMaxActiveDownloads, -1) == -1) {
            pref.put(keyMaxActiveDownloads, TorrentEngine.DEFAULT_ACTIVE_DOWNLOADS);
        }

        String keyMaxActiveTorrents = context.getString(R.string.pref_key_max_active_torrents);
        if (pref.getInt(keyMaxActiveTorrents, -1) == -1) {
            pref.put(keyMaxActiveTorrents, TorrentEngine.DEFAULT_ACTIVE_LIMIT);
        }

        String keyNotifySound = context.getString(R.string.pref_key_notify_sound);
        if (pref.getString(keyMaxActiveTorrents, null) == null) {
            pref.put(keyNotifySound, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString());
        }

        String keyLedIndicatorColor = context.getString(R.string.pref_key_led_indicator_color_notify);
        if (pref.getInt(keyLedIndicatorColor, -1) == -1) {
            pref.put(keyLedIndicatorColor, ContextCompat.getColor(context, R.color.primary));
        }

        String keyProxyType = context.getString(R.string.pref_key_proxy_type);
        if (pref.getInt(keyProxyType, -1) == -1) {
            pref.put(keyProxyType, Integer.parseInt(context.getString(R.string.pref_proxy_type_none_value)));
        }

        String keyProxyPort = context.getString(R.string.pref_key_proxy_port);
        if (pref.getInt(keyProxyPort, -1) == -1) {
            pref.put(keyProxyPort, TorrentEngine.DEFAULT_PROXY_PORT);
        }

        String keyEncryptMode = context.getString(R.string.pref_key_enc_mode);
        if (pref.getInt(keyEncryptMode, -1) == -1) {
            pref.put(keyEncryptMode, Integer.parseInt(context.getString(R.string.pref_enc_mode_prefer_value)));
        }

        String keySortTorrentBy = context.getString(R.string.pref_key_sort_torrent_by);
        if (pref.getString(keySortTorrentBy, null) == null) {
            pref.put(keySortTorrentBy, TorrentSorting.SortingColumns.name.name());
        }

        String keySortTorrentDirection = context.getString(R.string.pref_key_sort_torrent_direction);
        if (pref.getString(keySortTorrentDirection, null) == null) {
            pref.put(keySortTorrentDirection, TorrentSorting.Direction.ASC.name());
        }

        String keySaveTorrentFilesIn = context.getString(R.string.pref_key_save_torrent_files_in);
        if (pref.getString(keySaveTorrentFilesIn, null) == null) {
            pref.put(keySaveTorrentFilesIn, FileIOUtils.getDefaultDownloadPath());
        }
    }
}