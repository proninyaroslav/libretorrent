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

package org.proninyaroslav.libretorrent.ui.feeditems;

import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.core.model.data.entity.FeedItem;

public class FeedItemsListItem extends FeedItem implements Comparable<FeedItemsListItem>
{
    public FeedItemsListItem(@NonNull FeedItem item)
    {
        super(item.id, item.feedId, item.downloadUrl,
              item.articleUrl, item.title, item.pubDate);

        read = item.read;
        fetchDate = item.fetchDate;
    }

    @Override
    public int hashCode()
    {
        return id.hashCode();
    }

    @Override
    public int compareTo(@NonNull FeedItemsListItem o)
    {

        return Long.compare(o.pubDate, pubDate);
    }

    @Override
    public boolean equals(Object o)
    {
        return super.equals(o);
    }

    public boolean equalsContent(Object o)
    {
        if (!(o instanceof FeedItemsListItem))
            return false;

        if (o == this)
            return true;

        FeedItemsListItem item = (FeedItemsListItem) o;

        return id.equals(item.id) &&
                title.equals(item.title) &&
                feedId == item.feedId &&
                (downloadUrl == null || downloadUrl.equals(item.downloadUrl)) &&
                (articleUrl == null || articleUrl.equals(item.articleUrl)) &&
                pubDate == item.pubDate &&
                fetchDate == item.fetchDate &&
                read == item.read;
    }
}
