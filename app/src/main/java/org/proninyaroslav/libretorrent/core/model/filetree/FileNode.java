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

package org.proninyaroslav.libretorrent.core.model.filetree;

/*
 * The interface with basic functions for a file object.
 */

import androidx.annotation.NonNull;

import java.io.Serializable;

public interface FileNode<F> extends Comparable<F>
{
    class Type implements Serializable
    {
        public static int DIR = 0;
        public static int FILE = 1;
    }

    String getName();

    void setName(String name);

    int getType();

    void setType(int type);

    @Override
    int compareTo(@NonNull F another);
}
