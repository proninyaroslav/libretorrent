/*
 * Copyright (C) 2016-2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import org.libtorrent4j.Priority;
import org.proninyaroslav.libretorrent.core.storage.converter.PriorityListConverter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

/*
 * The class encapsulates the torrent file and its meta information.
 */

@Entity
public class Torrent implements Parcelable, Comparable<Torrent>
{
    /* Usually torrent SHA-1 hash */
    @NonNull
    @PrimaryKey
    public String id;
    @NonNull
    public String name;
    /* Path to the torrent file (or magnet URI if downloadingMetadata = true) */
    @NonNull
    public String source;
    @NonNull
    public Uri downloadPath;
    /*
     * The index position in array must be
     * equal to the priority position in array
     */
    @NonNull
    @TypeConverters({PriorityListConverter.class})
    public List<Priority> filePriorities;
    public boolean sequentialDownload = false;
    public boolean finished = false;
    public boolean paused = false;
    public boolean downloadingMetadata = false;
    public long dateAdded;
    public String error;

    public Torrent(@NonNull String id,
                   @NonNull String source,
                   @NonNull Uri downloadPath,
                   @NonNull String name,
                   @NonNull List<Priority> filePriorities,
                   long dateAdded)
    {
        this.id = id;
        this.source = source;
        this.name = name;
        this.filePriorities = filePriorities;
        this.downloadPath = downloadPath;
        this.dateAdded = dateAdded;
    }

    @Ignore
    public Torrent(Parcel source)
    {
        id = source.readString();
        this.source = source.readString();
        downloadPath = source.readParcelable(Uri.class.getClassLoader());
        filePriorities = source.readArrayList(Priority.class.getClassLoader());
        name = source.readString();
        sequentialDownload = source.readByte() != 0;
        finished = source.readByte() != 0;
        paused = source.readByte() != 0;
        downloadingMetadata = source.readByte() != 0;
        dateAdded = source.readLong();
        error = source.readString();
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(id);
        dest.writeString(source);
        dest.writeParcelable(downloadPath, flags);
        dest.writeList(filePriorities);
        dest.writeString(name);
        dest.writeByte((byte)(sequentialDownload ? 1 : 0));
        dest.writeByte((byte)(finished ? 1 : 0));
        dest.writeByte((byte)(paused ? 1 : 0));
        dest.writeByte((byte)(downloadingMetadata ? 1 : 0));
        dest.writeLong(dateAdded);
        dest.writeString(error);
    }

    public static final Creator<Torrent> CREATOR =
            new Creator<Torrent>()
            {
                @Override
                public Torrent createFromParcel(Parcel source)
                {
                    return new Torrent(source);
                }

                @Override
                public Torrent[] newArray(int size)
                {
                    return new Torrent[size];
                }
            };

    @Override
    public int compareTo(@NonNull Torrent another)
    {
        return name.compareTo(another.name);
    }

    @Override
    public int hashCode()
    {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof Torrent && (o == this || id.equals(((Torrent) o).id));
    }

    @Override
    public String toString()
    {
        return "Torrent{" +
                "id='" + id + '\'' +
                ", source='" + source + '\'' +
                ", downloadPath='" + downloadPath + '\'' +
                ", filePriorities=" + filePriorities +
                ", name='" + name + '\'' +
                ", sequentialDownload=" + sequentialDownload +
                ", finished=" + finished +
                ", paused=" + paused +
                ", downloadingMetadata=" + downloadingMetadata +
                ", dateAdded=" + SimpleDateFormat.getDateTimeInstance().format(new Date(dateAdded)) +
                ", error=" + error +
                '}';
    }
}
