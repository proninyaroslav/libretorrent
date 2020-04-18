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

package org.proninyaroslav.libretorrent.core.model.data.entity;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.text.SimpleDateFormat;
import java.util.Date;

/*
 * The class encapsulates the feed channel data.
 */

@Entity
public class FeedChannel implements Parcelable
{
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String url;
    public String name;
    public long lastUpdate;
    public boolean autoDownload = false;
    /* One filter per line */
    public String filter;
    public boolean isRegexFilter = false;
    public String fetchError;

    public FeedChannel(@NonNull String url, String name,
                       long lastUpdate, boolean autoDownload,
                       String filter, boolean isRegexFilter,
                       String fetchError)
    {
        this.url = url;
        this.name = name;
        this.lastUpdate = lastUpdate;
        this.autoDownload = autoDownload;
        this.filter = filter;
        this.isRegexFilter = isRegexFilter;
        this.fetchError = fetchError;
    }

    @Ignore
    public FeedChannel(@NonNull String url)
    {
        this.url = url;
    }

    @Ignore
    public FeedChannel(Parcel source)
    {
        id = source.readLong();
        url = source.readString();
        name = source.readString();
        lastUpdate = source.readLong();
        autoDownload = source.readByte() != 0;
        filter = source.readString();
        isRegexFilter = source.readByte() != 0;
        fetchError = source.readString();
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
        dest.writeString(url);
        dest.writeString(name);
        dest.writeLong(lastUpdate);
        dest.writeByte((byte)(autoDownload ? 1 : 0));
        dest.writeString(filter);
        dest.writeByte((byte)(isRegexFilter ? 1 : 0));
        dest.writeString(fetchError);
    }

    public static final Creator<FeedChannel> CREATOR =
            new Creator<FeedChannel>()
            {
                @Override
                public FeedChannel createFromParcel(Parcel source)
                {
                    return new FeedChannel(source);
                }

                @Override
                public FeedChannel[] newArray(int size)
                {
                    return new FeedChannel[size];
                }
            };

    @Override
    public int hashCode()
    {
        return (int)(id ^ (id >>> 32));
    }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof FeedChannel && (o == this || id == ((FeedChannel)o).id);
    }

    @Override
    public String toString()
    {
        return "FeedChannel{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", lastUpdate=" + SimpleDateFormat.getDateTimeInstance().format(new Date(lastUpdate)) +
                ", autoDownload=" + autoDownload +
                ", filter='" + filter + '\'' +
                ", isRegexFilter=" + isRegexFilter +
                ", fetchError='" + fetchError + '\'' +
                '}';
    }
}
