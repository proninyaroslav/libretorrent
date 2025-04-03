/*
 * Copyright (C) 2020-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model.session;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.List;

class FakeIPFilter implements IPFilter {
    private final ArrayList<Pair<String, String>> ranges = new ArrayList<>();

    @Override
    public void addRange(@NonNull String first, @NonNull String last) {
        ranges.add(Pair.create(first, last));
    }

    List<Pair<String, String>> getRanges() {
        return ranges;
    }
}
