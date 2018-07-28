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

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.fragments.DetailTorrentFragment;
import org.proninyaroslav.libretorrent.fragments.FragmentCallback;

import java.util.ArrayList;

public class DetailTorrentActivity extends AppCompatActivity
        implements
        DetailTorrentFragment.Callback,
        FragmentCallback
{
    @SuppressWarnings("unused")
    private static final String TAG = DetailTorrentActivity.class.getSimpleName();

    public static final String TAG_TORRENT_ID = "torrent_id";

    private DetailTorrentFragment detailTorrentFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setTheme(Utils.getAppTheme(getApplicationContext()));
        if (Utils.isTwoPane(getApplicationContext())) {
            finish();

            return;
        }

        setContentView(R.layout.activity_detail_torrent);

        detailTorrentFragment = (DetailTorrentFragment) getFragmentManager()
                .findFragmentById(R.id.detail_torrent_fragmentContainer);

        String id = getIntent().getStringExtra(TAG_TORRENT_ID);

        detailTorrentFragment.setTorrentId(id);
    }

    @Override
    public void onTorrentInfoChanged()
    {
        if (detailTorrentFragment != null) {
            detailTorrentFragment.onTorrentInfoChanged();
        }
    }

    @Override
    public void onTorrentInfoChangesUndone()
    {
        if (detailTorrentFragment != null) {
            detailTorrentFragment.onTorrentInfoChangesUndone();
        }
    }

    @Override
    public void onTorrentFilesChanged()
    {
        if (detailTorrentFragment != null) {
            detailTorrentFragment.onTorrentFilesChanged();
        }
    }

    @Override
    public void onTrackersChanged(ArrayList<String> trackers, boolean replace)
    {
        if (detailTorrentFragment != null) {
            detailTorrentFragment.onTrackersChanged(trackers, replace);
        }
    }

    @Override
    public void openFile(String relativePath)
    {
        if (detailTorrentFragment != null) {
            detailTorrentFragment.openFile(relativePath);
        }
    }

    @Override
    public void fragmentFinished(Intent intent, ResultCode code)
    {
        finish();
    }

    @Override
    public void onBackPressed()
    {
        detailTorrentFragment.onBackPressed();
    }
}
