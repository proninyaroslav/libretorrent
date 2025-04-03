/*
 * Copyright (C) 2020-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.InputFilterRange;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.ui.settings.CustomPreferenceFragment;

public class LogSettingsFragment extends CustomPreferenceFragment implements Preference.OnPreferenceChangeListener {
    private SettingsRepository pref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pref = RepositoryHelper.getSettingsRepository(requireContext().getApplicationContext());

        InputFilter[] maxFilter = new InputFilter[]{
                new InputFilterRange.Builder()
                        .setMin(1)
                        .setMax(Integer.MAX_VALUE)
                        .build()
        };

        String keyMaxLogSize = getString(R.string.pref_key_max_log_size);
        EditTextPreference maxLogSize = findPreference(keyMaxLogSize);
        if (maxLogSize != null) {
            String value = Integer.toString(pref.maxLogSize());
            maxLogSize.setOnBindEditTextListener((editText) -> editText.setFilters(maxFilter));
            maxLogSize.setSummary(value);
            maxLogSize.setText(value);
            bindOnPreferenceChangeListener(maxLogSize);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.appBar.setTitle(R.string.settings);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_log, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference) {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.getKey().equals(getString(R.string.pref_key_max_log_size))) {
            int value = 1;
            if (!TextUtils.isEmpty((String) newValue)) {
                value = Integer.parseInt((String) newValue);
            }
            pref.maxLogSize(value);
            preference.setSummary(Integer.toString(value));
        }

        return true;
    }
}
