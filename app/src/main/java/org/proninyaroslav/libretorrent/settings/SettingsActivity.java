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

package org.proninyaroslav.libretorrent.settings;

import android.os.Bundle;
import android.widget.TextView;

import org.proninyaroslav.libretorrent.R;

public class SettingsActivity extends BasePreferenceActivity implements SettingsFragment.Callback
{
    @SuppressWarnings("unused")
    private static final String TAG = SettingsActivity.class.getSimpleName();

    private static final String TAG_TITLE = "title";

    private TextView detailTitle;
    private String title;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.settings));

        detailTitle = findViewById(R.id.detail_title);

        if (savedInstanceState == null) {
            setFragment(SettingsFragment.newInstance());
        } else {
            title = savedInstanceState.getString(TAG_TITLE);
            if (title != null && detailTitle != null) {
                detailTitle.setText(title);
            }
        }
    }

    @Override
    public void onDetailTitleChanged(String title)
    {
        this.title = title;

        if (detailTitle != null && title != null) {
            detailTitle.setText(title);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        outState.putString(TAG_TITLE, title);

        super.onSaveInstanceState(outState);
    }

}
