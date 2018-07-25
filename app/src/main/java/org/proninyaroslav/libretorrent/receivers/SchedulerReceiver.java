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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.annotation.NonNull;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.services.FeedFetcherService;
import org.proninyaroslav.libretorrent.services.TorrentTaskService;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import java.util.Calendar;

/*
 * The receiver for AlarmManager scheduling
 */

public class SchedulerReceiver extends BroadcastReceiver
{
    public static final String ACTION_START_APP = "org.proninyaroslav.libretorrent.receivers.SchedulerReceiver.ACTION_START_APP";
    public static final String ACTION_STOP_APP = "org.proninyaroslav.libretorrent.receivers.SchedulerReceiver.ACTION_STOP_APP";
    public static final String ACTION_FETCH_FEEDS = "org.proninyaroslav.libretorrent.receivers.SchedulerReceiver.ACTION_FETCH_FEEDS";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (intent.getAction() == null)
            return;

        SharedPreferences pref = SettingsManager.getPreferences(context);
        switch (intent.getAction()) {
            case ACTION_START_APP: {
                if (!pref.getBoolean(context.getString(R.string.pref_key_enable_scheduling_start),
                                     SettingsManager.Default.enableSchedulingStart))
                    return;

                boolean oneshot = pref.getBoolean(context.getString(R.string.pref_key_scheduling_run_only_once),
                                                  SettingsManager.Default.schedulingRunOnlyOnce);
                if (oneshot) {
                    pref.edit().putBoolean(context.getString(R.string.pref_key_enable_scheduling_start), false)
                            .apply();
                } else {
                    setStartStopAppAlarm(context, ACTION_START_APP,
                            pref.getInt(context.getString(R.string.pref_key_scheduling_start_time),
                                        SettingsManager.Default.schedulingStartTime));
                }
                if (pref.getBoolean(context.getString(R.string.pref_key_scheduling_switch_wifi),
                                    SettingsManager.Default.schedulingSwitchWiFi))
                    ((WifiManager) context.getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(true);

                Utils.startTorrentServiceBackground(context, null);
                Utils.enableBootReceiverIfNeeded(context);
                break;
            } case ACTION_STOP_APP: {
                if (!pref.getBoolean(context.getString(R.string.pref_key_enable_scheduling_shutdown),
                                     SettingsManager.Default.enableSchedulingShutdown))
                    return;

                boolean oneshot = pref.getBoolean(context.getString(R.string.pref_key_scheduling_run_only_once),
                                                  SettingsManager.Default.schedulingRunOnlyOnce);
                if (oneshot) {
                    pref.edit().putBoolean(context.getString(R.string.pref_key_enable_scheduling_shutdown), false)
                            .apply();
                } else {
                    setStartStopAppAlarm(context, ACTION_STOP_APP,
                            pref.getInt(context.getString(R.string.pref_key_scheduling_shutdown_time),
                                        SettingsManager.Default.schedulingShutdownTime));
                }

                Utils.startTorrentServiceBackground(context, TorrentTaskService.ACTION_SHUTDOWN);

                if (pref.getBoolean(context.getString(R.string.pref_key_scheduling_switch_wifi),
                        SettingsManager.Default.schedulingSwitchWiFi))
                    ((WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(false);
                Utils.enableBootReceiverIfNeeded(context);
                break;
            } case ACTION_FETCH_FEEDS: {
                if (!pref.getBoolean(context.getString(R.string.pref_key_feed_auto_refresh),
                                     SettingsManager.Default.autoRefreshFeeds)) {
                    cancelScheduling(context, ACTION_FETCH_FEEDS);
                    Utils.enableBootReceiver(context, false);
                    break;
                }
                if (pref.getBoolean(context.getString(R.string.pref_key_feed_auto_refresh_wifi_only),
                                    SettingsManager.Default.autoRefreshWiFiOnly) && !Utils.isWifiEnabled(context))
                    break;

                Intent i = new Intent(context, FeedFetcherService.class);
                i.setAction(FeedFetcherService.ACTION_FETCH_ALL_CHANNELS);
                FeedFetcherService.enqueueWork(context, i);
                break;
            }
        }
    }

    /*
     * Time in minutes after 00:00
     */
    public static void setStartStopAppAlarm(Context context, String action, int time) {
        Calendar calendar = Calendar.getInstance();
        long timeInMillis = System.currentTimeMillis();
        calendar.setTimeInMillis(timeInMillis);
        calendar.set(Calendar.HOUR_OF_DAY, time / 60);
        calendar.set(Calendar.MINUTE, time % 60);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() < timeInMillis + 2000L)
            calendar.add(Calendar.DAY_OF_MONTH, 1);

        Intent intent = new Intent(context, SchedulerReceiver.class);
        intent.setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(context, action.hashCode(), intent, 0);
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
            am.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pi);
        else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            am.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pi);
        else
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pi);
    }

    public static void setRefreshFeedsAlarm(Context context, long interval)
    {
        Intent intent = new Intent(context, SchedulerReceiver.class);
        intent.setAction(ACTION_FETCH_FEEDS);
        PendingIntent pi = PendingIntent.getBroadcast(context, ACTION_FETCH_FEEDS.hashCode(), intent, 0);
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + interval, interval, pi);
    }

    public static void cancelScheduling(Context context, @NonNull String action)
    {
        Intent intent = new Intent(context, SchedulerReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, action.hashCode(), intent, 0);
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
    }
}
