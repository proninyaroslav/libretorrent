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
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.InputFilterRange;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.ui.settings.CustomPreferenceFragment;
import org.proninyaroslav.libretorrent.ui.settings.customprefs.SliderPreference;

public class I2PSettingsFragment extends CustomPreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private AppCompatActivity activity;
    private SettingsRepository pref;
    private boolean i2pChanged = false;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity a) {
            activity = a;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) requireActivity();
        }

        binding.appBar.setTitle(R.string.pref_i2p_settings_title);
        binding.fab.setIconResource(R.drawable.ic_save_24px);
        binding.fab.setContentDescription(getString(R.string.save));
        binding.fab.setText(R.string.save);
        binding.fab.setOnClickListener((v) -> {
            /* Value change is tracked in TorrentService */
            pref.applyI2p(true);
            i2pChanged = false;
        });
        binding.fab.show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (i2pChanged) {
            Toast.makeText(
                    activity,
                    R.string.i2p_settings_apply_after_reboot,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pref = RepositoryHelper.getSettingsRepository(activity.getApplicationContext());

        String keyI2pEnabled = getString(R.string.pref_key_i2p_enabled);
        SwitchPreferenceCompat i2pEnabled = findPreference(keyI2pEnabled);
        if (i2pEnabled != null) {
            i2pEnabled.setChecked(pref.i2pEnabled());
            bindOnPreferenceChangeListener(i2pEnabled);
        }

        String keyHostname = getString(R.string.pref_key_i2p_hostname);
        EditTextPreference hostname = findPreference(keyHostname);
        if (hostname != null) {
            String hostnameValue = pref.i2pHostname();
            hostname.setText(hostnameValue);
            hostname.setSummary(hostnameValue);
            bindOnPreferenceChangeListener(hostname);
        }

        String keyPort = getString(R.string.pref_key_i2p_port);
        EditTextPreference port = findPreference(keyPort);
        if (port != null) {
            InputFilter[] portFilter = new InputFilter[]{InputFilterRange.PORT_FILTER};
            int portNumber = pref.i2pPort();
            String portValue = Integer.toString(portNumber);
            port.setOnBindEditTextListener((editText) -> editText.setFilters(portFilter));
            port.setSummary(portValue);
            port.setText(portValue);
            bindOnPreferenceChangeListener(port);
        }

        String keyAllowMixed = getString(R.string.pref_key_i2p_allow_mixed);
        SwitchPreferenceCompat allowMixed = findPreference(keyAllowMixed);
        if (allowMixed != null) {
            allowMixed.setChecked(pref.allowI2pMixed());
            bindOnPreferenceChangeListener(allowMixed);
        }

        String keyInboundQuantity = getString(R.string.pref_key_i2p_inbound_quantity);
        SliderPreference inboundQuantity = findPreference(keyInboundQuantity);
        if (inboundQuantity != null) {
            inboundQuantity.setValue((float) pref.i2pInboundQuantity());
            bindOnPreferenceChangeListener(inboundQuantity);
        }

        String keyOutboundQuantity = getString(R.string.pref_key_i2p_outbound_quantity);
        SliderPreference outboundQuantity = findPreference(keyOutboundQuantity);
        if (outboundQuantity != null) {
            outboundQuantity.setValue((float) pref.i2pOutboundQuantity());
            bindOnPreferenceChangeListener(outboundQuantity);
        }

        String keyInboundLength = getString(R.string.pref_key_i2p_inbound_length);
        SliderPreference inboundLength = findPreference(keyInboundLength);
        if (inboundLength != null) {
            inboundLength.setValue((float) pref.i2pInboundLength());
            bindOnPreferenceChangeListener(inboundLength);
        }

        String keyOutboundLength = getString(R.string.pref_key_i2p_outbound_length);
        SliderPreference outboundLength = findPreference(keyOutboundLength);
        if (outboundLength != null) {
            outboundLength.setValue((float) pref.i2pOutboundLength());
            bindOnPreferenceChangeListener(outboundLength);
        }

        String keyInboundLengthVariance = getString(R.string.pref_key_i2p_inbound_length_variance);
        SliderPreference inboundLengthVariance = findPreference(keyInboundLengthVariance);
        if (inboundLengthVariance != null) {
            inboundLengthVariance.setValue((float) pref.i2pInboundLengthVariance());
            bindOnPreferenceChangeListener(inboundLengthVariance);
        }

        String keyOutboundLengthVariance = getString(R.string.pref_key_i2p_outbound_length_variance);
        SliderPreference outboundLengthVariance = findPreference(keyOutboundLengthVariance);
        if (outboundLengthVariance != null) {
            outboundLengthVariance.setValue((float) pref.i2pOutboundLengthVariance());
            bindOnPreferenceChangeListener(outboundLengthVariance);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_i2p, rootKey);
    }

    private void bindOnPreferenceChangeListener(Preference preference) {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.getKey().equals(getString(R.string.pref_key_i2p_enabled))) {
            pref.i2pEnabled((boolean) newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_i2p_hostname))) {
            pref.i2pHostname((String) newValue);
            preference.setSummary((String) newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_i2p_port))) {
            if (!TextUtils.isEmpty((String) newValue)) {
                int value = Integer.parseInt((String) newValue);
                pref.i2pPort(value);
                preference.setSummary(Integer.toString(value));
            }

        } else if (preference.getKey().equals(getString(R.string.pref_key_i2p_allow_mixed))) {
            pref.allowI2pMixed((boolean) newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_i2p_inbound_quantity))) {
            pref.i2pInboundQuantity(((Number) newValue).intValue());

        } else if (preference.getKey().equals(getString(R.string.pref_key_i2p_outbound_quantity))) {
            pref.i2pOutboundQuantity(((Number) newValue).intValue());

        } else if (preference.getKey().equals(getString(R.string.pref_key_i2p_inbound_length))) {
            pref.i2pInboundLength(((Number) newValue).intValue());

        } else if (preference.getKey().equals(getString(R.string.pref_key_i2p_outbound_length))) {
            pref.i2pOutboundLength(((Number) newValue).intValue());

        } else if (preference.getKey().equals(getString(R.string.pref_key_i2p_inbound_length_variance))) {
            pref.i2pInboundLengthVariance(((Number) newValue).intValue());

        } else if (preference.getKey().equals(getString(R.string.pref_key_i2p_outbound_length_variance))) {
            pref.i2pOutboundLengthVariance(((Number) newValue).intValue());
        }

        i2pChanged = true;

        return true;
    }
}
