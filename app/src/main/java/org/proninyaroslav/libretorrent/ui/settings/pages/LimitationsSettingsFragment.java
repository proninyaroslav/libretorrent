/*
 * Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.InputFilterRange;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SessionSettings;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.ui.settings.CustomPreferenceFragment;

public class LimitationsSettingsFragment extends CustomPreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String TAG = LimitationsSettingsFragment.class.getSimpleName();

    private static final String KEY_AUTO_MANAGE_SETTINGS_REQUEST = TAG + "_auto_manage_settings";

    private SettingsRepository pref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pref = RepositoryHelper.getSettingsRepository(requireContext().getApplicationContext());

        InputFilter[] speedFilter = new InputFilter[]{InputFilterRange.UNSIGNED_INT};
        InputFilter[] queueingFilter = new InputFilter[]{
                new InputFilterRange.Builder()
                        .setMin(-1)
                        .setMax(Integer.MAX_VALUE)
                        .build()
        };
        InputFilter[] maxFilter = new InputFilter[]{
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
        var autoManage = findPreference(keyAutoManage);
        if (autoManage != null) {
            autoManage.setSummary(pref.autoManage() ? R.string.switch_on : R.string.switch_off);
            autoManage.setOnPreferenceClickListener((p) -> {
                var action = LimitationsSettingsFragmentDirections.actionAutoManageSettings(
                        KEY_AUTO_MANAGE_SETTINGS_REQUEST
                );
                NavHostFragment.findNavController(this).navigate(action);
                return true;
            });
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

        setAutoManageSettingsListener();
    }

    private void setAutoManageSettingsListener() {
        getParentFragmentManager().setFragmentResultListener(
                KEY_AUTO_MANAGE_SETTINGS_REQUEST,
                this,
                (requestKey, result) -> {
                    boolean enabled = result.getBoolean(AutoManageSettingsFragment.KEY_RESULT);
                    var autoManage = findPreference(getString(R.string.pref_key_auto_manage));
                    if (autoManage != null) {
                        autoManage.setSummary(enabled ? R.string.switch_on : R.string.switch_off);
                    }
                }
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.appBar.setTitle(R.string.pref_header_limitations);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_limitations, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference) {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
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
            if (!TextUtils.isEmpty((String) newValue)) {
                summary = Integer.parseInt((String) newValue);
                value = summary * 1024;
            }
            pref.maxUploadSpeedLimit(value);
            preference.setSummary(Integer.toString(summary));

        } else if (preference.getKey().equals(getString(R.string.pref_key_max_download_speed))) {
            int value = 0;
            int summary = 0;
            if (!TextUtils.isEmpty((String) newValue)) {
                summary = Integer.parseInt((String) newValue);
                value = summary * 1024;
            }
            pref.maxDownloadSpeedLimit(value);
            preference.setSummary(Integer.toString(summary));

        } else if (preference.getKey().equals(getString(R.string.pref_key_max_uploads_per_torrent))) {
            int value = 1;
            if (!TextUtils.isEmpty((String) newValue))
                value = Integer.parseInt((String) newValue);
            pref.maxUploadsPerTorrent(value);
            preference.setSummary(Integer.toString(value));
        }

        return true;
    }

    private int parseMaxConnectionValue(Object newValue) {
        int value = SessionSettings.MIN_CONNECTIONS_LIMIT;
        if (!TextUtils.isEmpty((String) newValue))
            value = Integer.parseInt((String) newValue);
        if (value < SessionSettings.MIN_CONNECTIONS_LIMIT)
            value = SessionSettings.MIN_CONNECTIONS_LIMIT;

        return value;
    }
}
