/*
 * Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.settings.pages;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.ui.settings.CustomPreferenceFragment;
import org.proninyaroslav.libretorrent.ui.settings.customprefs.SliderPreference;

public class BehaviorSettingsFragment extends CustomPreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private AppCompatActivity activity;
    private SettingsRepository pref;

    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity) {
            activity = (AppCompatActivity) context;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.appBar.setTitle(R.string.pref_header_behavior);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) requireActivity();
        }

        pref = RepositoryHelper.getSettingsRepository(activity.getApplicationContext());

        String keyAutostart = getString(R.string.pref_key_autostart);
        SwitchPreferenceCompat autostart = findPreference(keyAutostart);
        if (autostart != null) {
            autostart.setChecked(pref.autostart());
            bindOnPreferenceChangeListener(autostart);
        }

        String keyKeepAlive = getString(R.string.pref_key_keep_alive);
        SwitchPreferenceCompat keepAlive = findPreference(keyKeepAlive);
        if (keepAlive != null) {
            keepAlive.setChecked(pref.keepAlive());
            bindOnPreferenceChangeListener(keepAlive);
        }

        String keyShutdownComplete = getString(R.string.pref_key_shutdown_downloads_complete);
        SwitchPreferenceCompat shutdownComplete = findPreference(keyShutdownComplete);
        if (shutdownComplete != null) {
            shutdownComplete.setChecked(pref.shutdownDownloadsComplete());
            bindOnPreferenceChangeListener(shutdownComplete);
        }

        String keyCpuSleep = getString(R.string.pref_key_cpu_do_not_sleep);
        SwitchPreferenceCompat cpuSleep = findPreference(keyCpuSleep);
        if (cpuSleep != null) {
            cpuSleep.setChecked(pref.cpuDoNotSleep());
            bindOnPreferenceChangeListener(cpuSleep);
        }

        String keyOnlyCharging = getString(R.string.pref_key_download_and_upload_only_when_charging);
        SwitchPreferenceCompat onlyCharging = findPreference(keyOnlyCharging);
        if (onlyCharging != null) {
            onlyCharging.setChecked(pref.onlyCharging());
            bindOnPreferenceChangeListener(onlyCharging);
        }

        String keyBatteryControl = getString(R.string.pref_key_battery_control);
        SwitchPreferenceCompat batteryControl = findPreference(keyBatteryControl);
        if (batteryControl != null) {
            batteryControl.setSummary(getString(R.string.pref_battery_control_summary,
                    Utils.getDefaultBatteryLowLevel()));
            batteryControl.setChecked(pref.batteryControl());
            bindOnPreferenceChangeListener(batteryControl);
        }

        String keyCustomBatteryControl = getString(R.string.pref_key_custom_battery_control);
        SwitchPreferenceCompat customBatteryControl = findPreference(keyCustomBatteryControl);
        if (customBatteryControl != null) {
            customBatteryControl.setSummary(getString(R.string.pref_custom_battery_control_summary,
                    Utils.getDefaultBatteryLowLevel()));
            customBatteryControl.setChecked(pref.customBatteryControl());
            bindOnPreferenceChangeListener(customBatteryControl);
        }

        String keyCustomBatteryControlValue = getString(R.string.pref_key_custom_battery_control_value);
        SliderPreference customBatteryControlValue = findPreference(keyCustomBatteryControlValue);
        if (customBatteryControlValue != null) {
            customBatteryControlValue.setValue((float) pref.customBatteryControlValue());
            customBatteryControlValue.setMin(10f);
            customBatteryControlValue.setMax(90f);
            bindOnPreferenceChangeListener(customBatteryControlValue);
        }

        String keyUmneteredOnly = getString(R.string.pref_key_unmetered_connections_only);
        SwitchPreferenceCompat unmeteredOnly = findPreference(keyUmneteredOnly);
        if (unmeteredOnly != null) {
            unmeteredOnly.setChecked(pref.unmeteredConnectionsOnly());
            bindOnPreferenceChangeListener(unmeteredOnly);
        }

        String keyRoaming = getString(R.string.pref_key_enable_roaming);
        SwitchPreferenceCompat roaming = findPreference(keyRoaming);
        if (roaming != null) {
            roaming.setChecked(pref.enableRoaming());
            bindOnPreferenceChangeListener(roaming);
        }

        String keyDefaultTrackersList = getString(R.string.pref_key_default_trackers_list);
        EditTextPreference defaultTrackersList = findPreference(keyDefaultTrackersList);
        if (defaultTrackersList != null) {
            defaultTrackersList.setDialogMessage(R.string.dialog_add_trackers);
            defaultTrackersList.setOnBindEditTextListener((editText) ->
                    editText.setSingleLine(false)
            );
            defaultTrackersList.setText(pref.defaultTrackersList());
            bindOnPreferenceChangeListener(defaultTrackersList);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_behavior, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference) {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, Object newValue) {
        if (preference.getKey().equals(getString(R.string.pref_key_autostart))) {
            Utils.enableBootReceiver(activity, (boolean) newValue);
            pref.autostart((boolean) newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_download_and_upload_only_when_charging))) {
            pref.onlyCharging((boolean) newValue);

            if (!((SwitchPreferenceCompat) preference).isChecked()) {
                disableBatteryControl();
            }

        } else if (preference.getKey().equals(getString(R.string.pref_key_battery_control))) {
            pref.batteryControl((boolean) newValue);

            if (((SwitchPreferenceCompat) preference).isChecked()) {
                disableCustomBatteryControl();
            }

        } else if (preference.getKey().equals(getString(R.string.pref_key_custom_battery_control))) {
            pref.customBatteryControl((boolean) newValue);

            if (!((SwitchPreferenceCompat) preference).isChecked()) {
                showCustomBatteryDialog();
            }
        } else if (preference.getKey().equals(getString(R.string.pref_key_unmetered_connections_only))) {
            pref.unmeteredConnectionsOnly((boolean) newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_enable_roaming))) {
            pref.enableRoaming((boolean) newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_keep_alive))) {
            pref.keepAlive((boolean) newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_shutdown_downloads_complete))) {
            pref.shutdownDownloadsComplete((boolean) newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_cpu_do_not_sleep))) {
            pref.cpuDoNotSleep((boolean) newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_custom_battery_control_value))) {
            pref.customBatteryControlValue(((Number) newValue).intValue());

        } else if (preference.getKey().equals(getString(R.string.pref_key_default_trackers_list))) {
            pref.defaultTrackersList((String) newValue);
        }

        return true;
    }

    private void showCustomBatteryDialog() {
        if (!isAdded()) {
            return;
        }

        new MaterialAlertDialogBuilder(activity)
                .setIcon(R.drawable.ic_warning_24px)
                .setTitle(R.string.warning)
                .setMessage(R.string.pref_custom_battery_control_dialog_summary)
                .setPositiveButton(R.string.yes, ((dialog, which) -> dialog.dismiss()))
                .setNegativeButton(R.string.no, ((dialog, which) -> disableCustomBatteryControl()))
                .show();
    }

    private void disableBatteryControl() {
        String keyBatteryControl = getString(R.string.pref_key_battery_control);
        SwitchPreferenceCompat batteryControl = findPreference(keyBatteryControl);
        if (batteryControl != null)
            batteryControl.setChecked(false);
        pref.batteryControl(false);
        disableCustomBatteryControl();
    }

    private void disableCustomBatteryControl() {
        String keyCustomBatteryControl = getString(R.string.pref_key_custom_battery_control);
        SwitchPreferenceCompat batteryControl = findPreference(keyCustomBatteryControl);
        if (batteryControl != null)
            batteryControl.setChecked(false);
        pref.customBatteryControl(false);
    }
}
