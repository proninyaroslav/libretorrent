/*
 * Copyright (C) 2016-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import org.proninyaroslav.libretorrent.core.model.data.TorrentInfo;

public class TorrentSorting extends BaseSorting
{
    public enum SortingColumns implements SortingColumnsInterface<TorrentInfo>
    {
        none {
            @Override
            public int compare(TorrentInfo item1, TorrentInfo item2,
                               Direction direction)
            {
                return 0;
            }
        },
        name {
            @Override
            public int compare(TorrentInfo item1, TorrentInfo item2,
                               Direction direction)
            {
                if (direction == Direction.ASC)
                    return item1.name.compareTo(item2.name);
                else
                    return item2.name.compareTo(item1.name);
            }
        },
        size {
            @Override
            public int compare(TorrentInfo item1, TorrentInfo item2,
                               Direction direction)
            {
                if (direction == Direction.ASC)
                    return Long.compare(item1.totalBytes, item2.totalBytes);
                else
                    return Long.compare(item2.totalBytes, item1.totalBytes);
            }
        },
        progress {
            @Override
            public int compare(TorrentInfo item1, TorrentInfo item2,
                               Direction direction)
            {
                if (direction == Direction.ASC)
                    return Integer.compare(item1.progress, item2.progress);
                else
                    return Integer.compare(item2.progress, item1.progress);
            }
        },
        ETA {
            @Override
            public int compare(TorrentInfo item1, TorrentInfo item2,
                               Direction direction)
            {
                if (direction == Direction.ASC)
                    return Long.compare(item1.ETA, item2.ETA);
                else
                    return Long.compare(item2.ETA, item1.ETA);
            }
        },
        peers {
            @Override
            public int compare(TorrentInfo item1, TorrentInfo item2,
                               Direction direction)
            {
                if (direction == Direction.ASC)
                    return Integer.compare(item1.peers, item2.peers);
                else
                    return Integer.compare(item2.peers, item1.peers);
            }
        },
        dateAdded {
            @Override
            public int compare(TorrentInfo item1, TorrentInfo item2,
                               Direction direction)
            {
                if (direction == Direction.ASC)
                    return Long.compare(item1.dateAdded, item2.dateAdded);
                else
                    return Long.compare(item2.dateAdded, item1.dateAdded);
            }
        };

        public static SortingColumns fromValue(String value)
        {
            for (SortingColumns column : SortingColumns.class.getEnumConstants())
                if (column.toString().equalsIgnoreCase(value))
                    return column;

            return SortingColumns.none;
        }
    }

    public TorrentSorting(SortingColumns columnName, Direction direction)
    {
        super(columnName.name(), direction);
    }
}
