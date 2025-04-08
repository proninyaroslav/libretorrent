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

package org.proninyaroslav.libretorrent.ui.settings.pages;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.ui.settings.CustomPreferenceFragment;
import org.proninyaroslav.libretorrent.ui.settings.customprefs.SwitchBarPreference;

public class AnonymousModeSettingsFragment extends CustomPreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    public static final String KEY_RESULT = "result";

    private SettingsRepository pref;
    private String requestKey;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        var args = AnonymousModeSettingsFragmentArgs.fromBundle(getArguments());
        requestKey = args.getFragmentRequestKey();

        pref = RepositoryHelper.getSettingsRepository(requireContext().getApplicationContext());

        String keyAnonymousMode = getString(R.string.pref_key_anonymous_mode);
        SwitchBarPreference anonymousMode = findPreference(keyAnonymousMode);
        if (anonymousMode != null) {
            anonymousMode.setChecked(pref.anonymousMode());
            bindOnPreferenceChangeListener(anonymousMode);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.appBar.setTitle(R.string.pref_anonymous_mode_title);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_anonymous_mode, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference) {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.getKey().equals(getString(R.string.pref_key_anonymous_mode))) {
            pref.anonymousMode((boolean) newValue);

            var bundle = new Bundle();
            bundle.putBoolean(KEY_RESULT, (boolean) newValue);
            getParentFragmentManager().setFragmentResult(requestKey, bundle);
        }
        return true;
    }
}
