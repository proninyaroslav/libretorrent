/*
 * Copyright (C) 2016-2022 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent;

import android.annotation.SuppressLint;
import android.database.CursorWindow;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.data.StringFormat;
import org.proninyaroslav.libretorrent.ui.TorrentNotifier;
import org.proninyaroslav.libretorrent.ui.errorreport.ErrorReportActivity;

public class MainApplication extends MultiDexApplication {
    public static final String TAG = MainApplication.class.getSimpleName();

    static {
        /* Vector Drawable support in ImageView for API < 21 */
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        CoreConfigurationBuilder builder = new CoreConfigurationBuilder();
        builder
                .withBuildConfigClass(BuildConfig.class)
                .withReportFormat(StringFormat.JSON);
        builder.withPluginConfigurations(new MailSenderConfigurationBuilder()
                .withMailTo("proninyaroslav@mail.ru")
                .build());
        builder.withPluginConfigurations(new DialogConfigurationBuilder()
                .withEnabled(true)
                .withReportDialogClass(ErrorReportActivity.class)
                .build());
        // Set stub handler
        if (Thread.getDefaultUncaughtExceptionHandler() == null) {
            Thread.setDefaultUncaughtExceptionHandler((t, e) ->
                    Log.e(TAG, "Uncaught exception in " + t + ": " + Log.getStackTraceString(e))
            );
        }
        ACRA.init(this, builder);

        increaseCursorWindowSize();

        TorrentNotifier.getInstance(this).makeNotifyChans();
    }

    @SuppressLint("DiscouragedPrivateApi")
    private void increaseCursorWindowSize() {
        try {
            var field = CursorWindow.class.getDeclaredField("sCursorWindowSize");
            field.setAccessible(true);
            field.set(null, 100 * 1024 * 1024); // 100MB
        } catch (Exception e) {
            Log.e(TAG, "Unable to increase CursorWindow size", e);
        }
    }
}