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

package org.proninyaroslav.libretorrent.core.storage.converter;

import android.text.TextUtils;

import org.libtorrent4j.Priority;
import org.proninyaroslav.libretorrent.core.storage.old.TorrentStorage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.room.TypeConverter;

public class PriorityListConverter
{
    @TypeConverter
    public static List<Priority> toPriorityList(@NonNull String prioritiesStr)
    {
        List<String> numbers = Arrays.asList(prioritiesStr.split(TorrentStorage.Model.FILE_LIST_SEPARATOR));
        int length = numbers.size();
        List<Priority> priorities = new ArrayList<>(length);

        for (int i = 0; i < length; i++) {
            if (TextUtils.isEmpty(numbers.get(i)))
                continue;
            priorities.add(Priority.fromSwig(Integer.valueOf(numbers.get(i))));
        }

        return priorities;
    }

    @NonNull
    @TypeConverter
    public static String fromPriorityList(@NonNull List<Priority> priorities)
    {
        List<Integer> val = new ArrayList<>(priorities.size());
        for (int i = 0; i < priorities.size(); i++)
            val.add(priorities.get(i).swig());

        return TextUtils.join(TorrentStorage.Model.FILE_LIST_SEPARATOR, val);
    }
}
