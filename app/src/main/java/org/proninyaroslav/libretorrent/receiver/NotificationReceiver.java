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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.proninyaroslav.libretorrent.service.TorrentService;
import org.proninyaroslav.libretorrent.ui.main.MainActivity;

/*
 * The receiver for actions of foreground notification, added by service.
 */

public class NotificationReceiver extends BroadcastReceiver
{
    public static final String NOTIFY_ACTION_SHUTDOWN_APP = "org.proninyaroslav.libretorrent.receivers.NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP";
    public static final String NOTIFY_ACTION_ADD_TORRENT = "org.proninyaroslav.libretorrent.receivers.NotificationReceiver.NOTIFY_ACTION_ADD_TORRENT";
    public static final String NOTIFY_ACTION_PAUSE_ALL = "org.proninyaroslav.libretorrent.receivers.NotificationReceiver.NOTIFY_ACTION_PAUSE_ALL";
    public static final String NOTIFY_ACTION_RESUME_ALL = "org.proninyaroslav.libretorrent.receivers.NotificationReceiver.NOTIFY_ACTION_RESUME_ALL";
    public static final String NOTIFY_ACTION_PAUSE_RESUME_ALL = "org.proninyaroslav.libretorrent.receivers.NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME_ALL";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        String action = intent.getAction();
        if (action == null)
            return;
        Intent mainIntent, serviceIntent;
        switch (action) {
            /* Send action to the already running service */
            case NOTIFY_ACTION_SHUTDOWN_APP:
                mainIntent = new Intent(context.getApplicationContext(), MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mainIntent.setAction(NOTIFY_ACTION_SHUTDOWN_APP);
                context.startActivity(mainIntent);

                serviceIntent = new Intent(context.getApplicationContext(), TorrentService.class);
                serviceIntent.setAction(NOTIFY_ACTION_SHUTDOWN_APP);
                context.startService(serviceIntent);
                break;
            case NOTIFY_ACTION_ADD_TORRENT:
                mainIntent = new Intent(context.getApplicationContext(), MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mainIntent.setAction(NOTIFY_ACTION_ADD_TORRENT);
                context.startActivity(mainIntent);
                break;
            case NOTIFY_ACTION_PAUSE_ALL:
                serviceIntent = new Intent(context.getApplicationContext(), TorrentService.class);
                serviceIntent.setAction(NOTIFY_ACTION_PAUSE_ALL);
                context.startService(serviceIntent);
                break;
            case NOTIFY_ACTION_RESUME_ALL:
                serviceIntent = new Intent(context.getApplicationContext(), TorrentService.class);
                serviceIntent.setAction(NOTIFY_ACTION_RESUME_ALL);
                context.startService(serviceIntent);
                break;
            case NOTIFY_ACTION_PAUSE_RESUME_ALL:
                serviceIntent = new Intent(context.getApplicationContext(), TorrentService.class);
                serviceIntent.setAction(NOTIFY_ACTION_PAUSE_RESUME_ALL);
                context.startService(serviceIntent);
                break;
        }
    }
}
