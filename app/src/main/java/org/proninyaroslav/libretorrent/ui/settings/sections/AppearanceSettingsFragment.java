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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.snackbar.Snackbar;
import com.jaredrummler.android.colorpicker.ColorPreferenceCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.ui.main.MainActivity;

public class AppearanceSettingsFragment extends PreferenceFragmentCompat
        implements
        Preference.OnPreferenceChangeListener
{
    private SettingsRepository pref;
    private CoordinatorLayout coordinatorLayout;

    public static AppearanceSettingsFragment newInstance()
    {
        AppearanceSettingsFragment fragment = new AppearanceSettingsFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        coordinatorLayout = view.findViewById(R.id.coordinator_layout);
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        pref = RepositoryHelper.getSettingsRepository(getActivity().getApplicationContext());

        String keyTheme = getString(R.string.pref_key_theme);
        ListPreference theme = findPreference(keyTheme);
        if (theme != null) {
            int type = pref.theme();
            theme.setValueIndex(type);
            bindOnPreferenceChangeListener(theme);
        }

        String keyTorrentFinishNotify = getString(R.string.pref_key_torrent_finish_notify);
        SwitchPreferenceCompat torrentFinishNotify = findPreference(keyTorrentFinishNotify);
        if (torrentFinishNotify != null) {
            torrentFinishNotify.setChecked(pref.torrentFinishNotify());
            bindOnPreferenceChangeListener(torrentFinishNotify);
        }

        String keyForegroundNotifyStatusFilter = getString(R.string.pref_key_foreground_notify_status_filter);
        ListPreference foregroundNotifyStatusFilter = findPreference(keyForegroundNotifyStatusFilter);
        if (foregroundNotifyStatusFilter != null) {
            int type = Integer.parseInt(pref.foregroundNotifyStatusFilter());
            foregroundNotifyStatusFilter.setValueIndex(type);
            bindOnPreferenceChangeListener(foregroundNotifyStatusFilter);
        }

        String keyForegroundNotifySorting = getString(R.string.pref_key_foreground_notify_sorting);
        ListPreference foregroundNotifySorting = findPreference(keyForegroundNotifySorting);
        if (foregroundNotifySorting != null) {
            int type = Integer.parseInt(pref.foregroundNotifySorting());
            foregroundNotifySorting.setValueIndex(type);
            bindOnPreferenceChangeListener(foregroundNotifySorting);
        }

        String keyCombinedPauseButton = getString(R.string.pref_key_foreground_notify_combined_pause_button);
        SwitchPreferenceCompat combinedPauseButton = findPreference(keyCombinedPauseButton);
        if (combinedPauseButton != null) {
            combinedPauseButton.setChecked(pref.foregroundNotifyCombinedPauseButton());
            bindOnPreferenceChangeListener(combinedPauseButton);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            initLegacyNotifySettings(pref);
    }

    /*
     * Note: starting with the version of Android 8.0,
     *       setting notifications from the app preferences isn't working,
     *       you can change them only in the settings of Android 8.0
     */

    private void initLegacyNotifySettings(SettingsRepository pref)
    {
        String keyPlaySound = getString(R.string.pref_key_play_sound_notify);
        SwitchPreferenceCompat playSound = findPreference(keyPlaySound);
        if (playSound != null) {
            playSound.setChecked(pref.playSoundNotify());
            bindOnPreferenceChangeListener(playSound);
        }

        final String keyNotifySound = getString(R.string.pref_key_notify_sound);
        Preference notifySound = findPreference(keyNotifySound);
        String ringtone = pref.notifySound();
        if (notifySound != null) {
            notifySound.setSummary(RingtoneManager.getRingtone(getActivity().getApplicationContext(), Uri.parse(ringtone))
                    .getTitle(getActivity().getApplicationContext()));
            /* See https://code.google.com/p/android/issues/detail?id=183255 */
            notifySound.setOnPreferenceClickListener((preference) -> {
                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI);

                String curRingtone = pref.notifySound();
                if (curRingtone != null) {
                    if (curRingtone.length() == 0) {
                        /* Select "Silent" */
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (Uri) null);
                    } else {
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(curRingtone));
                    }

                } else {
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Settings.System.DEFAULT_NOTIFICATION_URI);
                }

                ringtonePicker.launch(intent);

                return true;
            });
        }

        String keyLedIndicator = getString(R.string.pref_key_led_indicator_notify);
        SwitchPreferenceCompat ledIndicator = findPreference(keyLedIndicator);
        if (ledIndicator != null) {
            ledIndicator.setChecked(pref.ledIndicatorNotify());
            bindOnPreferenceChangeListener(ledIndicator);
        }

        String keyLedIndicatorColor = getString(R.string.pref_key_led_indicator_color_notify);
        ColorPreferenceCompat ledIndicatorColor = findPreference(keyLedIndicatorColor);
        if (ledIndicatorColor != null) {
            ledIndicatorColor.saveValue(pref.ledIndicatorColorNotify());
            bindOnPreferenceChangeListener(ledIndicatorColor);
        }

        String keyVibration = getString(R.string.pref_key_vibration_notify);
        SwitchPreferenceCompat vibration = findPreference(keyVibration);
        if (vibration != null) {
            vibration.setChecked(pref.vibrationNotify());
            bindOnPreferenceChangeListener(vibration);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_appearance, rootKey);
    }

    final ActivityResultLauncher<Intent> ringtonePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent data = result.getData();
                if (result.getResultCode() == Activity.RESULT_OK && data != null) {
                    Uri ringtone = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    if (ringtone != null) {
                        String keyNotifySound = getString(R.string.pref_key_notify_sound);
                        Preference notifySound = findPreference(keyNotifySound);
                        if (notifySound != null) {
                            Context context = getActivity().getApplicationContext();
                            notifySound.setSummary(
                                    RingtoneManager.getRingtone(context, ringtone).getTitle(context)
                            );
                        }
                        pref.notifySound(ringtone.toString());
                    }
                }
            }
    );

    private void bindOnPreferenceChangeListener(Preference preference)
    {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        if (preference.getKey().equals(getString(R.string.pref_key_theme))) {
            int type = Integer.parseInt((String)newValue);
            pref.theme(type);

            Snackbar.make(coordinatorLayout,
                    R.string.theme_settings_apply_after_reboot,
                    Snackbar.LENGTH_LONG)
                    .setAction(R.string.apply, (v) -> restartMainActivity())
                    .show();

        } else if (preference.getKey().equals(getString(R.string.pref_key_torrent_finish_notify))) {
            pref.torrentFinishNotify((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_play_sound_notify))) {
            pref.playSoundNotify((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_led_indicator_notify))) {
            pref.ledIndicatorNotify((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_vibration_notify))) {
            pref.vibrationNotify((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_led_indicator_color_notify))) {
            pref.ledIndicatorColorNotify((int)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_foreground_notify_status_filter))) {
            pref.foregroundNotifyStatusFilter((String)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_foreground_notify_sorting))) {
            pref.foregroundNotifySorting((String)newValue);

        }  else if (preference.getKey().equals(getString(R.string.pref_key_foreground_notify_combined_pause_button))) {
            pref.foregroundNotifyCombinedPauseButton((boolean)newValue);
        }

        return true;
    }

    private void restartMainActivity()
    {
        Intent intent = new Intent(getActivity().getApplicationContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
