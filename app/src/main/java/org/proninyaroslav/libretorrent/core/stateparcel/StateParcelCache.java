/*
 * Copyright (C) 2016, 2017 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.stateparcel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Caching packages, based on AbstractStateParcel,
 * thereby preventing duplicate packages.
 */

public class StateParcelCache<T extends AbstractStateParcel>
{
    private ConcurrentHashMap<String, T> cache = new ConcurrentHashMap<>();

    public void put(T state)
    {
        if (state == null) {
            return;
        }

        cache.put(state.parcelId, state);
    }

    public void putAll(Collection<T> states)
    {
        if (states == null) {
            return;
        }

        for (T state : states) {
            cache.put(state.parcelId, state);
        }
    }

    public void remove(String parcelId)
    {
        cache.remove(parcelId);
    }

    public void removeAll(Collection<T> states)
    {
        Set<String> keys = new HashSet<>(states.size());

        for (T state : states) {
            keys.add(state.parcelId);
        }

        cache.keySet().removeAll(keys);
    }

    public T get(String key)
    {
        return cache.get(key);
    }

    public List<T> getAll()
    {
        return new ArrayList<>(cache.values());
    }

    public boolean contains(String parcelId)
    {
        return cache.containsKey(parcelId);
    }

    public boolean contains(T state)
    {
        return cache.containsValue(state);
    }

    public boolean containsAll(List<T> states)
    {
        return cache.values().containsAll(states);
    }

    public void clear()
    {
        cache.clear();
    }

    public int size()
    {
        return cache.size();
    }

    @Override
    public String toString()
    {
        return "StateParcelCache{" +
                "cache=" + cache +
                '}';
    }
}
