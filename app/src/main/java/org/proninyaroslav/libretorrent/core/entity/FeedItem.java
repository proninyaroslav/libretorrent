/*
 * Copyright (C) 2018, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.entity;

import android.os.Parcel;
import android.os.Parcelable;

import java.text.SimpleDateFormat;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

/*
 * Representation of the feed article.
 */

@Entity(indices = {@Index(value = "feedUrl")},
        foreignKeys = @ForeignKey(
                entity = FeedChannel.class,
                parentColumns = "url",
                childColumns = "feedUrl",
                onDelete = CASCADE))

public class FeedItem implements Parcelable
{
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String title;
    @NonNull
    public String feedUrl;
    public String downloadUrl;
    public String articleUrl;
    public long pubDate;
    public long fetchDate;
    public boolean read = false;

    public FeedItem(@NonNull String feedUrl, String downloadUrl,
                    String articleUrl, @NonNull String title,
                    long pubDate)
    {
        this.feedUrl = feedUrl;
        this.downloadUrl = downloadUrl;
        this.articleUrl = articleUrl;
        this.title = title;
        this.pubDate = pubDate;
    }

    @Ignore
    public FeedItem(Parcel source)
    {
        id = source.readLong();
        feedUrl = source.readString();
        downloadUrl = source.readString();
        articleUrl = source.readString();
        title = source.readString();
        pubDate = source.readLong();
        fetchDate = source.readLong();
        read = source.readByte() != 0;
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeLong(id);
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
                "id=" + id +
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
