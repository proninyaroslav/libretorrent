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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import org.proninyaroslav.libretorrent.core.utils.old.Utils;
import org.proninyaroslav.libretorrent.fragments.DetailTorrentFragment;
import org.proninyaroslav.libretorrent.fragments.FragmentCallback;

public class DetailTorrentActivity extends AppCompatActivity
        implements FragmentCallback
{
    @SuppressWarnings("unused")
    private static final String TAG = DetailTorrentActivity.class.getSimpleName();

    public static final String TAG_TORRENT_ID = "torrent_id";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        setTheme(Utils.getAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        if (Utils.isTwoPane(this)) {
            finish();
            return;
        }

        setContentView(R.layout.activity_detail_torrent);

        DetailTorrentFragment detailTorrentFragment = (DetailTorrentFragment)getSupportFragmentManager()
                .findFragmentById(R.id.detail_torrent_fragmentContainer);

        if (detailTorrentFragment != null)
            detailTorrentFragment.setTorrentId(getIntent().getStringExtra(TAG_TORRENT_ID));
    }

    @Override
    public void onFragmentFinished(@NonNull Fragment f, Intent intent,
                                   @NonNull ResultCode code)
    {
        finish();
    }
}
