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
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.InputFilterRange;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;

public class ProxySettingsFragment extends PreferenceFragmentCompat
        implements
        Preference.OnPreferenceChangeListener
{
    private static final String TAG = ProxySettingsFragment.class.getSimpleName();

    private SettingsRepository pref;
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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        coordinatorLayout = view.findViewById(R.id.coordinator_layout);
        saveChangesButton = view.findViewById(R.id.save_changes_button);

        saveChangesButton.show();
        saveChangesButton.setOnClickListener((v) -> {
            /* Value change is tracked in TorrentService */
            pref.applyProxy(true);
            proxyChanged = false;
        });
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

        pref = RepositoryHelper.getSettingsRepository(getActivity().getApplicationContext());

        boolean enableAdvancedSettings;

        String keyProxyType = getString(R.string.pref_key_proxy_type);
        ListPreference proxyType = findPreference(keyProxyType);
        if (proxyType != null) {
            int type = pref.proxyType();
            proxyType.setValueIndex(type);
            enableAdvancedSettings = type != Integer.parseInt(getString(R.string.pref_proxy_type_none_value));
            bindOnPreferenceChangeListener(proxyType);

            String keyAddress = getString(R.string.pref_key_proxy_address);
            EditTextPreference address = findPreference(keyAddress);
            if (address != null) {
                address.setEnabled(enableAdvancedSettings);
                String addressValue = pref.proxyAddress();
                address.setText(addressValue);
                address.setSummary(addressValue);
                bindOnPreferenceChangeListener(address);
            }

            String keyPort = getString(R.string.pref_key_proxy_port);
            EditTextPreference port = findPreference(keyPort);
            if (port != null) {
                port.setEnabled(enableAdvancedSettings);
                InputFilter[] portFilter = new InputFilter[] { InputFilterRange.PORT_FILTER };
                int portNumber = pref.proxyPort();
                String portValue = Integer.toString(portNumber);
                port.setOnBindEditTextListener((editText) -> editText.setFilters(portFilter));
                port.setSummary(portValue);
                port.setText(portValue);
                bindOnPreferenceChangeListener(port);
            }

            String keyProxyPeersToo = getString(R.string.pref_key_proxy_peers_too);
            SwitchPreferenceCompat proxyPeersToo = findPreference(keyProxyPeersToo);
            if (proxyPeersToo != null) {
                proxyPeersToo.setEnabled(enableAdvancedSettings);
                proxyPeersToo.setChecked(pref.proxyPeersToo());
                bindOnPreferenceChangeListener(proxyPeersToo);
            }

            String keyRequiresAuth = getString(R.string.pref_key_proxy_requires_auth);
            SwitchPreferenceCompat requiresAuth = findPreference(keyRequiresAuth);
            if (requiresAuth != null) {
                requiresAuth.setEnabled(enableAdvancedSettings);
                requiresAuth.setChecked(pref.proxyRequiresAuth());
                bindOnPreferenceChangeListener(requiresAuth);
            }
        }

        String keyLogin = getString(R.string.pref_key_proxy_login);
        EditTextPreference login = findPreference(keyLogin);
        if (login != null) {
            String loginValue = pref.proxyLogin();
            login.setText(loginValue);
            login.setSummary(loginValue);
            bindOnPreferenceChangeListener(login);
        }

        String keyPassword = getString(R.string.pref_key_proxy_password);
        EditTextPreference password = findPreference(keyPassword);
        if (password != null) {
            String passwordValue = pref.proxyPassword();
            password.setText(passwordValue);
            password.setOnBindEditTextListener(editText -> editText.setTransformationMethod(
                PasswordTransformationMethod.getInstance()));
            bindOnPreferenceChangeListener(password);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
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
        EditTextPreference address = findPreference(keyAddress);
        if (address != null)
            address.setEnabled(enable);

        String keyPort = getString(R.string.pref_key_proxy_port);
        EditTextPreference port = findPreference(keyPort);
        if (port != null)
            port.setEnabled(enable);

        String keyProxyPeersToo = getString(R.string.pref_key_proxy_peers_too);
        SwitchPreferenceCompat proxyPeersToo = findPreference(keyProxyPeersToo);
        if (proxyPeersToo != null)
            proxyPeersToo.setEnabled(enable);

        String keyRequiresAuth = getString(R.string.pref_key_proxy_requires_auth);
        SwitchPreferenceCompat requiresAuth = findPreference(keyRequiresAuth);
        if (requiresAuth != null)
            requiresAuth.setEnabled(enable);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        if (preference.getKey().equals(getString(R.string.pref_key_proxy_type))) {
            int type = Integer.parseInt((String)newValue);
            pref.proxyType(type);

            boolean enableAdvancedSettings = type != Integer.parseInt(getString(R.string.pref_proxy_type_none_value));
            enableOrDisablePreferences(enableAdvancedSettings);

        } else if (preference.getKey().equals(getString(R.string.pref_key_proxy_port))) {
            if (!TextUtils.isEmpty((String)newValue)) {
                int value = Integer.parseInt((String) newValue);
                pref.proxyPort(value);
                preference.setSummary(Integer.toString(value));
            }

        } else if (preference.getKey().equals(getString(R.string.pref_key_proxy_address))) {
            pref.proxyAddress((String)newValue);
            preference.setSummary((String)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_proxy_login))) {
            pref.proxyLogin((String)newValue);
            preference.setSummary((String)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_proxy_password))) {
           pref.proxyPassword((String)newValue);
            ((EditTextPreference)preference).setOnBindEditTextListener((editText) -> preference.setSummary(editText
                    .getTransformationMethod()
                    .getTransformation((String)newValue, editText).toString()));

        } else if (preference.getKey().equals(getString(R.string.pref_key_proxy_peers_too))) {
            pref.proxyPeersToo((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_proxy_requires_auth))) {
            pref.proxyRequiresAuth((boolean)newValue);
        }

        proxyChanged = true;

        return true;
    }
}
