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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.takisoft.preferencex.PreferenceFragmentCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;

public class SettingsFragment extends PreferenceFragmentCompat
{
    @SuppressWarnings("unused")
    private static final String TAG = SettingsFragment.class.getSimpleName();

    private static final String AppearanceSettings = "AppearanceSettingsFragment";
    private static final String BehaviorSettings = "BehaviorSettingsFragment";
    private static final String NetworkSettings = "NetworkSettingsFragment";
    private static final String StorageSettings = "StorageSettingsFragment";
    private static final String LimitationsSettings = "LimitationsSettingsFragment";
    private static final String SchedulingSettings = "SchedulingSettingsFragment";
    private static final String FeedSettings = "FeedSettingsFragment";
    private static final String StreamingSettings = "StreamingSettingsFragment";

    private Callback callback;
    private AppCompatActivity activity;

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

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity)context;
        if (context instanceof Callback)
            callback = (Callback)context;
    }

    @Override
    public void onDetach()
    {
        super.onDetach();

        callback = null;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        if (Utils.isTwoPane(activity)) {
            Fragment f = activity.getSupportFragmentManager()
                    .findFragmentById(R.id.detail_fragment_container);
            if (f == null)
                setFragment(AppearanceSettingsFragment.newInstance(),
                        getString(R.string.pref_header_appearance));
        }

        Preference appearance = findPreference(AppearanceSettingsFragment.class.getSimpleName());
        appearance.setOnPreferenceClickListener(prefClickListener);

        Preference behavior = findPreference(BehaviorSettingsFragment.class.getSimpleName());
        behavior.setOnPreferenceClickListener(prefClickListener);

        Preference storage = findPreference(StorageSettingsFragment.class.getSimpleName());
        storage.setOnPreferenceClickListener(prefClickListener);

        Preference limitations = findPreference(LimitationsSettingsFragment.class.getSimpleName());
        limitations.setOnPreferenceClickListener(prefClickListener);

        Preference network = findPreference(NetworkSettingsFragment.class.getSimpleName());
        network.setOnPreferenceClickListener(prefClickListener);

        Preference scheduling = findPreference(SchedulingSettingsFragment.class.getSimpleName());
        scheduling.setOnPreferenceClickListener(prefClickListener);

        Preference feed = findPreference(FeedSettingsFragment.class.getSimpleName());
        feed.setOnPreferenceClickListener(prefClickListener);

        Preference streaming = findPreference(StreamingSettingsFragment.class.getSimpleName());
        streaming.setOnPreferenceClickListener(prefClickListener);
    }

    private Preference.OnPreferenceClickListener prefClickListener = (Preference preference) ->
    {
        openPreference(preference.getKey());

        return true;
    };

    private void openPreference(String prefName)
    {
        switch (prefName) {
            case AppearanceSettings:
                if (Utils.isLargeScreenDevice(getActivity())) {
                    setFragment(AppearanceSettingsFragment.newInstance(),
                            getString(R.string.pref_header_appearance));
                } else {
                    startActivity(AppearanceSettingsFragment.class,
                            getString(R.string.pref_header_appearance));
                }
                break;
            case BehaviorSettings:
                if (Utils.isLargeScreenDevice(getActivity())) {
                    setFragment(BehaviorSettingsFragment.newInstance(),
                            getString(R.string.pref_header_behavior));
                } else {
                    startActivity(BehaviorSettingsFragment.class,
                            getString(R.string.pref_header_behavior));
                }
                break;
            case NetworkSettings:
                if (Utils.isLargeScreenDevice(getActivity())) {
                    setFragment(NetworkSettingsFragment.newInstance(),
                            getString(R.string.pref_header_network));
                } else {
                    startActivity(NetworkSettingsFragment.class,
                            getString(R.string.pref_header_network));
                }
                break;
            case StorageSettings:
                if (Utils.isLargeScreenDevice(getActivity())) {
                    setFragment(StorageSettingsFragment.newInstance(),
                            getString(R.string.pref_header_storage));
                } else {
                    startActivity(StorageSettingsFragment.class,
                            getString(R.string.pref_header_storage));
                }
                break;
            case LimitationsSettings:
                if (Utils.isLargeScreenDevice(getActivity())) {
                    setFragment(LimitationsSettingsFragment.newInstance(),
                            getString(R.string.pref_header_limitations));
                } else {
                    startActivity(LimitationsSettingsFragment.class,
                            getString(R.string.pref_header_limitations));
                }
                break;
            case SchedulingSettings:
                if (Utils.isLargeScreenDevice(getActivity())) {
                    setFragment(SchedulingSettingsFragment.newInstance(),
                            getString(R.string.pref_header_scheduling));
                } else {
                    startActivity(SchedulingSettingsFragment.class,
                            getString(R.string.pref_header_scheduling));
                }
                break;
            case FeedSettings:
                if (Utils.isLargeScreenDevice(getActivity())) {
                    setFragment(FeedSettingsFragment.newInstance(),
                            getString(R.string.pref_header_feed));
                } else {
                    startActivity(FeedSettingsFragment.class,
                            getString(R.string.pref_header_feed));
                }
                break;
            case StreamingSettings:
                if (Utils.isLargeScreenDevice(getActivity())) {
                    setFragment(StreamingSettingsFragment.newInstance(),
                            getString(R.string.pref_header_streaming));
                } else {
                    startActivity(StreamingSettingsFragment.class,
                            getString(R.string.pref_header_streaming));
                }
                break;
        }
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_headers, rootKey);
    }

    private <F extends PreferenceFragmentCompat> void setFragment(F fragment, String title)
    {
        if (Utils.isLargeScreenDevice(activity)) {
            if (callback != null)
                callback.onDetailTitleChanged(title);

            activity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.detail_fragment_container, fragment)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();
        }
    }

    private <F extends PreferenceFragmentCompat> void startActivity(Class<F> fragment, String title)
    {
        Intent i = new Intent(activity, PreferenceActivity.class);
        PreferenceActivityConfig config = new PreferenceActivityConfig(
                fragment.getSimpleName(),
                title);

        i.putExtra(PreferenceActivity.TAG_CONFIG, config);
        startActivity(i);
    }
}
