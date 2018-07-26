/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.text.InputFilter;
import android.text.TextUtils;

import com.takisoft.fix.support.v7.preference.EditTextPreference;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;

import org.proninyaroslav.libretorrent.InputFilterMinMax;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.TorrentEngine;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerDialog;

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

    private SettingsFragment.Callback callback;

    /* For API < 23 */
    @Override
    public void onAttach(Activity activity)
    {
        super.onAttach(activity);

        if (activity instanceof SettingsFragment.Callback) {
            callback = (SettingsFragment.Callback) activity;
        }
    }

    @Override
    public void onDetach()
    {
        super.onDetach();

        callback = null;
    }

    public static NetworkSettingsFragment newInstance()
    {
        NetworkSettingsFragment fragment = new NetworkSettingsFragment();

        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        SharedPreferences pref = SettingsManager.getPreferences(getActivity());

        String keyEnableDht = getString(R.string.pref_key_enable_dht);
        SwitchPreferenceCompat enableDht = (SwitchPreferenceCompat) findPreference(keyEnableDht);
        enableDht.setChecked(pref.getBoolean(keyEnableDht, SettingsManager.Default.enableDht));

        String keyEnableLsd = getString(R.string.pref_key_enable_lsd);
        SwitchPreferenceCompat enableLsd = (SwitchPreferenceCompat) findPreference(keyEnableLsd);
        enableLsd.setChecked(pref.getBoolean(keyEnableLsd, SettingsManager.Default.enableLsd));

        String keyEnableUtp = getString(R.string.pref_key_enable_utp);
        SwitchPreferenceCompat enableUtp = (SwitchPreferenceCompat) findPreference(keyEnableUtp);
        enableUtp.setChecked(pref.getBoolean(keyEnableUtp, SettingsManager.Default.enableUtp));

        String keyEnableUpnp = getString(R.string.pref_key_enable_upnp);
        SwitchPreferenceCompat enableUpnp = (SwitchPreferenceCompat) findPreference(keyEnableUpnp);
        enableUpnp.setChecked(pref.getBoolean(keyEnableUpnp, SettingsManager.Default.enableUpnp));

        String keyEnableNatpmp = getString(R.string.pref_key_enable_natpmp);
        SwitchPreferenceCompat enableNatpmp = (SwitchPreferenceCompat) findPreference(keyEnableNatpmp);
        enableNatpmp.setChecked(pref.getBoolean(keyEnableNatpmp, SettingsManager.Default.enableNatPmp));

        String keyRandomPort = getString(R.string.pref_key_use_random_port);
        SwitchPreferenceCompat randomPort = (SwitchPreferenceCompat) findPreference(keyRandomPort);
        randomPort.setDisableDependentsState(true);
        randomPort.setChecked(pref.getBoolean(keyRandomPort, SettingsManager.Default.useRandomPort));

        InputFilter[] portFilter =
                new InputFilter[]{ new InputFilterMinMax(1, TorrentEngine.Settings.MAX_PORT_NUMBER)};
        String keyPort = getString(R.string.pref_key_port);
        EditTextPreference port = (EditTextPreference) findPreference(keyPort);
        String value = Integer.toString(pref.getInt(keyPort, SettingsManager.Default.port));
        port.getEditText().setFilters(portFilter);
        port.setSummary(value);
        port.setText(value);
        bindOnPreferenceChangeListener(port);

        boolean enableAdvancedEncryptSettings;

        String keyEncryptMode = getString(R.string.pref_key_enc_mode);
        ListPreference encryptMode = (ListPreference) findPreference(keyEncryptMode);
        int type = pref.getInt(keyEncryptMode, SettingsManager.Default.encryptMode(getContext()));
        encryptMode.setValueIndex(type);
        String typesName[] = getResources().getStringArray(R.array.pref_enc_mode_entries);
        encryptMode.setSummary(typesName[type]);
        enableAdvancedEncryptSettings = type != Integer.parseInt(getString(R.string.pref_enc_mode_disable_value));
        bindOnPreferenceChangeListener(encryptMode);

        String keyEncryptInConnections = getString(R.string.pref_key_enc_in_connections);
        SwitchPreferenceCompat encryptInConnections =
                (SwitchPreferenceCompat) findPreference(keyEncryptInConnections);
        encryptInConnections.setEnabled(enableAdvancedEncryptSettings);
        encryptInConnections.setChecked(pref.getBoolean(keyEncryptInConnections,
                                                        SettingsManager.Default.encryptInConnections));

        String keyEncryptOutConnections = getString(R.string.pref_key_enc_out_connections);
        SwitchPreferenceCompat encryptOutConnections =
                (SwitchPreferenceCompat) findPreference(keyEncryptOutConnections);
        encryptOutConnections.setEnabled(enableAdvancedEncryptSettings);
        encryptOutConnections.setChecked(pref.getBoolean(keyEncryptOutConnections,
                                                         SettingsManager.Default.encryptOutConnections));

        String keyIpFilter = getString(R.string.pref_key_enable_ip_filtering);
        SwitchPreferenceCompat ipFilter = (SwitchPreferenceCompat) findPreference(keyIpFilter);
        ipFilter.setChecked(pref.getBoolean(keyIpFilter, SettingsManager.Default.enableIpFiltering));

        String keyIpFilterFile = getString(R.string.pref_key_ip_filtering_file);
        Preference ipFilterFile = findPreference(keyIpFilterFile);
        ipFilterFile.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(Preference preference)
            {
                fileChooseDialog();

                return true;
            }
        });

        Preference proxy = findPreference(getString(R.string.pref_key_proxy_settings));
        proxy.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(Preference preference)
            {
                if (Utils.isLargeScreenDevice(getActivity().getApplicationContext())) {
                    setFragment(ProxySettingsFragment.newInstance(),
                            getString(R.string.pref_proxy_settings_title));
                } else {
                    startActivity(ProxySettingsFragment.class,
                            getString(R.string.pref_proxy_settings_title));
                }

                return true;
            }
        });
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_network, rootKey);
    }

    private void fileChooseDialog()
    {
        Intent i = new Intent(getActivity(), FileManagerDialog.class);
        List<String> fileTypes = new ArrayList<>();
        fileTypes.add("dat");
        fileTypes.add("p2p");
        FileManagerConfig config = new FileManagerConfig(null,
                null,
                fileTypes,
                FileManagerConfig.FILE_CHOOSER_MODE);
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
        SharedPreferences pref = SettingsManager.getPreferences(getActivity());

        if (preference.getKey().equals(getString(R.string.pref_key_port))) {
            int value = TorrentEngine.Settings.DEFAULT_PORT;
            if (!TextUtils.isEmpty((String) newValue)) {
                value = Integer.parseInt((String) newValue);
            }
            pref.edit().putInt(preference.getKey(), value).apply();
            preference.setSummary(Integer.toString(value));
        } else if (preference.getKey().equals(getString(R.string.pref_key_enc_mode))) {
            int type = Integer.parseInt((String) newValue);
            pref.edit().putInt(preference.getKey(), type).apply();
            String typesName[] = getResources().getStringArray(R.array.pref_enc_mode_entries);
            preference.setSummary(typesName[type]);

            boolean enableAdvancedEncryptSettings = type != Integer.parseInt(getString(R.string.pref_enc_mode_disable_value));

            String keyEncryptInConnections = getString(R.string.pref_key_enc_in_connections);
            SwitchPreferenceCompat encryptInConnections =
                    (SwitchPreferenceCompat) findPreference(keyEncryptInConnections);
            encryptInConnections.setEnabled(enableAdvancedEncryptSettings);
            encryptInConnections.setChecked(enableAdvancedEncryptSettings);

            String keyEncryptOutConnections = getString(R.string.pref_key_enc_out_connections);
            SwitchPreferenceCompat encryptOutConnections =
                    (SwitchPreferenceCompat) findPreference(keyEncryptOutConnections);
            encryptOutConnections.setEnabled(enableAdvancedEncryptSettings);
            encryptOutConnections.setChecked(enableAdvancedEncryptSettings);
        }

        return true;
    }

    private <F extends PreferenceFragmentCompat> void setFragment(F fragment, String title)
    {
        if (Utils.isLargeScreenDevice(getActivity().getApplicationContext())) {
            if (callback != null) {
                callback.onDetailTitleChanged(title);
            }

            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.detail_fragment_container, fragment)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();
        }
    }

    private <F extends PreferenceFragmentCompat> void startActivity(Class<F> fragment, String title)
    {
        Intent i = new Intent(getActivity(), BasePreferenceActivity.class);
        PreferenceActivityConfig config = new PreferenceActivityConfig(
                fragment.getSimpleName(),
                title);

        i.putExtra(BasePreferenceActivity.TAG_CONFIG, config);
        startActivity(i);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == FILE_CHOOSE_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data.hasExtra(FileManagerDialog.TAG_RETURNED_PATH)) {
                String path = data.getStringExtra(FileManagerDialog.TAG_RETURNED_PATH);

                SharedPreferences pref = SettingsManager.getPreferences(getActivity());
                pref.edit().putString(getString(R.string.pref_key_ip_filtering_file), path).apply();
            }
        }
    }
}
