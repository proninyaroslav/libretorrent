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

package org.proninyaroslav.libretorrent.core.sorting;

public class BaseSorting
{
    public enum Direction
    {
        ASC, DESC;

        public static Direction fromValue(String value)
        {
            for (Direction direction : Direction.class.getEnumConstants()) {
                if (direction.toString().equalsIgnoreCase(value)) {
                    return direction;
                }
            }

            return Direction.ASC;
        }
    }

    public interface SortingColumnsInterface<F>
    {
        int compare(F item1, F item2, Direction direction);

        String name();
    }

    private Direction direction;
    private String columnName;

    public BaseSorting(String columnName, Direction direction)
    {
        this.direction = direction;
        this.columnName = columnName;
    }

    public Direction getDirection()
    {
        return direction;
    }

    public String getColumnName()
    {
        return columnName;
    }

    @Override
    public String toString()
    {
        return "BaseSorting{" +
                "direction=" + direction +
                ", columnName='" + columnName + '\'' +
                '}';
    }
}
