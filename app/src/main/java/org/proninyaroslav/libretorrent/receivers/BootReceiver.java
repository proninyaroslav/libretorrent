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

package org.proninyaroslav.libretorrent.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

/*
 * The receiver for autostart service.
 */

public class BootReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (intent.getAction() == null)
            return;

        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            initScheduling(context);

            SharedPreferences pref = SettingsManager.getPreferences(context.getApplicationContext());
            if (pref.getBoolean(context.getString(R.string.pref_key_autostart), SettingsManager.Default.autostart) &&
                pref.getBoolean(context.getString(R.string.pref_key_keep_alive), SettingsManager.Default.keepAlive))
                Utils.startTorrentServiceBackground(context, null);
        }
    }

    private void initScheduling(Context context)
    {
        SharedPreferences pref = SettingsManager.getPreferences(context);
        if (pref.getBoolean(context.getString(R.string.pref_key_enable_scheduling_start),
                SettingsManager.Default.enableSchedulingStart)) {
            int time = pref.getInt(context.getString(R.string.pref_key_scheduling_start_time),
                    SettingsManager.Default.schedulingStartTime);
            SchedulerReceiver.setStartStopAppAlarm(context, SchedulerReceiver.ACTION_START_APP, time);
        }
        if (pref.getBoolean(context.getString(R.string.pref_key_enable_scheduling_shutdown),
                SettingsManager.Default.enableSchedulingShutdown)) {
            int time = pref.getInt(context.getString(R.string.pref_key_scheduling_shutdown_time),
                    SettingsManager.Default.schedulingShutdownTime);
            SchedulerReceiver.setStartStopAppAlarm(context, SchedulerReceiver.ACTION_STOP_APP, time);
        }
        if (pref.getBoolean(context.getString(R.string.pref_key_feed_auto_refresh),
                SettingsManager.Default.autoRefreshFeeds)) {
            long interval = pref.getLong(context.getString(R.string.pref_key_feed_refresh_interval),
                    SettingsManager.Default.refreshFeedsInterval);
            SchedulerReceiver.setRefreshFeedsAlarm(context, interval);
        }
    }
}
