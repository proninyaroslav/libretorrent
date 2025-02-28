/*
 * Copyright (C) 2017-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.proninyaroslav.libretorrent.core.model.data.Priority;

import java.io.Serializable;

public class FilePriority implements Serializable {
    private final int priority;
    private final Type type;

    public enum Type {MIXED, IGNORE, NORMAL, HIGH}

    public FilePriority(Priority priority) {
        this.priority = priority.value();
        this.type = typeFrom(priority);
    }

    public FilePriority(Type type) {
        this.priority = priorityFrom(type);
        this.type = type;
    }

    public Priority getPriority() {
        return Priority.fromValue(priority);
    }

    public Type getType() {
        return type;
    }

    public static Type typeFrom(Priority priority) {
        return switch (priority) {
            case IGNORE -> Type.IGNORE;
            case LOW, TWO, THREE, DEFAULT, FIVE, SIX -> Type.NORMAL;
            case TOP_PRIORITY -> Type.HIGH;
        };
    }

    private static int priorityFrom(Type type) {
        return switch (type) {
            case IGNORE -> Priority.IGNORE.value();
            case NORMAL -> Priority.DEFAULT.value();
            case HIGH -> Priority.TOP_PRIORITY.value();
            default -> -1;
        };
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof FilePriority filePriority)) {
            return false;
        }

        if (o == this) {
            return true;
        }

        return priority == filePriority.priority && type.equals(filePriority.getType());
    }

    @NonNull
    @Override
    public String toString() {
        return "FilePriority{" +
                "priority=" + priority +
                ", type=" + type +
                '}';
    }
}