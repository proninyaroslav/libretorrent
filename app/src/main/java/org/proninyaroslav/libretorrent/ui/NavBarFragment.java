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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.transition.TransitionManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.motion.MotionUtils;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.navigationrail.NavigationRailView;
import com.google.android.material.transition.MaterialFade;
import com.google.common.collect.Lists;

import org.proninyaroslav.libretorrent.R;

public class NavBarFragment extends Fragment {
    private NavController navController;
    private NavigationRailView navRail = null;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

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

    @NonNull
    public DrawerLayout getDrawerLayout() {
        return drawerLayout;
    }

    @NonNull
    public NavigationView getNavigationView() {
        return navigationView;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_nav_bar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        var navHostFragment = (NavHostFragment) getChildFragmentManager()
                .findFragmentById(R.id.nav_bar_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }

        drawerLayout = view.findViewById(R.id.drawer_layout);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        navigationView = view.findViewById(R.id.navigation_view);
        navRail = view.findViewById(R.id.navigation_rail);
        BottomNavigationView bottomNav = view.findViewById(R.id.bottom_navigation);
        if (navRail != null) {
            NavigationUI.setupWithNavController(navRail, navController);
            navRail.setOnItemSelectedListener(this::onNavigationItemSelected);
        }
        if (bottomNav != null) {
            NavigationUI.setupWithNavController(bottomNav, navController);
            bottomNav.setOnItemSelectedListener(this::onNavigationItemSelected);
        }
    }

    private boolean matchDestination(NavDestination dest, @IdRes int destId) {
        if (dest == null) {
            return false;
        } else {
            return NavigationUI.matchDestination$navigation_ui_release(dest, destId);
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

    private boolean onNavigationItemSelected(MenuItem item) {
        var prevDest = navController.getCurrentDestination();
        var isNavigated = NavigationUI.onNavDestinationSelected(item, navController);
        if (isNavigated && !matchDestination(prevDest, item.getItemId())) {
            removeNavRailHeaderView();
            navigationView.removeAllViews();
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }
        return isNavigated;
    }
}