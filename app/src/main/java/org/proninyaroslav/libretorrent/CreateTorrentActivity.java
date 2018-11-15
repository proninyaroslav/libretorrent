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
import androidx.appcompat.app.AppCompatActivity;

import org.greenrobot.eventbus.EventBus;
import org.proninyaroslav.libretorrent.core.AddTorrentParams;
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
        setTheme(Utils.getAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_create_torrent);

        resetResult();

        createTorrentFragment = (CreateTorrentFragment)getSupportFragmentManager()
                .findFragmentById(R.id.create_torrent_fragmentContainer);
    }

    public static void setResult(AddTorrentParams params)
    {
        if (params == null)
            return;
        EventBus.getDefault().postSticky(params);
    }

    public static AddTorrentParams getResult()
    {
        return EventBus.getDefault().removeStickyEvent(AddTorrentParams.class);
    }

    public static void resetResult()
    {
        getResult();
    }

    @Override
    public void fragmentFinished(Intent intent, ResultCode code)
    {
        /*
         * Transfer of result will be done only across EventBus sticky event, not intent.
         * This is necessary to add large torrents.
         */
        resetResult();
        if (code == ResultCode.OK) {
            setResult(intent.getParcelableExtra(TAG_CREATED_TORRENT));
            setResult(RESULT_OK, new Intent());
        } else if (code == ResultCode.BACK) {
            finish();
        } else if (code == ResultCode.CANCEL) {
            setResult(RESULT_CANCELED, intent);
        }

        finish();
    }

    @Override
    public void onBackPressed()
    {
        createTorrentFragment.onBackPressed();
    }
}
