/*
 * Copyright (C) 2018-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.ui.settings.CustomPreferenceFragment;

public class FeedSettingsFragment extends CustomPreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String TAG = FeedSettingsFragment.class.getSimpleName();

    private static final String KEY_AUTO_REFRESH_SETTINGS_REQUEST = TAG + "_auto_refresh_settings";

    private AppCompatActivity activity;
    private SettingsRepository pref;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity a) {
            activity = a;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) requireActivity();
        }

        pref = RepositoryHelper.getSettingsRepository(activity.getApplicationContext());

        String keyAutoRefresh = getString(R.string.pref_key_feed_auto_refresh);
        var autoRefresh = findPreference(keyAutoRefresh);
        if (autoRefresh != null) {
            autoRefresh.setSummary(pref.autoManage() ? R.string.switch_on : R.string.switch_off);
            autoRefresh.setOnPreferenceClickListener((p) -> {
                var action = FeedSettingsFragmentDirections.actionAutoRefreshSettings(
                        KEY_AUTO_REFRESH_SETTINGS_REQUEST
                );
                NavHostFragment.findNavController(this).navigate(action);
                return true;
            });
        }

        String keyKeepTime = getString(R.string.pref_key_feed_keep_items_time);
        ListPreference keepTime = findPreference(keyKeepTime);
        if (keepTime != null) {
            String time = Long.toString(pref.feedItemKeepTime());
            int timeIndex = keepTime.findIndexOfValue(time);
            if (timeIndex >= 0)
                keepTime.setValueIndex(timeIndex);
            bindOnPreferenceChangeListener(keepTime);
        }

        String keyStartTorrents = getString(R.string.pref_key_feed_start_torrents);
        SwitchPreferenceCompat startTorrents = findPreference(keyStartTorrents);
        if (startTorrents != null) {
            startTorrents.setChecked(pref.feedStartTorrents());
            bindOnPreferenceChangeListener(startTorrents);
        }

        String keyRemoveDuplicates = getString(R.string.pref_key_feed_remove_duplicates);
        SwitchPreferenceCompat removeDuplicates = findPreference(keyRemoveDuplicates);
        if (removeDuplicates != null) {
            removeDuplicates.setChecked(pref.feedRemoveDuplicates());
            bindOnPreferenceChangeListener(removeDuplicates);
        }

        setAutoRefreshSettingsListener();
    }

    private void setAutoRefreshSettingsListener() {
        getParentFragmentManager().setFragmentResultListener(
                KEY_AUTO_REFRESH_SETTINGS_REQUEST,
                this,
                (requestKey, result) -> {
                    boolean enabled = result.getBoolean(AutoManageSettingsFragment.KEY_RESULT);
                    var autoRefresh = findPreference(getString(R.string.pref_key_feed_auto_refresh));
                    if (autoRefresh != null) {
                        autoRefresh.setSummary(enabled ? R.string.switch_on : R.string.switch_off);
                    }
                }
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.appBar.setTitle(R.string.pref_header_feed);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_feed, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference) {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.getKey().equals(getString(R.string.pref_key_feed_keep_items_time))) {
            long keepTime = Long.parseLong((String) newValue);
            pref.feedItemKeepTime(keepTime);
        } else if (preference.getKey().equals(getString(R.string.pref_key_feed_start_torrents))) {
            pref.feedStartTorrents((boolean) newValue);
        } else if (preference.getKey().equals(getString(R.string.pref_key_feed_remove_duplicates))) {
            pref.feedRemoveDuplicates((boolean) newValue);
        }
        return true;
    }
}
