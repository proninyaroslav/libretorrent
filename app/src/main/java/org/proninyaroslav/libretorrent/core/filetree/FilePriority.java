/*
 * Copyright (C) 2017 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.filetree;

import com.frostwire.jlibtorrent.Priority;

import java.io.Serializable;

public class FilePriority implements Serializable
{
    private int priority;
    private Type type;

    public enum Type {MIXED, IGNORE, NORMAL, HIGH}

    public FilePriority(Priority priority)
    {
        this.priority = priority.swig();
        this.type = typeFrom(priority);
    }

    public FilePriority(Type type)
    {
        this.priority = priorityFrom(type);
        this.type = type;
    }

    public Priority getPriority()
    {
        return Priority.fromSwig(priority);
    }

    public Type getType()
    {
        return type;
    }

    public static Type typeFrom(Priority priority)
    {
        switch (priority) {
            case IGNORE:
                return Type.IGNORE;
            case NORMAL:
            case TWO:
            case THREE:
            case FOUR:
            case FIVE:
            case SIX:
                return Type.NORMAL;
            case SEVEN:
                return Type.HIGH;
            default:
                return null;
        }
    }

    private static int priorityFrom(Type type)
    {
        switch (type) {
            case IGNORE:
                return Priority.IGNORE.swig();
            case NORMAL:
                return Priority.NORMAL.swig();
            case HIGH:
                return Priority.SEVEN.swig();
            default:
                return -1;
        }
    }
}