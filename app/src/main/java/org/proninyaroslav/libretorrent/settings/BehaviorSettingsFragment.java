/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.settings;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.SeekBarPreference;
import android.support.v7.preference.SwitchPreferenceCompat;

import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.receivers.BootReceiver;

public class BehaviorSettingsFragment extends PreferenceFragmentCompat
        implements
        Preference.OnPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = BehaviorSettingsFragment.class.getSimpleName();

    public static BehaviorSettingsFragment newInstance()
    {
        BehaviorSettingsFragment fragment = new BehaviorSettingsFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        final SharedPreferences pref = SettingsManager.getPreferences(getActivity());

        String keyAutostart = getString(R.string.pref_key_autostart);
        SwitchPreferenceCompat autostart = (SwitchPreferenceCompat) findPreference(keyAutostart);
        autostart.setChecked(pref.getBoolean(keyAutostart, SettingsManager.Default.autostart));
        bindOnPreferenceChangeListener(autostart);

        String keyKeepAlive = getString(R.string.pref_key_keep_alive);
        SwitchPreferenceCompat keepAlive = (SwitchPreferenceCompat) findPreference(keyKeepAlive);
        keepAlive.setChecked(pref.getBoolean(keyKeepAlive, SettingsManager.Default.keepAlive));
        bindOnPreferenceChangeListener(keepAlive);

        String keyShutdownComplete = getString(R.string.pref_key_shutdown_downloads_complete);
        SwitchPreferenceCompat shutdownComplete = (SwitchPreferenceCompat) findPreference(keyShutdownComplete);
        shutdownComplete.setChecked(pref.getBoolean(keyShutdownComplete, SettingsManager.Default.shutdownDownloadsComplete));
        bindOnPreferenceChangeListener(shutdownComplete);

        String keyCpuSleep = getString(R.string.pref_key_cpu_do_not_sleep);
        SwitchPreferenceCompat cpuSleep = (SwitchPreferenceCompat) findPreference(keyCpuSleep);
        cpuSleep.setChecked(pref.getBoolean(keyCpuSleep, SettingsManager.Default.cpuDoNotSleep));
        bindOnPreferenceChangeListener(cpuSleep);

        String keyOnlyCharging = getString(R.string.pref_key_download_and_upload_only_when_charging);
        SwitchPreferenceCompat onlyCharging = (SwitchPreferenceCompat) findPreference(keyOnlyCharging);
        onlyCharging.setChecked(pref.getBoolean(keyOnlyCharging, SettingsManager.Default.onlyCharging));
        bindOnPreferenceChangeListener(onlyCharging);

        String keyBatteryControl = getString(R.string.pref_key_battery_control);
        SwitchPreferenceCompat batteryControl = (SwitchPreferenceCompat) findPreference(keyBatteryControl);
        batteryControl.setSummary(String.format(getString(R.string.pref_battery_control_summary),
                                  Utils.getDefaultBatteryLowLevel()));
        batteryControl.setChecked(pref.getBoolean(keyBatteryControl, SettingsManager.Default.batteryControl));
        bindOnPreferenceChangeListener(batteryControl);

        String keyCustomBatteryControl = getString(R.string.pref_key_custom_battery_control);
        SwitchPreferenceCompat customBatteryControl = (SwitchPreferenceCompat) findPreference(keyCustomBatteryControl);
        customBatteryControl.setSummary(String.format(getString(R.string.pref_custom_battery_control_summary),
                Utils.getDefaultBatteryLowLevel()));
        customBatteryControl.setChecked(pref.getBoolean(keyCustomBatteryControl, SettingsManager.Default.customBatteryControl));
        bindOnPreferenceChangeListener(customBatteryControl);

        String keyCustomBatteryControlValue = getString(R.string.pref_key_custom_battery_control_value);
        SeekBarPreference customBatteryControlValue = (SeekBarPreference) findPreference(keyCustomBatteryControlValue);
        customBatteryControlValue.setValue(pref.getInt(keyCustomBatteryControlValue, Utils.getDefaultBatteryLowLevel()));
        customBatteryControlValue.setMin(10);
        customBatteryControlValue.setMax(90);

        String keyWifiOnly = getString(R.string.pref_key_wifi_only);
        SwitchPreferenceCompat wifiOnly = (SwitchPreferenceCompat) findPreference(keyWifiOnly);
        wifiOnly.setChecked(pref.getBoolean(keyWifiOnly, SettingsManager.Default.wifiOnly));
        bindOnPreferenceChangeListener(wifiOnly);
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_behavior, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference)
    {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, Object newValue)
    {
        final SharedPreferences pref = SettingsManager.getPreferences(getActivity());

        if (preference instanceof SwitchPreferenceCompat) {
            if (preference.getKey().equals(getString(R.string.pref_key_autostart))) {
                Utils.enableBootReceiver(getActivity(), (boolean)newValue);
            }
            if(preference.getKey().equals(getString(R.string.pref_key_download_and_upload_only_when_charging))) {
                if(!((SwitchPreferenceCompat) preference).isChecked())
                    disableBatteryControl(pref);
            }
            if(preference.getKey().equals(getString(R.string.pref_key_battery_control))) {
                if(((SwitchPreferenceCompat) preference).isChecked())
                    disableCustomBatteryControl(pref);
            }
            if(preference.getKey().equals(getString(R.string.pref_key_custom_battery_control))) {
                if (!((SwitchPreferenceCompat) preference).isChecked()) {
                    new AlertDialog.Builder(getContext())
                            .setTitle(getString(R.string.warning))
                            .setMessage(getString(R.string.pref_custom_battery_control_dialog_summary))
                            .setCancelable(false)
                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                }
                            })
                            .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    disableCustomBatteryControl(pref);
                                }
                            })
                            .create()
                            .show();
                }
            }
        }

        return true;
    }

    private void disableBatteryControl(SharedPreferences pref)
    {
        String keyBatteryControl = getString(R.string.pref_key_battery_control);
        SwitchPreferenceCompat batteryControl = (SwitchPreferenceCompat) findPreference(keyBatteryControl);
        batteryControl.setChecked(false);
        pref.edit().putBoolean(batteryControl.getKey(), false).apply();
        disableCustomBatteryControl(pref);
    }

    private void disableCustomBatteryControl(SharedPreferences pref)
    {
        String keyCustomBatteryControl = getString(R.string.pref_key_custom_battery_control);
        SwitchPreferenceCompat batteryControl = (SwitchPreferenceCompat) findPreference(keyCustomBatteryControl);
        batteryControl.setChecked(false);
        pref.edit().putBoolean(batteryControl.getKey(), false).apply();
    }
}
