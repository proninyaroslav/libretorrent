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
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.SwitchPreferenceCompat;

import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.receivers.SchedulerReceiver;

public class FeedSettingsFragment extends PreferenceFragmentCompat
        implements
        Preference.OnPreferenceChangeListener
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

        SharedPreferences pref = SettingsManager.getPreferences(getActivity());

        String keyAutoRefresh = getString(R.string.pref_key_feed_auto_refresh);
        SwitchPreferenceCompat autoRefresh = (SwitchPreferenceCompat)findPreference(keyAutoRefresh);
        autoRefresh.setChecked(pref.getBoolean(keyAutoRefresh, SettingsManager.Default.autoRefreshFeeds));
        bindOnPreferenceChangeListener(autoRefresh);

        String keyRefreshInterval = getString(R.string.pref_key_feed_refresh_interval);
        ListPreference refreshInterval = (ListPreference)findPreference(keyRefreshInterval);
        String interval = Long.toString(pref.getLong(keyRefreshInterval, SettingsManager.Default.refreshFeedsInterval));
        int intervalIndex = refreshInterval.findIndexOfValue(interval);
        if (intervalIndex >= 0) {
            refreshInterval.setValueIndex(intervalIndex);
            refreshInterval.setSummary(refreshInterval.getEntries()[intervalIndex]);
        }
        bindOnPreferenceChangeListener(refreshInterval);

        String keyWiFiOnly = getString(R.string.pref_key_feed_auto_refresh_wifi_only);
        SwitchPreferenceCompat wifiOnly = (SwitchPreferenceCompat)findPreference(keyWiFiOnly);
        wifiOnly.setChecked(pref.getBoolean(keyWiFiOnly, SettingsManager.Default.autoRefreshWiFiOnly));

        String keyKeepTime = getString(R.string.pref_key_feed_keep_items_time);
        ListPreference keepTime = (ListPreference)findPreference(keyKeepTime);
        String time = Long.toString(pref.getLong(keyKeepTime, SettingsManager.Default.feedItemKeepTime));
        int timeIndex = keepTime.findIndexOfValue(time);
        if (timeIndex >= 0) {
            keepTime.setValueIndex(timeIndex);
            keepTime.setSummary(keepTime.getEntries()[timeIndex]);
        }
        bindOnPreferenceChangeListener(keepTime);

        String keyStartTorrents = getString(R.string.pref_key_feed_start_torrents);
        SwitchPreferenceCompat startTorrents = (SwitchPreferenceCompat)findPreference(keyStartTorrents);
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
        SharedPreferences pref = SettingsManager.getPreferences(getActivity());

        if (preference.getKey().equals(getString(R.string.pref_key_feed_auto_refresh))) {
            if ((boolean)newValue) {
                long interval = pref.getLong(getString(R.string.pref_key_feed_refresh_interval),
                                             SettingsManager.Default.refreshFeedsInterval);
                SchedulerReceiver.setRefreshFeedsAlarm(getActivity(), interval);
            } else {
                SchedulerReceiver.cancelScheduling(getActivity(), SchedulerReceiver.ACTION_FETCH_FEEDS);
            }
            Utils.enableBootReceiver(getActivity(), (boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_feed_refresh_interval))) {
            ListPreference refreshPreference = (ListPreference)preference;
            long interval = Long.parseLong((String)newValue);
            pref.edit().putLong(getString(R.string.pref_key_feed_refresh_interval), interval).apply();
            int index = refreshPreference.findIndexOfValue((String)newValue);
            if (index >= 0)
                refreshPreference.setSummary(refreshPreference.getEntries()[index]);
            SchedulerReceiver.setRefreshFeedsAlarm(getActivity(), interval);

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
