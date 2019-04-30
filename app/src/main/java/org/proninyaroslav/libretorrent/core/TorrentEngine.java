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

package org.proninyaroslav.libretorrent.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.proninyaroslav.libretorrent.MainApplication;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepository;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.receivers.ConnectionReceiver;
import org.proninyaroslav.libretorrent.receivers.old.PowerReceiver;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import androidx.annotation.NonNull;
import io.reactivex.disposables.CompositeDisposable;

public class TorrentEngine
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentEngine.class.getSimpleName();

    private Context appContext;
    private TorrentSession session;
    private TorrentRepository repo;
    private SharedPreferences pref;
    private CompositeDisposable disposables = new CompositeDisposable();

    private PowerReceiver powerReceiver = new PowerReceiver();
    private ConnectionReceiver connectionReceiver = new ConnectionReceiver();

    private static TorrentEngine INSTANCE;

    public static TorrentEngine getInstance(@NonNull Context appContext)
    {
        if (INSTANCE == null) {
            synchronized (TorrentEngine.class) {
                if (INSTANCE == null)
                    INSTANCE = new TorrentEngine(appContext);
            }
        }

        return INSTANCE;
    }

    private TorrentEngine(@NonNull Context appContext)
    {
        this.appContext = appContext;
        repo = ((MainApplication)appContext).getTorrentRepository();
        pref = SettingsManager.getInstance(appContext).getPreferences();
        session = new TorrentSession(appContext);
        session.setSettings(SettingsManager.getInstance(appContext).readSessionSettings(appContext));

        switchConnectionReceiver();
        switchPowerReceiver();
        pref.registerOnSharedPreferenceChangeListener(sharedPrefListener);
    }

    public void startSession()
    {
        if (session.isRunning())
            return;

        session.start();
    }

    public void stopSession()
    {
        if (!session.isRunning())
            return;

        session.stop();
    }

    public void addSessionListener(TorrentSessionListener listener)
    {
        session.addListener(listener);
    }

    public void removeSessionListener(TorrentSessionListener listener)
    {
        session.removeListener(listener);
    }

    public void reschedulePendingTorrents()
    {
        /* TODO: implement */
    }

    public void rescheduleTorrents()
    {
        /* TODO: implement */
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener sharedPrefListener = (sharedPreferences, key) -> {
        boolean reschedule = false;

        if (key.equals(appContext.getString(R.string.pref_key_umnetered_connections_only)) ||
            key.equals(appContext.getString(R.string.pref_key_enable_roaming))) {
            reschedule = true;
            switchConnectionReceiver();

        } else if (key.equals(appContext.getString(R.string.pref_key_download_and_upload_only_when_charging)) ||
                   key.equals(appContext.getString(R.string.pref_key_battery_control))) {
            reschedule = true;
            switchPowerReceiver();

        } else if (key.equals(appContext.getString(R.string.pref_key_custom_battery_control))) {
            switchPowerReceiver();
        }

        if (reschedule) {
            reschedulePendingTorrents();
            rescheduleTorrents();
        }
    };

    private void switchPowerReceiver()
    {
        boolean batteryControl = pref.getBoolean(appContext.getString(R.string.pref_key_battery_control),
                                                 SettingsManager.Default.batteryControl);
        boolean customBatteryControl = pref.getBoolean(appContext.getString(R.string.pref_key_custom_battery_control),
                                                       SettingsManager.Default.customBatteryControl);
        boolean onlyCharging = pref.getBoolean(appContext.getString(R.string.pref_key_download_and_upload_only_when_charging),
                                               SettingsManager.Default.onlyCharging);

        try {
            appContext.unregisterReceiver(powerReceiver);

        } catch (IllegalArgumentException e) {
            /* Ignore non-registered receiver */
        }
        if (customBatteryControl) {
            appContext.registerReceiver(powerReceiver, PowerReceiver.getCustomFilter());
            /* Custom receiver doesn't send sticky intent, reschedule manually */
            rescheduleTorrents();
        } else if (batteryControl || onlyCharging) {
            appContext.registerReceiver(powerReceiver, PowerReceiver.getFilter());
        }
    }

    private void switchConnectionReceiver()
    {
        boolean unmeteredOnly = pref.getBoolean(appContext.getString(R.string.pref_key_umnetered_connections_only),
                                                SettingsManager.Default.unmeteredConnectionsOnly);
        boolean roaming = pref.getBoolean(appContext.getString(R.string.pref_key_enable_roaming),
                                          SettingsManager.Default.enableRoaming);

        try {
            appContext.unregisterReceiver(connectionReceiver);

        } catch (IllegalArgumentException e) {
            /* Ignore non-registered receiver */
        }
        if (unmeteredOnly || roaming)
            appContext.registerReceiver(connectionReceiver, ConnectionReceiver.getFilter());
    }

    private boolean checkStopDownloads()
    {
        boolean batteryControl = pref.getBoolean(appContext.getString(R.string.pref_key_battery_control),
                                                 SettingsManager.Default.batteryControl);
        boolean customBatteryControl = pref.getBoolean(appContext.getString(R.string.pref_key_custom_battery_control),
                                                       SettingsManager.Default.customBatteryControl);
        int customBatteryControlValue = pref.getInt(appContext.getString(R.string.pref_key_custom_battery_control_value),
                                                    Utils.getDefaultBatteryLowLevel());
        boolean onlyCharging = pref.getBoolean(appContext.getString(R.string.pref_key_download_and_upload_only_when_charging),
                                               SettingsManager.Default.onlyCharging);
        boolean unmeteredOnly = pref.getBoolean(appContext.getString(R.string.pref_key_umnetered_connections_only),
                                                SettingsManager.Default.unmeteredConnectionsOnly);
        boolean roaming = pref.getBoolean(appContext.getString(R.string.pref_key_enable_roaming),
                                          SettingsManager.Default.enableRoaming);

        boolean stop = false;
        if (roaming)
            stop = Utils.isRoaming(appContext);
        if (unmeteredOnly)
            stop = Utils.isMetered(appContext);
        if (onlyCharging)
            stop |= !Utils.isBatteryCharging(appContext);
        if (customBatteryControl)
            stop |= Utils.isBatteryBelowThreshold(appContext, customBatteryControlValue);
        else if (batteryControl)
            stop |= Utils.isBatteryLow(appContext);

        return stop;
    }
}
