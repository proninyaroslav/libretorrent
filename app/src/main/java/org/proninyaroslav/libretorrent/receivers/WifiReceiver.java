/*
 * Copyright (C) 2017 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.net.wifi.WifiManager;

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
        if (action != null && action.equals((WifiManager.WIFI_STATE_CHANGED_ACTION))) {
            Context appContext = context.getApplicationContext();
            WifiManager manager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            if (manager != null) {
                int state = manager.getWifiState();
                switch (state) {
                    case WifiManager.WIFI_STATE_ENABLED:
                    case WifiManager.WIFI_STATE_DISABLED:
                        Intent serviceIntent = new Intent(appContext, TorrentTaskService.class);
                        serviceIntent.setAction(state == WifiManager.WIFI_STATE_ENABLED ?
                                                ACTION_WIFI_ENABLED : ACTION_WIFI_DISABLED);
                        context.startService(serviceIntent);
                        break;
                }
            }
        }
    }

    public static IntentFilter getFilter()
    {
        return new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
    }
}
