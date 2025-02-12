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

package org.proninyaroslav.libretorrent.ui.main;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.navigationrail.NavigationRailView;

import org.proninyaroslav.libretorrent.R;

public class NavBarFragment extends Fragment {
    private NavController navController;
    private NavigationRailView navRail = null;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    public void setNavRailHeaderView(@NonNull View view) {
        if (!isAdded()) {
            return;
        }
        if (navRail != null) {
            navRail.addHeaderView(view);
        }
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
        navigationView = view.findViewById(R.id.navigation_view);
        navRail = view.findViewById(R.id.navigation_rail);
        BottomNavigationView bottomNav = view.findViewById(R.id.bottom_navigation);
        if (navRail != null) {
            NavigationUI.setupWithNavController(navRail, navController);
        }
        if (bottomNav != null) {
            NavigationUI.setupWithNavController(bottomNav, navController);
        }
    }
}