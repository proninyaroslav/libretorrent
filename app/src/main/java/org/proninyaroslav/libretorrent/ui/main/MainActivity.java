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
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.receiver.NotificationReceiver;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;
import org.proninyaroslav.libretorrent.ui.PermissionDeniedDialog;
import org.proninyaroslav.libretorrent.ui.PermissionManager;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class MainActivity extends AppCompatActivity {
    private static final String TAG_PERM_DENIED_DIALOG = "perm_denied_dialog";

    public static final String ACTION_ADD_TORRENT_SHORTCUT = "org.proninyaroslav.libretorrent.ADD_TORRENT_SHORTCUT";

    private NavController navController;

    private MainViewModel viewModel;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private BaseAlertDialog.SharedViewModel dialogViewModel;

    private PermissionDeniedDialog permDeniedDialog;
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

        if (getIntent().getAction() != null &&
                getIntent().getAction().equals(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP)) {
            finish();
            return;
        }

        ViewModelProvider provider = new ViewModelProvider(this);
        viewModel = provider.get(MainViewModel.class);
        dialogViewModel = provider.get(BaseAlertDialog.SharedViewModel.class);

        FragmentManager fm = getSupportFragmentManager();
        permissionManager = new PermissionManager(this, new PermissionManager.Callback() {
            @Override
            public void onStorageResult(boolean isGranted, boolean shouldRequestStoragePermission) {
                if (!isGranted && shouldRequestStoragePermission) {
                    if (fm.findFragmentByTag(TAG_PERM_DENIED_DIALOG) == null) {
                        permDeniedDialog = PermissionDeniedDialog.newInstance();
                        FragmentTransaction ft = fm.beginTransaction();
                        ft.add(permDeniedDialog, TAG_PERM_DENIED_DIALOG);
                        ft.commitAllowingStateLoss();
                    }
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
        permDeniedDialog = (PermissionDeniedDialog) fm.findFragmentByTag(TAG_PERM_DENIED_DIALOG);

        if (!permissionManager.checkPermissions() && permDeniedDialog == null) {
            permissionManager.requestPermissions();
        }
        Utils.showManageAllFilesWarningDialog(getApplicationContext(), getSupportFragmentManager());

        setContentView(R.layout.activity_main);

        var navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navController.navigateUp();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeAlertDialog();
    }

    @Override
    protected void onStop() {
        super.onStop();

        disposables.clear();
    }

    @Override
    protected void onDestroy() {
        if (viewModel != null)
            viewModel.requestStopEngine();

        super.onDestroy();
    }

    private void subscribeAlertDialog() {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (event.dialogTag.equals(TAG_PERM_DENIED_DIALOG)) {
                        if (event.type != BaseAlertDialog.EventType.DIALOG_SHOWN) {
                            permDeniedDialog.dismiss();
                        }
                        if (event.type == BaseAlertDialog.EventType.NEGATIVE_BUTTON_CLICKED) {
                            permissionManager.requestPermissions();
                        }
                        if (event.type == BaseAlertDialog.EventType.POSITIVE_BUTTON_CLICKED) {
                            permissionManager.setDoNotAskStorage(true);
                        }
                    }
                });
        disposables.add(d);
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
