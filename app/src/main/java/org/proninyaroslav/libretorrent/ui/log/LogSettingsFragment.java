/*
 * Copyright (C) 2020-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.log;

import android.content.Context;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.InputFilterRange;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;

public class LogSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener
{
    private static final String TAG = LogSettingsFragment.class.getSimpleName();

    private SettingsRepository pref;

    public static LogSettingsFragment newInstance()
    {
        LogSettingsFragment fragment = new LogSettingsFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Context context = getActivity().getApplicationContext();
        pref = RepositoryHelper.getSettingsRepository(context);

        InputFilter[] maxFilter = new InputFilter[] {
                new InputFilterRange.Builder()
                        .setMin(1)
                        .setMax(Integer.MAX_VALUE)
                        .build()
        };

        String keyMaxLogSize= getString(R.string.pref_key_max_log_size);
        EditTextPreference maxLogSize  = findPreference(keyMaxLogSize);
        if (maxLogSize != null) {
            String value = Integer.toString(pref.maxLogSize());
            maxLogSize.setOnBindEditTextListener((editText) -> editText.setFilters(maxFilter));
            maxLogSize.setSummary(value);
            maxLogSize.setText(value);
            bindOnPreferenceChangeListener(maxLogSize);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_log, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference)
    {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        if (preference.getKey().equals(getString(R.string.pref_key_max_log_size))) {
            int value = 1;
            if (!TextUtils.isEmpty((String)newValue))
                value = Integer.parseInt((String)newValue);
            pref.maxLogSize(value);
            preference.setSummary(Integer.toString(value));
        }

        return true;
    }
}
