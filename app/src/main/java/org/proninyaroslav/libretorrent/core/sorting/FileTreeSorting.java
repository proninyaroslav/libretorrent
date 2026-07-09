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

import org.proninyaroslav.libretorrent.core.model.filetree.FileTree;

import java.util.Objects;

public class FileTreeSorting extends BaseSorting
{
    public enum SortingColumns implements SortingColumnsInterface<FileTree<?>>
    {
        name {
            @Override
            public int compare(FileTree<?> item1, FileTree<?> item2,
                               Direction direction)
            {
                if (direction == Direction.ASC)
                    return item1.getName().compareToIgnoreCase(item2.getName());
                else
                    return item2.getName().compareToIgnoreCase(item1.getName());
            }
        },
        size {
            @Override
            public int compare(FileTree<?> item1, FileTree<?> item2,
                               Direction direction)
            {
                if (direction == Direction.ASC)
                    return Long.compare(item1.size(), item2.size());
                else
                    return Long.compare(item2.size(), item1.size());
            }
        };

        public static SortingColumns fromValue(String value)
        {
            for (SortingColumns column : Objects.requireNonNull(SortingColumns.class.getEnumConstants()))
                if (column.toString().equalsIgnoreCase(value))
                    return column;

            return SortingColumns.name;
        }
    }

    public FileTreeSorting(SortingColumns columnName, Direction direction)
    {
        super(columnName.name(), direction);
    }
}
