/*
 * Copyright (C) 2016-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class BehaviorSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener
{
    private static final String TAG = BehaviorSettingsFragment.class.getSimpleName();

    private static final String TAG_CUSTOM_BATTERY_DIALOG = "custom_battery_dialog";

    private SettingsRepository pref;
    private CompositeDisposable disposables = new CompositeDisposable();
    private BaseAlertDialog.SharedViewModel dialogViewModel;

    public static BehaviorSettingsFragment newInstance()
    {
        BehaviorSettingsFragment fragment = new BehaviorSettingsFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @SuppressLint("StringFormatMatches")
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        dialogViewModel = new ViewModelProvider(getActivity()).get(BaseAlertDialog.SharedViewModel.class);

        pref = RepositoryHelper.getSettingsRepository(getActivity().getApplicationContext());

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
        SeekBarPreference customBatteryControlValue = findPreference(keyCustomBatteryControlValue);
        if (customBatteryControlValue != null) {
            customBatteryControlValue.setValue(pref.customBatteryControlValue());
            customBatteryControlValue.setMin(10);
            customBatteryControlValue.setMax(90);
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
                    if (event.dialogTag == null || !event.dialogTag.equals(TAG_CUSTOM_BATTERY_DIALOG))
                        return;
                    if (event.type == BaseAlertDialog.EventType.NEGATIVE_BUTTON_CLICKED)
                        disableCustomBatteryControl();
                });
        disposables.add(d);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
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
        if (preference.getKey().equals(getString(R.string.pref_key_autostart))) {
            Utils.enableBootReceiver(getActivity(), (boolean)newValue);
            pref.autostart((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_download_and_upload_only_when_charging))) {
            pref.onlyCharging((boolean)newValue);

            if(!((SwitchPreferenceCompat) preference).isChecked())
                disableBatteryControl();

        } else if (preference.getKey().equals(getString(R.string.pref_key_battery_control))) {
            pref.batteryControl((boolean)newValue);

            if(((SwitchPreferenceCompat) preference).isChecked())
                disableCustomBatteryControl();

        } else if (preference.getKey().equals(getString(R.string.pref_key_custom_battery_control))) {
            pref.customBatteryControl((boolean)newValue);

            if (!((SwitchPreferenceCompat) preference).isChecked())
                showCustomBatteryDialog();
        } else if (preference.getKey().equals(getString(R.string.pref_key_unmetered_connections_only))) {
            pref.unmeteredConnectionsOnly((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_enable_roaming))) {
            pref.enableRoaming((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_keep_alive))) {
            pref.keepAlive((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_shutdown_downloads_complete))) {
            pref.shutdownDownloadsComplete((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_cpu_do_not_sleep))) {
            pref.cpuDoNotSleep((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_custom_battery_control_value))) {
            pref.customBatteryControlValue((int)newValue);

        }  else if (preference.getKey().equals(getString(R.string.pref_key_default_trackers_list))) {
            pref.defaultTrackersList((String)newValue);
        }

        return true;
    }

    private void showCustomBatteryDialog()
    {
        if (!isAdded())
            return;

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
        String keyBatteryControl = getString(R.string.pref_key_battery_control);
        SwitchPreferenceCompat batteryControl = findPreference(keyBatteryControl);
        if (batteryControl != null)
            batteryControl.setChecked(false);
        pref.batteryControl(false);
        disableCustomBatteryControl();
    }

    private void disableCustomBatteryControl()
    {
        String keyCustomBatteryControl = getString(R.string.pref_key_custom_battery_control);
        SwitchPreferenceCompat batteryControl = findPreference(keyCustomBatteryControl);
        if (batteryControl != null)
            batteryControl.setChecked(false);
        pref.customBatteryControl(false);
    }
}
