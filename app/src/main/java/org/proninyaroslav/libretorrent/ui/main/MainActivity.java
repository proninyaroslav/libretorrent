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

package org.proninyaroslav.libretorrent.ui.main;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.receiver.NotificationReceiver;
import org.proninyaroslav.libretorrent.ui.PermissionDeniedDialog;
import org.proninyaroslav.libretorrent.ui.PermissionManager;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String KEY_PERMISSION_DENIED_DIALOG_REQUEST = TAG + "_permission_denied";

    public static final String ACTION_ADD_TORRENT_SHORTCUT = "org.proninyaroslav.libretorrent.ADD_TORRENT_SHORTCUT";
    public static final String ACTION_OPEN_TORRENT_DETAILS = "org.proninyaroslav.libretorrent.ACTION_OPEN_TORRENT_DETAILS";
    public static final String KEY_TORRENT_ID = "torrent_id";

    private NavController navController;
    private HomeViewModel viewModel;
    private PermissionManager permissionManager;

    @NonNull
    public NavController getRootNavController() {
        return navController;
    }

    @Nullable
    public NavBarFragment findNavBarFragment(@NonNull Fragment fragment) {
        var currentFragment = fragment;
        while (currentFragment != null) {
            if (currentFragment instanceof NavBarFragment navBar) {
                return navBar;
            } else {
                currentFragment = currentFragment.getParentFragment();
            }
        }
        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Utils.enableEdgeToEdge(this);

        var intentAction = getIntent().getAction();
        if (intentAction != null && intentAction.equals(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP)) {
            finish();
            return;
        }

        getSupportFragmentManager().setFragmentResultListener(
                KEY_PERMISSION_DENIED_DIALOG_REQUEST,
                this,
                (requestKey, result) -> {
                    var resultValue = (PermissionDeniedDialog.Result)
                            result.getSerializable(PermissionDeniedDialog.KEY_RESULT_VALUE);
                    if (resultValue != null) {
                        switch (resultValue) {
                            case RETRY -> permissionManager.requestPermissions();
                            case DENIED -> permissionManager.setDoNotAskStorage(true);
                        }
                    }
                }
        );

        ViewModelProvider provider = new ViewModelProvider(this);
        viewModel = provider.get(HomeViewModel.class);
        permissionManager = new PermissionManager(this, new PermissionManager.Callback() {
            @Override
            public void onStorageResult(boolean isGranted, boolean shouldRequestStoragePermission) {
                if (!isGranted && shouldRequestStoragePermission) {
                    var action = NavBarFragmentDirections
                            .actionPermissionDeniedDialog(KEY_PERMISSION_DENIED_DIALOG_REQUEST);
                    navController.navigate(action);
                }
            }

            @Override
            public void onNotificationResult(boolean isGranted, boolean shouldRequestNotificationPermission) {
                permissionManager.setDoNotAskNotifications(!isGranted);
                if (isGranted) {
                    viewModel.restartForegroundNotification();
                }
            }
        });

        FragmentManager fm = getSupportFragmentManager();
        Utils.showManageAllFilesWarningDialog(getApplicationContext(), fm);

        setContentView(R.layout.activity_main);

        var navHostFragment = (NavHostFragment) fm.findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }

        var dest = navController.getCurrentDestination();
        var dialogExists = dest != null && dest.getId() == R.id.permissionDeniedDialog;
        if (!permissionManager.checkPermissions() && !dialogExists) {
            permissionManager.requestPermissions();
        }

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!navController.navigateUp()) {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (viewModel != null) {
            viewModel.requestStopEngine();
        }

        super.onDestroy();
    }
}
