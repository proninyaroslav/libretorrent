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

package org.proninyaroslav.libretorrent.adapters;

import android.content.Context;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.fragments.AddTorrentFilesFragment;
import org.proninyaroslav.libretorrent.fragments.AddTorrentInfoFragment;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public class AddTorrentPagerAdapter extends ViewPagerAdapter
{
    public static final int NUM_FRAGMENTS = 2;
    public static final int INFO_FRAG_POS = 0;
    public static final int FILES_FRAG_POS = 1;

    public AddTorrentPagerAdapter(FragmentManager fm, Context context)
    {
        super(null, fm);

        fragmentTitleList.add(context.getString(R.string.torrent_info));
        fragmentTitleList.add(context.getString(R.string.torrent_files));
    }

    @Override
    public Fragment getItem(int position)
    {
        switch (position) {
            case INFO_FRAG_POS:
                return AddTorrentInfoFragment.newInstance();
            case FILES_FRAG_POS:
                return AddTorrentFilesFragment.newInstance();
            default:
                return null;
        }
    }

    @Override
    public int getCount()
    {
        return NUM_FRAGMENTS;
    }
}
