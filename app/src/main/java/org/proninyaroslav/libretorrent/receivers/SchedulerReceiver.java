/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.net.wifi.WifiManager;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.services.TorrentTaskService;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

/*
 * The receiver for AlarmManager scheduling
 */

public class SchedulerReceiver extends BroadcastReceiver
{
    public static final String ACTION_START_APP = "org.proninyaroslav.libretorrent.receivers.SchedulerReceiver.ACTION_START_APP";
    public static final String ACTION_STOP_APP = "org.proninyaroslav.libretorrent.receivers.SchedulerReceiver.ACTION_STOP_APP";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        SharedPreferences pref = SettingsManager.getPreferences(context);
        if (intent.getAction().equals(ACTION_START_APP)) {
            if (!pref.getBoolean(context.getString(R.string.pref_key_enable_scheduling_start),
                                 SettingsManager.Default.enableSchedulingStart))
                return;

            boolean oneshot = pref.getBoolean(context.getString(R.string.pref_key_scheduling_run_only_once),
                                              SettingsManager.Default.schedulingRunOnlyOnce);
            if (oneshot) {
                pref.edit().putBoolean(context.getString(R.string.pref_key_enable_scheduling_start), false)
                        .apply();
            } else {
                Utils.addScheduledTime(context, ACTION_START_APP,
                        pref.getInt(context.getString(R.string.pref_key_scheduling_start_time),
                                    SettingsManager.Default.schedulingStartTime));
            }
            if (pref.getBoolean(context.getString(R.string.pref_key_scheduling_switch_wifi),
                                SettingsManager.Default.schedulingSwitchWiFi))
                ((WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(true);

            Utils.startServiceAfterBoot(context);

        } else if (intent.getAction().equals(ACTION_STOP_APP)) {
            if (!pref.getBoolean(context.getString(R.string.pref_key_enable_scheduling_shutdown),
                                 SettingsManager.Default.enableSchedulingShutdown))
                return;

            boolean oneshot = pref.getBoolean(context.getString(R.string.pref_key_scheduling_run_only_once),
                                              SettingsManager.Default.schedulingRunOnlyOnce);
            if (oneshot) {
                pref.edit().putBoolean(context.getString(R.string.pref_key_enable_scheduling_shutdown), false)
                        .apply();
            } else {
                Utils.addScheduledTime(context, ACTION_STOP_APP,
                        pref.getInt(context.getString(R.string.pref_key_scheduling_shutdown_time),
                                    SettingsManager.Default.schedulingShutdownTime));
            }

            Intent i = new Intent(context, TorrentTaskService.class);
            i.setAction(TorrentTaskService.SHUTDOWN_ACTION);
            context.startService(i);

            if (pref.getBoolean(context.getString(R.string.pref_key_scheduling_switch_wifi),
                                SettingsManager.Default.schedulingSwitchWiFi))
                ((WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(false);
        }
        Utils.enableBootReceiverIfNeeded(context);
    }
}
