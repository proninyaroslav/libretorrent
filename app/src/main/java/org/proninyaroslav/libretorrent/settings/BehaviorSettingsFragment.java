/*
 * Copyright (C) 2016, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.Preference;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.takisoft.preferencex.PreferenceFragmentCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.dialogs.BaseAlertDialog;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class BehaviorSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = BehaviorSettingsFragment.class.getSimpleName();

    private static final String TAG_CUSTOM_BATTERY_DIALOG = "custom_battery_dialog";
    private CompositeDisposable disposables = new CompositeDisposable();
    private BaseAlertDialog.SharedViewModel dialogViewModel;


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

        dialogViewModel = ViewModelProviders.of(getActivity()).get(BaseAlertDialog.SharedViewModel.class);

        SharedPreferences pref = SettingsManager.getInstance(getActivity().getApplicationContext())
                .getPreferences();

        String keyAutostart = getString(R.string.pref_key_autostart);
        SwitchPreferenceCompat autostart = findPreference(keyAutostart);
        if (autostart != null) {
            autostart.setChecked(pref.getBoolean(keyAutostart, SettingsManager.Default.autostart));
            bindOnPreferenceChangeListener(autostart);
        }

        String keyKeepAlive = getString(R.string.pref_key_keep_alive);
        SwitchPreferenceCompat keepAlive = findPreference(keyKeepAlive);
        if (keepAlive != null) {
            keepAlive.setChecked(pref.getBoolean(keyKeepAlive, SettingsManager.Default.keepAlive));
            bindOnPreferenceChangeListener(keepAlive);
        }

        String keyShutdownComplete = getString(R.string.pref_key_shutdown_downloads_complete);
        SwitchPreferenceCompat shutdownComplete = findPreference(keyShutdownComplete);
        if (shutdownComplete != null) {
            shutdownComplete.setChecked(pref.getBoolean(keyShutdownComplete, SettingsManager.Default.shutdownDownloadsComplete));
            bindOnPreferenceChangeListener(shutdownComplete);
        }

        String keyCpuSleep = getString(R.string.pref_key_cpu_do_not_sleep);
        SwitchPreferenceCompat cpuSleep = findPreference(keyCpuSleep);
        if (cpuSleep != null) {
            cpuSleep.setChecked(pref.getBoolean(keyCpuSleep, SettingsManager.Default.cpuDoNotSleep));
            bindOnPreferenceChangeListener(cpuSleep);
        }

        String keyOnlyCharging = getString(R.string.pref_key_download_and_upload_only_when_charging);
        SwitchPreferenceCompat onlyCharging = findPreference(keyOnlyCharging);
        if (onlyCharging != null) {
            onlyCharging.setChecked(pref.getBoolean(keyOnlyCharging, SettingsManager.Default.onlyCharging));
            bindOnPreferenceChangeListener(onlyCharging);
        }

        String keyBatteryControl = getString(R.string.pref_key_battery_control);
        SwitchPreferenceCompat batteryControl = findPreference(keyBatteryControl);
        if (batteryControl != null) {
            batteryControl.setSummary(String.format(getString(R.string.pref_battery_control_summary),
                    Utils.getDefaultBatteryLowLevel()));
            batteryControl.setChecked(pref.getBoolean(keyBatteryControl, SettingsManager.Default.batteryControl));
            bindOnPreferenceChangeListener(batteryControl);
        }

        String keyCustomBatteryControl = getString(R.string.pref_key_custom_battery_control);
        SwitchPreferenceCompat customBatteryControl = findPreference(keyCustomBatteryControl);
        if (customBatteryControl != null) {
            customBatteryControl.setSummary(String.format(getString(R.string.pref_custom_battery_control_summary),
                    Utils.getDefaultBatteryLowLevel()));
            customBatteryControl.setChecked(pref.getBoolean(keyCustomBatteryControl, SettingsManager.Default.customBatteryControl));
            bindOnPreferenceChangeListener(customBatteryControl);
        }

        String keyCustomBatteryControlValue = getString(R.string.pref_key_custom_battery_control_value);
        SeekBarPreference customBatteryControlValue = findPreference(keyCustomBatteryControlValue);
        if (customBatteryControlValue != null) {
            customBatteryControlValue.setValue(pref.getInt(keyCustomBatteryControlValue, Utils.getDefaultBatteryLowLevel()));
            customBatteryControlValue.setMin(10);
            customBatteryControlValue.setMax(90);
        }

        String keyUmneteredOnly = getString(R.string.pref_key_umnetered_connections_only);
        SwitchPreferenceCompat unmeteredOnly = findPreference(keyUmneteredOnly);
        if (unmeteredOnly != null) {
            unmeteredOnly.setChecked(pref.getBoolean(keyUmneteredOnly, SettingsManager.Default.unmeteredConnectionsOnly));
            bindOnPreferenceChangeListener(unmeteredOnly);
        }

        String keyRoaming = getString(R.string.pref_key_enable_roaming);
        SwitchPreferenceCompat roaming = findPreference(keyRoaming);
        if (roaming != null)
            roaming.setChecked(pref.getBoolean(keyRoaming, SettingsManager.Default.enableRoaming));
    }

    @Override
    public void onStop()
    {
        super.onStop();

        disposables.clear();
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeAlertDialog();
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (!event.dialogTag.equals(TAG_CUSTOM_BATTERY_DIALOG))
                        return;
                    if (event.type == BaseAlertDialog.EventType.NEGATIVE_BUTTON_CLICKED)
                        disableCustomBatteryControl();
                });
        disposables.add(d);
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
        if (preference instanceof SwitchPreferenceCompat) {
            if (preference.getKey().equals(getString(R.string.pref_key_autostart))) {
                Utils.enableBootReceiver(getActivity(), (boolean)newValue);

            } else if(preference.getKey().equals(getString(R.string.pref_key_download_and_upload_only_when_charging))) {
                if(!((SwitchPreferenceCompat) preference).isChecked())
                    disableBatteryControl();

            } else if(preference.getKey().equals(getString(R.string.pref_key_battery_control))) {
                if(((SwitchPreferenceCompat) preference).isChecked())
                    disableCustomBatteryControl();

            } else if(preference.getKey().equals(getString(R.string.pref_key_custom_battery_control))) {
                if (!((SwitchPreferenceCompat) preference).isChecked())
                    showCustomBatteryDialog();
            }
        }

        return true;
    }

    private void showCustomBatteryDialog()
    {
        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_CUSTOM_BATTERY_DIALOG) == null) {
            BaseAlertDialog customBatteryDialog = BaseAlertDialog.newInstance(
                    getString(R.string.warning),
                    getString(R.string.pref_custom_battery_control_dialog_summary),
                    0,
                    getString(R.string.yes),
                    getString(R.string.no),
                    null,
                    true);

            customBatteryDialog.show(fm, TAG_CUSTOM_BATTERY_DIALOG);
        }
    }

    private void disableBatteryControl()
    {
        SharedPreferences pref = SettingsManager.getInstance(getActivity().getApplicationContext())
                .getPreferences();

        String keyBatteryControl = getString(R.string.pref_key_battery_control);
        SwitchPreferenceCompat batteryControl = findPreference(keyBatteryControl);
        if (batteryControl != null)
            batteryControl.setChecked(false);
        pref.edit().putBoolean(keyBatteryControl, false).apply();
        disableCustomBatteryControl();
    }

    private void disableCustomBatteryControl()
    {
        SharedPreferences pref = SettingsManager.getInstance(getActivity().getApplicationContext())
                .getPreferences();

        String keyCustomBatteryControl = getString(R.string.pref_key_custom_battery_control);
        SwitchPreferenceCompat batteryControl = findPreference(keyCustomBatteryControl);
        if (batteryControl != null)
            batteryControl.setChecked(false);
        pref.edit().putBoolean(keyCustomBatteryControl, false).apply();
    }
}
