/*
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import org.proninyaroslav.libretorrent.core.Scheduler;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.services.TorrentService;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import static org.proninyaroslav.libretorrent.core.Scheduler.SCHEDULER_WORK_START_APP;
import static org.proninyaroslav.libretorrent.core.Scheduler.SCHEDULER_WORK_STOP_APP;

/*
 * The receiver for AlarmManager scheduling
 */

public class SchedulerReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (intent.getAction() == null)
            return;

        Context appContext = context.getApplicationContext();
        SharedPreferences pref = SettingsManager.getInstance(appContext).getPreferences();

        switch (intent.getAction()) {
            case SCHEDULER_WORK_START_APP: {
                onStartApp(appContext, pref);
                break;
            }
            case SCHEDULER_WORK_STOP_APP: {
                onStopApp(appContext, pref);
                break;
            }
        }
    }

    private void onStartApp(Context appContext, SharedPreferences pref)
    {
        if (!pref.getBoolean(appContext.getString(R.string.pref_key_enable_scheduling_start),
                             SettingsManager.Default.enableSchedulingStart))
            return;

        boolean oneshot = pref.getBoolean(appContext.getString(R.string.pref_key_scheduling_run_only_once),
                                          SettingsManager.Default.schedulingRunOnlyOnce);
        if (oneshot) {
            pref.edit().putBoolean(appContext.getString(R.string.pref_key_enable_scheduling_start), false)
                    .apply();
        } else {
            Scheduler.setStartAppAlarm(appContext,
                    pref.getInt(appContext.getString(R.string.pref_key_scheduling_start_time),
                                SettingsManager.Default.schedulingStartTime));
        }
        if (pref.getBoolean(appContext.getString(R.string.pref_key_scheduling_switch_wifi),
                            SettingsManager.Default.schedulingSwitchWiFi))
            ((WifiManager)appContext.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(true);

        Utils.startServiceBackground(appContext, new Intent(appContext, TorrentService.class));
        Utils.enableBootReceiverIfNeeded(appContext);
    }

    private void onStopApp(Context appContext, SharedPreferences pref)
    {
        if (!pref.getBoolean(appContext.getString(R.string.pref_key_enable_scheduling_shutdown),
                             SettingsManager.Default.enableSchedulingShutdown))
            return;

        boolean oneshot = pref.getBoolean(appContext.getString(R.string.pref_key_scheduling_run_only_once),
                                          SettingsManager.Default.schedulingRunOnlyOnce);
        if (oneshot) {
            pref.edit().putBoolean(appContext.getString(R.string.pref_key_enable_scheduling_shutdown), false)
                    .apply();
        } else {
            Scheduler.setStartAppAlarm(appContext,
                    pref.getInt(appContext.getString(R.string.pref_key_scheduling_shutdown_time),
                                SettingsManager.Default.schedulingShutdownTime));
        }

        Intent i = new Intent(appContext, TorrentService.class);
        i.setAction(TorrentService.ACTION_SHUTDOWN);
        Utils.startServiceBackground(appContext, i);

        if (pref.getBoolean(appContext.getString(R.string.pref_key_scheduling_switch_wifi),
                            SettingsManager.Default.schedulingSwitchWiFi))
            ((WifiManager)appContext.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(false);

        Utils.enableBootReceiverIfNeeded(appContext);
    }
}
