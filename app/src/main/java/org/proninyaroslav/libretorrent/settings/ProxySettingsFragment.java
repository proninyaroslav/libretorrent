/*
 * Copyright (C) 2016, 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.takisoft.fix.support.v7.preference.EditTextPreference;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;

import org.proninyaroslav.libretorrent.InputFilterMinMax;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.ProxySettingsPack;
import org.proninyaroslav.libretorrent.core.TorrentEngine;

public class ProxySettingsFragment extends PreferenceFragmentCompat
        implements
        Preference.OnPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = ProxySettingsFragment.class.getSimpleName();

    private CoordinatorLayout coordinatorLayout;
    private FloatingActionButton saveChangesButton;
    private boolean proxyChanged = false;

    public static ProxySettingsFragment newInstance()
    {
        ProxySettingsFragment fragment = new ProxySettingsFragment();

        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        coordinatorLayout = view.findViewById(R.id.coordinator_layout);
        saveChangesButton = view.findViewById(R.id.save_changes_button);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        if (proxyChanged) {
            Toast.makeText(getActivity().getApplicationContext(),
                    R.string.proxy_settings_apply_after_reboot,
                    Toast.LENGTH_SHORT)
                    .show();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        SharedPreferences pref = SettingsManager.getPreferences(getActivity());

        boolean enableAdvancedSettings;

        String keyProxyType = getString(R.string.pref_key_proxy_type);
        ListPreference proxyType = (ListPreference) findPreference(keyProxyType);
        int type = pref.getInt(keyProxyType, SettingsManager.Default.proxyType);
        proxyType.setValueIndex(type);
        String typesName[] = getResources().getStringArray(R.array.pref_proxy_type_entries);
        proxyType.setSummary(typesName[type]);
        enableAdvancedSettings = type != Integer.parseInt(getString(R.string.pref_proxy_type_none_value));
        bindOnPreferenceChangeListener(proxyType);

        String keyAddress = getString(R.string.pref_key_proxy_address);
        EditTextPreference address = (EditTextPreference) findPreference(keyAddress);
        address.setEnabled(enableAdvancedSettings);
        String addressValue = pref.getString(keyAddress, SettingsManager.Default.proxyAddress);
        address.setText(addressValue);
        address.setSummary(addressValue);
        bindOnPreferenceChangeListener(address);

        String keyPort = getString(R.string.pref_key_proxy_port);
        EditTextPreference port = (EditTextPreference) findPreference(keyPort);
        port.setEnabled(enableAdvancedSettings);
        InputFilter[] portFilter =
                new InputFilter[]{new InputFilterMinMax(0, 65535)};
        int portNumber = pref.getInt(keyPort, SettingsManager.Default.proxyPort);
        String portValue = Integer.toString(portNumber);
        port.getEditText().setFilters(portFilter);
        port.setSummary(portValue);
        port.setText(portValue);
        bindOnPreferenceChangeListener(port);

        String keyProxyPeersToo = getString(R.string.pref_key_proxy_peers_too);
        SwitchPreferenceCompat proxyPeersToo = (SwitchPreferenceCompat) findPreference(keyProxyPeersToo);
        proxyPeersToo.setEnabled(enableAdvancedSettings);
        proxyPeersToo.setChecked(pref.getBoolean(keyProxyPeersToo, SettingsManager.Default.proxyPeersToo));

        String keyRequiresAuth = getString(R.string.pref_key_proxy_requires_auth);
        SwitchPreferenceCompat requiresAuth = (SwitchPreferenceCompat) findPreference(keyRequiresAuth);
        requiresAuth.setEnabled(enableAdvancedSettings);
        requiresAuth.setChecked(pref.getBoolean(keyRequiresAuth, SettingsManager.Default.proxyRequiresAuth));

        String keyLogin = getString(R.string.pref_key_proxy_login);
        EditTextPreference login = (EditTextPreference) findPreference(keyLogin);
        String loginValue = pref.getString(keyLogin, SettingsManager.Default.proxyLogin);
        login.setText(loginValue);
        login.setSummary(loginValue);
        bindOnPreferenceChangeListener(login);

        String keyPassword = getString(R.string.pref_key_proxy_password);
        EditTextPreference password = (EditTextPreference) findPreference(keyPassword);
        String passwordValue = pref.getString(keyPassword, SettingsManager.Default.proxyPassword);
        password.setText(passwordValue);
        EditText edit = password.getEditText();
        password.setSummary(edit.getTransformationMethod().getTransformation(passwordValue, edit).toString());
        bindOnPreferenceChangeListener(password);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        saveChangesButton.show();
        saveChangesButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                SharedPreferences pref = SettingsManager.getPreferences(getActivity());
                /* Value change is tracked in TorrentService */
                pref.edit().putBoolean(getString(R.string.pref_key_apply_proxy), true).apply();
                proxyChanged = false;
            }
        });
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_proxy, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference)
    {
        preference.setOnPreferenceChangeListener(this);
    }

    private void enableOrDisablePreferences(boolean enable)
    {
        String keyAddress = getString(R.string.pref_key_proxy_address);
        EditTextPreference address = (EditTextPreference) findPreference(keyAddress);
        address.setEnabled(enable);

        String keyPort = getString(R.string.pref_key_proxy_port);
        EditTextPreference port = (EditTextPreference) findPreference(keyPort);
        port.setEnabled(enable);

        String keyProxyPeersToo = getString(R.string.pref_key_proxy_peers_too);
        SwitchPreferenceCompat proxyPeersToo = (SwitchPreferenceCompat) findPreference(keyProxyPeersToo);
        proxyPeersToo.setEnabled(enable);

        String keyRequiresAuth = getString(R.string.pref_key_proxy_requires_auth);
        SwitchPreferenceCompat requiresAuth = (SwitchPreferenceCompat) findPreference(keyRequiresAuth);
        requiresAuth.setEnabled(enable);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        SharedPreferences pref = SettingsManager.getPreferences(getActivity());

        if (preference.getKey().equals(getString(R.string.pref_key_proxy_type))) {
            int type = Integer.parseInt((String) newValue);
            pref.edit().putInt(preference.getKey(), type).apply();
            String typesName[] = getResources().getStringArray(R.array.pref_proxy_type_entries);
            preference.setSummary(typesName[type]);

            boolean enableAdvancedSettings = type != Integer.parseInt(getString(R.string.pref_proxy_type_none_value));
            enableOrDisablePreferences(enableAdvancedSettings);
        } else if (preference.getKey().equals(getString(R.string.pref_key_proxy_port))) {
            int value = SettingsManager.Default.proxyPort;
            if (!TextUtils.isEmpty((String) newValue))
                value = Integer.parseInt((String) newValue);

            pref.edit().putInt(preference.getKey(), value).apply();
            preference.setSummary(Integer.toString(value));
        } else if (preference.getKey().equals(getString(R.string.pref_key_proxy_address)) ||
                preference.getKey().equals(getString(R.string.pref_key_proxy_login))) {
            pref.edit().putString(preference.getKey(), (String)newValue).apply();
            preference.setSummary((String) newValue);
        } else if (preference.getKey().equals(getString(R.string.pref_key_proxy_password))) {
            pref.edit().putString(preference.getKey(), (String)newValue).apply();
            EditText edit = ((EditTextPreference) preference).getEditText();
            String passwordValue = edit.getTransformationMethod().getTransformation((String) newValue, edit).toString();
            preference.setSummary(passwordValue);
        }

        if (!proxyChanged) {
            proxyChanged = true;
            pref.edit().putBoolean(getString(R.string.pref_key_proxy_changed), true).apply();
        }

        return true;
    }
}
