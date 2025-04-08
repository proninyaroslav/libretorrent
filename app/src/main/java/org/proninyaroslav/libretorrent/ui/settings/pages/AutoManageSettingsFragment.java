/*
 * Copyright (C) 2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import org.proninyaroslav.libretorrent.ui.settings.customprefs.SwitchBarPreference;

public class AutoManageSettingsFragment extends CustomPreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    public static final String KEY_RESULT = "result";

    private SettingsRepository pref;
    private String requestKey;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        var args = AutoManageSettingsFragmentArgs.fromBundle(getArguments());
        requestKey = args.getFragmentRequestKey();

        pref = RepositoryHelper.getSettingsRepository(requireContext().getApplicationContext());

        InputFilter[] queueingFilter = new InputFilter[]{
                new InputFilterRange.Builder()
                        .setMin(-1)
                        .setMax(Integer.MAX_VALUE)
                        .build()
        };

        String keyAutoManage = getString(R.string.pref_key_auto_manage);
        SwitchBarPreference autoManage = findPreference(keyAutoManage);
        if (autoManage != null) {
            autoManage.setChecked(pref.autoManage());
            bindOnPreferenceChangeListener(autoManage);
        }

        String keyMaxActiveUploads = getString(R.string.pref_key_max_active_uploads);
        EditTextPreference maxActiveUploads = findPreference(keyMaxActiveUploads);
        if (maxActiveUploads != null) {
            maxActiveUploads.setDialogMessage(R.string.pref_max_active_uploads_downloads_dialog_msg);
            String value = Integer.toString(pref.maxActiveUploads());
            maxActiveUploads.setOnBindEditTextListener((editText) -> editText.setFilters(queueingFilter));
            maxActiveUploads.setSummary(value);
            maxActiveUploads.setText(value);
            bindOnPreferenceChangeListener(maxActiveUploads);
        }

        String keyMaxActiveDownloads = getString(R.string.pref_key_max_active_downloads);
        EditTextPreference maxActiveDownloads = findPreference(keyMaxActiveDownloads);
        if (maxActiveDownloads != null) {
            maxActiveDownloads.setDialogMessage(R.string.pref_max_active_uploads_downloads_dialog_msg);
            String value = Integer.toString(pref.maxActiveDownloads());
            maxActiveDownloads.setOnBindEditTextListener((editText) -> editText.setFilters(queueingFilter));
            maxActiveDownloads.setSummary(value);
            maxActiveDownloads.setText(value);
            bindOnPreferenceChangeListener(maxActiveDownloads);
        }

        String keyMaxActiveTorrents = getString(R.string.pref_key_max_active_torrents);
        EditTextPreference maxActiveTorrents = findPreference(keyMaxActiveTorrents);
        if (maxActiveTorrents != null) {
            maxActiveTorrents.setDialogMessage(R.string.pref_max_active_uploads_downloads_dialog_msg);
            String value = Integer.toString(pref.maxActiveTorrents());
            maxActiveTorrents.setOnBindEditTextListener((editText) -> editText.setFilters(queueingFilter));
            maxActiveTorrents.setSummary(value);
            maxActiveTorrents.setText(value);
            bindOnPreferenceChangeListener(maxActiveTorrents);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.appBar.setTitle(R.string.pref_auto_manage_title);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_auto_manage, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference) {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
       if (preference.getKey().equals(getString(R.string.pref_key_max_active_downloads))) {
            int value = 1;
            if (!TextUtils.isEmpty((String) newValue))
                value = Integer.parseInt((String) newValue);
            pref.maxActiveDownloads(value);
            preference.setSummary(Integer.toString(value));

        } else if (preference.getKey().equals(getString(R.string.pref_key_max_active_uploads))) {
            int value = 1;
            if (!TextUtils.isEmpty((String) newValue))
                value = Integer.parseInt((String) newValue);
            pref.maxActiveUploads(value);
            preference.setSummary(Integer.toString(value));

        } else if (preference.getKey().equals(getString(R.string.pref_key_max_active_torrents))) {
            int value = 1;
            if (!TextUtils.isEmpty((String) newValue))
                value = Integer.parseInt((String) newValue);
            pref.maxActiveTorrents(value);
            preference.setSummary(Integer.toString(value));

        } else if (preference.getKey().equals(getString(R.string.pref_key_auto_manage))) {
            pref.autoManage((boolean) newValue);

            var bundle = new Bundle();
            bundle.putBoolean(KEY_RESULT, (boolean) newValue);
            getParentFragmentManager().setFragmentResult(requestKey, bundle);
        }

        return true;
    }
}
