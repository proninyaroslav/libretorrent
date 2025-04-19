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

package org.proninyaroslav.libretorrent.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.transition.TransitionManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.motion.MotionUtils;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.navigationrail.NavigationRailView;
import com.google.android.material.transition.MaterialFade;

import org.proninyaroslav.libretorrent.FeedNavDirections;
import org.proninyaroslav.libretorrent.MainActivity;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class NavBarFragment extends Fragment {
    private static final String TAG = NavBarFragment.class.getSimpleName();

    private MainActivity activity;
    private NavController navController;
    private NavigationRailView navRail = null;
    private BottomNavigationView bottomNav = null;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private NavBarFragmentViewModel viewModel;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public void setNavRailHeaderView(@NonNull View view) {
        if (!isAdded() || navRail == null) {
            return;
        }
        animateHeaderTransition();
        navRail.addHeaderView(view);
    }

    public void removeNavRailHeaderView() {
        if (!isAdded() || navRail == null) {
            return;
        }
        animateHeaderTransition();
        navRail.removeHeaderView();
    }

    public void removeDrawerNavigationView() {
        if (!isAdded() || drawerLayout == null || navigationView == null) {
            return;
        }
        navigationView.removeAllViews();
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    @NonNull
    public DrawerLayout getDrawerLayout() {
        return drawerLayout;
    }

    @NonNull
    public NavigationView getNavigationView() {
        return navigationView;
    }

    private NavigationBarView getNavigationBarView() {
        return navRail == null ? bottomNav : navRail;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof MainActivity a) {
            activity = a;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_nav_bar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null) {
            activity = (MainActivity) requireActivity();
        }

        viewModel = new ViewModelProvider(this).get(NavBarFragmentViewModel.class);

        var navHostFragment = (NavHostFragment) getChildFragmentManager()
                .findFragmentById(R.id.nav_bar_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }

        drawerLayout = view.findViewById(R.id.drawer_layout);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        navigationView = view.findViewById(R.id.navigation_view);
        navRail = view.findViewById(R.id.navigation_rail);
        bottomNav = view.findViewById(R.id.bottom_navigation);
        if (navRail != null) {
            NavigationUI.setupWithNavController(navRail, navController);
        }
        if (bottomNav != null) {
            NavigationUI.setupWithNavController(bottomNav, navController);
        }

        activity.getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (navController == null || activity == null) {
                    return;
                }
                if (!navController.navigateUp() && !activity.getRootNavController().navigateUp()) {
                    activity.finish();
                }
            }
        });

        handleImplicitIntent();
    }

    private void handleImplicitIntent() {
        var i = activity.getIntent();
        Uri uri = null;
        // Implicit intent with path to torrent file, http, magnet link or RSS/Atom feed
        if (i.getData() != null) {
            uri = i.getData();
        } else if (i.hasExtra(Intent.EXTRA_TEXT)) {
            var text = i.getStringExtra(Intent.EXTRA_TEXT);
            uri = TextUtils.isEmpty(text) ? null : Uri.parse(text);
        } else if (i.hasExtra(Intent.EXTRA_STREAM) && i.getExtras() != null) {
            uri = (Uri) i.getExtras().get(Intent.EXTRA_STREAM);
        }
        if (uri != null) {
            handleUri(uri, i.getType());
            // Avoid looping
            activity.setIntent(new Intent());
        }
    }

    private void handleUri(Uri uri, @Nullable String mimeType) {
        if (mimeType != null && Utils.matchFeedMimeType(mimeType)
                || uri.getPath() != null && Utils.matchFeedFilePath(uri.getPath())) {
            var action = FeedNavDirections.actionAddFeedDialog(uri);
            navController.navigate(action);
        } else {
            var action = NavBarFragmentDirections.actionAddTorrent(uri);
            activity.getRootNavController().navigate(action);
        }
    }

    private void animateHeaderTransition() {
        var duration = MotionUtils.resolveThemeDuration(
                requireContext(),
                R.attr.motionDurationShort3,
                400
        );
        var fade = new MaterialFade();
        fade.setDuration(duration);
        TransitionManager.beginDelayedTransition(navRail, fade);
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeUnreadFeedsBadge();
    }

    private void subscribeUnreadFeedsBadge() {
        disposables.add(viewModel.observeUnreadFeedsCount()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((count) -> setBadge(R.id.feed_nav, count),
                        (Throwable t) -> Log.e(TAG, "Getting unread feed list error: " +
                                Log.getStackTraceString(t)))
        );
    }

    private void setBadge(@IdRes int menuId, int count) {
        var navBar = getNavigationBarView();
        if (navBar == null) {
            return;
        }
        var badge = navBar.getOrCreateBadge(menuId);
        if (count == 0) {
            badge.setVisible(false);
            badge.clearNumber();
        } else {
            badge.setVisible(true);
            badge.setNumber(count);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        disposables.clear();
    }
}