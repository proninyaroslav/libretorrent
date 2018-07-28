/*
 * Copyright (C) 2016, 2017 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.app.FragmentManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.fragments.DetailTorrentFragment;
import org.proninyaroslav.libretorrent.fragments.MainFragment;
import org.proninyaroslav.libretorrent.fragments.FragmentCallback;
import org.proninyaroslav.libretorrent.receivers.NotificationReceiver;
import org.proninyaroslav.libretorrent.services.TorrentTaskService;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity
        implements
        FragmentCallback,
        DetailTorrentFragment.Callback
{
    @SuppressWarnings("unused")
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String TAG_PERM_DIALOG_IS_SHOW = "perm_dialog_is_show";
    public static final String ACTION_ADD_TORRENT_SHORTCUT = "org.proninyaroslav.libretorrent.ADD_TORRENT_SHORTCUT";

    private boolean permDialogIsShow = false;
    MainFragment mainFragment;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setTheme(Utils.getAppTheme(getApplicationContext()));
        if (getIntent().getAction() != null &&
                getIntent().getAction().equals(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP)) {
            finish();

            return;
        }

        if (savedInstanceState != null) {
            permDialogIsShow = savedInstanceState.getBoolean(TAG_PERM_DIALOG_IS_SHOW);
        }

        if (!Utils.checkStoragePermission(getApplicationContext()) && !permDialogIsShow) {
            permDialogIsShow = true;
            startActivity(new Intent(this, RequestPermissions.class));
        }

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

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        SharedPreferences pref = SettingsManager.getPreferences(this);
        if (isFinishing() && !pref.getBoolean(getString(R.string.pref_key_keep_alive),
                                              SettingsManager.Default.keepAlive)) {
            Intent i = new Intent(getApplicationContext(), TorrentTaskService.class);
            i.setAction(TorrentTaskService.ACTION_SHUTDOWN);
            startService(i);
        }
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
                Intent i = new Intent(getApplicationContext(), TorrentTaskService.class);
                i.setAction(TorrentTaskService.ACTION_SHUTDOWN);
                startService(i);
                finish();
                break;
            case CANCEL:
            case BACK:
                if (mainFragment != null)
                    mainFragment.resetCurOpenTorrent();
                break;
        }
    }
}
