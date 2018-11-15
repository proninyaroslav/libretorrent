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

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import android.text.InputFilter;
import android.text.TextUtils;

import com.takisoft.preferencex.EditTextPreference;
import com.takisoft.preferencex.PreferenceFragmentCompat;

import org.proninyaroslav.libretorrent.InputFilterMinMax;
import org.proninyaroslav.libretorrent.R;

public class StreamingSettingsFragment extends PreferenceFragmentCompat
        implements
        Preference.OnPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = StreamingSettingsFragment.class.getSimpleName();

    public static StreamingSettingsFragment newInstance()
    {
        StreamingSettingsFragment fragment = new StreamingSettingsFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        SharedPreferences pref = SettingsManager.getPreferences(getActivity());

        String keyEnable = getString(R.string.pref_key_streaming_enable);
        SwitchPreferenceCompat enable = (SwitchPreferenceCompat)findPreference(keyEnable);
        enable.setChecked(pref.getBoolean(keyEnable, SettingsManager.Default.enableStreaming));

        String keyHostname = getString(R.string.pref_key_streaming_hostname);
        EditTextPreference hostname = (EditTextPreference)findPreference(keyHostname);
        String addressValue = pref.getString(keyHostname, SettingsManager.Default.streamingHostname);
        hostname.setText(addressValue);
        hostname.setSummary(addressValue);
        bindOnPreferenceChangeListener(hostname);

        String keyPort = getString(R.string.pref_key_streaming_port);
        EditTextPreference port = (EditTextPreference)findPreference(keyPort);
        InputFilter[] portFilter = new InputFilter[]{new InputFilterMinMax(0, 65535)};
        int portNumber = pref.getInt(keyPort, SettingsManager.Default.streamingPort);
        String portValue = Integer.toString(portNumber);
        port.getEditText().setFilters(portFilter);
        port.setSummary(portValue);
        port.setText(portValue);
        bindOnPreferenceChangeListener(port);
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_streaming, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference)
    {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        SharedPreferences pref = SettingsManager.getPreferences(getActivity());

        if (preference.getKey().equals(getString(R.string.pref_key_streaming_hostname))) {
            pref.edit().putString(preference.getKey(), (String)newValue).apply();
            preference.setSummary((String)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_streaming_port))) {
            int value = SettingsManager.Default.streamingPort;
            if (!TextUtils.isEmpty((String)newValue))
                value = Integer.parseInt((String)newValue);

            pref.edit().putInt(preference.getKey(), value).apply();
            preference.setSummary(Integer.toString(value));
        }

        return true;
    }
}
