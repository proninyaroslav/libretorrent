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

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.service.Scheduler;
import org.proninyaroslav.libretorrent.ui.settings.CustomPreferenceFragment;
import org.proninyaroslav.libretorrent.ui.settings.customprefs.SwitchBarPreference;

public class FeedAutoRefreshSettingsFragment extends CustomPreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    public static final String KEY_RESULT = "result";

    private AppCompatActivity activity;
    private SettingsRepository pref;
    private String requestKey;

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

        var args = FeedAutoRefreshSettingsFragmentArgs.fromBundle(getArguments());
        requestKey = args.getFragmentRequestKey();

        pref = RepositoryHelper.getSettingsRepository(activity.getApplicationContext());

        String keyAutoRefresh = getString(R.string.pref_key_feed_auto_refresh);
        SwitchBarPreference autoRefresh = findPreference(keyAutoRefresh);
        if (autoRefresh != null) {
            autoRefresh.setChecked(pref.autoRefreshFeeds());
            bindOnPreferenceChangeListener(autoRefresh);
        }

        String keyRefreshInterval = getString(R.string.pref_key_feed_refresh_interval);
        ListPreference refreshInterval = findPreference(keyRefreshInterval);
        if (refreshInterval != null) {
            String interval = Long.toString(pref.refreshFeedsInterval());
            int intervalIndex = refreshInterval.findIndexOfValue(interval);
            if (intervalIndex >= 0)
                refreshInterval.setValueIndex(intervalIndex);
            bindOnPreferenceChangeListener(refreshInterval);
        }

        String keyUnmeteredOnly = getString(R.string.pref_key_feed_auto_refresh_unmetered_connections_only);
        SwitchPreferenceCompat unmeteredOnly = findPreference(keyUnmeteredOnly);
        if (unmeteredOnly != null) {
            unmeteredOnly.setChecked(pref.autoRefreshFeedsUnmeteredConnectionsOnly());
            bindOnPreferenceChangeListener(unmeteredOnly);
        }

        String keyRoaming = getString(R.string.pref_key_feed_auto_refresh_enable_roaming);
        SwitchPreferenceCompat roaming = findPreference(keyRoaming);
        if (roaming != null) {
            roaming.setChecked(pref.autoRefreshFeedsEnableRoaming());
            bindOnPreferenceChangeListener(roaming);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.appBar.setTitle(R.string.pref_feed_auto_refresh_title);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_feed_auto_refresh, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference) {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        var context = activity.getApplicationContext();
        if (preference.getKey().equals(getString(R.string.pref_key_feed_auto_refresh))) {
            pref.autoRefreshFeeds((boolean) newValue);
            if ((boolean) newValue) {
                long interval = pref.refreshFeedsInterval();
                Scheduler.runPeriodicalRefreshFeeds(context, interval);
            } else {
                Scheduler.cancelPeriodicalRefreshFeeds(context);
            }

            var bundle = new Bundle();
            bundle.putBoolean(KEY_RESULT, (boolean) newValue);
            getParentFragmentManager().setFragmentResult(requestKey, bundle);
        } else if (preference.getKey().equals(getString(R.string.pref_key_feed_refresh_interval))) {
            long interval = Long.parseLong((String) newValue);
            pref.refreshFeedsInterval(interval);
            Scheduler.runPeriodicalRefreshFeeds(context, interval);
        } else if (preference.getKey().equals(getString(R.string.pref_key_feed_auto_refresh_unmetered_connections_only))) {
            pref.autoRefreshFeedsUnmeteredConnectionsOnly((boolean) newValue);
        } else if (preference.getKey().equals(getString(R.string.pref_key_feed_auto_refresh_enable_roaming))) {
            pref.autoRefreshFeedsEnableRoaming((boolean) newValue);
        }
        return true;
    }
}
