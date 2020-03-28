/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui;

/*
 * Helper of showing notifications.
 */

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.model.data.entity.Torrent;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;

import java.util.ArrayList;

import static android.content.Context.NOTIFICATION_SERVICE;

public class TorrentNotifier
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentNotifier.class.getSimpleName();

    public static final String FOREGROUND_NOTIFY_CHAN_ID = "org.proninyaroslav.libretorrent.FOREGROUND_NOTIFY_CHAN";
    public static final String DEFAULT_NOTIFY_CHAN_ID = "org.proninyaroslav.libretorrent.DEFAULT_NOTIFY_CHAN_ID";
    public static final String FINISH_NOTIFY_CHAN_ID = "org.proninyaroslav.libretorrent.FINISH_NOTIFY_CHAN_ID";

    private static final int SESSION_ERROR_NOTIFICATION_ID = 1;
    private static final int NAT_ERROR_NOTIFICATION_ID = 2;

    private static volatile TorrentNotifier INSTANCE;

    private Context appContext;
    private NotificationManager notifyManager;
    private SettingsRepository pref;

    public static TorrentNotifier getInstance(@NonNull Context appContext)
    {
        if (INSTANCE == null) {
            synchronized (TorrentNotifier.class) {
                if (INSTANCE == null)
                    INSTANCE = new TorrentNotifier(appContext);
            }
        }
        return INSTANCE;
    }

    private TorrentNotifier(Context appContext)
    {
        this.appContext = appContext;
        notifyManager = (NotificationManager)appContext.getSystemService(NOTIFICATION_SERVICE);
        pref = RepositoryHelper.getSettingsRepository(appContext);
    }

    public void makeNotifyChans()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;

        ArrayList<NotificationChannel> channels = new ArrayList<>();

        channels.add(new NotificationChannel(DEFAULT_NOTIFY_CHAN_ID,
                appContext.getString(R.string.def),
                NotificationManager.IMPORTANCE_DEFAULT));
        NotificationChannel foregroundChan = new NotificationChannel(FOREGROUND_NOTIFY_CHAN_ID,
                appContext.getString(R.string.foreground_notification),
                NotificationManager.IMPORTANCE_LOW);
        foregroundChan.setShowBadge(false);
        channels.add(foregroundChan);
        channels.add(new NotificationChannel(FINISH_NOTIFY_CHAN_ID,
                appContext.getString(R.string.finished),
                NotificationManager.IMPORTANCE_DEFAULT));

        notifyManager.createNotificationChannels(channels);
    }

    public void makeTorrentErrorNotify(@NonNull String name, @NonNull String message)
    {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext,
                DEFAULT_NOTIFY_CHAN_ID)
                .setSmallIcon(R.drawable.ic_error_white_24dp)
                .setColor(ContextCompat.getColor(appContext, R.color.primary))
                .setContentTitle(name)
                .setTicker(appContext.getString(R.string.torrent_error_notify_title))
                .setContentText(appContext.getString(R.string.error_template, message))
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_ERROR);

        notifyManager.notify(name.hashCode(), builder.build());
    }

    public void makeTorrentInfoNotify(@NonNull String name, @NonNull String message)
    {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext,
                DEFAULT_NOTIFY_CHAN_ID)
                .setSmallIcon(R.drawable.ic_info_white_24dp)
                .setColor(ContextCompat.getColor(appContext, R.color.primary))
                .setContentTitle(name)
                .setTicker(message)
                .setContentText(message)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_STATUS);

        notifyManager.notify(name.hashCode(), builder.build());
    }

    public void makeErrorNotify(@NonNull String message)
    {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext,
                DEFAULT_NOTIFY_CHAN_ID)
                .setSmallIcon(R.drawable.ic_error_white_24dp)
                .setColor(ContextCompat.getColor(appContext, R.color.primary))
                .setContentTitle(appContext.getString(R.string.error))
                .setTicker(message)
                .setContentText(message)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_ERROR);

        notifyManager.notify(message.hashCode(), builder.build());
    }

    public void makeSessionErrorNotify(@NonNull String message)
    {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext,
                DEFAULT_NOTIFY_CHAN_ID)
                .setSmallIcon(R.drawable.ic_error_white_24dp)
                .setColor(ContextCompat.getColor(appContext, R.color.primary))
                .setContentTitle(appContext.getString(R.string.session_error_title))
                .setTicker(appContext.getString(R.string.session_error_title))
                .setContentText(appContext.getString(R.string.error_template, message))
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_ERROR);

        notifyManager.notify(SESSION_ERROR_NOTIFICATION_ID, builder.build());
    }

    public void makeNatErrorNotify(@NonNull String message)
    {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext,
                DEFAULT_NOTIFY_CHAN_ID)
                .setSmallIcon(R.drawable.ic_error_white_24dp)
                .setColor(ContextCompat.getColor(appContext, R.color.primary))
                .setContentTitle(appContext.getString(R.string.nat_error_title))
                .setTicker(appContext.getString(R.string.nat_error_title))
                .setContentText(appContext.getString(R.string.error_template, message))
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_ERROR);

        notifyManager.notify(NAT_ERROR_NOTIFICATION_ID, builder.build());
    }

    public void makeMovingTorrentNotify(@NonNull String name)
    {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext,
                DEFAULT_NOTIFY_CHAN_ID)
                .setSmallIcon(R.drawable.ic_folder_move_white_24dp)
                .setColor(ContextCompat.getColor(appContext, R.color.primary))
                .setContentTitle(name)
                .setTicker(appContext.getString(R.string.torrent_moving_title))
                .setContentText(appContext.getString(R.string.torrent_moving_content, name))
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_STATUS);

        notifyManager.notify(name.hashCode(), builder.build());
    }

    public void makeTorrentFinishedNotify(@NonNull Torrent torrent)
    {
        if (!pref.torrentFinishNotify() || torrent.visibility == Torrent.VISIBILITY_HIDDEN)
            return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext,
                FINISH_NOTIFY_CHAN_ID)
                .setSmallIcon(R.drawable.ic_done_white_24dp)
                .setColor(ContextCompat.getColor(appContext, R.color.primary))
                .setContentTitle(appContext.getString(R.string.torrent_finished_notify))
                .setTicker(appContext.getString(R.string.torrent_finished_notify))
                .setContentText(torrent.name)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_STATUS);

        applyLegacyNotifySettings(builder);

        notifyManager.notify(torrent.id.hashCode(), builder.build());
    }

    /*
     * Starting with the version of Android 8.0,
     * setting notifications from the app preferences isn't working,
     * you can change them only in the settings of Android 8.0
     */

    private void applyLegacyNotifySettings(NotificationCompat.Builder builder)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            return;

        SettingsRepository pref = RepositoryHelper.getSettingsRepository(appContext);

        if (pref.playSoundNotify()) {
            Uri sound = Uri.parse(pref.notifySound());
            builder.setSound(sound);
        }

        if (pref.vibrationNotify())
            builder.setVibrate(new long[] {1000}); /* ms */

        if (pref.ledIndicatorNotify()) {
            int color = pref.ledIndicatorColorNotify();
            builder.setLights(color, 1000, 1000); /* ms */
        }
    }
}
