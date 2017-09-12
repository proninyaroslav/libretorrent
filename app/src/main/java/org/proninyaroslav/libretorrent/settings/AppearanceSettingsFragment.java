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

import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.widget.Toast;

import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.settings.customprefs.ColorPreference;

public class AppearanceSettingsFragment extends PreferenceFragmentCompat
        implements
        Preference.OnPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = AppearanceSettingsFragment.class.getSimpleName();

    private static final int REQUEST_CODE_ALERT_RINGTONE = 1;

    public static AppearanceSettingsFragment newInstance()
    {
        AppearanceSettingsFragment fragment = new AppearanceSettingsFragment();

        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        final SettingsManager pref = new SettingsManager(getActivity().getApplicationContext());

        String keyTheme = getString(R.string.pref_key_theme);
        ListPreference theme = (ListPreference) findPreference(keyTheme);
        int type = pref.getInt(keyTheme, Integer.parseInt(getString(R.string.pref_theme_light_value)));
        theme.setValueIndex(type);
        String typesName[] = getResources().getStringArray(R.array.pref_theme_entries);
        theme.setSummary(typesName[type]);
        bindOnPreferenceChangeListener(theme);

        String keyTorrentFinishNotify = getString(R.string.pref_key_torrent_finish_notify);
        SwitchPreferenceCompat torrentFinishNotify = (SwitchPreferenceCompat) findPreference(keyTorrentFinishNotify);
        torrentFinishNotify.setChecked(pref.getBoolean(keyTorrentFinishNotify, true));
        bindOnPreferenceChangeListener(torrentFinishNotify);

        String keyPlaySound = getString(R.string.pref_key_play_sound_notify);
        SwitchPreferenceCompat playSound = (SwitchPreferenceCompat) findPreference(keyPlaySound);
        playSound.setChecked(pref.getBoolean(keyPlaySound, true));
        bindOnPreferenceChangeListener(playSound);

        final String keyNotifySound = getString(R.string.pref_key_notify_sound);
        Preference notifySound = findPreference(keyNotifySound);
        String ringtone = pref.getString(keyNotifySound,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString());
        notifySound.setSummary(RingtoneManager.getRingtone(getActivity().getApplicationContext(), Uri.parse(ringtone))
                .getTitle(getActivity().getApplicationContext()));
        /* See https://code.google.com/p/android/issues/detail?id=183255 */
        notifySound.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(Preference preference)
            {
                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI);

                String curRingtone = pref.getString(keyNotifySound, null);
                if (curRingtone != null) {
                    if (curRingtone.length() == 0) {
                        // Select "Silent"
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (Uri) null);
                    } else {
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(curRingtone));
                    }

                } else {
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Settings.System.DEFAULT_NOTIFICATION_URI);
                }

                startActivityForResult(intent, REQUEST_CODE_ALERT_RINGTONE);

                return true;
            }
        });

        String keyLedIndicator = getString(R.string.pref_key_led_indicator_notify);
        SwitchPreferenceCompat ledIndicator = (SwitchPreferenceCompat) findPreference(keyLedIndicator);
        ledIndicator.setChecked(pref.getBoolean(keyLedIndicator, true));
        bindOnPreferenceChangeListener(ledIndicator);

        String keyLedIndicatorColor = getString(R.string.pref_key_led_indicator_color_notify);
        ColorPreference ledIndicatorColor = (ColorPreference) findPreference(keyLedIndicatorColor);
        ledIndicatorColor.forceSetValue(pref.getInt(keyLedIndicatorColor,
                ContextCompat.getColor(getActivity().getApplicationContext(), R.color.primary)));
        bindOnPreferenceChangeListener(ledIndicatorColor);

        String keyVibration = getString(R.string.pref_key_vibration_notify);
        SwitchPreferenceCompat vibration = (SwitchPreferenceCompat) findPreference(keyVibration);
        vibration.setChecked(pref.getBoolean(keyVibration, true));
        bindOnPreferenceChangeListener(vibration);
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_appearance, rootKey);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_CODE_ALERT_RINGTONE && data != null) {
            Uri ringtone = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (ringtone != null) {
                SettingsManager pref = new SettingsManager(getActivity().getApplicationContext());

                String keyNotifySound = getString(R.string.pref_key_notify_sound);
                Preference notifySound = findPreference(keyNotifySound);
                notifySound.setSummary(RingtoneManager.getRingtone(getActivity().getApplicationContext(), ringtone)
                        .getTitle(getActivity().getApplicationContext()));
                pref.put(keyNotifySound, ringtone.toString());
            }

        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void bindOnPreferenceChangeListener(Preference preference)
    {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        SettingsManager pref = new SettingsManager(getActivity().getApplicationContext());

        if (preference instanceof SwitchPreferenceCompat) {
            pref.put(preference.getKey(), (boolean) newValue);

        } else if (preference instanceof ColorPreference) {
            ColorPreference ledIndicatorColor = (ColorPreference) findPreference(preference.getKey());
            ledIndicatorColor.forceSetValue((int) newValue);
            pref.put(preference.getKey(), (int) newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_theme))) {
            int type = Integer.parseInt((String) newValue);

            pref.put(preference.getKey(), type);
            String typesName[] = getResources().getStringArray(R.array.pref_theme_entries);
            preference.setSummary(typesName[type]);

            Toast.makeText(getActivity().getApplicationContext(),
                    R.string.theme_settings_apply_after_reboot,
                    Toast.LENGTH_SHORT)
                    .show();
        }

        return true;
    }
}
