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

package org.proninyaroslav.libretorrent.ui.feeds;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.core.model.data.entity.FeedChannel;

public class FeedChannelItem extends FeedChannel implements Comparable<FeedChannelItem>
{
    public FeedChannelItem(@NonNull FeedChannel channel)
    {
        super(channel.url, channel.name,
              channel.lastUpdate, channel.autoDownload,
              channel.filter, channel.isRegexFilter, channel.fetchError);

        id = channel.id;
    }

    @Override
    public int hashCode()
    {
        return (int)(id ^ (id >>> 32));
    }

    @Override
    public int compareTo(@NonNull FeedChannelItem o)
    {
        String cmp1 = (TextUtils.isEmpty(name) ? url : name);
        String cmp2 = (TextUtils.isEmpty(o.name) ? o.url : o.name);

        return cmp1.compareTo(cmp2);
    }

    @Override
    public boolean equals(Object o)
    {
        return super.equals(o);
    }

    public boolean equalsContent(Object o)
    {
        if (!(o instanceof FeedChannelItem))
            return false;

        if (o == this)
            return true;

        FeedChannelItem item = (FeedChannelItem)o;

        return id == item.id &&
                url.equals(item.url) &&
                (name == null || name.equals(item.name)) &&
                lastUpdate == item.lastUpdate &&
                autoDownload == item.autoDownload &&
                (filter == null || filter.equals(item.filter)) &&
                isRegexFilter == item.isRegexFilter &&
                (fetchError == null || fetchError.equals(item.fetchError));

   }
}
