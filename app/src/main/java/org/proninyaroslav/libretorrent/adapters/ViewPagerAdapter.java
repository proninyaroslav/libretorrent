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

package org.proninyaroslav.libretorrent.adapters;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.util.SparseArray;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/*
 * The adapter for tabs in activity.
 */

public class ViewPagerAdapter extends FragmentStatePagerAdapter
{
    @SuppressWarnings("unused")
    private static final String TAG = ViewPagerAdapter.class.getSimpleName();

    private SparseArray<Fragment> fragmentList = new SparseArray<>();
    private List<String> fragmentTitleList = new ArrayList<>();

    public ViewPagerAdapter(FragmentManager fm)
    {
        super(fm);
    }

    public void addFragment(Fragment fragment, int position, String title)
    {
        fragmentList.put(position, fragment);
        fragmentTitleList.add(title);
    }

    @Override
    public Fragment getItem(int position)
    {
        if (position < 0) {
            return new Fragment();
        }

        return fragmentList.get(position);
    }

    @Override
    public CharSequence getPageTitle(int position)
    {
        if (position < 0) {
            return "";
        }

        return fragmentTitleList.get(position);
    }

    @Override
    public int getCount()
    {
        return fragmentList.size();
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position)
    {
        Fragment fragment = (Fragment) super.instantiateItem(container, position);
        fragmentList.put(position, fragment);

        return fragment;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object)
    {
        fragmentList.remove(position);

        super.destroyItem(container, position, object);
    }
}
