/*
 * Copyright (C) 2016, 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;

import android.os.Parcelable;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/*
 * The abstract adapter for tabs in activity (based on PagerAdapter).
 */

public abstract class ViewPagerAdapter extends PagerAdapter
{
    @SuppressWarnings("unused")
    private static final String TAG = ViewPagerAdapter.class.getSimpleName();

    protected SparseArray<Fragment> registeredFragments = new SparseArray<>();
    protected List<String> fragmentTitleList = new ArrayList<>();
    private FragmentManager fm;
    private FragmentTransaction curTransaction = null;
    private Fragment currentPrimaryItem = null;
    private String baseTag;

    public ViewPagerAdapter(String baseTag, FragmentManager fm)
    {
        this.fm = fm;
        this.baseTag = baseTag;
    }

    public abstract Fragment getItem(int position);

    public abstract int getCount();

    public Fragment getFragment(int position)
    {
        return registeredFragments.get(position);
    }

    @Override
    public void startUpdate(@NonNull ViewGroup container) {
        if (container.getId() == View.NO_ID)
            throw new IllegalStateException("ViewPager with adapter " + this
                    + " requires a view id");
    }

    @Override
    public CharSequence getPageTitle(int position)
    {
        if (position < 0 || position >= getCount())
            return null;

        return fragmentTitleList.get(position);
    }

    @NonNull
    @Override
    public Object instantiateItem(ViewGroup container, int position)
    {
        if (position < 0 || position >= getCount())
            return null;

        if (curTransaction == null)
            curTransaction = fm.beginTransaction();

        String name = makeFragmentName(container.getId(), position);
        Fragment f = fm.findFragmentByTag(name);
        if (f != null) {
            curTransaction.attach(f);
        } else {
            f = getItem(position);
            curTransaction.add(container.getId(), f,
                    makeFragmentName(container.getId(), position));
        }
        if (f != currentPrimaryItem) {
            f.setMenuVisibility(false);
            f.setUserVisibleHint(false);
        }
        registeredFragments.put(position, f);

        return f;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object)
    {
        if (position < 0 || position >= getCount())
            return;

        if (curTransaction == null)
            curTransaction = fm.beginTransaction();
        curTransaction.detach((Fragment)object);
        registeredFragments.remove(position);
    }

    public void clearFragments()
    {
        if (curTransaction == null)
            curTransaction = fm.beginTransaction();

        for (int pos = 0; pos < getCount(); pos++) {
            Fragment f = registeredFragments.get(pos);
            if (f == null)
                continue;
            registeredFragments.remove(pos);
            curTransaction.remove(f);
        }
        try {
            curTransaction.commitAllowingStateLoss();

        } catch (IllegalStateException e) {
            /* Ignore */
        }
    }

    @Override
    public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object)
    {
        Fragment f = (Fragment)object;
        if (f == currentPrimaryItem)
            return;

        if (currentPrimaryItem != null) {
            currentPrimaryItem.setMenuVisibility(false);
            currentPrimaryItem.setUserVisibleHint(false);
        }
        f.setMenuVisibility(true);
        f.setUserVisibleHint(true);
        currentPrimaryItem = f;
    }

    @Override
    public void finishUpdate(@NonNull ViewGroup container)
    {
        if (curTransaction == null)
            return;
        try {
            curTransaction.commitNowAllowingStateLoss();

        } catch (IllegalStateException e) {
            /* Ignore */
        }
        curTransaction = null;
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object)
    {
        return ((Fragment)object).getView() == view;
    }

    @Override
    public Parcelable saveState()
    {
        return null;
    }

    @Override
    public void restoreState(Parcelable state, ClassLoader loader) { }

    private String makeFragmentName(int viewId, int position)
    {
        return "android:switcher:" + baseTag + ":" + viewId + ":" + position;
    }
}
