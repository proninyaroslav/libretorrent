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

package org.proninyaroslav.libretorrent.core.filter;

import org.proninyaroslav.libretorrent.core.model.data.TorrentStateCode;
import org.proninyaroslav.libretorrent.core.utils.DateUtils;

public class TorrentFilterCollection
{
    public static TorrentFilter all()
    {
        return (state) -> true;
    }

    public static TorrentFilter statusDownloading()
    {
        return (state) -> state.stateCode == TorrentStateCode.DOWNLOADING;
    }

    public static TorrentFilter statusDownloaded()
    {
        return (state) -> state.stateCode == TorrentStateCode.SEEDING || state.receivedBytes == state.totalBytes;
    }

    public static TorrentFilter statusDownloadingMetadata()
    {
        return (state) -> state.stateCode == TorrentStateCode.DOWNLOADING_METADATA;
    }

    public static TorrentFilter statusError()
    {
        return (state) -> state.error != null;
    }

    public static TorrentFilter dateAddedToday()
    {
        return (state) -> {
            long dateAdded = state.dateAdded;
            long timeMillis = System.currentTimeMillis();

            return dateAdded >= DateUtils.startOfToday(timeMillis) &&
                    dateAdded <= DateUtils.endOfToday(timeMillis);
        };
    }

    public static TorrentFilter dateAddedYesterday()
    {
        return (state) -> {
            long dateAdded = state.dateAdded;
            long timeMillis = System.currentTimeMillis();

            return dateAdded >= DateUtils.startOfYesterday(timeMillis) &&
                    dateAdded <= DateUtils.endOfYesterday(timeMillis);
        };
    }

    public static TorrentFilter dateAddedWeek()
    {
        return (state) -> {
            long dateAdded = state.dateAdded;
            long timeMillis = System.currentTimeMillis();

            return dateAdded >= DateUtils.startOfWeek(timeMillis) &&
                    dateAdded <= DateUtils.endOfWeek(timeMillis);
        };
    }

    public static TorrentFilter dateAddedMonth()
    {
        return (state) -> {
            long dateAdded = state.dateAdded;
            long timeMillis = System.currentTimeMillis();

            return dateAdded >= DateUtils.startOfMonth(timeMillis) &&
                    dateAdded <= DateUtils.endOfMonth(timeMillis);
        };
    }

    public static TorrentFilter dateAddedYear()
    {
        return (state) -> {
            long dateAdded = state.dateAdded;
            long timeMillis = System.currentTimeMillis();

            return dateAdded >= DateUtils.startOfYear(timeMillis) &&
                    dateAdded <= DateUtils.endOfYear(timeMillis);
        };
    }
}
