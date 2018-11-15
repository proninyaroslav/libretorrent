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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.fragments.FragmentCallback;
import org.proninyaroslav.libretorrent.fragments.FeedFragment;

public class FeedActivity extends AppCompatActivity implements FragmentCallback
{
    @SuppressWarnings("unused")
    private static final String TAG = FeedActivity.class.getSimpleName();

    public static final String ACTION_ADD_CHANNEL_SHORTCUT = "org.proninyaroslav.libretorrent.ADD_CHANNEL_SHORTCUT";

    FeedFragment feedFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        setTheme(Utils.getAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_feed);
        Utils.showColoredStatusBar_KitKat(this);

        feedFragment = (FeedFragment)getSupportFragmentManager()
                .findFragmentById(R.id.feed_fragmentContainer);
    }

    @Override
    public void fragmentFinished(Intent intent, ResultCode code)
    {
        switch (code) {
            case BACK:
                if (feedFragment != null)
                    feedFragment.resetCurOpenFeed();
                break;
            case OK:
            case CANCEL:
                finish();
                break;
        }
    }

    @Override
    public void onBackPressed()
    {
        feedFragment.onBackPressed();
    }
}
