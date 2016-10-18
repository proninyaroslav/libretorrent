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

package org.proninyaroslav.libretorrent;

import android.Manifest;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.dialogs.BaseAlertDialog;
import org.proninyaroslav.libretorrent.fragments.DetailTorrentFragment;
import org.proninyaroslav.libretorrent.fragments.MainFragment;
import org.proninyaroslav.libretorrent.fragments.FragmentCallback;
import org.proninyaroslav.libretorrent.services.TorrentTaskService;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity
        implements
        BaseAlertDialog.OnClickListener,
        FragmentCallback,
        DetailTorrentFragment.Callback
{
    @SuppressWarnings("unused")
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String TAG_PERM_DIALOG = "perm_dialog";
    private static final String TAG_PERM_DIALOG_IS_SHOW = "perm_dialog_is_show";
    private static final String TAG_ACTION_SHUTDOWN = "action_shutdown";

    public static boolean permIsGranted = false;
    private boolean permDialogIsShow = false;
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    MainFragment mainFragment;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if (getIntent().getAction() != null && getIntent().getAction().equals(TAG_ACTION_SHUTDOWN)) {
            finish();

            return;
        }

        if (savedInstanceState != null) {
            permDialogIsShow = savedInstanceState.getBoolean(TAG_PERM_DIALOG_IS_SHOW);
        }

        verifyStoragePermissions(this);

        SettingsManager.initPreferences(getApplicationContext());
        startService(new Intent(this, TorrentTaskService.class));

        setContentView(R.layout.activity_main);

        Utils.showColoredStatusBar_KitKat(this);

        FragmentManager fm = getFragmentManager();
        mainFragment = (MainFragment) fm.findFragmentById(R.id.main_fragmentContainer);
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);

        outState.putBoolean(TAG_PERM_DIALOG_IS_SHOW, permDialogIsShow);
    }

    /*
     * Changed in detail fragment.
     */

    @Override
    public void onTorrentInfoChanged()
    {
        if (mainFragment == null) {
            return;
        }

        DetailTorrentFragment fragment = mainFragment.getCurrentDetailFragment();
        if (fragment != null) {
            fragment.onTorrentInfoChanged();
        }
    }

    @Override
    public void onTorrentInfoChangesUndone()
    {
        if (mainFragment == null) {
            return;
        }

        DetailTorrentFragment fragment = mainFragment.getCurrentDetailFragment();
        if (fragment != null) {
            fragment.onTorrentInfoChangesUndone();
        }
    }

    @Override
    public void onTorrentFilesChanged()
    {
        if (mainFragment == null) {
            return;
        }

        DetailTorrentFragment fragment = mainFragment.getCurrentDetailFragment();
        if (fragment != null) {
            fragment.onTorrentFilesChanged();
        }
    }

    @Override
    public void onTrackersChanged(ArrayList<String> trackers, boolean replace)
    {
        if (mainFragment == null) {
            return;
        }

        DetailTorrentFragment fragment = mainFragment.getCurrentDetailFragment();
        if (fragment != null) {
            fragment.onTrackersChanged(trackers, replace);
        }
    }

    @Override
    public void openFile(String relativePath)
    {
        if (mainFragment == null) {
            return;
        }

        DetailTorrentFragment fragment = mainFragment.getCurrentDetailFragment();
        if (fragment != null) {
            fragment.openFile(relativePath);
        }
    }

    @Override
    public void fragmentFinished(Intent intent, ResultCode code)
    {
        switch (code) {
            case OK:
                finish();
                break;
            case CANCEL:
            case BACK:
                if (mainFragment != null) {
                    mainFragment.resetCurOpenTorrent();
                }
                break;
            case SHUTDOWN:
                Intent i = new Intent(getApplicationContext(), MainActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                i.setAction(TAG_ACTION_SHUTDOWN);
                startActivity(i);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults)
    {
        switch (requestCode) {
            case REQUEST_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permIsGranted = true;
                } else {
                    permIsGranted = false;

                    if (getFragmentManager().findFragmentByTag(TAG_PERM_DIALOG) == null) {
                        BaseAlertDialog permDialog = BaseAlertDialog.newInstance(
                                getString(R.string.perm_denied_title),
                                getString(R.string.perm_denied_warning),
                                0,
                                getString(R.string.yes),
                                getString(R.string.no),
                                null,
                                R.style.BaseTheme_Dialog,
                                this);

                        permDialog.show(getFragmentManager(), TAG_PERM_DIALOG);
                    }
                }

                break;
            }
        }
    }

    /*
     * Permission dialog.
     */

    @Override
    public void onPositiveClicked(@Nullable View v)
    {
        /* Nothing */
    }

    @Override
    public void onNegativeClicked(@Nullable View v)
    {
        ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
    }

    @Override
    public void onNeutralClicked(@Nullable View v)
    {
        /* Nothing */
    }

    public void verifyStoragePermissions(Activity activity)
    {
        /* Prevents duplication permission dialog */
        if (permDialogIsShow) {
            return;
        }

        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            permDialogIsShow = true;
            ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
        } else {
            permIsGranted = true;
        }
    }
}
