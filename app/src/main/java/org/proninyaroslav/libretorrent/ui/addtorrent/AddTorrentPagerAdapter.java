/*
 * Copyright (C) 2018-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.addtorrent;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

public class AddTorrentPagerAdapter extends FragmentStateAdapter {
    @ViewPager2.OffscreenPageLimit
    public static final int NUM_FRAGMENTS = 2;

    public static final int INFO_FRAG_POS = 0;
    public static final int FILES_FRAG_POS = 1;

    public AddTorrentPagerAdapter(FragmentActivity fa) {
        super(fa);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
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
    public int getItemCount() {
        return NUM_FRAGMENTS;
    }
}
