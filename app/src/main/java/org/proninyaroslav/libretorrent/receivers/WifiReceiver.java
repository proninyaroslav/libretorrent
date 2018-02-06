/*
 * Copyright (C) 2017, 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.proninyaroslav.libretorrent.services.TorrentTaskService;

/*
 * The receiver for Wi-Fi connection state changes.
 */

public class WifiReceiver extends BroadcastReceiver
{
    public static final String ACTION_WIFI_ENABLED = "org.proninyaroslav.libretorrent.receivers.WifiReceiver.ACTION_WIFI_ENABLED";
    public static final String ACTION_WIFI_DISABLED = "org.proninyaroslav.libretorrent.receivers.WifiReceiver.ACTION_WIFI_DISABLED";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        String action = intent.getAction();
        if (action != null && action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            NetworkInfo info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
            if (info != null && info.getType() == ConnectivityManager.TYPE_WIFI) {
                Intent serviceIntent = new Intent(context.getApplicationContext(), TorrentTaskService.class);
                serviceIntent.setAction(info.isConnected() ? ACTION_WIFI_ENABLED : ACTION_WIFI_DISABLED);
                context.startService(serviceIntent);
            }
        }
    }

    public static IntentFilter getFilter()
    {
        return new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    }
}
