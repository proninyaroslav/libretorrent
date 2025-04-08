/*
 * Copyright (C) 2018-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.service.Scheduler;
import org.proninyaroslav.libretorrent.ui.settings.CustomPreferenceFragment;
import org.proninyaroslav.libretorrent.ui.settings.customprefs.TimePickerPreference;

public class SchedulingSettingsFragment extends CustomPreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private AppCompatActivity activity;
    private SettingsRepository pref;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity a) {
            activity = a;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) requireActivity();
        }

        pref = RepositoryHelper.getSettingsRepository(activity.getApplicationContext());

        String keyEnableStart = getString(R.string.pref_key_enable_scheduling_start);
        SwitchPreferenceCompat enableStart = findPreference(keyEnableStart);
        if (enableStart != null) {
            enableStart.setChecked(pref.enableSchedulingStart());
            bindOnPreferenceChangeListener(enableStart);
        }

        String keyEnableStop = getString(R.string.pref_key_enable_scheduling_shutdown);
        SwitchPreferenceCompat enableStop = findPreference(keyEnableStop);
        if (enableStop != null) {
            enableStop.setChecked(pref.enableSchedulingShutdown());
            bindOnPreferenceChangeListener(enableStop);
        }

        String keyStartTime = getString(R.string.pref_key_scheduling_start_time);
        TimePickerPreference startTime = findPreference(keyStartTime);
        if (startTime != null) {
            startTime.setTime(pref.schedulingStartTime());
            bindOnPreferenceChangeListener(startTime);
        }

        String keyStopTime = getString(R.string.pref_key_scheduling_shutdown_time);
        TimePickerPreference stopTime = findPreference(keyStopTime);
        if (stopTime != null) {
            stopTime.setTime(pref.schedulingShutdownTime());
            bindOnPreferenceChangeListener(stopTime);
        }

        String keyRunOnlyOnce = getString(R.string.pref_key_scheduling_run_only_once);
        CheckBoxPreference runOnlyOnce = findPreference(keyRunOnlyOnce);
        if (runOnlyOnce != null) {
            runOnlyOnce.setChecked(pref.schedulingRunOnlyOnce());
            bindOnPreferenceChangeListener(runOnlyOnce);
        }

        String keySwitchWiFi = getString(R.string.pref_key_scheduling_switch_wifi);
        CheckBoxPreference switchWiFi = findPreference(keySwitchWiFi);
        if (switchWiFi != null) {
            switchWiFi.setChecked(pref.schedulingSwitchWiFi());
            bindOnPreferenceChangeListener(switchWiFi);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.appBar.setTitle(R.string.pref_header_scheduling);
    }

    private void showExactAlarmPermissionDialog() {
        if (!isAdded()) {
            return;
        }

        new MaterialAlertDialogBuilder(activity)
                .setIcon(R.drawable.ic_warning_24px)
                .setTitle(R.string.permission_denied)
                .setMessage(R.string.exact_alarm_permission_warning)
                .setPositiveButton(R.string.yes, ((dialog, which) -> Utils.requestExactAlarmPermission(activity)))
                .setNegativeButton(R.string.no, ((dialog, which) -> dialog.dismiss()))
                .show();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_scheduling, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference) {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        var context = activity.getApplicationContext();

        if (preference.getKey().equals(getString(R.string.pref_key_enable_scheduling_start))) {
            pref.enableSchedulingStart((boolean) newValue);

            if ((boolean) newValue) {
                int time = pref.schedulingStartTime();
                if (!Scheduler.setStartAppAlarm(context, time)) {
                    showExactAlarmPermissionDialog();
                }
            } else {
                Scheduler.cancelStartAppAlarm(context);
            }
            Utils.enableBootReceiver(context, (boolean) newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_enable_scheduling_shutdown))) {
            pref.enableSchedulingShutdown((boolean) newValue);

            if ((boolean) newValue) {
                int time = pref.schedulingStartTime();
                if (!Scheduler.setStopAppAlarm(context, time)) {
                    showExactAlarmPermissionDialog();
                }
            } else {
                Scheduler.cancelStopAppAlarm(context);
            }
            Utils.enableBootReceiver(activity, (boolean) newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_scheduling_start_time))) {
            pref.schedulingStartTime((int) newValue);
            if (!Scheduler.setStartAppAlarm(context, (int) newValue)) {
                showExactAlarmPermissionDialog();
            }

        } else if (preference.getKey().equals(getString(R.string.pref_key_scheduling_shutdown_time))) {
            pref.schedulingShutdownTime((int) newValue);
            if (!Scheduler.setStopAppAlarm(context, (int) newValue)) {
                showExactAlarmPermissionDialog();
            }

        } else if (preference.getKey().equals(getString(R.string.pref_key_scheduling_run_only_once))) {
            pref.schedulingRunOnlyOnce((boolean) newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_scheduling_switch_wifi))) {
            pref.schedulingSwitchWiFi((boolean) newValue);
        }

        return true;
    }
}
