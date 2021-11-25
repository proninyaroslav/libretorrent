/*
 * Copyright (C) 2016-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.receiver;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.service.Scheduler;
import org.proninyaroslav.libretorrent.ui.TorrentNotifier;

/*
 * The receiver for autostart service.
 */

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        var appContext = context.getApplicationContext();
        var pref = RepositoryHelper.getSettingsRepository(appContext);

        var action = intent.getAction();
        if (isBootCompleted(action)) {
            initScheduling(context, pref);
            if (pref.autostart() && pref.keepAlive()) {
                TorrentEngine.getInstance(appContext).start();
            }
        } else if (AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            initScheduling(context, pref);
        }
    }

    private boolean isBootCompleted(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
                "com.htc.intent.action.QUICKBOOT_POWERON".equals(action);
    }

    private void initScheduling(Context appContext, SettingsRepository pref) {
        var notifier = TorrentNotifier.getInstance(appContext);

        if (pref.enableSchedulingStart()) {
            if (!Scheduler.setStartAppAlarm(appContext, pref.schedulingStartTime())) {
                notifier.makeExactAlarmPermissionNotify();
            }
        }
        if (pref.enableSchedulingShutdown()) {
            if (!Scheduler.setStopAppAlarm(appContext, pref.schedulingShutdownTime())) {
                notifier.makeExactAlarmPermissionNotify();
            }
        }
    }
}
