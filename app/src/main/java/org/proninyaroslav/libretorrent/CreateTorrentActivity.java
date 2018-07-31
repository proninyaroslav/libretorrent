/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.support.v7.app.AppCompatActivity;

import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.fragments.CreateTorrentFragment;
import org.proninyaroslav.libretorrent.fragments.FragmentCallback;

/*
 * The dialog for adding torrent. The parent window.
 */

public class CreateTorrentActivity extends AppCompatActivity
        implements FragmentCallback
{

    @SuppressWarnings("unused")
    private static final String TAG = CreateTorrentActivity.class.getSimpleName();

    public static final String TAG_CREATED_TORRENT = "created_torrent";

    private CreateTorrentFragment createTorrentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setTheme(Utils.getAppTheme(getApplicationContext()));
        setContentView(R.layout.activity_create_torrent);

        createTorrentFragment = (CreateTorrentFragment)getFragmentManager()
                .findFragmentById(R.id.create_torrent_fragmentContainer);
    }

    @Override
    public void fragmentFinished(Intent intent, ResultCode code)
    {
        if (code == ResultCode.OK)
            setResult(RESULT_OK, intent);
        else if (code == ResultCode.BACK)
            finish();
        else if (code == ResultCode.CANCEL)
            setResult(RESULT_CANCELED, intent);

        finish();
    }

    @Override
    public void onBackPressed()
    {
        createTorrentFragment.onBackPressed();
    }
}
