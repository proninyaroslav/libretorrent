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
    public static final String ACTION_ADD_TORRENT_SHORTCUT = "org.proninyaroslav.libretorrent.ADD_TORRENT_SHORTCUT";

    private NavController navController;
    private MainViewModel viewModel;
    private PermissionManager permissionManager;

    @NonNull
    public NavController getRootNavController() {
        return navController;
    }

    @Nullable
    public NavBarFragment getNavBarFragment(@NonNull Fragment fragment) {
        if (fragment.getParentFragment() instanceof NavHostFragment navHost) {
            if (navHost.getParentFragment() instanceof NavBarFragment) {
                return (NavBarFragment) navHost.getParentFragment();
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
                PermissionDeniedDialog.KEY_RESULT,
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
        viewModel = provider.get(MainViewModel.class);
        permissionManager = new PermissionManager(this, new PermissionManager.Callback() {
            @Override
            public void onStorageResult(boolean isGranted, boolean shouldRequestStoragePermission) {
                if (!isGranted && shouldRequestStoragePermission) {
                    var action = NavBarFragmentDirections.actionPermissionDeniedDialog();
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
                navController.navigateUp();
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

    // TODO
//    private void subscribeMsgViewModel() {
//        disposables.add(msgViewModel.observeTorrentDetailsOpened()
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(this::showDetailTorrent));
//
//        disposables.add(msgViewModel.observeTorrentDetailsClosed()
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe((__) -> showBlankFragment()));
//
//        disposables.add(viewModel.observeTorrentsDeleted()
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe((id) -> {
//                    DetailTorrentFragment f = getCurrentDetailFragment();
//                    if (f != null && id.equals(f.getTorrentId()))
//                        showBlankFragment();
//                }));
//    }


//    private void cleanGarbageFragments() {
//        /* Clean detail and blank fragments after rotate for tablets */
//        if (Utils.isLargeScreenDevice(this)) {
//            FragmentManager fm = getSupportFragmentManager();
//            List<Fragment> fragments = fm.getFragments();
//            FragmentTransaction ft = fm.beginTransaction();
//            for (Fragment f : fragments)
//                if (f instanceof DetailTorrentFragment || f instanceof BlankFragment)
//                    ft.remove(f);
//            ft.commitAllowingStateLoss();
//        }
//    }
//
//    private void showDetailTorrent(String id) {
//        if (Utils.isTwoPane(this)) {
//            FragmentManager fm = getSupportFragmentManager();
//            DetailTorrentFragment detail = DetailTorrentFragment.newInstance(id);
//            Fragment fragment = fm.findFragmentById(R.id.detail_torrent_fragmentContainer);
//
//            if (fragment instanceof DetailTorrentFragment) {
//                String oldId = ((DetailTorrentFragment) fragment).getTorrentId();
//                if (id.equals(oldId))
//                    return;
//            }
//            fm.beginTransaction()
//                    .replace(R.id.detail_torrent_fragmentContainer, detail)
//                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
//                    .commit();
//
//        } else {
//            Intent i = new Intent(this, DetailTorrentActivity.class);
//            i.putExtra(DetailTorrentActivity.TAG_TORRENT_ID, id);
//            startActivity(i);
//        }
//    }
//
//    private void showBlankFragment() {
//        if (Utils.isTwoPane(this)) {
//            FragmentManager fm = getSupportFragmentManager();
//            BlankFragment blank = BlankFragment.newInstance(getString(R.string.select_or_add_torrent));
//            fm.beginTransaction()
//                    .replace(R.id.detail_torrent_fragmentContainer, blank)
//                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
//                    .commitAllowingStateLoss();
//        }
//    }
//
//    public DetailTorrentFragment getCurrentDetailFragment() {
//        if (!Utils.isTwoPane(this))
//            return null;
//
//        FragmentManager fm = getSupportFragmentManager();
//        Fragment fragment = fm.findFragmentById(R.id.detail_torrent_fragmentContainer);
//
//        return (fragment instanceof DetailTorrentFragment ? (DetailTorrentFragment) fragment : null);
//    }
//
//    @Override
//    public void onFragmentFinished(@NonNull Fragment f, Intent intent,
//                                   @NonNull ResultCode code) {
//        if (f instanceof DetailTorrentFragment && Utils.isTwoPane(this))
//            msgViewModel.torrentDetailsClosed();
//    }
}
