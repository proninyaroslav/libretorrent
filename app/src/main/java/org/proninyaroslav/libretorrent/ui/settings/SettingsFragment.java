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

package org.proninyaroslav.libretorrent.ui.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavDirections;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.AbstractListDetailFragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.slidingpanelayout.widget.SlidingPaneLayout;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.databinding.FragmentSettingsHeaderBinding;
import org.proninyaroslav.libretorrent.ui.settings.pages.AppearanceSettingsFragmentDirections;
import org.proninyaroslav.libretorrent.ui.settings.pages.BehaviorSettingsFragmentDirections;
import org.proninyaroslav.libretorrent.ui.settings.pages.FeedSettingsFragmentDirections;
import org.proninyaroslav.libretorrent.ui.settings.pages.LimitationsSettingsFragmentDirections;
import org.proninyaroslav.libretorrent.ui.settings.pages.NetworkSettingsFragmentDirections;
import org.proninyaroslav.libretorrent.ui.settings.pages.SchedulingSettingsFragmentDirections;
import org.proninyaroslav.libretorrent.ui.settings.pages.StorageSettingsFragmentDirections;
import org.proninyaroslav.libretorrent.ui.settings.pages.StreamingSettingsFragmentDirections;

public class SettingsFragment extends AbstractListDetailFragment {
    private AppCompatActivity activity;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity a) {
            activity = a;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setSettingsHeaderListener();
    }

    private void setSettingsHeaderListener() {
        getChildFragmentManager().setFragmentResultListener(
                SettingsHeaderFragment.KEY_OPEN_REFERENCE_REQUEST,
                this,
                (requestKey, result) -> {
                    var key = result.getString(SettingsHeaderFragment.KEY_RESULT_PREFERENCE_KEY);
                    if (key != null) {
                        openPreference(key);
                    }
                }
        );
    }

    @NonNull
    @Override
    public View onCreateListPaneView(@NonNull LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
        var binding = FragmentSettingsHeaderBinding.inflate(layoutInflater, viewGroup, false);
        return binding.getRoot();
    }

    @Override
    public void onListPaneViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onListPaneViewCreated(view, savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) requireActivity();
        }

        getSlidingPaneLayout().setLockMode(SlidingPaneLayout.LOCK_MODE_LOCKED);

        activity.getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), onBackPressedCallback);
    }

    private final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            var detailsNavController = getDetailPaneNavHostFragment().getNavController();
            var prevEntry = detailsNavController.getPreviousBackStackEntry();
            var startDestId = detailsNavController.getGraph().getStartDestinationId();

            if (prevEntry == null || prevEntry.getDestination().getId() == startDestId) {
                if (getSlidingPaneLayout().isOpen() && getSlidingPaneLayout().isSlideable()) {
                    getSlidingPaneLayout().closePane();
                } else {
                    setEnabled(false);
                    activity.getOnBackPressedDispatcher().onBackPressed();
                }
            } else {
                detailsNavController.navigateUp();
            }
        }
    };

    @NonNull
    @Override
    public NavHostFragment onCreateDetailPaneNavHostFragment() {
        return NavHostFragment.create(R.navigation.settings_two_pane_graph);
    }

    private void openPreference(String key) {
        var navController = getDetailPaneNavHostFragment().getNavController();
        var slidingPaneLayout = getSlidingPaneLayout();

        NavDirections action;
        if (key.equals(getString(R.string.pref_key_appearance_settings))) {
            action = AppearanceSettingsFragmentDirections.actionAppearanceSettings();
        } else if (key.equals(getString(R.string.pref_key_behavior_settings))) {
            action = BehaviorSettingsFragmentDirections.actionBehaviorSettings();
        } else if (key.equals(getString(R.string.pref_key_network_settings))) {
            action = NetworkSettingsFragmentDirections.actionNetworkSettings();
        } else if (key.equals(getString(R.string.pref_key_storage_settings))) {
            action = StorageSettingsFragmentDirections.actionStorageSettings();
        } else if (key.equals(getString(R.string.pref_key_limitations_settings))) {
            action = LimitationsSettingsFragmentDirections.actionLimitationSettings();
        } else if (key.equals(getString(R.string.pref_key_scheduling_settings))) {
            action = SchedulingSettingsFragmentDirections.actionSchedulingSettings();
        } else if (key.equals(getString(R.string.pref_key_feed_settings))) {
            action = FeedSettingsFragmentDirections.actionFeedSettings();
        } else if (key.equals(getString(R.string.pref_key_streaming_settings))) {
            action = StreamingSettingsFragmentDirections.actionStreamingSettings();
        } else {
            throw new IllegalArgumentException("Unknown preference key: " + key);
        }

        // Clear back stack
        if (getDetailPaneNavHostFragment().getChildFragmentManager().getBackStackEntryCount() > 0) {
            navController.popBackStack();
        }

        var options = new NavOptions.Builder();
        setDetailsNavAnimation(options);
        navController.navigate(action, options.build());
        slidingPaneLayout.open();
    }

    private void setDetailsNavAnimation(NavOptions.Builder options) {
        var slidingPaneLayout = getSlidingPaneLayout();
        if (slidingPaneLayout.isOpen()) {
            options.setEnterAnim(R.anim.nav_slide_enter_anim)
                    .setExitAnim(R.anim.nav_fade_exit_anim);
        }
    }
}
