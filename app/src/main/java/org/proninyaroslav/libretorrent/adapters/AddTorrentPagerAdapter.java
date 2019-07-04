/*
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

public class AddTorrentPagerAdapter extends FragmentStatePagerAdapter
{
    public static final int NUM_FRAGMENTS = 2;
    public static final int INFO_FRAG_POS = 0;
    public static final int FILES_FRAG_POS = 1;

    private Context context;

    public AddTorrentPagerAdapter(Context context, FragmentManager fm)
    {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        this.context = context;
    }

    @Override
    public CharSequence getPageTitle(int position)
    {
        switch (position) {
            case INFO_FRAG_POS:
                return context.getString(R.string.torrent_info);
            case FILES_FRAG_POS:
                return context.getString(R.string.torrent_files);
            default:
                return null;
        }
    }

    @NonNull
    @Override
    public Fragment getItem(int position)
    {
        switch (position) {
            case INFO_FRAG_POS:
                return AddTorrentInfoFragment.newInstance();
            case FILES_FRAG_POS:
                return AddTorrentFilesFragment.newInstance();
            default:
                return new Fragment();
        }
    }

    @Override
    public int getCount()
    {
        return NUM_FRAGMENTS;
    }
}
