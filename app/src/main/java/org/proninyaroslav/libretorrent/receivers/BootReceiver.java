/*
 * Copyright (C) 2016, 2017 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.services.TorrentTaskService;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

/*
 * The receiver for autostart service.
 */

public class BootReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            SettingsManager pref = new SettingsManager(context.getApplicationContext());
            if (pref.getBoolean(context.getString(R.string.pref_key_autostart), SettingsManager.Default.autostart) &&
                pref.getBoolean(context.getString(R.string.pref_key_keep_alive), SettingsManager.Default.keepAlive))
                context.startService(new Intent(context, TorrentTaskService.class));
        }
    }
}
