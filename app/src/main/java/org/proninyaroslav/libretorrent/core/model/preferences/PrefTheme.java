/*
 * Copyright (C) 2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model.preferences;

public abstract sealed class PrefTheme permits PrefTheme.Black, PrefTheme.Dark,
        PrefTheme.Light, PrefTheme.System, PrefTheme.Unknown {
    protected final int id;

    public int getId() {
        return id;
    }

    protected PrefTheme(int id) {
        this.id = id;
    }

    public static final class Unknown extends PrefTheme {
        public Unknown() {
            super(-1);
        }
    }

    public static final class System extends PrefTheme {
        public System() {
            super(0);
        }
    }

    public static final class Light extends PrefTheme {
        public Light() {
            super(1);
        }
    }

    public static final class Dark extends PrefTheme {
        public Dark() {
            super(2);
        }
    }

    public static final class Black extends PrefTheme {
        public Black() {
            super(3);
        }
    }

    public static PrefTheme fromId(int id) {
        switch (id) {
            case 0 -> {
                return new System();
            }
            case 1 -> {
                return new Light();
            }
            case 2 -> {
                return new Dark();
            }
            case 3 -> {
                return new Black();
            }
        }

        return new Unknown();
    }
}
