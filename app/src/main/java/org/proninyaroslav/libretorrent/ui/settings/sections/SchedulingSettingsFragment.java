/*
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.settings.sections;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.takisoft.preferencex.PreferenceFragmentCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.settings.SettingsManager;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.service.Scheduler;
import org.proninyaroslav.libretorrent.ui.settings.customprefs.TimePreference;
import org.proninyaroslav.libretorrent.ui.settings.customprefs.TimePreferenceDialogFragmentCompat;

public class SchedulingSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener
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

        SharedPreferences pref = SettingsManager.getInstance(getActivity().getApplicationContext())
                .getPreferences();

        String keyEnableStart = getString(R.string.pref_key_enable_scheduling_start);
        SwitchPreferenceCompat enableStart = findPreference(keyEnableStart);
        if (enableStart != null) {
            enableStart.setChecked(pref.getBoolean(keyEnableStart, SettingsManager.Default.enableSchedulingStart));
            bindOnPreferenceChangeListener(enableStart);
        }

        String keyEnableStop = getString(R.string.pref_key_enable_scheduling_shutdown);
        SwitchPreferenceCompat enableStop = findPreference(keyEnableStop);
        if (enableStop != null) {
            enableStop.setChecked(pref.getBoolean(keyEnableStop, SettingsManager.Default.enableSchedulingShutdown));
            bindOnPreferenceChangeListener(enableStop);
        }

        String keyStartTime = getString(R.string.pref_key_scheduling_start_time);
        TimePreference startTime = findPreference(keyStartTime);
        if (startTime != null) {
            startTime.setTime(pref.getInt(keyStartTime, SettingsManager.Default.schedulingStartTime));
            bindOnPreferenceChangeListener(startTime);
        }

        String keyStopTime = getString(R.string.pref_key_scheduling_shutdown_time);
        TimePreference stopTime = findPreference(keyStopTime);
        if (stopTime != null) {
            stopTime.setTime(pref.getInt(keyStopTime, SettingsManager.Default.schedulingShutdownTime));
            bindOnPreferenceChangeListener(stopTime);
        }

        String keyRunOnlyOnce = getString(R.string.pref_key_scheduling_run_only_once);
        CheckBoxPreference runOnlyOnce = findPreference(keyRunOnlyOnce);
        if (runOnlyOnce != null)
            runOnlyOnce.setChecked(pref.getBoolean(keyRunOnlyOnce, SettingsManager.Default.schedulingRunOnlyOnce));

        String keySwitchWiFi = getString(R.string.pref_key_scheduling_switch_wifi);
        CheckBoxPreference switchWiFi = findPreference(keySwitchWiFi);
        if (switchWiFi != null)
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
        Context context = getActivity().getApplicationContext();
        SharedPreferences pref = SettingsManager.getInstance(context).getPreferences();

        if (preference.getKey().equals(getString(R.string.pref_key_enable_scheduling_start))) {
            if ((boolean)newValue) {
                int time = pref.getInt(getString(R.string.pref_key_scheduling_start_time),
                                       SettingsManager.Default.schedulingStartTime);
                Scheduler.setStartAppAlarm(context, time);
            } else {
                Scheduler.cancelStartAppAlarm(context);
            }
            Utils.enableBootReceiver(context, (boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_enable_scheduling_shutdown))) {
            if ((boolean)newValue) {
                int time = pref.getInt(getString(R.string.pref_key_scheduling_shutdown_time),
                                       SettingsManager.Default.schedulingStartTime);
                Scheduler.setStopAppAlarm(context, time);
            } else {
                Scheduler.cancelStopAppAlarm(context);
            }
            Utils.enableBootReceiver(getActivity(), (boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_scheduling_start_time))) {
            Scheduler.setStartAppAlarm(context, (int)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_scheduling_shutdown_time))) {
            Scheduler.setStopAppAlarm(context, (int)newValue);
        }

        return true;
    }
}
