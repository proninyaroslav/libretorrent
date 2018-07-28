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
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.fragments.FeedItemsFragment;
import org.proninyaroslav.libretorrent.fragments.FragmentCallback;

public class FeedItemsActivity extends AppCompatActivity
        implements FragmentCallback
{
    @SuppressWarnings("unused")
    private static final String TAG = FeedItemsActivity.class.getSimpleName();

    public static final String TAG_FEED_URL = "feed_url";

    private FeedItemsFragment feedItemsFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setTheme(Utils.getAppTheme(getApplicationContext()));
        if (Utils.isTwoPane(getApplicationContext())) {
            finish();

            return;
        }

        setContentView(R.layout.activity_feed_items);

        feedItemsFragment = (FeedItemsFragment)getFragmentManager()
                .findFragmentById(R.id.feed_items_fragmentContainer);

        String feedUrl = getIntent().getStringExtra(TAG_FEED_URL);

        feedItemsFragment.setFeedUrl(feedUrl);
    }

    @Override
    public void fragmentFinished(Intent intent, ResultCode code)
    {
        finish();
    }

    @Override
    public void onBackPressed()
    {
        feedItemsFragment.onBackPressed();
    }
}
