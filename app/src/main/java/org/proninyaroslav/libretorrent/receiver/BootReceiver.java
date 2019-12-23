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

package org.proninyaroslav.libretorrent.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.service.Scheduler;

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

        Context appContext = context.getApplicationContext();
        SettingsRepository pref = RepositoryHelper.getSettingsRepository(appContext);

        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            initScheduling(context, pref);

            if (pref.autostart() && pref.keepAlive())
                TorrentEngine.getInstance(appContext).start();
        }
    }

    private void initScheduling(Context appContext, SettingsRepository pref)
    {
        if (pref.enableSchedulingStart())
            Scheduler.setStartAppAlarm(appContext, pref.schedulingStartTime());

        if (pref.enableSchedulingShutdown())
            Scheduler.setStopAppAlarm(appContext, pref.schedulingShutdownTime());
    }
}
