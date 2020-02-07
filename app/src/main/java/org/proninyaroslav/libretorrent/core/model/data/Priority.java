/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model.data;

public enum Priority
{
    /*
     * piece or file is not downloaded at all
     */
    IGNORE(0),

    LOW(1),

    /*
     * higher than normal priority. Pieces are preferred over pieces with
     * the same availability, but not over pieces with lower availability
     */
    TWO(2),

    /*
     * pieces are as likely to be picked as partial pieces.
     */
    THREE(3),

    /*
     * pieces are preferred over partial pieces, but not over pieces with
     * lower availability
     */
    DEFAULT(4),

    FIVE(5),

    SIX(6),

    /*
     * maximum priority, availability is disregarded, the piece is
     * preferred over any other piece with lower priority
     */
    TOP_PRIORITY(7);

    Priority(int val)
    {
        this.val = val;
    }

    private final int val;

    public int value()
    {
        return val;
    }

    public static Priority fromValue(int value)
    {
        Priority[] enumValues = Priority.class.getEnumConstants();
        for (Priority ev : enumValues) {
            if (ev.value() == value)
                return ev;
        }
        throw new IllegalArgumentException("Invalid value");
    }
}
