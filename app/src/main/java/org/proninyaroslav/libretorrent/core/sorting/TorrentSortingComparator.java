/*
 * Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import java.util.Comparator;

public record TorrentSortingComparator(TorrentSorting sorting) implements Comparator<TorrentInfo> {
    @Override
    public int compare(TorrentInfo s1, TorrentInfo s2) {
        return TorrentSorting.SortingColumns
                .fromValue(sorting.getColumnName())
                .compare(s1, s2, sorting.getDirection());
    }
}
