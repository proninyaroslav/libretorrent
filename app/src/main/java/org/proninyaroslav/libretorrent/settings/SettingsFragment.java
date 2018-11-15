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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.takisoft.preferencex.PreferenceFragmentCompat;

import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;

public class SettingsFragment extends PreferenceFragmentCompat
{
    @SuppressWarnings("unused")
    private static final String TAG = SettingsFragment.class.getSimpleName();

    private Callback callback;

    public interface Callback
    {
        void onDetailTitleChanged(String title);
    }

    public static SettingsFragment newInstance()
    {
        SettingsFragment fragment = new SettingsFragment();

        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);

        if (context instanceof Callback)
            callback = (Callback) context;
    }

    @Override
    public void onDetach()
    {
        super.onDetach();

        callback = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            setFragment(AppearanceSettingsFragment.newInstance(),
                    getString(R.string.pref_header_appearance));
        }

        Preference appearance = findPreference(AppearanceSettingsFragment.class.getSimpleName());
        appearance.setOnPreferenceClickListener((Preference preference) -> {
            if (Utils.isLargeScreenDevice(getActivity().getApplicationContext())) {
                setFragment(AppearanceSettingsFragment.newInstance(),
                        getString(R.string.pref_header_appearance));
            } else {
                startActivity(AppearanceSettingsFragment.class,
                        getString(R.string.pref_header_appearance));
            }

            return true;
        });

        Preference behavior = findPreference(BehaviorSettingsFragment.class.getSimpleName());
        behavior.setOnPreferenceClickListener((Preference preference) -> {
            if (Utils.isLargeScreenDevice(getActivity().getApplicationContext())) {
                setFragment(BehaviorSettingsFragment.newInstance(),
                        getString(R.string.pref_header_behavior));
            } else {
                startActivity(BehaviorSettingsFragment.class,
                        getString(R.string.pref_header_behavior));
            }

            return true;
        });

        Preference storage = findPreference(StorageSettingsFragment.class.getSimpleName());
        storage.setOnPreferenceClickListener((Preference preference) -> {
            if (Utils.isLargeScreenDevice(getActivity().getApplicationContext())) {
                setFragment(StorageSettingsFragment.newInstance(),
                        getString(R.string.pref_header_storage));
            } else {
                startActivity(StorageSettingsFragment.class,
                        getString(R.string.pref_header_storage));
            }

            return true;
        });

        Preference limitations = findPreference(LimitationsSettingsFragment.class.getSimpleName());
        limitations.setOnPreferenceClickListener((Preference preference) -> {
            if (Utils.isLargeScreenDevice(getActivity().getApplicationContext())) {
                setFragment(LimitationsSettingsFragment.newInstance(),
                        getString(R.string.pref_header_limitations));
            } else {
                startActivity(LimitationsSettingsFragment.class,
                        getString(R.string.pref_header_limitations));
            }

            return true;
        });

        Preference network = findPreference(NetworkSettingsFragment.class.getSimpleName());
        network.setOnPreferenceClickListener((Preference preference) -> {
            if (Utils.isLargeScreenDevice(getActivity().getApplicationContext())) {
                setFragment(NetworkSettingsFragment.newInstance(),
                        getString(R.string.pref_header_network));
            } else {
                startActivity(NetworkSettingsFragment.class,
                        getString(R.string.pref_header_network));
            }

            return true;
        });

        Preference scheduling = findPreference(SchedulingSettingsFragment.class.getSimpleName());
        scheduling.setOnPreferenceClickListener((Preference preference) -> {
            if (Utils.isLargeScreenDevice(getActivity().getApplicationContext())) {
                setFragment(SchedulingSettingsFragment.newInstance(),
                        getString(R.string.pref_header_scheduling));
            } else {
                startActivity(SchedulingSettingsFragment.class,
                        getString(R.string.pref_header_scheduling));
            }

            return true;
        });

        Preference feed = findPreference(FeedSettingsFragment.class.getSimpleName());
        feed.setOnPreferenceClickListener((Preference preference) -> {
            if (Utils.isLargeScreenDevice(getActivity().getApplicationContext())) {
                setFragment(FeedSettingsFragment.newInstance(),
                        getString(R.string.pref_header_feed));
            } else {
                startActivity(FeedSettingsFragment.class,
                        getString(R.string.pref_header_feed));
            }

            return true;
        });

        Preference streaming = findPreference(StreamingSettingsFragment.class.getSimpleName());
        streaming.setOnPreferenceClickListener((Preference preference) -> {
            if (Utils.isLargeScreenDevice(getActivity().getApplicationContext())) {
                setFragment(StreamingSettingsFragment.newInstance(),
                        getString(R.string.pref_header_streaming));
            } else {
                startActivity(StreamingSettingsFragment.class,
                        getString(R.string.pref_header_streaming));
            }

            return true;
        });
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_headers, rootKey);
    }

    private <F extends PreferenceFragmentCompat> void setFragment(F fragment, String title)
    {
        if (Utils.isLargeScreenDevice(getActivity().getApplicationContext())) {
            if (callback != null)
                callback.onDetailTitleChanged(title);

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
}
