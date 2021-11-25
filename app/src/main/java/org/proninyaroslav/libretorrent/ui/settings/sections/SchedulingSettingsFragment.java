/*
 * Copyright (C) 2018-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.service.Scheduler;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;
import org.proninyaroslav.libretorrent.ui.settings.customprefs.TimePreference;
import org.proninyaroslav.libretorrent.ui.settings.customprefs.TimePreferenceDialogFragmentCompat;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class SchedulingSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener
{
    private static final String TAG_EXACT_ALARM_PERMISSION_DIALOG = "exact_alarm_permission_dialog";

    private SettingsRepository pref;
    private CompositeDisposable disposables = new CompositeDisposable();
    private BaseAlertDialog.SharedViewModel dialogViewModel;

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

        pref = RepositoryHelper.getSettingsRepository(getActivity().getApplicationContext());
        dialogViewModel = new ViewModelProvider(getActivity()).get(BaseAlertDialog.SharedViewModel.class);

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
        TimePreference startTime = findPreference(keyStartTime);
        if (startTime != null) {
            startTime.setTime(pref.schedulingStartTime());
            bindOnPreferenceChangeListener(startTime);
        }

        String keyStopTime = getString(R.string.pref_key_scheduling_shutdown_time);
        TimePreference stopTime = findPreference(keyStopTime);
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
                    if (event.dialogTag == null || !event.dialogTag.equals(TAG_EXACT_ALARM_PERMISSION_DIALOG)) {
                        return;
                    }
                    if (event.type == BaseAlertDialog.EventType.POSITIVE_BUTTON_CLICKED) {
                        Utils.requestExactAlarmPermission(getContext());
                    }
                });
        disposables.add(d);
    }

    private void showExactAlarmPermissionDialog()
    {
        if (!isAdded())
            return;

        var fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_EXACT_ALARM_PERMISSION_DIALOG) == null) {
            var exactAlarmDialog = BaseAlertDialog.newInstance(
                    getString(R.string.permission_denied),
                    getString(R.string.exact_alarm_permission_warning),
                    0,
                    getString(R.string.yes),
                    getString(R.string.no),
                    null,
                    true);

            exactAlarmDialog.show(fm, TAG_EXACT_ALARM_PERMISSION_DIALOG);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
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
        if (dialogFragment != null && isAdded()) {
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(this.getParentFragmentManager(), "android.support.v7.preference" +
                    ".PreferenceFragment.DIALOG");
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        Context context = getActivity().getApplicationContext();

        if (preference.getKey().equals(getString(R.string.pref_key_enable_scheduling_start))) {
            pref.enableSchedulingStart((boolean)newValue);

            if ((boolean)newValue) {
                int time = pref.schedulingStartTime();
                if (!Scheduler.setStartAppAlarm(context, time)) {
                    showExactAlarmPermissionDialog();
                }
            } else {
                Scheduler.cancelStartAppAlarm(context);
            }
            Utils.enableBootReceiver(context, (boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_enable_scheduling_shutdown))) {
            pref.enableSchedulingShutdown((boolean)newValue);

            if ((boolean)newValue) {
                int time = pref.schedulingStartTime();
                if (!Scheduler.setStopAppAlarm(context, time)) {
                    showExactAlarmPermissionDialog();
                }
            } else {
                Scheduler.cancelStopAppAlarm(context);
            }
            Utils.enableBootReceiver(getActivity(), (boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_scheduling_start_time))) {
            pref.schedulingStartTime((int)newValue);
            if (!Scheduler.setStartAppAlarm(context, (int)newValue)) {
                showExactAlarmPermissionDialog();
            }

        } else if (preference.getKey().equals(getString(R.string.pref_key_scheduling_shutdown_time))) {
            pref.schedulingShutdownTime((int)newValue);
            if (!Scheduler.setStopAppAlarm(context, (int)newValue)) {
                showExactAlarmPermissionDialog();
            }

        } else if (preference.getKey().equals(getString(R.string.pref_key_scheduling_run_only_once))) {
            pref.schedulingRunOnlyOnce((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_scheduling_switch_wifi))) {
            pref.schedulingSwitchWiFi((boolean)newValue);
        }

        return true;
    }
}
