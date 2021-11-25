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

import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.InputFilterRange;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;

public class StreamingSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener
{
    private static final String TAG = StreamingSettingsFragment.class.getSimpleName();

    private SettingsRepository pref;

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

        pref = RepositoryHelper.getSettingsRepository(getActivity().getApplicationContext());

        String keyEnable = getString(R.string.pref_key_streaming_enable);
        SwitchPreferenceCompat enable = findPreference(keyEnable);
        if (enable != null) {
            enable.setChecked(pref.enableStreaming());
            bindOnPreferenceChangeListener(enable);
        }

        String keyHostname = getString(R.string.pref_key_streaming_hostname);
        EditTextPreference hostname = findPreference(keyHostname);
        if (hostname != null) {
            String addressValue = pref.streamingHostname();
            hostname.setText(addressValue);
            hostname.setSummary(addressValue);
            bindOnPreferenceChangeListener(hostname);
        }

        String keyPort = getString(R.string.pref_key_streaming_port);
        EditTextPreference port = findPreference(keyPort);
        if (port != null) {
            InputFilter[] portFilter = new InputFilter[] { InputFilterRange.PORT_FILTER };
            int portNumber = pref.streamingPort();
            String portValue = Integer.toString(portNumber);
            port.setOnBindEditTextListener((editText) -> editText.setFilters(portFilter));
            port.setSummary(portValue);
            port.setText(portValue);
            bindOnPreferenceChangeListener(port);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
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
        if (preference.getKey().equals(getString(R.string.pref_key_streaming_hostname))) {
            pref.streamingHostname((String)newValue);
            preference.setSummary((String)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_streaming_port))) {
            if (!TextUtils.isEmpty((String)newValue)) {
                int value = Integer.parseInt((String) newValue);
                pref.streamingPort(value);
                preference.setSummary(Integer.toString(value));
            }

        } else if (preference.getKey().equals(getString(R.string.pref_key_streaming_enable))) {
            pref.enableStreaming((boolean)newValue);
        }

        return true;
    }
}
