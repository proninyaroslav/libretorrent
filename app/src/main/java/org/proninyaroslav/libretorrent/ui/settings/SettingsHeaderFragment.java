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

package org.proninyaroslav.libretorrent.ui.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceScreen;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.FragmentPreferenceBinding;

public class SettingsHeaderFragment extends CustomPreferenceFragment {
    private static final String TAG = SettingsHeaderFragment.class.getSimpleName();

    private static final String TAG_CUR_OPEN_ITEM_KEY = "cur_open_item_key";

    public static final String KEY_OPEN_REFERENCE_REQUEST = TAG + "_open_preference";
    public static final String KEY_RESULT_PREFERENCE_KEY = "preference_key";

    private AppCompatActivity activity;
    private SettingsHeaderAdapter adapter;
    private String curOpenItemKey;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity a) {
            activity = a;
        }
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null) {
            curOpenItemKey = savedInstanceState.getString(TAG_CUR_OPEN_ITEM_KEY);
        }
        if (curOpenItemKey != null) {
            markAsOpen(curOpenItemKey);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(TAG_CUR_OPEN_ITEM_KEY, curOpenItemKey);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.pref_headers);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        var binding = FragmentPreferenceBinding.bind(view);
        binding.appBar.setTitle(R.string.settings);
        binding.appBar.setNavigationIcon(null);

        if (activity == null) {
            activity = (AppCompatActivity) requireActivity();
        }

        for (var i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            var preference = getPreferenceScreen().getPreference(i);
            preference.setOnPreferenceClickListener((p) -> {
                markAsOpen(p.getKey());
                var bundle = new Bundle();
                bundle.putString(KEY_RESULT_PREFERENCE_KEY, p.getKey());
                getParentFragmentManager().setFragmentResult(KEY_OPEN_REFERENCE_REQUEST, bundle);
                return true;
            });
        }

        super.onViewCreated(view, savedInstanceState);
    }

    @NonNull
    @Override
    protected SettingsHeaderAdapter onCreateAdapter(@NonNull PreferenceScreen preferenceScreen) {
        adapter = new SettingsHeaderAdapter(preferenceScreen);
        adapter.setSelectable(Utils.isTwoPane(activity));
        return adapter;
    }

    private void markAsOpen(String key) {
        curOpenItemKey = key;
        if (adapter != null) {
            adapter.markAsOpen(key);
        }
    }
}
