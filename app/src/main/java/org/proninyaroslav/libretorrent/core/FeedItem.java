/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core;

import android.os.Parcel;
import android.os.Parcelable;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;

/*
 * Representation of the feed article.
 */

public class FeedItem implements Parcelable
{
    private String feedUrl;
    private String downloadUrl;
    private String articleUrl;
    private String title;
    private long pubDate;
    private long fetchDate;
    private boolean read = false;

    public FeedItem(String feedUrl, String downloadUrl, String articleUrl, String title, long pubDate)
    {
        this.feedUrl = feedUrl;
        this.downloadUrl = downloadUrl;
        this.articleUrl = articleUrl;
        this.title = title;
        this.pubDate = pubDate;
    }

    public FeedItem(Parcel source)
    {
        feedUrl = source.readString();
        downloadUrl = source.readString();
        articleUrl = source.readString();
        title = source.readString();
        pubDate = source.readLong();
        fetchDate = source.readLong();
        read = source.readByte() != 0;
    }

    public String getFeedUrl()
    {
        return feedUrl;
    }

    public String getDownloadUrl()
    {
        return downloadUrl;
    }

    public String getTitle()
    {
        return title;
    }

    public long getPubDate()
    {
        return pubDate;
    }

    public boolean isRead()
    {
        return read;
    }

    public void setRead(boolean read)
    {
        this.read = read;
    }

    public long getFetchDate()
    {
        return fetchDate;
    }

    public void setFetchDate(long fetchDate)
    {
        this.fetchDate = fetchDate;
    }

    public String getArticleUrl()
    {
        return articleUrl;
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(feedUrl);
        dest.writeString(downloadUrl);
        dest.writeString(articleUrl);
        dest.writeString(title);
        dest.writeLong(pubDate);
        dest.writeLong(fetchDate);
        dest.writeInt((byte)(read ? 1 : 0));
    }

    public static final Creator<FeedItem> CREATOR =
            new Creator<FeedItem>()
            {
                @Override
                public FeedItem createFromParcel(Parcel source)
                {
                    return new FeedItem(source);
                }

                @Override
                public FeedItem[] newArray(int size)
                {
                    return new FeedItem[size];
                }
            };

    @Override
    public int hashCode() { return title.hashCode(); }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof FeedItem && (o == this || title.equals(((FeedItem)o).title));
    }

    @Override
    public String toString()
    {
        return "FeedItem{" +
                "feedUrl='" + feedUrl + '\'' +
                ", title='" + title + '\'' +
                ", downloadUrl='" + downloadUrl + '\'' +
                ", articleUrl='" + articleUrl + '\'' +
                ", pubDate=" + SimpleDateFormat.getDateTimeInstance().format(new Date(pubDate)) +
                ", fetchDate=" + SimpleDateFormat.getDateTimeInstance().format(new Date(fetchDate)) +
                ", read=" + read +
                '}';
    }
}
