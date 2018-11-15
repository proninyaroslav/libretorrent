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

package org.proninyaroslav.libretorrent.fragments;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.HashMap;

/*
 * A neat trick to avoid TransactionTooLargeException while saving instance state
 */

public class HeavyInstanceStorage extends Fragment
{
    private static final String TAG = HeavyInstanceStorage.class.getSimpleName();

    private HashMap<String, Bundle> bundles = new HashMap<>();

    public HeavyInstanceStorage()
    {
        super();

        setRetainInstance(true);
    }

    public HeavyInstanceStorage pushData(String key, Bundle instanceState)
    {
        if (key == null)
            throw new IllegalArgumentException("key is null");

        Bundle b = bundles.get(key);
        if (b == null)
            bundles.put(key, instanceState);
        else
            b.putAll(instanceState);

        return this;
    }

    public Bundle popData(String key)
    {
        if (key == null)
            throw new IllegalArgumentException("key is null");

        return bundles.remove(key);
    }

    public static HeavyInstanceStorage getInstance(FragmentManager fragmentManager)
    {
        if (fragmentManager == null)
            return null;

        HeavyInstanceStorage out = (HeavyInstanceStorage)fragmentManager.findFragmentByTag(TAG);

        if (out == null) {
            out = new HeavyInstanceStorage();
            fragmentManager.beginTransaction().add(out, TAG).commitAllowingStateLoss();
        }

        return out;
    }
}