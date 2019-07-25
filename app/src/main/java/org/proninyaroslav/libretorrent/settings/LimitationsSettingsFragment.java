/*
 * Copyright (C) 2016, 2017, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.text.InputFilter;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.takisoft.preferencex.EditTextPreference;
import com.takisoft.preferencex.PreferenceFragmentCompat;

import org.proninyaroslav.libretorrent.InputFilterMinMax;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.TorrentSession;

public class LimitationsSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = LimitationsSettingsFragment.class.getSimpleName();

    public static LimitationsSettingsFragment newInstance()
    {
        LimitationsSettingsFragment fragment = new LimitationsSettingsFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        SharedPreferences pref = SettingsManager.getInstance(getActivity().getApplicationContext())
                .getPreferences();

        InputFilter[] speedFilter = new InputFilter[]{ new InputFilterMinMax(0, Integer.MAX_VALUE) };
        InputFilter[] queueingFilter = new InputFilter[]{ new InputFilterMinMax(-1, Integer.MAX_VALUE) };

        String keyMaxDownloadSpeedLimit = getString(R.string.pref_key_max_download_speed);
        EditTextPreference maxDownloadSpeedLimit = findPreference(keyMaxDownloadSpeedLimit);
        if (maxDownloadSpeedLimit != null) {
            maxDownloadSpeedLimit.setDialogMessage(R.string.speed_limit_dialog);
            String value = Integer.toString(pref.getInt(keyMaxDownloadSpeedLimit,
                                            SettingsManager.Default.maxDownloadSpeedLimit) / 1024);
            maxDownloadSpeedLimit.setOnBindEditTextListener((editText) -> editText.setFilters(speedFilter));
            maxDownloadSpeedLimit.setSummary(value);
            maxDownloadSpeedLimit.setText(value);
            bindOnPreferenceChangeListener(maxDownloadSpeedLimit);
        }

        String keyMaxUploadSpeedLimit = getString(R.string.pref_key_max_upload_speed);
        EditTextPreference maxUploadSpeedLimit = findPreference(keyMaxUploadSpeedLimit);
        if (maxUploadSpeedLimit != null) {
            maxUploadSpeedLimit.setDialogMessage(R.string.speed_limit_dialog);
            String value = Integer.toString(pref.getInt(keyMaxUploadSpeedLimit,
                                            SettingsManager.Default.maxUploadSpeedLimit) / 1024);
            maxUploadSpeedLimit.setOnBindEditTextListener((editText) -> editText.setFilters(speedFilter));
            maxUploadSpeedLimit.setSummary(value);
            maxUploadSpeedLimit.setText(value);
            bindOnPreferenceChangeListener(maxUploadSpeedLimit);
        }

        String keyMaxConnections = getString(R.string.pref_key_max_connections);
        EditTextPreference maxConnections = findPreference(keyMaxConnections);
        if (maxConnections != null) {
            maxConnections.setDialogMessage(R.string.pref_max_connections_summary);
            String value = Integer.toString(pref.getInt(keyMaxConnections, SettingsManager.Default.maxConnections));
            maxConnections.setSummary(value);
            maxConnections.setText(value);
            bindOnPreferenceChangeListener(maxConnections);
        }

        String keyAutoManage = getString(R.string.pref_key_auto_manage);
        SwitchPreferenceCompat autoManage = findPreference(keyAutoManage);
        if (autoManage != null) {
            autoManage.setChecked(pref.getBoolean(keyAutoManage, SettingsManager.Default.autoManage));
            bindOnPreferenceChangeListener(autoManage);
        }

        String keyMaxActiveUploads = getString(R.string.pref_key_max_active_uploads);
        EditTextPreference maxActiveUploads  = findPreference(keyMaxActiveUploads);
        if (maxActiveUploads != null) {
            maxActiveUploads.setDialogMessage(R.string.pref_max_active_uploads_downloads_dialog_msg);
            String value = Integer.toString(pref.getInt(keyMaxActiveUploads, SettingsManager.Default.maxActiveUploads));
            maxActiveUploads.setOnBindEditTextListener((editText) -> editText.setFilters(queueingFilter));
            maxActiveUploads.setSummary(value);
            maxActiveUploads.setText(value);
            bindOnPreferenceChangeListener(maxActiveUploads);
        }

        String keyMaxActiveDownloads = getString(R.string.pref_key_max_active_downloads);
        EditTextPreference maxActiveDownloads  = findPreference(keyMaxActiveDownloads);
        if (maxActiveDownloads != null) {
            maxActiveDownloads.setDialogMessage(R.string.pref_max_active_uploads_downloads_dialog_msg);
            String value = Integer.toString(pref.getInt(keyMaxActiveDownloads, SettingsManager.Default.maxActiveDownloads));
            maxActiveDownloads.setOnBindEditTextListener((editText) -> editText.setFilters(queueingFilter));
            maxActiveDownloads.setSummary(value);
            maxActiveDownloads.setText(value);
            bindOnPreferenceChangeListener(maxActiveDownloads);
        }

        String keyMaxActiveTorrents = getString(R.string.pref_key_max_active_torrents);
        EditTextPreference maxActiveTorrents  = findPreference(keyMaxActiveTorrents);
        if (maxActiveTorrents != null) {
            maxActiveTorrents.setDialogMessage(R.string.pref_max_active_uploads_downloads_dialog_msg);
            String value = Integer.toString(pref.getInt(keyMaxActiveTorrents, SettingsManager.Default.maxActiveTorrents));
            maxActiveTorrents.setOnBindEditTextListener((editText) -> editText.setFilters(queueingFilter));
            maxActiveTorrents.setSummary(value);
            maxActiveTorrents.setText(value);
            bindOnPreferenceChangeListener(maxActiveTorrents);
        }

        String keyMaxConnectionsPerTorrent = getString(R.string.pref_key_max_connections_per_torrent);
        EditTextPreference maxConnectionsPerTorrent = findPreference(keyMaxConnectionsPerTorrent);
        if (maxConnectionsPerTorrent != null) {
            maxConnectionsPerTorrent.setDialogMessage(R.string.pref_max_connections_per_torrent_summary);
            String value = Integer.toString(pref.getInt(keyMaxConnectionsPerTorrent, SettingsManager.Default.maxConnectionsPerTorrent));
            maxConnectionsPerTorrent.setSummary(value);
            maxConnectionsPerTorrent.setText(value);
            bindOnPreferenceChangeListener(maxConnectionsPerTorrent);
        }

        String keyMaxUploadsPerTorrent = getString(R.string.pref_key_max_uploads_per_torrent);
        EditTextPreference maxUploadsPerTorrent = findPreference(keyMaxUploadsPerTorrent);
        if (maxUploadsPerTorrent != null) {
            maxUploadsPerTorrent.setDialogMessage(R.string.pref_max_active_uploads_downloads_dialog_msg);
            String value = Integer.toString(pref.getInt(keyMaxUploadsPerTorrent, SettingsManager.Default.maxUploadsPerTorrent));
            maxUploadsPerTorrent.setOnBindEditTextListener((editText) -> editText.setFilters(queueingFilter));
            maxUploadsPerTorrent.setSummary(value);
            maxUploadsPerTorrent.setText(value);
            bindOnPreferenceChangeListener(maxUploadsPerTorrent);
        }
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_limitations, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference)
    {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        SharedPreferences pref = SettingsManager.getInstance(getActivity().getApplicationContext())
                .getPreferences();

        if (preference.getKey().equals(getString(R.string.pref_key_max_connections)) ||
            preference.getKey().equals(getString(R.string.pref_key_max_connections_per_torrent))) {
            int value = TorrentSession.Settings.MIN_CONNECTIONS_LIMIT;
            if (!TextUtils.isEmpty((String) newValue))
                value = Integer.parseInt((String) newValue);
            if (value < TorrentSession.Settings.MIN_CONNECTIONS_LIMIT)
                value = TorrentSession.Settings.MIN_CONNECTIONS_LIMIT;
            pref.edit().putInt(preference.getKey(), value).apply();
            preference.setSummary(Integer.toString(value));
        } else if (preference.getKey().equals(getString(R.string.pref_key_max_upload_speed)) ||
                   preference.getKey().equals(getString(R.string.pref_key_max_download_speed))) {
            int value = 0;
            int summary = 0;
            if (!TextUtils.isEmpty((String) newValue)) {
                summary = Integer.parseInt((String) newValue);
                value = summary * 1024;
            }
            pref.edit().putInt(preference.getKey(), value).apply();
            preference.setSummary(Integer.toString(summary));
        } else if (preference.getKey().equals(getString(R.string.pref_key_max_active_downloads)) ||
                   preference.getKey().equals(getString(R.string.pref_key_max_active_uploads)) ||
                   preference.getKey().equals(getString(R.string.pref_key_max_active_torrents)) ||
                   preference.getKey().equals(getString(R.string.pref_key_max_uploads_per_torrent))) {
            int value = 1;
            if (!TextUtils.isEmpty((String) newValue))
                value = Integer.parseInt((String) newValue);
            pref.edit().putInt(preference.getKey(), value).apply();
            preference.setSummary(Integer.toString(value));
        }

        return true;
    }
}
