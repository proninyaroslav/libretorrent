/*
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.takisoft.preferencex.PreferenceFragmentCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.Scheduler;

public class FeedSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = FeedSettingsFragment.class.getSimpleName();

    public static FeedSettingsFragment newInstance()
    {
        FeedSettingsFragment fragment = new FeedSettingsFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        SharedPreferences pref = SettingsManager.getInstance(getActivity().getApplicationContext())
                .getPreferences();

        String keyAutoRefresh = getString(R.string.pref_key_feed_auto_refresh);
        SwitchPreferenceCompat autoRefresh = findPreference(keyAutoRefresh);
        if (autoRefresh != null) {
            autoRefresh.setChecked(pref.getBoolean(keyAutoRefresh, SettingsManager.Default.autoRefreshFeeds));
            bindOnPreferenceChangeListener(autoRefresh);
        }

        String keyRefreshInterval = getString(R.string.pref_key_feed_refresh_interval);
        ListPreference refreshInterval = findPreference(keyRefreshInterval);
        if (refreshInterval != null) {
            String interval = Long.toString(pref.getLong(keyRefreshInterval, SettingsManager.Default.refreshFeedsInterval));
            int intervalIndex = refreshInterval.findIndexOfValue(interval);
            if (intervalIndex >= 0) {
                refreshInterval.setValueIndex(intervalIndex);
                refreshInterval.setSummary(refreshInterval.getEntries()[intervalIndex]);
            }
            bindOnPreferenceChangeListener(refreshInterval);
        }

        String keyUnmeteredOnly = getString(R.string.pref_key_feed_auto_refresh_unmetered_connections_only);
        SwitchPreferenceCompat unmeteredOnly = findPreference(keyUnmeteredOnly);
        if (unmeteredOnly != null)
            unmeteredOnly.setChecked(pref.getBoolean(keyUnmeteredOnly, SettingsManager.Default.autoRefreshUnmeteredConnectionsOnly));

        String keyRoaming = getString(R.string.pref_key_feed_auto_refresh_enable_roaming);
        SwitchPreferenceCompat roaming = findPreference(keyRoaming);
        if (roaming != null)
            roaming.setChecked(pref.getBoolean(keyRoaming, SettingsManager.Default.autoRefreshEnableRoaming));

        String keyKeepTime = getString(R.string.pref_key_feed_keep_items_time);
        ListPreference keepTime = findPreference(keyKeepTime);
        if (keepTime != null) {
            String time = Long.toString(pref.getLong(keyKeepTime, SettingsManager.Default.feedItemKeepTime));
            int timeIndex = keepTime.findIndexOfValue(time);
            if (timeIndex >= 0) {
                keepTime.setValueIndex(timeIndex);
                keepTime.setSummary(keepTime.getEntries()[timeIndex]);
            }
            bindOnPreferenceChangeListener(keepTime);
        }

        String keyStartTorrents = getString(R.string.pref_key_feed_start_torrents);
        SwitchPreferenceCompat startTorrents = findPreference(keyStartTorrents);
        if (startTorrents != null)
            startTorrents.setChecked(pref.getBoolean(keyStartTorrents, SettingsManager.Default.feedStartTorrents));
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_feed, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference)
    {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        Context context = getActivity().getApplicationContext();
        SharedPreferences pref = SettingsManager.getInstance(context).getPreferences();

        if (preference.getKey().equals(getString(R.string.pref_key_feed_auto_refresh))) {
            if ((boolean)newValue) {
                long interval = pref.getLong(getString(R.string.pref_key_feed_refresh_interval),
                                             SettingsManager.Default.refreshFeedsInterval);
                Scheduler.runPeriodicalRefreshFeeds(context, interval);
            } else {
                Scheduler.cancelPeriodicalRefreshFeeds(context);
            }

        } else if (preference.getKey().equals(getString(R.string.pref_key_feed_refresh_interval))) {
            ListPreference refreshPreference = (ListPreference)preference;
            long interval = Long.parseLong((String)newValue);
            pref.edit().putLong(getString(R.string.pref_key_feed_refresh_interval), interval).apply();
            int index = refreshPreference.findIndexOfValue((String)newValue);
            if (index >= 0)
                refreshPreference.setSummary(refreshPreference.getEntries()[index]);
            Scheduler.runPeriodicalRefreshFeeds(context, interval);

        } else if (preference.getKey().equals(getString(R.string.pref_key_feed_keep_items_time))) {
            ListPreference keepTimePreference = (ListPreference)preference;
            long keepTime = Long.parseLong((String)newValue);
            pref.edit().putLong(getString(R.string.pref_key_feed_keep_items_time), keepTime).apply();
            int index = keepTimePreference.findIndexOfValue((String)newValue);
            if (index >= 0)
                keepTimePreference.setSummary(keepTimePreference.getEntries()[index]);
        }

        return true;
    }
}
