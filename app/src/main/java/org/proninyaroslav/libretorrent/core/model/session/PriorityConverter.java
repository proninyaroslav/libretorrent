/*
 * Copyright (C) 2019-2024 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model.session;

import android.util.SparseArray;

import androidx.annotation.NonNull;

import com.frostwire.jlibtorrent.swig.byte_vector;

import org.proninyaroslav.libretorrent.core.model.data.Priority;

class PriorityConverter
{
    private static final SparseArray<com.frostwire.jlibtorrent.Priority> PRIOR_TO_LIB_PRIOR = new SparseArray<>();
    static {
        PRIOR_TO_LIB_PRIOR.put(Priority.IGNORE.value(), com.frostwire.jlibtorrent.Priority.IGNORE);
        PRIOR_TO_LIB_PRIOR.put(Priority.LOW.value(), com.frostwire.jlibtorrent.Priority.NORMAL);
        PRIOR_TO_LIB_PRIOR.put(Priority.TWO.value(), com.frostwire.jlibtorrent.Priority.TWO);
        PRIOR_TO_LIB_PRIOR.put(Priority.THREE.value(), com.frostwire.jlibtorrent.Priority.THREE);
        PRIOR_TO_LIB_PRIOR.put(Priority.DEFAULT.value(), com.frostwire.jlibtorrent.Priority.FOUR);
        PRIOR_TO_LIB_PRIOR.put(Priority.FIVE.value(), com.frostwire.jlibtorrent.Priority.FIVE);
        PRIOR_TO_LIB_PRIOR.put(Priority.SIX.value(), com.frostwire.jlibtorrent.Priority.SIX);
        PRIOR_TO_LIB_PRIOR.put(Priority.TOP_PRIORITY.value(), com.frostwire.jlibtorrent.Priority.SEVEN);
    }

    private static final SparseArray<Priority> LIB_PRIOR_TO_PRIOR = new SparseArray<>();
    static {
        LIB_PRIOR_TO_PRIOR.put(com.frostwire.jlibtorrent.Priority.IGNORE.swig(), Priority.IGNORE);
        LIB_PRIOR_TO_PRIOR.put(com.frostwire.jlibtorrent.Priority.NORMAL.swig(), Priority.LOW);
        LIB_PRIOR_TO_PRIOR.put(com.frostwire.jlibtorrent.Priority.TWO.swig(), Priority.TWO);
        LIB_PRIOR_TO_PRIOR.put(com.frostwire.jlibtorrent.Priority.THREE.swig(), Priority.THREE);
        LIB_PRIOR_TO_PRIOR.put(com.frostwire.jlibtorrent.Priority.FOUR.swig(), Priority.DEFAULT);
        LIB_PRIOR_TO_PRIOR.put(com.frostwire.jlibtorrent.Priority.FIVE.swig(), Priority.FIVE);
        LIB_PRIOR_TO_PRIOR.put(com.frostwire.jlibtorrent.Priority.SIX.swig(), Priority.SIX);
        LIB_PRIOR_TO_PRIOR.put(com.frostwire.jlibtorrent.Priority.SEVEN.swig(), Priority.TOP_PRIORITY);
    }

    public static com.frostwire.jlibtorrent.Priority[] convert(@NonNull Priority[] priorities)
    {
        int n = priorities.length;
        com.frostwire.jlibtorrent.Priority[] p = new com.frostwire.jlibtorrent.Priority[n];
        for (int i = 0; i < n; i++) {
            Priority priority = priorities[i];
            if (priority == null) {
                p[i] = null;
                continue;
            }
            com.frostwire.jlibtorrent.Priority converted = PRIOR_TO_LIB_PRIOR.get(priority.value());
            if (converted == null)
                converted = com.frostwire.jlibtorrent.Priority.NORMAL;

            p[i] = converted;
        }

        return p;
    }

    public static Priority[] convert(@NonNull com.frostwire.jlibtorrent.Priority[] priorities)
    {
        int n = priorities.length;
        Priority[] p = new Priority[n];
        for (int i = 0; i < n; i++) {
            com.frostwire.jlibtorrent.Priority priority = priorities[i];
            if (priority == null) {
                p[i] = null;
                continue;
            }
            Priority converted = LIB_PRIOR_TO_PRIOR.get(priority.swig());
            if (converted == null)
                converted = Priority.DEFAULT;

            p[i] = converted;
        }

        return p;
    }

    public static Priority[] convert(@NonNull byte_vector v)
    {
        int size = v.size();
        var arr = new com.frostwire.jlibtorrent.Priority[size];
        for (int i = 0; i < size; i++) {
            arr[i] = com.frostwire.jlibtorrent.Priority.fromSwig(v.get(i));
        }
        return convert(arr);
    }

    public static com.frostwire.jlibtorrent.Priority convert(@NonNull Priority priority)
    {
        com.frostwire.jlibtorrent.Priority converted = PRIOR_TO_LIB_PRIOR.get(priority.value());
        if (converted == null)
            converted = com.frostwire.jlibtorrent.Priority.FOUR;

        return converted;
    }

    public static Priority convert(@NonNull com.frostwire.jlibtorrent.Priority priority)
    {
        Priority converted = LIB_PRIOR_TO_PRIOR.get(priority.swig());
        if (converted == null)
            converted = Priority.DEFAULT;

        return converted;
    }
}
