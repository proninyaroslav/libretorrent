/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.SwitchPreferenceCompat;

import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.receivers.BootReceiver;
import org.proninyaroslav.libretorrent.receivers.SchedulerReceiver;
import org.proninyaroslav.libretorrent.settings.customprefs.TimePreference;

public class SchedulingSettingsFragment extends PreferenceFragmentCompat
        implements
        Preference.OnPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = SchedulingSettingsFragment.class.getSimpleName();

    public static SchedulingSettingsFragment newInstance()
    {
        SchedulingSettingsFragment fragment = new SchedulingSettingsFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        SharedPreferences pref = SettingsManager.getPreferences(getActivity());

        String keyEnableStart = getString(R.string.pref_key_enable_scheduling_start);
        SwitchPreferenceCompat enableStart = (SwitchPreferenceCompat)findPreference(keyEnableStart);
        enableStart.setChecked(pref.getBoolean(keyEnableStart, SettingsManager.Default.enableSchedulingStart));
        bindOnPreferenceChangeListener(enableStart);

        String keyEnableStop = getString(R.string.pref_key_enable_scheduling_shutdown);
        SwitchPreferenceCompat enableStop = (SwitchPreferenceCompat)findPreference(keyEnableStop);
        enableStop.setChecked(pref.getBoolean(keyEnableStop, SettingsManager.Default.enableSchedulingShutdown));
        bindOnPreferenceChangeListener(enableStop);

        String keyStartTime = getString(R.string.pref_key_scheduling_start_time);
        TimePreference startTime = (TimePreference)findPreference(keyStartTime);
        startTime.setTime(pref.getInt(keyStartTime, SettingsManager.Default.schedulingStartTime));
        bindOnPreferenceChangeListener(startTime);

        String keyStopTime = getString(R.string.pref_key_scheduling_shutdown_time);
        TimePreference stopTime = (TimePreference)findPreference(keyStopTime);
        stopTime.setTime(pref.getInt(keyStopTime, SettingsManager.Default.schedulingShutdownTime));
        bindOnPreferenceChangeListener(stopTime);

        String keyRunOnlyOnce = getString(R.string.pref_key_scheduling_run_only_once);
        CheckBoxPreference runOnlyOnce = (CheckBoxPreference)findPreference(keyRunOnlyOnce);
        runOnlyOnce.setChecked(pref.getBoolean(keyRunOnlyOnce, SettingsManager.Default.schedulingRunOnlyOnce));

        String keySwitchWiFi = getString(R.string.pref_key_scheduling_switch_wifi);
        CheckBoxPreference switchWiFi = (CheckBoxPreference)findPreference(keySwitchWiFi);
        switchWiFi.setChecked(pref.getBoolean(keySwitchWiFi, SettingsManager.Default.schedulingSwitchWiFi));
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_scheduling, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference)
    {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference)
    {
        DialogFragment dialogFragment = null;
        if (preference instanceof TimePreference) {
            dialogFragment = TimePreferenceDialogFragmentCompat.newInstance(preference.getKey());
        }
        if (dialogFragment != null) {
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(this.getFragmentManager(), "android.support.v7.preference" +
                    ".PreferenceFragment.DIALOG");
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        SharedPreferences pref = SettingsManager.getPreferences(getActivity());

        if (preference.getKey().equals(getString(R.string.pref_key_enable_scheduling_start))) {
            if ((boolean)newValue) {
                int time = pref.getInt(getString(R.string.pref_key_scheduling_start_time),
                        SettingsManager.Default.schedulingStartTime);
                SchedulerReceiver.setStartStopAppAlarm(getActivity(), SchedulerReceiver.ACTION_START_APP, time);
            } else {
                SchedulerReceiver.cancelScheduling(getActivity(), SchedulerReceiver.ACTION_START_APP);
            }
            Utils.enableBootReceiver(getActivity(), (boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_enable_scheduling_shutdown))) {
            if ((boolean)newValue) {
                int time = pref.getInt(getString(R.string.pref_key_scheduling_shutdown_time),
                        SettingsManager.Default.schedulingStartTime);
                SchedulerReceiver.setStartStopAppAlarm(getActivity(), SchedulerReceiver.ACTION_STOP_APP, time);
            } else {
                SchedulerReceiver.cancelScheduling(getActivity(), SchedulerReceiver.ACTION_STOP_APP);
            }
            Utils.enableBootReceiver(getActivity(), (boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_scheduling_start_time))) {
            SchedulerReceiver.setStartStopAppAlarm(getActivity(), SchedulerReceiver.ACTION_START_APP, (int)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_scheduling_shutdown_time))) {
            SchedulerReceiver.setStartStopAppAlarm(getActivity(), SchedulerReceiver.ACTION_STOP_APP, (int)newValue);
        }

        return true;
    }
}
