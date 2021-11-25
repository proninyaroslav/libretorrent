/*
 * Copyright (C) 2016-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import org.proninyaroslav.libretorrent.core.settings.SessionSettings;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;

public class LimitationsSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener
{
    private static final String TAG = LimitationsSettingsFragment.class.getSimpleName();

    private SettingsRepository pref;

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

        pref = RepositoryHelper.getSettingsRepository(getActivity().getApplicationContext());

        InputFilter[] speedFilter = new InputFilter[] { InputFilterRange.UNSIGNED_INT };
        InputFilter[] queueingFilter = new InputFilter[] {
                new InputFilterRange.Builder()
                        .setMin(-1)
                        .setMax(Integer.MAX_VALUE)
                        .build()
        };
        InputFilter[] maxFilter = new InputFilter[] {
                new InputFilterRange.Builder()
                        .setMax(Integer.MAX_VALUE)
                        .build()
        };

        String keyMaxDownloadSpeedLimit = getString(R.string.pref_key_max_download_speed);
        EditTextPreference maxDownloadSpeedLimit = findPreference(keyMaxDownloadSpeedLimit);
        if (maxDownloadSpeedLimit != null) {
            maxDownloadSpeedLimit.setDialogMessage(R.string.speed_limit_dialog);
            String value = Integer.toString(pref.maxDownloadSpeedLimit() / 1024);
            maxDownloadSpeedLimit.setOnBindEditTextListener((editText) -> editText.setFilters(speedFilter));
            maxDownloadSpeedLimit.setSummary(value);
            maxDownloadSpeedLimit.setText(value);
            bindOnPreferenceChangeListener(maxDownloadSpeedLimit);
        }

        String keyMaxUploadSpeedLimit = getString(R.string.pref_key_max_upload_speed);
        EditTextPreference maxUploadSpeedLimit = findPreference(keyMaxUploadSpeedLimit);
        if (maxUploadSpeedLimit != null) {
            maxUploadSpeedLimit.setDialogMessage(R.string.speed_limit_dialog);
            String value = Integer.toString(pref.maxUploadSpeedLimit() / 1024);
            maxUploadSpeedLimit.setOnBindEditTextListener((editText) -> editText.setFilters(speedFilter));
            maxUploadSpeedLimit.setSummary(value);
            maxUploadSpeedLimit.setText(value);
            bindOnPreferenceChangeListener(maxUploadSpeedLimit);
        }

        String keyMaxConnections = getString(R.string.pref_key_max_connections);
        EditTextPreference maxConnections = findPreference(keyMaxConnections);
        if (maxConnections != null) {
            maxConnections.setDialogMessage(R.string.pref_max_connections_summary);
            String value = Integer.toString(pref.maxConnections());
            maxConnections.setOnBindEditTextListener((editText) -> editText.setFilters(maxFilter));
            maxConnections.setSummary(value);
            maxConnections.setText(value);
            bindOnPreferenceChangeListener(maxConnections);
        }

        String keyAutoManage = getString(R.string.pref_key_auto_manage);
        SwitchPreferenceCompat autoManage = findPreference(keyAutoManage);
        if (autoManage != null) {
            autoManage.setChecked(pref.autoManage());
            bindOnPreferenceChangeListener(autoManage);
        }

        String keyMaxActiveUploads = getString(R.string.pref_key_max_active_uploads);
        EditTextPreference maxActiveUploads  = findPreference(keyMaxActiveUploads);
        if (maxActiveUploads != null) {
            maxActiveUploads.setDialogMessage(R.string.pref_max_active_uploads_downloads_dialog_msg);
            String value = Integer.toString(pref.maxActiveUploads());
            maxActiveUploads.setOnBindEditTextListener((editText) -> editText.setFilters(queueingFilter));
            maxActiveUploads.setSummary(value);
            maxActiveUploads.setText(value);
            bindOnPreferenceChangeListener(maxActiveUploads);
        }

        String keyMaxActiveDownloads = getString(R.string.pref_key_max_active_downloads);
        EditTextPreference maxActiveDownloads  = findPreference(keyMaxActiveDownloads);
        if (maxActiveDownloads != null) {
            maxActiveDownloads.setDialogMessage(R.string.pref_max_active_uploads_downloads_dialog_msg);
            String value = Integer.toString(pref.maxActiveDownloads());
            maxActiveDownloads.setOnBindEditTextListener((editText) -> editText.setFilters(queueingFilter));
            maxActiveDownloads.setSummary(value);
            maxActiveDownloads.setText(value);
            bindOnPreferenceChangeListener(maxActiveDownloads);
        }

        String keyMaxActiveTorrents = getString(R.string.pref_key_max_active_torrents);
        EditTextPreference maxActiveTorrents  = findPreference(keyMaxActiveTorrents);
        if (maxActiveTorrents != null) {
            maxActiveTorrents.setDialogMessage(R.string.pref_max_active_uploads_downloads_dialog_msg);
            String value = Integer.toString(pref.maxActiveTorrents());
            maxActiveTorrents.setOnBindEditTextListener((editText) -> editText.setFilters(queueingFilter));
            maxActiveTorrents.setSummary(value);
            maxActiveTorrents.setText(value);
            bindOnPreferenceChangeListener(maxActiveTorrents);
        }

        String keyMaxConnectionsPerTorrent = getString(R.string.pref_key_max_connections_per_torrent);
        EditTextPreference maxConnectionsPerTorrent = findPreference(keyMaxConnectionsPerTorrent);
        if (maxConnectionsPerTorrent != null) {
            maxConnectionsPerTorrent.setDialogMessage(R.string.pref_max_connections_per_torrent_summary);
            String value = Integer.toString(pref.maxConnectionsPerTorrent());
            maxConnectionsPerTorrent.setOnBindEditTextListener((editText) -> editText.setFilters(maxFilter));
            maxConnectionsPerTorrent.setSummary(value);
            maxConnectionsPerTorrent.setText(value);
            bindOnPreferenceChangeListener(maxConnectionsPerTorrent);
        }

        String keyMaxUploadsPerTorrent = getString(R.string.pref_key_max_uploads_per_torrent);
        EditTextPreference maxUploadsPerTorrent = findPreference(keyMaxUploadsPerTorrent);
        if (maxUploadsPerTorrent != null) {
            maxUploadsPerTorrent.setDialogMessage(R.string.pref_max_active_uploads_downloads_dialog_msg);
            String value = Integer.toString(pref.maxUploadsPerTorrent());
            maxUploadsPerTorrent.setOnBindEditTextListener((editText) -> editText.setFilters(queueingFilter));
            maxUploadsPerTorrent.setSummary(value);
            maxUploadsPerTorrent.setText(value);
            bindOnPreferenceChangeListener(maxUploadsPerTorrent);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
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
        if (preference.getKey().equals(getString(R.string.pref_key_max_connections))) {
            int value = parseMaxConnectionValue(newValue);
            pref.maxConnections(value);
            preference.setSummary(Integer.toString(value));

        } else if (preference.getKey().equals(getString(R.string.pref_key_max_connections_per_torrent))) {
            int value = parseMaxConnectionValue(newValue);
            pref.maxConnectionsPerTorrent(value);
            preference.setSummary(Integer.toString(value));

        } else if (preference.getKey().equals(getString(R.string.pref_key_max_upload_speed))) {
            int value = 0;
            int summary = 0;
            if (!TextUtils.isEmpty((String)newValue)) {
                summary = Integer.parseInt((String)newValue);
                value = summary * 1024;
            }
            pref.maxUploadSpeedLimit(value);
            preference.setSummary(Integer.toString(summary));

        } else if (preference.getKey().equals(getString(R.string.pref_key_max_download_speed))) {
            int value = 0;
            int summary = 0;
            if (!TextUtils.isEmpty((String)newValue)) {
                summary = Integer.parseInt((String)newValue);
                value = summary * 1024;
            }
            pref.maxDownloadSpeedLimit(value);
            preference.setSummary(Integer.toString(summary));

        } else if (preference.getKey().equals(getString(R.string.pref_key_max_active_downloads))) {
            int value = 1;
            if (!TextUtils.isEmpty((String)newValue))
                value = Integer.parseInt((String)newValue);
            pref.maxActiveDownloads(value);
            preference.setSummary(Integer.toString(value));

        } else if (preference.getKey().equals(getString(R.string.pref_key_max_active_uploads))) {
            int value = 1;
            if (!TextUtils.isEmpty((String)newValue))
                value = Integer.parseInt((String)newValue);
            pref.maxActiveUploads(value);
            preference.setSummary(Integer.toString(value));

        } else if (preference.getKey().equals(getString(R.string.pref_key_max_active_torrents))) {
            int value = 1;
            if (!TextUtils.isEmpty((String)newValue))
                value = Integer.parseInt((String)newValue);
            pref.maxActiveTorrents(value);
            preference.setSummary(Integer.toString(value));

        } else if (preference.getKey().equals(getString(R.string.pref_key_max_uploads_per_torrent))) {
            int value = 1;
            if (!TextUtils.isEmpty((String)newValue))
                value = Integer.parseInt((String)newValue);
            pref.maxUploadsPerTorrent(value);
            preference.setSummary(Integer.toString(value));

        } else if (preference.getKey().equals(getString(R.string.pref_key_auto_manage))) {
            pref.autoManage((boolean)newValue);
        }

        return true;
    }

    private int parseMaxConnectionValue(Object newValue)
    {
        int value = SessionSettings.MIN_CONNECTIONS_LIMIT;
        if (!TextUtils.isEmpty((String) newValue))
            value = Integer.parseInt((String) newValue);
        if (value < SessionSettings.MIN_CONNECTIONS_LIMIT)
            value = SessionSettings.MIN_CONNECTIONS_LIMIT;

        return value;
    }
}
