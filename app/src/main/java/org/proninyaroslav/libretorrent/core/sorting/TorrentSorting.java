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

package org.proninyaroslav.libretorrent.core.sorting;

import org.proninyaroslav.libretorrent.adapters.TorrentListItem;

public class TorrentSorting extends BaseSorting
{
    public enum SortingColumns implements SortingColumnsInterface<TorrentListItem>
    {
        none {
            @Override
            public int compare(TorrentListItem item1,
                               TorrentListItem item2, Direction direction)
            {
                return 0;
            }
        },
        name {
            @Override
            public int compare(TorrentListItem item1,
                               TorrentListItem item2, Direction direction)
            {
                if (direction == Direction.ASC)
                    return item1.name.compareTo(item2.name);
                else
                    return item2.name.compareTo(item1.name);
            }
        },
        size {
            @Override
            public int compare(TorrentListItem item1,
                               TorrentListItem item2, Direction direction)
            {
                if (direction == Direction.ASC)
                    return Long.valueOf(item2.totalBytes).compareTo(item1.totalBytes);
                else
                    return Long.valueOf(item1.totalBytes).compareTo(item2.totalBytes);
            }
        },
        progress {
            @Override
            public int compare(TorrentListItem item1,
                               TorrentListItem item2, Direction direction)
            {
                if (direction == Direction.ASC)
                    return Integer.valueOf(item2.progress).compareTo(item1.progress);
                else
                    return Integer.valueOf(item1.progress).compareTo(item2.progress);
            }
        },
        ETA {
            @Override
            public int compare(TorrentListItem item1,
                               TorrentListItem item2, Direction direction)
            {
                if (direction == Direction.ASC)
                    return Long.valueOf(item2.ETA).compareTo(item1.ETA);
                else
                    return Long.valueOf(item1.ETA).compareTo(item2.ETA);
            }
        },
        peers {
            @Override
            public int compare(TorrentListItem item1,
                               TorrentListItem item2, Direction direction)
            {
                if (direction == Direction.ASC)
                    return Integer.valueOf(item2.peers).compareTo(item1.peers);
                else
                    return Integer.valueOf(item1.peers).compareTo(item2.peers);
            }
        },
        dateAdded {
            @Override
            public int compare(TorrentListItem item1,
                               TorrentListItem item2, Direction direction)
            {
                if (direction == Direction.ASC) {
                    return Long.valueOf(item2.dateAdded).compareTo(item1.dateAdded);
                } else {
                    return Long.valueOf(item1.dateAdded).compareTo(item2.dateAdded);
                }
            }
        };

        public static String[] valuesToStringArray()
        {
            SortingColumns[] values = SortingColumns.class.getEnumConstants();
            String[] arr = new String[values.length];

            for (int i = 0; i < values.length; i++){
                arr[i] = values[i].toString();
            }

            return arr;
        }

        public static SortingColumns fromValue(String value)
        {
            for (SortingColumns column : SortingColumns.class.getEnumConstants()) {
                if (column.toString().equalsIgnoreCase(value)) {
                    return column;
                }
            }

            return SortingColumns.none;
        }
    }

    public TorrentSorting(SortingColumns columnName, Direction direction)
    {
        super(columnName.name(), direction);
    }

    public TorrentSorting()
    {
        this(SortingColumns.name , Direction.DESC);
    }
}
