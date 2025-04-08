/*
 * Copyright (C) 2018-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

public class AddTorrentPagerAdapter extends FragmentStateAdapter {
    @ViewPager2.OffscreenPageLimit
    public static final int NUM_FRAGMENTS = 2;

    public enum Page {
        INFO(0),
        FILES(1);

        public final int position;

        Page(int position) {
            this.position = position;
        }

        public static Page fromPosition(int position) {
            var enumValues = Page.class.getEnumConstants();
            if (enumValues == null) {
                throw new IllegalArgumentException("Unknown position: " + position);
            }
            for (var ev : enumValues) {
                if (ev.position == position) {
                    return ev;
                }
            }
            throw new IllegalArgumentException("Unknown position: " + position);
        }
    }

    public AddTorrentPagerAdapter(Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return switch (Page.fromPosition(position)) {
            case INFO -> AddTorrentInfoFragment.newInstance();
            case FILES -> AddTorrentFilesFragment.newInstance();
        };
    }

    @Override
    public int getItemCount() {
        return NUM_FRAGMENTS;
    }
}
