/*
 * Copyright (C) 2016, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.takisoft.preferencex.PreferenceFragmentCompat;

import org.proninyaroslav.libretorrent.InputFilterMinMax;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.TorrentSession;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerDialog;
import org.proninyaroslav.libretorrent.viewmodel.settings.SettingsViewModel;

import java.util.ArrayList;
import java.util.List;

/*
 * TODO: add PeX enable/disable feature
 */

public class NetworkSettingsFragment extends PreferenceFragmentCompat
        implements
        Preference.OnPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = NetworkSettingsFragment.class.getSimpleName();

    private static final int FILE_CHOOSE_REQUEST = 1;

    private AppCompatActivity activity;
    private SettingsViewModel viewModel;

    public static NetworkSettingsFragment newInstance()
    {
        NetworkSettingsFragment fragment = new NetworkSettingsFragment();

        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity)context;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        viewModel = ViewModelProviders.of(activity).get(SettingsViewModel.class);
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        SharedPreferences pref = SettingsManager.getInstance(activity.getApplicationContext())
                .getPreferences();

        String keyEnableDht = getString(R.string.pref_key_enable_dht);
        SwitchPreferenceCompat enableDht = findPreference(keyEnableDht);
        if (enableDht != null)
            enableDht.setChecked(pref.getBoolean(keyEnableDht, SettingsManager.Default.enableDht));

        String keyEnableLsd = getString(R.string.pref_key_enable_lsd);
        SwitchPreferenceCompat enableLsd = findPreference(keyEnableLsd);
        if (enableLsd != null)
            enableLsd.setChecked(pref.getBoolean(keyEnableLsd, SettingsManager.Default.enableLsd));

        String keyEnableUtp = getString(R.string.pref_key_enable_utp);
        SwitchPreferenceCompat enableUtp = findPreference(keyEnableUtp);
        if (enableUtp != null)
            enableUtp.setChecked(pref.getBoolean(keyEnableUtp, SettingsManager.Default.enableUtp));

        String keyEnableUpnp = getString(R.string.pref_key_enable_upnp);
        SwitchPreferenceCompat enableUpnp = findPreference(keyEnableUpnp);
        if (enableUpnp != null)
            enableUpnp.setChecked(pref.getBoolean(keyEnableUpnp, SettingsManager.Default.enableUpnp));

        String keyEnableNatpmp = getString(R.string.pref_key_enable_natpmp);
        SwitchPreferenceCompat enableNatpmp = findPreference(keyEnableNatpmp);
        if (enableNatpmp != null)
            enableNatpmp.setChecked(pref.getBoolean(keyEnableNatpmp, SettingsManager.Default.enableNatPmp));

        String keyRandomPort = getString(R.string.pref_key_use_random_port);
        SwitchPreferenceCompat randomPort = findPreference(keyRandomPort);
        if (randomPort != null) {
            randomPort.setSummary(String.format(getString(R.string.pref_use_random_port_summarty),
                    TorrentSession.Settings.DEFAULT_PORT_RANGE_FIRST,
                    TorrentSession.Settings.DEFAULT_PORT_RANGE_SECOND - 10));
            randomPort.setDisableDependentsState(true);
            randomPort.setChecked(pref.getBoolean(keyRandomPort, SettingsManager.Default.useRandomPort));
        }

        InputFilter[] portFilter = new InputFilter[]{ new InputFilterMinMax(0, 65535) };

        String keyPortStart = getString(R.string.pref_key_port_range_first);
        EditTextPreference portStart = findPreference(keyPortStart);
        if (portStart != null) {
            String value = Integer.toString(pref.getInt(keyPortStart, SettingsManager.Default.portRangeFirst));
            portStart.setOnBindEditTextListener((editText) -> editText.setFilters(portFilter));
            portStart.setSummary(value);
            portStart.setText(value);
            bindOnPreferenceChangeListener(portStart);
        }

        String keyPortEnd = getString(R.string.pref_key_port_range_second);
        EditTextPreference portEnd = findPreference(keyPortEnd);
        if (portEnd != null) {
            String value = Integer.toString(pref.getInt(keyPortEnd, SettingsManager.Default.portRangeSecond));
            portEnd.setOnBindEditTextListener((editText) -> editText.setFilters(portFilter));
            portEnd.setSummary(value);
            portEnd.setText(value);
            bindOnPreferenceChangeListener(portEnd);
        }

        boolean enableAdvancedEncryptSettings;

        String keyEncryptMode = getString(R.string.pref_key_enc_mode);
        ListPreference encryptMode = findPreference(keyEncryptMode);
        int type = pref.getInt(keyEncryptMode, SettingsManager.Default.encryptMode(activity));
        if (encryptMode != null) {
            encryptMode.setValueIndex(type);
            String[] typesName = getResources().getStringArray(R.array.pref_enc_mode_entries);
            encryptMode.setSummary(typesName[type]);
            enableAdvancedEncryptSettings = type != Integer.parseInt(getString(R.string.pref_enc_mode_disable_value));
            bindOnPreferenceChangeListener(encryptMode);

            String keyEncryptInConnections = getString(R.string.pref_key_enc_in_connections);
            SwitchPreferenceCompat encryptInConnections = findPreference(keyEncryptInConnections);
            if (encryptInConnections != null) {
                encryptInConnections.setEnabled(enableAdvancedEncryptSettings);
                encryptInConnections.setChecked(pref.getBoolean(keyEncryptInConnections,
                                                SettingsManager.Default.encryptInConnections));
            }

            String keyEncryptOutConnections = getString(R.string.pref_key_enc_out_connections);
            SwitchPreferenceCompat encryptOutConnections = findPreference(keyEncryptOutConnections);
            if (encryptOutConnections != null) {
                encryptOutConnections.setEnabled(enableAdvancedEncryptSettings);
                encryptOutConnections.setChecked(pref.getBoolean(keyEncryptOutConnections,
                                                 SettingsManager.Default.encryptOutConnections));
            }
        }

        String keyIpFilter = getString(R.string.pref_key_enable_ip_filtering);
        SwitchPreferenceCompat ipFilter = findPreference(keyIpFilter);
        if (ipFilter != null)
            ipFilter.setChecked(pref.getBoolean(keyIpFilter, SettingsManager.Default.enableIpFiltering));

        String keyIpFilterFile = getString(R.string.pref_key_ip_filtering_file);
        Preference ipFilterFile = findPreference(keyIpFilterFile);
        if (ipFilterFile != null) {
            ipFilterFile.setOnPreferenceClickListener((Preference preference) -> {
                fileChooseDialog();

                return true;
            });
        }

        String keyShowNatErrors = getString(R.string.pref_key_show_nat_errors);
        SwitchPreferenceCompat showNatErrors = findPreference(keyShowNatErrors);
        if (showNatErrors != null)
            showNatErrors.setChecked(pref.getBoolean(keyShowNatErrors, SettingsManager.Default.showNatErrors));

        Preference proxy = findPreference(getString(R.string.pref_key_proxy_settings));
        if (proxy != null) {
            proxy.setOnPreferenceClickListener((Preference preference) -> {
                if (Utils.isLargeScreenDevice(activity)) {
                    setFragment(ProxySettingsFragment.newInstance(),
                            getString(R.string.pref_proxy_settings_title));
                } else {
                    startActivity(ProxySettingsFragment.class,
                            getString(R.string.pref_proxy_settings_title));
                }

                return true;
            });
        }
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_network, rootKey);
    }

    private void fileChooseDialog()
    {
        Intent i = new Intent(getActivity(), FileManagerDialog.class);
        FileManagerConfig config = new FileManagerConfig(
                null,
                null,
                FileManagerConfig.FILE_CHOOSER_MODE);
        List<String> fileTypes = new ArrayList<>();
        fileTypes.add("dat");
        fileTypes.add("p2p");
        config.highlightFileTypes = fileTypes;

        i.putExtra(FileManagerDialog.TAG_CONFIG, config);
        startActivityForResult(i, FILE_CHOOSE_REQUEST);
    }

    private void bindOnPreferenceChangeListener(Preference preference)
    {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        SharedPreferences pref = SettingsManager.getInstance(activity.getApplicationContext())
                .getPreferences();

        if (preference.getKey().equals(getString(R.string.pref_key_port_range_first))) {
            int value = TorrentSession.Settings.DEFAULT_PORT_RANGE_FIRST;
            if (!TextUtils.isEmpty((String)newValue))
                value = Integer.parseInt((String)newValue);
            pref.edit().putInt(preference.getKey(), value).apply();
            preference.setSummary(Integer.toString(value));

        } else if (preference.getKey().equals(getString(R.string.pref_key_port_range_second))) {
            int value = TorrentSession.Settings.DEFAULT_PORT_RANGE_SECOND;
            if (!TextUtils.isEmpty((String)newValue))
                value = Integer.parseInt((String)newValue);
            pref.edit().putInt(preference.getKey(), value).apply();
            preference.setSummary(Integer.toString(value));

        } else if (preference.getKey().equals(getString(R.string.pref_key_enc_mode))) {
            int type = Integer.parseInt((String) newValue);
            pref.edit().putInt(preference.getKey(), type).apply();
            String[] typesName = getResources().getStringArray(R.array.pref_enc_mode_entries);
            preference.setSummary(typesName[type]);

            boolean enableAdvancedEncryptSettings = type != Integer.parseInt(getString(R.string.pref_enc_mode_disable_value));

            String keyEncryptInConnections = getString(R.string.pref_key_enc_in_connections);
            SwitchPreferenceCompat encryptInConnections = findPreference(keyEncryptInConnections);
            if (encryptInConnections != null) {
                encryptInConnections.setEnabled(enableAdvancedEncryptSettings);
                encryptInConnections.setChecked(enableAdvancedEncryptSettings);
            }

            String keyEncryptOutConnections = getString(R.string.pref_key_enc_out_connections);
            SwitchPreferenceCompat encryptOutConnections = findPreference(keyEncryptOutConnections);
            if (encryptOutConnections != null) {
                encryptOutConnections.setEnabled(enableAdvancedEncryptSettings);
                encryptOutConnections.setChecked(enableAdvancedEncryptSettings);
            }
        }

        return true;
    }

    private <F extends PreferenceFragmentCompat> void setFragment(F fragment, String title)
    {
        if (Utils.isLargeScreenDevice(getActivity().getApplicationContext())) {
            viewModel.detailTitleChanged.setValue(title);
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.detail_fragment_container, fragment)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();
        }
    }

    private <F extends PreferenceFragmentCompat> void startActivity(Class<F> fragment, String title)
    {
        Intent i = new Intent(getActivity(), PreferenceActivity.class);
        PreferenceActivityConfig config = new PreferenceActivityConfig(
                fragment.getSimpleName(),
                title);

        i.putExtra(PreferenceActivity.TAG_CONFIG, config);
        startActivity(i);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == FILE_CHOOSE_REQUEST && resultCode == Activity.RESULT_OK) {
            Uri path = data.getData();
            if (path == null)
                return;

            SharedPreferences pref = SettingsManager.getInstance(activity.getApplicationContext())
                    .getPreferences();
            pref.edit().putString(getString(R.string.pref_key_ip_filtering_file), path.toString())
                    .apply();
        }
    }
}
